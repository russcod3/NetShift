package edu.cs312.netshift.ui.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.ui.components.InfoCard
import edu.cs312.netshift.ui.components.MetricRow
import edu.cs312.netshift.ui.components.ScreenPage

@Composable
fun LiveScreen(network: NetworkSnapshot) {
    ScreenPage("Connection overview", "See the network your phone is using right now.") {
        InfoCard {
            Text(network.transport.displayName, style = MaterialTheme.typography.headlineSmall)
            MetricRow("Internet", when {
                network.unavailableReason != null -> "Unavailable"
                network.transport == NetworkTransport.NONE -> "Offline"
                network.isCaptivePortal -> "Sign-in required"
                network.isValidated -> "Validated"
                else -> "Not verified"
            })
            MetricRow("Metered", when (network.isMetered) {
                true -> "Yes"
                false -> "No"
                null -> "Unavailable"
            })
        }
        InfoCard {
            Text("Assigned IP addresses", style = MaterialTheme.typography.titleMedium)
            if (network.ipAddresses.isEmpty()) {
                Text("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                network.ipAddresses.forEach { Text(it) }
            }
            Text(
                "These are addresses assigned to your phone. They may differ from your public internet address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        network.unavailableReason?.let { Text(it) }
        Text(
            "Your test history stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
