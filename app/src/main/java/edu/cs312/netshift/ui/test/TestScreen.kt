package edu.cs312.netshift.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.material3.Surface
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
    val message = state.message
    val result = state.result
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            ScreenPage("Test your connection", "A clearer picture of the network around you.") {
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
                networkCard(state.network.transport, state.network.isValidated, state.network.isMetered)

                if (state.isRunning) {
                    NetworkTestMeter(state.measurement, progressTitle(state), progressDetail(state))
                }

                if (message != null) {
                    InfoCard { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                if (result != null && result.status != "RUNNING") {
                    MeasurementSummary(result, state.measurement.saveError != null, state.isRetryingSave, viewModel::retrySaving)
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

            }
        }
        Button(
            onClick = { focus.clearFocus(); if (state.isRunning) viewModel.cancel() else viewModel.requestStart() },
            enabled = state.isRunning || (!state.isStarting && state.locationName.isNotBlank()),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 54.dp),
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
internal fun MeasurementSummary(row: TestResultEntity, saveError: Boolean, retrying: Boolean, onRetry: () -> Unit) {
    val reveal = remember(row.id) { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    val verdicts = remember(row.activityVerdictsJson) { MeasurementJson.parseActivityVerdicts(row.activityVerdictsJson) }
    val statusText = when (row.status) {
        "COMPLETE" -> "Test complete"
        "PARTIAL" -> "Partial result"
        "CANCELLED" -> "Test cancelled"
        else -> "Test couldn't complete"
    }
    AnimatedVisibility(visibleState = reveal, enter = fadeIn(tween(450)) + slideInVertically(tween(500)) { it / 10 }) {
    InfoCard {
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        Text("${row.locationName} · ${row.measuredTransport ?: row.requestedTransport}")
        if (row.status == "FAILED" || row.status == "PARTIAL") {
            Text(TestViewModel.failureMessage(row.failureReason.orEmpty()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile("Download", formatMetric(row.downloadMbps, "Mbps"), Modifier.weight(1f))
            ResultTile("Response time", formatMetric(row.latencyMedianMs, "ms"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile("Response variation", formatMetric(row.jitterMs, "ms"), Modifier.weight(1f))
            ResultTile("HTTP checks", "${row.probeCountSucceeded}/${row.probeCountAttempted}", Modifier.weight(1f))
        }
        MetricRow("HTTP probe failure rate", formatMetric(row.probeLossPercent, "%"))
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

}

@Composable
private fun ActivityVerdictCard(verdict: ActivityVerdict) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accent = when (verdict.level) {
        VerdictLevel.GOOD -> colors.secondary
        VerdictLevel.FAIR -> colors.tertiary
        VerdictLevel.POOR -> colors.error
        VerdictLevel.UNKNOWN -> colors.onSurfaceVariant
    }
    val label = when (verdict.level) {
        VerdictLevel.GOOD -> "Good"
        VerdictLevel.FAIR -> "Fair"
        VerdictLevel.POOR -> "Poor"
        VerdictLevel.UNKNOWN -> "Not enough data"
    }
    Surface(color = accent.copy(alpha = .08f), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = .2f))) {
    Column(Modifier.fillMaxWidth().animateContentSize(spring()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${verdict.activity.label.replaceFirstChar { it.uppercase() }} · $label", style = MaterialTheme.typography.titleSmall, color = accent)
        Text(when (verdict.limitingMetric) {
            edu.cs312.netshift.measurement.LimitingMetric.DOWNLOAD_SPEED -> "Download speed is the limiting factor."
            edu.cs312.netshift.measurement.LimitingMetric.RESPONSE_TIME -> "Response time is the limiting factor."
            edu.cs312.netshift.measurement.LimitingMetric.JITTER -> "Response variation is the limiting factor."
            edu.cs312.netshift.measurement.LimitingMetric.PROBE_FAILURES -> "Failed checks are the limiting factor."
            null -> if (verdict.level == VerdictLevel.GOOD) "All Good thresholds passed." else "More measurement evidence is needed."
        }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less detail" else "Why this verdict?") }
        AnimatedVisibility(expanded) { Text(verdict.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        if (verdict.activity.uploadNotTested) Text("Upload not tested", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
    }
}

@Composable
private fun ResultTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(value, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(180)) }, label = "Result value") {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }
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
