package edu.cs312.netshift.ui.results

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import edu.cs312.netshift.measurement.MeasurementJson
import edu.cs312.netshift.measurement.VerdictLevel
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
            val verdicts = remember(result.activityVerdictsJson) {
                MeasurementJson.parseActivityVerdicts(result.activityVerdictsJson)
            }
            InfoCard {
                MetricRow("Time", formatTimestamp(result.startedAtEpochMs))
                MetricRow("Connection", result.measuredTransport ?: result.requestedTransport)
                MetricRow("Status", result.status)
                MetricRow("Latency", formatMetric(result.latencyMedianMs, "ms"))
                MetricRow("Jitter", formatMetric(result.jitterMs, "ms"))
                MetricRow("HTTP probe failure rate", formatMetric(result.probeLossPercent, "%"))
                MetricRow("Download", formatMetric(result.downloadMbps, "Mbps"))
                Text("Failed HTTP checks are a proxy for packet loss, not a direct packet-loss measurement.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (verdicts != null) {
                    Text("What this connection may support", style = MaterialTheme.typography.titleMedium)
                    verdicts.activities.forEach { verdict ->
                        val level = when (verdict.level) {
                            VerdictLevel.GOOD -> "Good"
                            VerdictLevel.FAIR -> "Fair"
                            VerdictLevel.POOR -> "Poor"
                            VerdictLevel.UNKNOWN -> "Not enough data"
                        }
                        Text("${verdict.activity.label.replaceFirstChar { it.uppercase() }} · $level", style = MaterialTheme.typography.titleSmall)
                        Text(verdict.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onCompare) { androidx.compose.material3.Text("Compare networks") }
        }
    }
}
