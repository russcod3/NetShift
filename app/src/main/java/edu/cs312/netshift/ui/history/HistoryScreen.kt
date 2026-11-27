package edu.cs312.netshift.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(tests: List<TestResultEntity>, comparisonCount: Int = 0,
    onResult: (String) -> Unit, onCompare: () -> Unit, onTest: () -> Unit = {}) {
    var prompt by rememberSaveable { mutableStateOf(false) }
    var moreInfo by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closePrompt(action: () -> Unit = {}) {
        scope.launch { sheetState.hide(); prompt = false; action() }
    }
    ScreenPage("Test history", "Every connection has a story. Here are yours.", scrollable = false) {
        FilledTonalButton(onClick = { prompt = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Compare networks")
        }
        if (tests.isEmpty()) {
            EmptyState("No saved tests", "Take your first test to start a picture of your connection over time.")
            OutlinedButton(onClick = onTest) { Text("Take a test") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 12.dp)) {
                items(tests, key = { it.id }) { test ->
                    Card(onClick = { onResult(test.id) }, modifier = Modifier.fillMaxWidth().animateItem(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(test.locationName, style = MaterialTheme.typography.titleMedium)
                                    Text(formatTimestamp(test.startedAtEpochMs), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(if ((test.measuredTransport ?: test.requestedTransport) == "WIFI") "Wi-Fi" else "Mobile",
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatMetric(test.downloadMbps, "Mbps"), style = MaterialTheme.typography.titleMedium)
                                    Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(formatMetric(test.latencyMedianMs, "ms"), style = MaterialTheme.typography.titleMedium)
                                    Text("Response time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(test.status.lowercase().replaceFirstChar { it.uppercase() } + "  ·  View result →",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    if (prompt) {
        ModalBottomSheet(onDismissRequest = { prompt = false }, sheetState = sheetState, shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)
                .animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { closePrompt() }) { Text("Close") }
                }
                ShiftyMascot(Modifier.size(128.dp), waving = true)
                Text("Compare your connections", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(if (comparisonCount > 0) "Your saved Wi-Fi and mobile results are ready. Let’s find what works better."
                    else "Test Wi-Fi and mobile data at the same place to see them side by side.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = {
                    closePrompt { if (comparisonCount > 0) onCompare() else onTest() }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (comparisonCount > 0) "View comparison" else "Take a test")
                }
                OutlinedButton(onClick = { moreInfo = !moreInfo }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("How comparison works")
                }
                if (moreInfo) Text(
                    "Use the same location name for both networks. Complete tests need at least 16 successful checks, 10 adjacent pairs and one second of download evidence. Pairs within 15 minutes are preferred.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { closePrompt { onCompare() } }) { Text("Open comparisons") }
            }
        }
    }
}
