package edu.cs312.netshift.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.ui.components.ShiftyMascot

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, transport: NetworkTransport = NetworkTransport.WIFI) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val heroHeight = (maxHeight * .34f).coerceIn(150.dp, 260.dp)
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("NETSHIFT", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Box(Modifier.fillMaxWidth().height(heroHeight), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(heroHeight * .85f).background(
                            Brush.radialGradient(listOf(colors.primary.copy(alpha = .19f), colors.secondary.copy(alpha = .06f), colors.background)), CircleShape))
                        ShiftyMascot(Modifier.size(heroHeight + 20.dp), transport, waving = true)
                    }
                    Text("Say hello to Shifty", style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                    Text("Welcome to\nNetShift", textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineLarge)
                    Text("Find your better connection. Test Wi-Fi and mobile data, understand your results, and keep every measurement in one place.",
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Get started") }
                    Text("Your results stay on this device.", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
