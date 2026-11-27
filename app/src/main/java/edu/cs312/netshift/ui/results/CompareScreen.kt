package edu.cs312.netshift.ui.results

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.data.LocationComparison
import edu.cs312.netshift.data.MetricPair
import edu.cs312.netshift.ui.components.*
import edu.cs312.netshift.ui.theme.WifiMint
import edu.cs312.netshift.ui.theme.MobileGold

@Composable
fun CompareScreen(comparisons: List<LocationComparison> = emptyList(), onTest: () -> Unit = {}) {
    ScreenPage("Compare networks", "Same place. Two connections. A clearer choice.") {
        if (comparisons.isEmpty()) {
            EmptyState("No comparison yet", "Save a qualifying Wi-Fi test and a mobile data test at the same location to unlock your comparison.")
            Button(onClick = onTest, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Take a test") }
        }
        comparisons.forEach { pair ->
            InfoCard {
                Text(pair.locationName, style = MaterialTheme.typography.titleLarge)
                Text("Wi-Fi · ${formatTimestamp(pair.wifi.startedAtEpochMs)}\nMobile · ${formatTimestamp(pair.cellular.startedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!pair.withinPreferredGap) Text("These tests were taken more than 15 minutes apart. Conditions may have changed.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                ComparisonMetric("Download", pair.downloadMbps, "Mbps", lowerBetter = false)
                ComparisonMetric("HTTP response time", pair.latencyMs, "ms", lowerBetter = true)
                ComparisonMetric("Response variation", pair.jitterMs, "ms", lowerBetter = true)
                ComparisonMetric("HTTP probe failures", pair.probeLossPercent, "%", lowerBetter = true)
            }
        }
        Text("These are HTTP measurements. Upload speed is not tested.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun ComparisonMetric(label: String, pair: MetricPair, unit: String, lowerBetter: Boolean) {
    val largest = maxOf(pair.wifi ?: 0.0, pair.cellular ?: 0.0).coerceAtLeast(.001)
    Text(label, style = MaterialTheme.typography.titleMedium)
    listOf(Triple("Wi-Fi", pair.wifi, WifiMint), Triple("Mobile", pair.cellular, MobileGold)).forEach { (name,value,color) ->
        val fraction by animateFloatAsState(((value ?: 0.0) / largest).toFloat(), tween(650), label = "Comparison bar")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(formatMetric(value, unit), style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (value != null) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f,1f)).fillMaxHeight().background(color))
        }
    }
    val wifi = pair.wifi; val mobile = pair.cellular
    val verdict = when {
        wifi == null || mobile == null -> "Not enough data"
        kotlin.math.abs(wifi-mobile) < .0001 -> "Both measured the same"
        (wifi < mobile) == lowerBetter -> "Wi-Fi measured better"
        else -> "Mobile measured better"
    }
    Text(verdict + if (lowerBetter) " · lower is better" else " · higher is better",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
