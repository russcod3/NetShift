package edu.cs312.netshift.ui.test

import edu.cs312.netshift.model.MeasurementConfig
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState

/** Progress within the current stage, never an invented time estimate or speed. */
fun meterStageProgress(state: MeasurementState): Float? = when (state.phase) {
    MeasurementPhase.PROBING -> if (state.probesPlanned > 0)
        (state.probesAttempted.toFloat() / state.probesPlanned).coerceIn(0f, 1f) else null
    MeasurementPhase.DOWNLOADING -> (state.downloadBytes.toDouble() / MeasurementConfig.DOWNLOAD_BYTES).toFloat().coerceIn(0f, 1f)
    MeasurementPhase.COMPLETE -> 1f
    MeasurementPhase.IDLE -> 0f
    else -> null
}
