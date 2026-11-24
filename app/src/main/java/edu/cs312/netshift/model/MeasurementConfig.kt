package edu.cs312.netshift.model

/** Shared limits for the first measurement profile; no measurement traffic is launched yet. */
object MeasurementConfig {
    const val PROFILE_ID = "https20-v1"
    const val ENDPOINT = "https://speed.cloudflare.com/__down"
    const val PROBE_COUNT = 20
    const val PROBE_SLOT_MS = 600L
    const val ACQUISITION_TIMEOUT_MS = 3_000L
    const val WARMUP_TIMEOUT_MS = 2_000L
    const val DOWNLOAD_TIMEOUT_MS = 10_000L
    const val TOTAL_TIMEOUT_MS = 29_000L
    const val DOWNLOAD_BYTES = 18_000_000L
    const val MAX_PAYLOAD_BYTES = 19_000_000L
}
