package edu.cs312.netshift.debug

import org.junit.Assert.assertTrue
import org.junit.Test

class TimingSummaryTest {
    @Test fun includesTerminalFailuresAndRunsWithoutTerminalState() {
        val summary = formatTimingSummary(
            listOf(
                TimingSample(2_000, "FAILED"),
                TimingSample(22_800, "COMPLETE"),
                TimingSample(45_000, "NO_TERMINAL"),
            ),
        )

        assertTrue(summary.contains("n=3"))
        assertTrue(summary.contains("complete=1"))
        assertTrue(summary.contains("terminalFailure=1"))
        assertTrue(summary.contains("noTerminal=1"))
        assertTrue(summary.contains("over30000ms=1"))
        assertTrue(summary.contains("FAILED:2000"))
        assertTrue(summary.contains("NO_TERMINAL:45000"))
    }
}
