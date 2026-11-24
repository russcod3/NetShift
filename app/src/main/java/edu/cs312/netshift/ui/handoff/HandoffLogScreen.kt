package edu.cs312.netshift.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.data.db.entity.HandoffEventEntity
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.ScreenPage
import edu.cs312.netshift.ui.components.formatTimestamp

@Composable
fun HandoffLogScreen(events: List<HandoffEventEntity>) {
    ScreenPage("Handoff log", "Follow changes between Wi-Fi and mobile data.", scrollable = false) {
        Button(onClick = {}, enabled = false) { Text("Start tracking") }
        if (events.isEmpty()) {
            EmptyState(
                "No recorded handoffs",
                "Background handoff tracking is not available in this initial build. Live status updates while the app is open.",
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${event.fromTransport} → ${event.toTransport}", style = MaterialTheme.typography.titleMedium)
                        Text(formatTimestamp(event.detectedAtEpochMs))
                        Text(
                            "${event.fromPrimaryIp ?: "Unavailable"} → ${event.toPrimaryIp ?: "Unavailable"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
