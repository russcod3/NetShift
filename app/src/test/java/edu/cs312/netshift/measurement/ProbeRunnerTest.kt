package edu.cs312.netshift.measurement

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProbeRunnerTest {
    private lateinit var server: MockWebServer
    private val runner = ProbeRunner(SystemNanoClock)
    private val farDeadline get() = SystemNanoClock.nowNs() + 60_000_000_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private val endpoint get() = server.url("/__down").toString()

    private suspend fun probes(slotMs: Long = 5, timeoutMs: Long = 500, sink: MutableList<ProbeRecord>) =
        runner.runProbes(plainClient(), endpoint, slotMs, timeoutMs, farDeadline, PayloadCounter(1_000_000)) { sink += it }

    @Test fun twentySuccessfulProbesInOrder() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val records = mutableListOf<ProbeRecord>()
        probes(sink = records)
        assertEquals((0 until 20).toList(), records.map { it.sample.index })
        assertTrue(records.all { it.sample.rttMs != null && it.httpStatus == 200 })
    }

    @Test fun warmUpIsNotCountedAsAProbe() = runBlocking {
        server.dispatcher = fakeEndpoint()
        runner.warmUp(plainClient(), endpoint, 500, PayloadCounter(1_000_000))
        assertEquals(1, server.requestCount)
    }

    @Test fun timedOutSlotsAreFailedSamplesWithNullRtt() = runBlocking {
        repeat(20) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
        val records = mutableListOf<ProbeRecord>()
        probes(slotMs = 50, timeoutMs = 50, sink = records)
        assertEquals(20, records.size)
        assertTrue(records.all { it.sample.rttMs == null && it.sample.failureReason == "TIMEOUT" })
        val metrics = MetricCalculator.calculate(records.map { it.sample })
        assertNull(metrics.latencyMedianMs)
        assertEquals(100.0, metrics.probeLossPercent!!, 0.0001)
    }

    @Test fun cancellationLeavesUnlaunchedSlotsOutOfTheLossCalculation() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val records = CopyOnWriteArrayList<ProbeRecord>()
        val job = launch(Dispatchers.Default) { probes(slotMs = 100, sink = records) }
        while (records.size < 3) delay(5)
        job.cancelAndJoin()
        assertTrue(records.size in 3..19)
        assertTrue(records.all { it.sample.rttMs != null })
        assertNull(MetricCalculator.calculate(records.map { it.sample }).probeLossPercent)
    }

    @Test fun deadlineStopsEarlyWithoutInventingSamples() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val records = mutableListOf<ProbeRecord>()
        runner.runProbes(plainClient(), endpoint, 100, 500, SystemNanoClock.nowNs() + 350_000_000L, PayloadCounter(1_000_000)) { records += it }
        assertTrue(records.size in 1..5)
    }

    private fun expectEndpointError(expected: String) = runBlocking {
        try {
            runner.warmUp(plainClient(), endpoint, 500, PayloadCounter(1_000_000))
            fail("Expected EndpointException($expected)")
        } catch (e: EndpointException) {
            assertEquals(expected, e.category)
        }
    }

    @Test fun nonOkStatusIsAnEndpointErrorNotLoss() {
        server.enqueue(MockResponse().setResponseCode(429))
        expectEndpointError("HTTP_429")
    }

    @Test fun redirectIsRejected() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/portal"))
        expectEndpointError("REDIRECT")
    }

    @Test fun wrongBodySizeIsRejected() {
        server.enqueue(MockResponse().setBody("ab"))
        expectEndpointError("WRONG_BODY_SIZE")
    }

    @Test fun compressedResponseIsRejected() {
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(Buffer().write(ByteArray(1))))
        expectEndpointError("COMPRESSED")
    }

    @Test fun probeRequestsCarryNonceAndIdentityEncoding() = runBlocking {
        server.dispatcher = fakeEndpoint()
        runner.warmUp(plainClient(), endpoint, 500, PayloadCounter(1_000_000))
        val request = server.takeRequest()
        assertEquals("1", request.requestUrl!!.queryParameter("bytes"))
        assertTrue(request.requestUrl!!.queryParameter("netshiftNonce")!!.isNotBlank())
        assertEquals("identity", request.getHeader("Accept-Encoding"))
    }
}
