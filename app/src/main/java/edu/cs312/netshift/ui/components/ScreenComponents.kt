package edu.cs312.netshift.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScreenPage(
    title: String,
    description: String,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().then(scrollModifier).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.9f, stiffness = 350f)),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatTimestamp(epochMs: Long): String = DateTimeFormatter
    .ofPattern("MMM d, h:mm a", Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMs))

fun formatMetric(value: Double?, unit: String): String =
    value?.let { String.format(Locale.getDefault(), "%.1f %s", it, unit) } ?: "Unavailable"
