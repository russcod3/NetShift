package edu.cs312.netshift.measurement

import edu.cs312.netshift.model.MeasurementConfig

/**
 * Runtime limits for a run. Production uses the defaults (profile https20-v1); tests shrink the
 * time values so logic can be exercised quickly. The probe count is always 20.
 */
data class MeasurementProfile(
    val endpoint: String = MeasurementConfig.ENDPOINT,
    val probeSlotMs: Long = MeasurementConfig.PROBE_SLOT_MS,
    val probeTimeoutMs: Long = MeasurementConfig.PROBE_TIMEOUT_MS,
    val acquisitionTimeoutMs: Long = MeasurementConfig.ACQUISITION_TIMEOUT_MS,
    val warmupTimeoutMs: Long = MeasurementConfig.WARMUP_TIMEOUT_MS,
    val downloadTimeoutMs: Long = MeasurementConfig.DOWNLOAD_TIMEOUT_MS,
    val finalizeAllowanceMs: Long = MeasurementConfig.FINALIZE_ALLOWANCE_MS,
    val totalTimeoutMs: Long = MeasurementConfig.TOTAL_TIMEOUT_MS,
    val downloadBytes: Long = MeasurementConfig.DOWNLOAD_BYTES,
    val maxPayloadBytes: Long = MeasurementConfig.MAX_PAYLOAD_BYTES,
)
