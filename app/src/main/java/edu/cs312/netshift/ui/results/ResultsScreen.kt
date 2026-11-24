package edu.cs312.netshift.ui.results

import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.InfoCard
import edu.cs312.netshift.ui.components.MetricRow
import edu.cs312.netshift.ui.components.ScreenPage
import edu.cs312.netshift.ui.components.formatMetric
import edu.cs312.netshift.ui.components.formatTimestamp

@Composable
fun ResultsScreen(result: TestResultEntity?, onCompare: () -> Unit) {
    ScreenPage("Test result", result?.locationName ?: "Saved measurement details") {
        if (result == null) {
            EmptyState("Result unavailable", "This test is not in the local history.")
        } else {
            InfoCard {
                MetricRow("Time", formatTimestamp(result.startedAtEpochMs))
                MetricRow("Connection", result.measuredTransport ?: result.requestedTransport)
                MetricRow("Status", result.status)
                MetricRow("Latency", formatMetric(result.latencyMedianMs, "ms"))
                MetricRow("Jitter", formatMetric(result.jitterMs, "ms"))
                MetricRow("Probe loss", formatMetric(result.probeLossPercent, "%"))
                MetricRow("Download", formatMetric(result.downloadMbps, "Mbps"))
            }
            OutlinedButton(onClick = onCompare) { androidx.compose.material3.Text("Compare networks") }
        }
    }
}
