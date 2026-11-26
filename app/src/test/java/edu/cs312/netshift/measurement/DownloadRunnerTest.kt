package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.StopReason
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
        assertEquals(StopReason.BYTE_LIMIT, result.stopReason)
        assertTrue(result.bytes < 300_000)
    }
}
