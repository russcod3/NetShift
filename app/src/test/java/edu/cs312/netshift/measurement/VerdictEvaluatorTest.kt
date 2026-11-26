package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.QualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictEvaluatorTest {
    private data class Limits(
        val download: Double,
        val latency: Double,
        val jitter: Double,
        val failures: Int,
    )

    private data class Case(
        val activity: ActivityType,
        val good: Limits,
        val fair: Limits,
    )

    private val cases = listOf(
        Case(ActivityType.VIDEO_CALL, Limits(3.0, 150.0, 30.0, 0), Limits(1.5, 300.0, 60.0, 1)),
        Case(ActivityType.HD_STREAMING, Limits(5.0, 250.0, 50.0, 1), Limits(2.5, 500.0, 100.0, 2)),
        Case(ActivityType.BROWSING, Limits(1.0, 500.0, 100.0, 1), Limits(0.5, 1_000.0, 200.0, 3)),
        Case(ActivityType.ONLINE_GAMING, Limits(1.0, 80.0, 20.0, 0), Limits(1.0, 150.0, 40.0, 1)),
    )

    private fun reliable(limits: Limits) = VerdictInput(
        completed = true,
        probeCountPlanned = 20,
        probeCountAttempted = 20,
        probeCountSucceeded = 20 - limits.failures,
        probeCountFailed = limits.failures,
        jitterPairCount = 19 - limits.failures,
        latencyMedianMs = limits.latency,
        jitterMs = limits.jitter,
        downloadBodyDurationMs = 1_000.0,
        downloadMbps = limits.download,
    )

    private fun activityVerdict(input: VerdictInput, activity: ActivityType) =
        VerdictEvaluator.evaluate(input).activities.single { it.activity == activity }

    private fun LimitingMetric.failsSameInBothTiers(good: Limits, fair: Limits): Boolean = when (this) {
        LimitingMetric.DOWNLOAD_SPEED -> good.download == fair.download
        LimitingMetric.RESPONSE_TIME -> good.latency == fair.latency
        LimitingMetric.JITTER -> good.jitter == fair.jitter
        LimitingMetric.PROBE_FAILURES -> good.failures == fair.failures
    }

    @Test fun exactGoodThresholdsPassForEveryActivity() {
        cases.forEach { case ->
            assertEquals(case.activity.name, VerdictLevel.GOOD, activityVerdict(reliable(case.good), case.activity).level)
        }
    }

    @Test fun crossingEachGoodThresholdByTheSmallestAmountDropsToFair() {
        cases.forEach { case ->
            val base = reliable(case.good)
            val justBelowGoodDownload = base.copy(downloadMbps = case.good.download - 0.01)
            val justAboveGoodLatency = base.copy(latencyMedianMs = case.good.latency + 0.01)
            val justAboveGoodJitter = base.copy(jitterMs = case.good.jitter + 0.01)
            val oneMoreFailedProbe = base.copy(
                probeCountSucceeded = 19 - case.good.failures,
                probeCountFailed = case.good.failures + 1,
                jitterPairCount = 18 - case.good.failures,
            )
            listOf(
                justBelowGoodDownload to LimitingMetric.DOWNLOAD_SPEED,
                justAboveGoodLatency to LimitingMetric.RESPONSE_TIME,
                justAboveGoodJitter to LimitingMetric.JITTER,
                oneMoreFailedProbe to LimitingMetric.PROBE_FAILURES,
            ).forEach { (input, metric) ->
                val verdict = activityVerdict(input, case.activity)
                val expected = if (metric.failsSameInBothTiers(case.good, case.fair)) {
                    VerdictLevel.POOR
                } else {
                    VerdictLevel.FAIR
                }
                assertEquals("${case.activity} $metric", expected, verdict.level)
                assertEquals("${case.activity} $metric", metric, verdict.limitingMetric)
            }
        }
    }

    @Test fun exactFairThresholdsAreFairForEveryActivity() {
        cases.forEach { case ->
            assertEquals(case.activity.name, VerdictLevel.FAIR, activityVerdict(reliable(case.fair), case.activity).level)
        }
    }

    @Test fun crossingEachFairThresholdByTheSmallestAmountDropsToPoor() {
        cases.forEach { case ->
            val base = reliable(case.fair)
            val belowFairDownload = base.copy(downloadMbps = case.fair.download - 0.01)
            val aboveFairLatency = base.copy(latencyMedianMs = case.fair.latency + 0.01)
            val aboveFairJitter = base.copy(jitterMs = case.fair.jitter + 0.01)
            val oneMoreFailedProbe = base.copy(
                probeCountSucceeded = 19 - case.fair.failures,
                probeCountFailed = case.fair.failures + 1,
                jitterPairCount = 18 - case.fair.failures,
            )
            listOf(
                belowFairDownload to LimitingMetric.DOWNLOAD_SPEED,
                aboveFairLatency to LimitingMetric.RESPONSE_TIME,
                aboveFairJitter to LimitingMetric.JITTER,
                oneMoreFailedProbe to LimitingMetric.PROBE_FAILURES,
            ).forEach { (input, metric) ->
                val verdict = activityVerdict(input, case.activity)
                assertEquals("${case.activity} $metric", VerdictLevel.POOR, verdict.level)
                assertEquals("${case.activity} $metric", metric, verdict.limitingMetric)
            }
        }
    }

    @Test fun incompleteRunReturnsUnknownEvenWhenNumbersLookGood() {
        val verdicts = VerdictEvaluator.evaluate(reliable(cases.first().good).copy(completed = false))
        assertTrue(verdicts.activities.all { it.level == VerdictLevel.UNKNOWN })
        assertTrue(verdicts.activities.all { it.reason.contains("did not complete") })
    }

    @Test fun sparseProbesReturnUnknownInsteadOfPoor() {
        val input = reliable(Limits(10.0, 500.0, 150.0, 15)).copy(
            probeCountSucceeded = 5,
            probeCountFailed = 15,
            qualityFlags = setOf(QualityFlag.SPARSE_PROBES),
        )
        val video = activityVerdict(input, ActivityType.VIDEO_CALL)
        assertEquals(VerdictLevel.UNKNOWN, video.level)
        assertTrue(video.reason.contains("5 of 20"))
        assertTrue(video.summary.contains("Upload not tested"))
    }

    @Test fun tooFewJitterPairsReturnUnknown() {
        val verdicts = VerdictEvaluator.evaluate(reliable(cases.first().good).copy(jitterPairCount = 9))
        assertTrue(verdicts.activities.all { it.level == VerdictLevel.UNKNOWN })
        assertTrue(verdicts.activities.first().reason.contains("9 adjacent probe pairs"))
    }

    @Test fun shortDownloadAndMissingMetricsReturnUnknown() {
        val reliableInput = reliable(cases.first().good)
        assertEquals(
            VerdictLevel.UNKNOWN,
            activityVerdict(reliableInput.copy(downloadBodyDurationMs = 999.99), ActivityType.BROWSING).level,
        )
        assertEquals(
            VerdictLevel.UNKNOWN,
            activityVerdict(reliableInput.copy(qualityFlags = setOf(QualityFlag.SHORT_DOWNLOAD)), ActivityType.BROWSING).level,
        )
        assertEquals(
            VerdictLevel.UNKNOWN,
            activityVerdict(reliableInput.copy(downloadMbps = null), ActivityType.BROWSING).level,
        )
    }

    @Test fun eachActivityHasAPlainSummaryAndGoodResultsIdentifyLimitingMetricAsNone() {
        val results = VerdictEvaluator.evaluate(reliable(Limits(10.0, 10.0, 1.0, 0)))
        assertEquals(4, results.activities.size)
        assertEquals(1, results.rulesVersion)
        results.activities.forEach {
            assertEquals(VerdictLevel.GOOD, it.level)
            assertNull(it.limitingMetric)
            assertNotNull(it.summary)
            assertTrue(it.summary.isNotBlank())
        }
    }
}
