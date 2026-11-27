package edu.cs312.netshift.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.ui.components.*
import edu.cs312.netshift.ui.test.MeasurementSummary

@Composable
fun ResultsScreen(result: TestResultEntity?, onCompare: () -> Unit) {
    ScreenPage("Test result", result?.locationName ?: "Saved measurement details") {
        if (result == null) EmptyState("Result unavailable", "This test is not in the local history.")
        else {
            InfoCard {
                MetricRow("Time", formatTimestamp(result.startedAtEpochMs))
                MetricRow("Connection", if (result.measuredTransport == "WIFI") "Wi-Fi" else result.measuredTransport ?: result.requestedTransport)
            }
            MeasurementSummary(result, saveError = false, retrying = false, onRetry = {})
            Text("Failed HTTP checks are a proxy for packet loss, not a direct packet-loss measurement.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Compare networks") }
        }
    }
}
