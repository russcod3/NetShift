package edu.cs312.netshift.ui.test

import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import org.junit.Assert.*
import org.junit.Test

class MeterProgressTest {
    @Test fun connectionSetupHasNoFabricatedPercentage() {
        assertNull(meterStageProgress(MeasurementState(phase = MeasurementPhase.ACQUIRING)))
        assertNull(meterStageProgress(MeasurementState(phase = MeasurementPhase.WARMING_UP)))
    }
    @Test fun probesUseActualCompletedCount() {
        assertEquals(.35f, meterStageProgress(MeasurementState(phase = MeasurementPhase.PROBING, probesAttempted = 7))!!, .0001f)
        assertNull(meterStageProgress(MeasurementState(phase = MeasurementPhase.PROBING, probesPlanned = 0)))
    }
    @Test fun downloadUsesReceivedBytesNotTime() {
        assertEquals(.5f, meterStageProgress(MeasurementState(phase = MeasurementPhase.DOWNLOADING, downloadBytes = 9_000_000))!!, .0001f)
        assertEquals(1f, meterStageProgress(MeasurementState(phase = MeasurementPhase.DOWNLOADING, downloadBytes = Long.MAX_VALUE))!!, .0001f)
    }
    @Test fun unsuccessfulTerminalsDoNotClaimCompletion() {
        listOf(MeasurementPhase.CANCELLED, MeasurementPhase.PARTIAL, MeasurementPhase.FAILED).forEach {
            assertNull(meterStageProgress(MeasurementState(phase = it)))
        }
    }
}
