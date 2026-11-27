package edu.cs312.netshift.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.measurement.ActivityVerdict
import edu.cs312.netshift.measurement.MeasurementJson
import edu.cs312.netshift.measurement.VerdictLevel
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.model.TestTransport
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.InfoCard
import edu.cs312.netshift.ui.components.MetricRow
import edu.cs312.netshift.ui.components.ScreenPage
import edu.cs312.netshift.ui.components.formatMetric

@Composable
fun TestScreen(viewModel: TestViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDetails by remember { mutableStateOf(false) }
    val failureReason = state.measurement.failureReason
    val message = state.message
    val result = state.result

    ScreenPage("Test your connection", "Measure the network at a place you want to remember.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.selectedTransport == TestTransport.WIFI,
                enabled = !state.isRunning,
                onClick = { viewModel.selectTransport(TestTransport.WIFI) },
                label = { Text("Wi-Fi") },
            )
            FilterChip(
                selected = state.selectedTransport == TestTransport.CELLULAR,
                enabled = !state.isRunning,
                onClick = { viewModel.selectTransport(TestTransport.CELLULAR) },
                label = { Text("Mobile data") },
            )
        }
        OutlinedTextField(
            value = state.locationName,
            onValueChange = viewModel::updateLocation,
            label = { Text("Location name") },
            placeholder = { Text("Home, library, classroom…") },
            supportingText = { Text("Used to find this result in History.") },
            singleLine = true,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth(),
        )
        networkCard(state.network.transport, state.network.isValidated, state.network.isMetered)

        if (state.isRunning) {
            InfoCard {
                Text(progressTitle(state), style = MaterialTheme.typography.titleMedium)
                Text(progressDetail(state))
                if (failureReason != null) {
                    Text(TestViewModel.failureMessage(failureReason))
                }
            }
        }

        if (message != null) {
            InfoCard { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (result != null && result.status != "RUNNING") {
            resultSummary(result, state.measurement.saveError != null, state.isRetryingSave, viewModel::retrySaving)
            OutlinedButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showDetails) "Hide technical details" else "Technical details")
            }
            if (showDetails) resultDetails(result)
        } else if (!state.isRunning) {
            EmptyState(
                "Ready to measure",
                "NetShift checks response time and download speed on the selected connection. Each test uses up to about 18 MB, plus a small amount of network overhead.",
            )
        }

        Button(
            onClick = if (state.isRunning) viewModel::cancel else viewModel::requestStart,
            enabled = state.isRunning || !state.isStarting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isRunning) "Cancel test" else "Start test") }
    }

    if (state.showDataConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDataConfirmation,
            title = { Text("Use mobile data?") },
            text = {
                Text("This test can use up to about 18 MB of data, plus network overhead. Your carrier may count it toward your data plan. Continue only if that's okay.")
            },
            confirmButton = { TextButton(onClick = viewModel::confirmStart) { Text("Continue") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDataConfirmation) { Text("Cancel") } },
        )
    }
}

@Composable
private fun networkCard(transport: NetworkTransport, validated: Boolean, metered: Boolean?) {
    InfoCard {
        MetricRow("Current connection", transport.displayName)
        MetricRow("Internet check", if (validated) "Available" else "Not confirmed")
        if (metered != null) MetricRow("Data use", if (metered) "Metered" else "Unmetered")
    }
}

@Composable
private fun resultSummary(row: TestResultEntity, saveError: Boolean, retrying: Boolean, onRetry: () -> Unit) {
    val verdicts = remember(row.activityVerdictsJson) { MeasurementJson.parseActivityVerdicts(row.activityVerdictsJson) }
    val statusText = when (row.status) {
        "COMPLETE" -> "Test complete"
        "PARTIAL" -> "Partial result"
        "CANCELLED" -> "Test cancelled"
        else -> "Test couldn't complete"
    }
    InfoCard {
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        Text("${row.locationName} · ${row.measuredTransport ?: row.requestedTransport}")
        if (row.status == "FAILED" || row.status == "PARTIAL") {
            Text(TestViewModel.failureMessage(row.failureReason.orEmpty()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MetricRow("HTTP response time", formatMetric(row.latencyMedianMs, "ms"))
        MetricRow("Response variation", formatMetric(row.jitterMs, "ms"))
        MetricRow("HTTP probes", "${row.probeCountSucceeded}/${row.probeCountAttempted} succeeded")
        MetricRow("Download speed", formatMetric(row.downloadMbps, "Mbps"))
        if (verdicts != null) {
            Text("What this connection may support", style = MaterialTheme.typography.titleMedium)
            verdicts.activities.forEach { ActivityVerdictCard(it) }
        }
        if (saveError) {
            Text("This result is still waiting to be saved to History.")
            OutlinedButton(onClick = onRetry, enabled = !retrying) { Text(if (retrying) "Retrying…" else "Retry saving") }
        } else {
            Text("Saved to History", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityVerdictCard(verdict: ActivityVerdict) {
    val label = when (verdict.level) {
        VerdictLevel.GOOD -> "Good"
        VerdictLevel.FAIR -> "Fair"
        VerdictLevel.POOR -> "Poor"
        VerdictLevel.UNKNOWN -> "Not enough data"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${verdict.activity.label.replaceFirstChar { it.uppercase() }} · $label", style = MaterialTheme.typography.titleSmall)
        Text(verdict.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun resultDetails(row: TestResultEntity) {
    InfoCard {
        MetricRow("Test status", row.status)
        MetricRow("Failure reason", row.failureReason ?: "None")
        MetricRow("Download sample", row.downloadStopReason ?: "Unavailable")
        MetricRow("Probes planned", row.probeCountPlanned.toString())
        MetricRow("Probes attempted", row.probeCountAttempted.toString())
        MetricRow("Probes failed", row.probeCountFailed.toString())
        MetricRow("Download received", String.format("%.1f MB", row.downloadBytes / 1_000_000.0))
        MetricRow("Measurement payload", String.format("%.1f MB", row.measurementPayloadBytes / 1_000_000.0))
        val flags = remember(row.qualityFlagsJson) { runCatching { org.json.JSONArray(row.qualityFlagsJson) }.getOrNull() }
        MetricRow("Quality flags", flags?.let { array -> (0 until array.length()).joinToString { array.optString(it) }.ifBlank { "None" } } ?: "Unavailable")
    }
}

private fun progressTitle(state: TestUiState): String = when (state.measurement.phase) {
    MeasurementPhase.ACQUIRING -> "Connecting to ${state.selectedTransport.displayName}…"
    MeasurementPhase.WARMING_UP -> "Checking the test server…"
    MeasurementPhase.PROBING -> "Measuring response time…"
    MeasurementPhase.DOWNLOADING -> "Measuring download speed…"
    MeasurementPhase.SAVING -> "Saving your result…"
    else -> if (state.isStarting) "Starting test…" else "Test in progress…"
}

private fun progressDetail(state: TestUiState): String = when (state.measurement.phase) {
    MeasurementPhase.PROBING -> "${state.measurement.probesAttempted} of ${state.measurement.probesPlanned} HTTP checks completed."
    MeasurementPhase.DOWNLOADING -> String.format("%.1f MB received so far.", state.measurement.downloadBytes / 1_000_000.0)
    else -> "You can cancel at any time. Any measurements already collected will be kept."
}
