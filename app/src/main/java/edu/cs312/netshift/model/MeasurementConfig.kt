package edu.cs312.netshift.model

/**
 * Shared limits for measurement profile https20-v1. Changing any measurement definition or limit
 * requires a new PROFILE_ID so results from different definitions are never compared.
 */
object MeasurementConfig {
    const val PROFILE_ID = "https20-v1"
    const val ENDPOINT = "https://speed.cloudflare.com/__down"
    const val PROBE_COUNT = 20
    const val PROBE_SLOT_MS = 600L
    const val PROBE_TIMEOUT_MS = 600L
    const val ACQUISITION_TIMEOUT_MS = 3_000L
    const val WARMUP_TIMEOUT_MS = 2_000L
    const val DOWNLOAD_TIMEOUT_MS = 10_000L
    const val FINALIZE_ALLOWANCE_MS = 1_000L
    const val TOTAL_TIMEOUT_MS = 29_000L
    const val SAVE_TIMEOUT_MS = 1_500L
    const val DOWNLOAD_BYTES = 18_000_000L
    const val MAX_PAYLOAD_BYTES = 19_000_000L

    // Confidence cutoffs (project rules, versioned with the profile).
    const val MIN_SUCCESSFUL_PROBES = 16
    const val MIN_JITTER_PAIRS = 10
    const val MIN_DOWNLOAD_BODY_MS = 1_000.0
}
