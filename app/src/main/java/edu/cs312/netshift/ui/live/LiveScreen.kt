package edu.cs312.netshift.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.ui.components.*

@Composable
fun LiveScreen(network: NetworkSnapshot) {
    val status = when {
        network.unavailableReason != null -> "Unavailable"
        network.transport == NetworkTransport.NONE -> "Offline"
        network.isCaptivePortal -> "Sign-in required"
        network.isValidated -> "Validated"
        else -> "Not verified"
    }
    ScreenPage("Connection overview", "A little clarity, wherever you connect.") {
        Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF493082), Color(0xFF7960C9)))).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RIGHT NOW", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE3D8FF))
                    Text(network.transport.displayName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Internet · $status", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                ShiftyMascot(Modifier.size(110.dp), network.transport)
            }
        }
        InfoCard {
            Text("Connection details", style = MaterialTheme.typography.titleMedium)
            MetricRow("Internet", status)
            MetricRow("Metered", when (network.isMetered) { true -> "Yes"; false -> "No"; null -> "Unavailable" })
        }
        InfoCard {
            Text("Assigned IP addresses", style = MaterialTheme.typography.titleMedium)
            if (network.ipAddresses.isEmpty()) Text("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else network.ipAddresses.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text("Assigned to your phone. These may differ from your public internet address.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        network.unavailableReason?.let { Text(it) }
        Text("Your test history stays on this device.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
