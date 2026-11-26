package edu.cs312.netshift.data

import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.StopReason
import edu.cs312.netshift.model.TestStatus
import edu.cs312.netshift.model.TransportKey
import kotlin.math.abs

/** One metric for both transports. Null means unavailable, never zero. */
data class MetricPair(val wifi: Double?, val cellular: Double?)

/** A side-by-side Wi-Fi vs mobile data comparison for one location, ready for the four paired charts. */
data class LocationComparison(
    val locationKey: String,
    val locationName: String,
    val wifi: TestResultEntity,
    val cellular: TestResultEntity,
    val timeGapMs: Long,
    /** False when no Wi-Fi/cellular pair was within the preferred gap; the UI must show the time difference. */
    val withinPreferredGap: Boolean,
) {
    val latencyMs = MetricPair(wifi.latencyMedianMs, cellular.latencyMedianMs)           // lower is better
    val jitterMs = MetricPair(wifi.jitterMs, cellular.jitterMs)                           // lower is better
    val probeLossPercent = MetricPair(wifi.probeLossPercent, cellular.probeLossPercent)   // lower is better
    val downloadMbps = MetricPair(wifi.downloadMbps, cellular.downloadMbps)               // higher is better
    val latestStartedAtEpochMs: Long = maxOf(wifi.startedAtEpochMs, cellular.startedAtEpochMs)
}

object CompareModel {
    const val PREFERRED_GAP_MS = 15 * 60 * 1_000L

    private val validStops = setOf(StopReason.EOF, StopReason.BYTE_LIMIT, StopReason.TIME_LIMIT)

    /**
     * Automatic-comparison eligibility (PLAN.md 8.2): COMPLETE, a full 20-attempt probe phase with enough
     * successes and adjacent pairs, a download body interval of at least 1 s, and no endpoint/IO/network error.
     * Ineligible rows stay in history; they are only excluded here.
     */
    fun isEligible(t: TestResultEntity): Boolean =
        t.status == TestStatus.COMPLETE &&
            t.failureReason == null &&
            (t.measuredTransport == TransportKey.WIFI || t.measuredTransport == TransportKey.CELLULAR) &&
            t.probeCountAttempted == MeasurementConfig.PROBE_COUNT &&
            t.probeCountSucceeded >= MeasurementConfig.MIN_SUCCESSFUL_PROBES &&
            t.jitterPairCount >= MeasurementConfig.MIN_JITTER_PAIRS &&
            t.latencyMedianMs != null && t.jitterMs != null && t.probeLossPercent != null &&
            t.downloadMbps != null &&
            (t.downloadBodyDurationMs ?: 0.0) >= MeasurementConfig.MIN_DOWNLOAD_BODY_MS &&
            t.downloadStopReason in validStops

    /**
     * For each (normalized location, profile) with both transports, picks the most recent eligible pair
     * within PREFERRED_GAP_MS; if none is that close, the newest of each transport, flagged as not close.
     * Different locations or profiles are never paired.
     */
    fun latestPairs(tests: List<TestResultEntity>): List<LocationComparison> =
        tests.filter(::isEligible)
            .groupBy { it.locationKey to it.profileId }
            .mapNotNull { (_, group) ->
                val wifi = group.filter { it.measuredTransport == TransportKey.WIFI }
                val cellular = group.filter { it.measuredTransport == TransportKey.CELLULAR }
                if (wifi.isEmpty() || cellular.isEmpty()) null else bestPair(wifi, cellular)
            }
            .sortedByDescending { it.latestStartedAtEpochMs }

    private fun bestPair(wifi: List<TestResultEntity>, cellular: List<TestResultEntity>): LocationComparison {
        val close = wifi.flatMap { w -> cellular.map { c -> w to c } }
            .filter { (w, c) -> abs(w.startedAtEpochMs - c.startedAtEpochMs) <= PREFERRED_GAP_MS }
        val (w, c) = close.maxByOrNull { (w, c) -> maxOf(w.startedAtEpochMs, c.startedAtEpochMs) }
            ?: (wifi.maxBy { it.startedAtEpochMs } to cellular.maxBy { it.startedAtEpochMs })
        val gap = abs(w.startedAtEpochMs - c.startedAtEpochMs)
        val newer = if (w.startedAtEpochMs >= c.startedAtEpochMs) w else c
        return LocationComparison(
            locationKey = newer.locationKey,
            locationName = newer.locationName,
            wifi = w,
            cellular = c,
            timeGapMs = gap,
            withinPreferredGap = gap <= PREFERRED_GAP_MS,
        )
    }
}
