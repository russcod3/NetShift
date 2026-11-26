package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.StopReason
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadRunnerTest {
    private lateinit var server: MockWebServer
    private val runner = DownloadRunner(SystemNanoClock)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }
    @After fun resetDiagnostics() { DebugDiagnostics.enabled = false }

    private val endpoint get() = server.url("/__down").toString()
    private fun body(size: Int) = Buffer().write(ByteArray(size))

    private fun download(
        requested: Long,
        planMs: Long = 5_000,
        ceiling: Long = 100_000_000,
    ) = runBlocking {
        runner.run(plainClient(), endpoint, requested, planMs, SystemNanoClock.nowNs() + 30_000_000_000L, PayloadCounter(ceiling))
    }

    @Test fun readsToEofAndCountsActualBytes() {
        server.enqueue(MockResponse().setBody(body(1_000_000)))
        val result = download(requested = 5_000_000)
        assertEquals(StopReason.EOF, result.stopReason)
        assertEquals(1_000_000L, result.bytes)
        assertNotNull(result.bodyDurationMs)
        assertTrue(MetricCalculator.downloadMbps(result.bytes, result.bodyDurationMs!!)!! > 0)
    }

    @Test fun splitsCombinedTargetIntoSequentialRequestsNoLargerThanConfiguredLimit() {
        val requestedSizes = mutableListOf<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val size = request.requestUrl!!.queryParameter("bytes")!!.toLong()
                requestedSizes += size
                return MockResponse().setBody(body(size.toInt()))
            }
        }
        val result = runBlocking {
            runner.run(
                plainClient(), endpoint, bytesRequested = 18_000_000,
                maxRequestBytes = 9_000_000, planTimeoutMs = 10_000,
                deadlineNs = SystemNanoClock.nowNs() + 30_000_000_000L,
                counter = PayloadCounter(18_000_021),
            )
        }
        assertEquals(listOf(9_000_000L, 9_000_000L), requestedSizes)
        assertEquals(18_000_000L, result.bytes)
        assertEquals(StopReason.BYTE_LIMIT, result.stopReason)
    }

    @Test fun stopsExactlyAtTheRequestedByteCount() {
        server.enqueue(MockResponse().setBody(body(300_000)))
        val result = download(requested = 300_000)
        assertEquals(StopReason.BYTE_LIMIT, result.stopReason)
        assertEquals(300_000L, result.bytes)
    }

    @Test fun plannedTimeLimitUsesActualBytesReceived() {
        server.enqueue(MockResponse().setBody(body(2_000_000)).throttleBody(50_000, 100, TimeUnit.MILLISECONDS))
        val result = download(requested = 5_000_000, planMs = 600)
        assertEquals(StopReason.TIME_LIMIT, result.stopReason)
        assertTrue(result.bytes > 0 && result.bytes < 2_000_000)
        assertTrue(result.wallDurationMs < 3_000)
    }

    @Test fun oversizedContentLengthIsRejectedWithoutReadingTheBody() {
        server.enqueue(MockResponse().setBody(body(1_000_000)))
        val result = download(requested = 300_000)
        assertEquals(StopReason.HTTP_ERROR, result.stopReason)
        assertEquals("OVERSIZED", result.detail)
        assertEquals(0L, result.bytes)
        assertNull(result.bodyDurationMs)
    }

    @Test fun nonOkStatusIsAnHttpError() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = download(requested = 100_000)
        assertEquals(StopReason.HTTP_ERROR, result.stopReason)
        assertEquals("HTTP_404", result.detail)
    }

    @Test fun payloadCeilingStopsTheRead() {
        server.enqueue(MockResponse().setBody(body(300_000)))
        val result = download(requested = 300_000, ceiling = 100_000)
        assertEquals("bytes=${result.bytes}, detail=${result.detail}", StopReason.BYTE_LIMIT, result.stopReason)
        assertTrue(result.bytes < 300_000)
    }

    @Test fun forbiddenIsAnHttpErrorWithoutRetryAndWithoutDiagnosticsByDefault() {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-Test", "1").setBody("error code: 1010"))
        val result = download(requested = 10_000_000)
        assertEquals(StopReason.HTTP_ERROR, result.stopReason)
        assertEquals("HTTP_403", result.detail)
        assertEquals(0L, result.bytes)
        assertNull(result.bodyDurationMs)
        assertNull(result.diagnostics) // capture is debug-only and off unless enabled
        assertEquals(1, server.requestCount)
    }

    @Test fun debugDiagnosticsCapturesHeadersAndBodySliceOfAForbiddenResponse() {
        DebugDiagnostics.enabled = true
        server.enqueue(
            MockResponse().setResponseCode(403).setHeader("X-Test", "1")
                .setHeader("Set-Cookie", "secret=abc").setBody("error code: 1010"),
        )
        val text = download(requested = 10_000_000).diagnostics!!
        assertTrue(text, text.contains("HTTP 403"))
        assertTrue(text, text.lowercase().contains("x-test: 1"))
        assertTrue(text, text.contains("error code: 1010"))
        assertTrue(text, !text.contains("secret=abc"))
    }

    @Test fun rateLimitedIsAnHttpErrorAndIsNotRetried() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3500"))
        val result = download(requested = 10_000_000)
        assertEquals(StopReason.HTTP_ERROR, result.stopReason)
        assertEquals("HTTP_429", result.detail)
        assertEquals(0L, result.bytes)
        assertEquals(1, server.requestCount)
    }
}
