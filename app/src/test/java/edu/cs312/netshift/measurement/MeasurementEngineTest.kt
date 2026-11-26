package edu.cs312.netshift.measurement

import edu.cs312.netshift.data.CompareModel
import edu.cs312.netshift.data.TestRepository
import edu.cs312.netshift.model.FailureReason
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.model.StartResult
import edu.cs312.netshift.model.TestTransport
import edu.cs312.netshift.platform.LeaseResult
import edu.cs312.netshift.platform.NetworkLeaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeasurementEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: FakeTestResultDao

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        dao = FakeTestResultDao()
    }

    @After fun tearDown() { server.shutdown() }
    @After fun resetDebugDiagnostics() { DebugDiagnostics.enabled = false }

    private fun profile(slotMs: Long = 5, totalMs: Long = 20_000) = MeasurementProfile(
        endpoint = server.url("/__down").toString(),
        probeSlotMs = slotMs,
        probeTimeoutMs = 500,
        downloadBytes = 500_000,
        downloadTimeoutMs = 2_000,
        finalizeAllowanceMs = 100,
        totalTimeoutMs = totalMs,
    )

    private fun engine(provider: NetworkLeaseProvider, profile: MeasurementProfile = profile()) = MeasurementEngine(
        testRepository = TestRepository(dao),
        leaseProvider = provider,
        clock = SystemNanoClock,
        profile = profile,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private fun acquired(lease: FakeLease = FakeLease()) = FakeLeaseProvider(LeaseResult.Acquired(lease))

    private suspend fun MeasurementEngine.startOk(transport: TestTransport = TestTransport.WIFI, name: String = "Library"): String =
        (start(transport, name) as StartResult.Started).testId

    private suspend fun MeasurementEngine.awaitTerminal(id: String): MeasurementState =
        withTimeout(15_000) { state.first { it.testId == id && it.isTerminal } }

    private suspend fun awaitSavedRow(id: String) = withTimeout(5_000) {
        while (dao.rows[id]?.status == "RUNNING") delay(10)
        dao.rows.getValue(id)
    }

    @Test fun happyPathCompletesAndSavesEveryMetric() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val engine = engine(acquired())
        val id = engine.startOk(name = "  Library ")
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.COMPLETE, state.phase)
        assertNull(state.saveError)

        val row = dao.rows.getValue(id)
        assertEquals("COMPLETE", row.status)
        assertNull(row.failureReason)
        assertEquals("Library", row.locationName)
        assertEquals("WIFI", row.measuredTransport)
        assertEquals(20, row.probeCountAttempted)
        assertEquals(20, row.probeCountSucceeded)
        assertEquals(0.0, row.probeLossPercent!!, 0.0)
        assertNotNull(row.latencyMedianMs)
        assertNotNull(row.downloadMbps)
        assertEquals(200_000L, row.downloadBytes)
        assertTrue(row.qualityFlagsJson.contains("SHORT_DOWNLOAD"))
        assertTrue(row.probeSamplesJson.contains("\"ok\":true"))
        assertEquals(1, row.verdictRulesVersion)
        val verdictJson = JSONObject(row.activityVerdictsJson)
        assertEquals(1, verdictJson.getInt("version"))
        assertEquals(4, verdictJson.getJSONArray("activities").length())
        assertEquals(
            "UNKNOWN",
            verdictJson.getJSONArray("activities").getJSONObject(0).getString("status"),
        ) // the short download prevents a confident activity verdict
        // 1 untimed warm-up + 20 timed probes + 1 download; the warm-up is not a sample.
        assertEquals(22, server.requestCount)
    }

    @Test fun endpointErrorFailsTheRunWithoutHeadlineMetrics() = runBlocking {
        DebugDiagnostics.enabled = true
        server.dispatcher = fakeEndpoint(status = 429)
        val engine = engine(acquired())
        val id = engine.startOk()
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.FAILED, state.phase)
        val row = dao.rows.getValue(id)
        assertEquals(FailureReason.ENDPOINT_ERROR, row.failureReason)
        assertNull(row.probeLossPercent)
        assertNull(row.latencyMedianMs)
        assertEquals(1, server.requestCount)
        val diagnostic = engine.lastDiagnostics.value!!
        assertTrue(diagnostic.contains("phase=WARMING_UP"))
        assertTrue(diagnostic.contains("request=warm-up"))
        assertTrue(diagnostic.contains("exception=EndpointException"))
        assertTrue(diagnostic.contains("httpStatus=429"))
    }

    @Test fun missingRouteFailsPromptlyWithoutAnyHttp() = runBlocking {
        val engine = engine(FakeLeaseProvider(LeaseResult.Failed(FailureReason.NETWORK_UNAVAILABLE)))
        val id = engine.startOk(TestTransport.CELLULAR)
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.FAILED, state.phase)
        assertEquals(FailureReason.NETWORK_UNAVAILABLE, dao.rows.getValue(id).failureReason)
        assertEquals(0, server.requestCount)
    }

    @Test fun acquiredMobileNetworkWithNoWorkingInternetIsEndpointUnreachable() = runBlocking {
        DebugDiagnostics.enabled = true
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val engine = engine(acquired(), profile().copy(warmupTimeoutMs = 300))
        val id = engine.startOk(TestTransport.CELLULAR)
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.FAILED, state.phase)
        assertEquals(FailureReason.ENDPOINT_UNREACHABLE, dao.rows.getValue(id).failureReason)
        assertEquals(1, server.requestCount)
        val diagnostic = engine.lastDiagnostics.value!!
        assertTrue(diagnostic.contains("phase=WARMING_UP"))
        assertTrue(diagnostic.contains("request=warm-up"))
        assertTrue(diagnostic.contains("exception="))
        assertTrue(diagnostic.contains("message="))
        assertTrue(diagnostic.contains("httpStatus=none"))
    }

    @Test fun cancelKeepsPartialEvidenceAndNoHeadlineLoss() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val lease = FakeLease()
        val engine = engine(acquired(lease), profile(slotMs = 100))
        val id = engine.startOk()
        withTimeout(10_000) { engine.state.first { it.probesAttempted >= 3 } }
        engine.cancel()
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.CANCELLED, state.phase)
        val row = dao.rows.getValue(id)
        assertEquals("CANCELLED", row.status)
        assertTrue(row.probeCountAttempted in 3..19)
        assertNull(row.probeLossPercent)
        assertTrue(lease.released)
    }

    @Test fun secondStartIsRejectedWhileRunning() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val engine = engine(acquired(), profile(slotMs = 100))
        val id = engine.startOk()
        val second = engine.start(TestTransport.WIFI, "Other")
        assertEquals(StartResult.Rejected(FailureReason.ALREADY_RUNNING), second)
        assertEquals(1, dao.rows.size)
        engine.cancel()
        engine.awaitTerminal(id)
        assertEquals(1, dao.rows.size)
    }

    @Test fun lostSelectedNetworkSavesPartialNetworkChanged() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val lease = FakeLease()
        val engine = engine(acquired(lease), profile(slotMs = 100))
        val id = engine.startOk()
        withTimeout(10_000) { engine.state.first { it.probesAttempted >= 2 } }
        lease.interrupt()
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.PARTIAL, state.phase)
        val row = dao.rows.getValue(id)
        assertEquals(FailureReason.NETWORK_CHANGED, row.failureReason)
        assertNull(row.probeLossPercent)
        assertTrue(lease.released)
    }

    @Test fun globalDeadlinePublishesTerminalStatePromptly() = runBlocking {
        server.dispatcher = fakeEndpoint()
        val engine = engine(acquired(), profile(slotMs = 100, totalMs = 800))
        val startedNs = System.nanoTime()
        val id = engine.startOk()
        val state = engine.awaitTerminal(id)
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
        assertTrue("terminal state took $elapsedMs ms", elapsedMs < 1_500)
        assertEquals(MeasurementPhase.PARTIAL, state.phase)
        assertEquals(FailureReason.DEADLINE, state.failureReason)
        val row = awaitSavedRow(id)
        assertEquals("PARTIAL", row.status)
        assertTrue(row.probeCountAttempted < 20)
        assertNull(row.probeLossPercent)
    }

    @Test fun forbiddenDownloadSavesPartialKeepsProbeMetricsAndIsNeverRetried() = runBlocking {
        DebugDiagnostics.enabled = true
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val bytes = request.requestUrl?.queryParameter("bytes")?.toIntOrNull() ?: 0
                return if (bytes <= 1) MockResponse().setBody(Buffer().write(ByteArray(bytes)))
                else MockResponse().setResponseCode(403).setBody("error code: 1010")
            }
        }
        val engine = engine(acquired())
        val id = engine.startOk()
        val state = engine.awaitTerminal(id)
        assertEquals(MeasurementPhase.PARTIAL, state.phase)
        val row = dao.rows.getValue(id)
        assertEquals("PARTIAL", row.status)
        assertEquals(FailureReason.ENDPOINT_ERROR, row.failureReason)
        assertEquals(20, row.probeCountSucceeded)
        assertEquals(0.0, row.probeLossPercent!!, 0.0)
        assertEquals(0L, row.downloadBytes)
        assertNull(row.downloadMbps)
        assertEquals("HTTP_ERROR", row.downloadStopReason)
        assertTrue(row.qualityFlagsJson.contains("ENDPOINT_DETAIL:HTTP_403"))
        val diagnostic = engine.lastDiagnostics.value!!
        assertTrue(diagnostic.contains("phase=DOWNLOADING"))
        assertTrue(diagnostic.contains("request=download[1]"))
        assertTrue(diagnostic.contains("httpStatus=403"))
        assertTrue(!CompareModel.isEligible(row))
        assertEquals(22, server.requestCount) // warm-up + 20 probes + exactly one download attempt
    }
}
