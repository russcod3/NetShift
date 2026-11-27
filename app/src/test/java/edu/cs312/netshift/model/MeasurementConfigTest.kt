package edu.cs312.netshift.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementConfigTest {
    @Test fun worstCasePayloadStaysUnderTheTwentyMegabyteBudget() {
        val worstCase = MeasurementConfig.DOWNLOAD_BYTES + 21 // warm-up + 20 probes, 1 byte each
        assertTrue(worstCase <= MeasurementConfig.MAX_PAYLOAD_BYTES)
        assertTrue(MeasurementConfig.MAX_PAYLOAD_BYTES < 20_000_000L)
        assertTrue(MeasurementConfig.DOWNLOAD_REQUEST_BYTES <= 10_000_000L)
        assertEquals(2L, (MeasurementConfig.DOWNLOAD_BYTES + MeasurementConfig.DOWNLOAD_REQUEST_BYTES - 1) / MeasurementConfig.DOWNLOAD_REQUEST_BYTES)
    }
}
