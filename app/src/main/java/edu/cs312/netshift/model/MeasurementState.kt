package edu.cs312.netshift.model

enum class TestTransport(val key: String, val displayName: String) {
    WIFI(TransportKey.WIFI, "Wi-Fi"),
    CELLULAR(TransportKey.CELLULAR, "Mobile data"),
}

/** IDLE -> ACQUIRING -> WARMING_UP -> PROBING -> DOWNLOADING -> SAVING -> one terminal phase. */
enum class MeasurementPhase { IDLE, ACQUIRING, WARMING_UP, PROBING, DOWNLOADING, SAVING, COMPLETE, PARTIAL, FAILED, CANCELLED }

data class MeasurementState(
    val phase: MeasurementPhase = MeasurementPhase.IDLE,
    val testId: String? = null,
    val probesAttempted: Int = 0,
    val probesPlanned: Int = MeasurementConfig.PROBE_COUNT,
    val downloadBytes: Long = 0,
    val failureReason: String? = null,
    /** Non-null when the final row could not be written; call MeasurementEngine.retryUnsaved(). */
    val saveError: String? = null,
) {
    val isTerminal: Boolean
        get() = phase == MeasurementPhase.COMPLETE || phase == MeasurementPhase.PARTIAL ||
            phase == MeasurementPhase.FAILED || phase == MeasurementPhase.CANCELLED
    val isRunning: Boolean get() = phase != MeasurementPhase.IDLE && !isTerminal
}

sealed interface StartResult {
    data class Started(val testId: String) : StartResult
    data class Rejected(val reason: String) : StartResult
}
