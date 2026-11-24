package edu.cs312.netshift.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.ScreenPage
import edu.cs312.netshift.ui.components.formatTimestamp

@Composable
fun HistoryScreen(tests: List<TestResultEntity>, onResult: (String) -> Unit, onCompare: () -> Unit) {
    ScreenPage("Test history", "Your saved measurements, kept on this phone.", scrollable = false) {
        OutlinedButton(onClick = onCompare) { Text("Compare networks") }
        if (tests.isEmpty()) {
            EmptyState("No saved tests", "Completed, interrupted, and failed tests will appear here once testing is available.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tests, key = { it.id }) { test ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onResult(test.id) }.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(test.locationName, style = MaterialTheme.typography.titleMedium)
                        Text("${test.measuredTransport ?: test.requestedTransport} · ${test.status}")
                        Text(formatTimestamp(test.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
