package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.ProbeSample
import kotlin.math.abs

data class ProbeMetrics(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val latencyMedianMs: Double?,
    val jitterMs: Double?,
    val jitterPairCount: Int,
    val probeLossPercent: Double?,
)

object MetricCalculator {
    fun calculate(
        samples: List<ProbeSample>,
        plannedCount: Int = MeasurementConfig.PROBE_COUNT,
    ): ProbeMetrics {
        require(plannedCount > 0)
        require(samples.size <= plannedCount)
        require(samples.all { it.index < plannedCount })
        require(samples.map { it.index }.distinct().size == samples.size) {
            "A probe slot can have only one outcome"
        }

        val ordered = samples.sortedBy { it.index }
        val successfulRtts = ordered.mapNotNull { it.rttMs }.sorted()
        val median = when {
            successfulRtts.isEmpty() -> null
            successfulRtts.size % 2 == 1 -> successfulRtts[successfulRtts.size / 2]
            else -> {
                val middle = successfulRtts.size / 2
                (successfulRtts[middle - 1] + successfulRtts[middle]) / 2.0
            }
        }
        val differences = ordered.zipWithNext().mapNotNull { (previous, current) ->
            val previousRtt = previous.rttMs
            val currentRtt = current.rttMs
            if (current.index == previous.index + 1 && previousRtt != null && currentRtt != null) {
                abs(currentRtt - previousRtt)
            } else {
                null
            }
        }
        val failed = samples.size - successfulRtts.size

        return ProbeMetrics(
            attempted = samples.size,
            succeeded = successfulRtts.size,
            failed = failed,
            latencyMedianMs = median,
            jitterMs = differences.takeIf { it.isNotEmpty() }?.average(),
            jitterPairCount = differences.size,
            probeLossPercent = if (samples.size == plannedCount) {
                100.0 * failed / plannedCount
            } else {
                null
            },
        )
    }

    fun downloadMbps(bytes: Long, durationMs: Double): Double? {
        require(bytes >= 0)
        if (bytes == 0L || !durationMs.isFinite() || durationMs <= 0) return null
        return bytes.toDouble() * 8.0 / durationMs / 1_000.0
    }
}
