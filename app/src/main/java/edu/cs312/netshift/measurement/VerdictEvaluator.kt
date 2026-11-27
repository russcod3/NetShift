package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.QualityFlag
import java.util.Locale

/** Inputs copied from a completed measurement; this type has no Android or Room dependency. */
data class VerdictInput(
    val completed: Boolean,
    val probeCountPlanned: Int,
    val probeCountAttempted: Int,
    val probeCountSucceeded: Int,
    val probeCountFailed: Int,
    val jitterPairCount: Int,
    val latencyMedianMs: Double?,
    val jitterMs: Double?,
    val downloadBodyDurationMs: Double?,
    val downloadMbps: Double?,
    val qualityFlags: Set<String> = emptySet(),
)

enum class ActivityType(val key: String, val label: String, val uploadNotTested: Boolean = false) {
    VIDEO_CALL("VIDEO_CALL", "video calls", uploadNotTested = true),
    HD_STREAMING("HD_STREAMING", "HD streaming"),
    BROWSING("BROWSING", "browsing"),
    ONLINE_GAMING("ONLINE_GAMING", "online gaming", uploadNotTested = true),
}

enum class VerdictLevel { GOOD, FAIR, POOR, UNKNOWN }

enum class LimitingMetric { DOWNLOAD_SPEED, RESPONSE_TIME, JITTER, PROBE_FAILURES }

data class ActivityVerdict(
    val activity: ActivityType,
    val level: VerdictLevel,
    val limitingMetric: LimitingMetric?,
    val reason: String,
    val summary: String,
)

data class VerdictSet(
    val rulesVersion: Int,
    val activities: List<ActivityVerdict>,
)

/** Versioned classroom heuristics. They are not provider service guarantees or end-to-end app tests. */
object VerdictRules {
    const val VERSION = 1

    // Download Mbps minimum, median HTTP RTT ms maximum, HTTP jitter ms maximum, failed probes maximum.
    internal data class Limits(
        val downloadMbps: Double,
        val latencyMs: Double,
        val jitterMs: Double,
        val failedProbes: Int,
    )

    internal data class ActivityRule(
        val activity: ActivityType,
        val good: Limits,
        val fair: Limits,
    )

    internal val activities = listOf(
        ActivityRule(
            ActivityType.VIDEO_CALL,
            good = Limits(3.0, 150.0, 30.0, 0),
            fair = Limits(1.5, 300.0, 60.0, 1),
        ),
        ActivityRule(
            ActivityType.HD_STREAMING,
            good = Limits(5.0, 250.0, 50.0, 1),
            fair = Limits(2.5, 500.0, 100.0, 2),
        ),
        ActivityRule(
            ActivityType.BROWSING,
            good = Limits(1.0, 500.0, 100.0, 1),
            fair = Limits(0.5, 1_000.0, 200.0, 3),
        ),
        ActivityRule(
            ActivityType.ONLINE_GAMING,
            good = Limits(1.0, 80.0, 20.0, 0),
            fair = Limits(1.0, 150.0, 40.0, 1),
        ),
    )
}

/** Produces one qualified verdict per activity. Missing or weak evidence always yields UNKNOWN. */
object VerdictEvaluator {
    fun evaluate(input: VerdictInput): VerdictSet {
        val unknownReason = insufficientDataReason(input)
        val verdicts = VerdictRules.activities.map { rule ->
            if (unknownReason != null) {
                verdict(rule.activity, VerdictLevel.UNKNOWN, null, unknownReason)
            } else {
                evaluateActivity(input, rule)
            }
        }
        return VerdictSet(VerdictRules.VERSION, verdicts)
    }

