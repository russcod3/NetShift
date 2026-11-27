package edu.cs312.netshift.ui.test

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.ui.components.InfoCard
import edu.cs312.netshift.ui.theme.LocalUiMotion
import edu.cs312.netshift.ui.theme.Violet
import edu.cs312.netshift.ui.theme.MobileGold

@Composable
fun NetworkTestMeter(state: MeasurementState, title: String, detail: String) {
    val reading = meterStageProgress(state)
    val fraction by animateFloatAsState(reading ?: 0f, tween(350), label = "Measured stage progress")
    val loop = rememberInfiniteTransition(label = "Waiting for network")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "Indeterminate stage")
    val motion = LocalUiMotion.current
    val colors = MaterialTheme.colorScheme
    InfoCard {
        Box(Modifier.fillMaxWidth().height(195.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp, 190.dp).semantics {
                contentDescription = "Network test stage progress"
                progressBarRangeInfo = if (reading == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(reading, 0f..1f)
            }) {
                val inset = 16.dp.toPx()
                val arcSize = Size(size.width-inset*2, size.width-inset*2)
                drawArc(colors.surfaceVariant, 150f, 240f, false, Offset(inset,inset), arcSize, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(Violet, MobileGold, Violet)),
                    if (reading == null && motion) 150f + spin else 150f,
                    if (reading == null) 55f else fraction*240f,
                    false, Offset(inset,inset), arcSize, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 22.dp)) {
                Text(when (state.phase) {
                    MeasurementPhase.PROBING -> "${state.probesAttempted}/${state.probesPlanned}"
                    MeasurementPhase.DOWNLOADING -> String.format("%.1f MB", state.downloadBytes / 1_000_000.0)
                    else -> "Working"
                }, style = MaterialTheme.typography.headlineMedium)
                Text(when (state.phase) {
                    MeasurementPhase.PROBING -> "HTTP checks completed"
                    MeasurementPhase.DOWNLOADING -> "received · 18 MB maximum"
                    else -> "Preparing your measurement"
                }, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text("Stage progress · no estimated speed", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