    private fun insufficientDataReason(input: VerdictInput): String? {
        if (!input.completed) return "The test did not complete."
        if (QualityFlag.ENDPOINT_ERROR in input.qualityFlags) return "The test endpoint returned an invalid response."
        if (input.probeCountPlanned != MeasurementConfig.PROBE_COUNT ||
            input.probeCountAttempted != input.probeCountPlanned
        ) return "The test did not finish all ${MeasurementConfig.PROBE_COUNT} probes."
        if (input.probeCountFailed < 0 || input.probeCountSucceeded < 0 ||
            input.probeCountFailed + input.probeCountSucceeded != input.probeCountAttempted
        ) return "The probe results were incomplete."
        if (QualityFlag.SPARSE_PROBES in input.qualityFlags ||
            input.probeCountSucceeded < MeasurementConfig.MIN_SUCCESSFUL_PROBES
        ) return "Only ${input.probeCountSucceeded} of ${input.probeCountPlanned} HTTP probes succeeded; " +
            "at least ${MeasurementConfig.MIN_SUCCESSFUL_PROBES} are needed for a verdict."
        if (input.jitterPairCount < MeasurementConfig.MIN_JITTER_PAIRS) {
            return "There were only ${input.jitterPairCount} adjacent probe pairs; " +
                "at least ${MeasurementConfig.MIN_JITTER_PAIRS} are needed for a verdict."
        }
        if (input.jitterPairCount > input.probeCountSucceeded - 1) return "The probe-pair count was inconsistent."
        val duration = input.downloadBodyDurationMs
        if (QualityFlag.SHORT_DOWNLOAD in input.qualityFlags || duration == null ||
            !duration.isFinite() || duration < MeasurementConfig.MIN_DOWNLOAD_BODY_MS
        ) return "The download sample was shorter than one second."
        if (!input.latencyMedianMs.isValidMetric() || !input.jitterMs.isValidMetric() ||
            !input.downloadMbps.isValidMetric()
        ) return "One or more required measurements were unavailable."
        return null
    }

    private fun evaluateActivity(
        input: VerdictInput,
        rule: VerdictRules.ActivityRule,
    ): ActivityVerdict {
        val failedGood = failedMetrics(input, rule.good)
        if (failedGood.isEmpty()) return verdict(rule.activity, VerdictLevel.GOOD, null, "All Good thresholds passed.")

        val failedFair = failedMetrics(input, rule.fair)
        if (failedFair.isEmpty()) {
            val limiting = failedGood.first()
            return verdict(rule.activity, VerdictLevel.FAIR, limiting, explain(input, limiting, rule.good))
        }
        val limiting = failedFair.first()
        return verdict(rule.activity, VerdictLevel.POOR, limiting, explain(input, limiting, rule.fair))
    }

    private fun failedMetrics(input: VerdictInput, limits: VerdictRules.Limits): List<LimitingMetric> = buildList {
        if (input.downloadMbps!! < limits.downloadMbps) add(LimitingMetric.DOWNLOAD_SPEED)
        if (input.latencyMedianMs!! > limits.latencyMs) add(LimitingMetric.RESPONSE_TIME)
        if (input.jitterMs!! > limits.jitterMs) add(LimitingMetric.JITTER)
        if (input.probeCountFailed > limits.failedProbes) add(LimitingMetric.PROBE_FAILURES)
    }

    private fun explain(
        input: VerdictInput,
        metric: LimitingMetric,
        limits: VerdictRules.Limits,
    ): String = when (metric) {
        LimitingMetric.DOWNLOAD_SPEED ->
            "Download was ${fmt(input.downloadMbps!!)} Mbps; this tier needs at least ${fmt(limits.downloadMbps)} Mbps."
        LimitingMetric.RESPONSE_TIME ->
            "Median HTTP response time was ${fmt(input.latencyMedianMs!!)} ms; this tier allows up to ${fmt(limits.latencyMs)} ms."
        LimitingMetric.JITTER ->
            "HTTP response-time variation was ${fmt(input.jitterMs!!)} ms; this tier allows up to ${fmt(limits.jitterMs)} ms."
        LimitingMetric.PROBE_FAILURES ->
            "${input.probeCountFailed} of ${input.probeCountPlanned} HTTP probes failed; this tier allows ${limits.failedProbes}."
    }

    private fun verdict(
        activity: ActivityType,
        level: VerdictLevel,
        limitingMetric: LimitingMetric?,
        reason: String,
    ): ActivityVerdict {
        val main = when (level) {
            VerdictLevel.GOOD -> "Likely suitable for ${activity.label} on this download check."
            VerdictLevel.FAIR -> "May struggle with ${activity.label}: ${reason.removeSuffix(".")}."
            VerdictLevel.POOR -> "Unlikely to meet the basic ${activity.label} thresholds: ${reason.removeSuffix(".")}."
            VerdictLevel.UNKNOWN -> "Verdict unavailable for ${activity.label}: ${reason}"
        }
        val summary = if (activity.uploadNotTested) "$main Upload not tested." else main
        return ActivityVerdict(activity, level, limitingMetric, reason, summary)
    }

    private fun Double?.isValidMetric(): Boolean {
        val value = this ?: return false
        return value.isFinite() && value >= 0.0
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
