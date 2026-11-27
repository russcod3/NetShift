package edu.cs312.netshift.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cs312.netshift.ui.theme.Violet
import edu.cs312.netshift.ui.theme.MobileGold

data class NavItem(val route: String, val label: String, val icon: Int)

@Composable
fun GooeyNavigation(items: List<NavItem>, selected: Int, onSelect: (NavItem) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val barHeight = (80f + ((fontScale - 1f).coerceAtLeast(0f) * 44f)).dp
    val x by animateFloatAsState(selected.toFloat(), tween(RiffMotion.TAB_MS, easing = RiffMotion.positionEasing), label = "Gooey tab position")
    val shapePhase = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        shapePhase.snapTo(0f)
        shapePhase.animateTo(1f, tween(RiffMotion.TAB_MS, easing = LinearEasing))
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(barHeight)
            .shadow(12.dp, CircleShape).clip(CircleShape)
            .background(colors.surfaceContainerLow).border(1.dp, colors.outlineVariant, CircleShape)
            .selectableGroup()) {
            Canvas(Modifier.matchParentSize()) {
                val slot = size.width / items.size
                val w = slot * .79f * RiffMotion.widthAt(shapePhase.value)
                val h = (size.height - 16.dp.toPx()) * RiffMotion.heightAt(shapePhase.value)
                val center = slot * (x + .5f)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Violet, MobileGold), center-w/2, center+w/2),
                    Offset(center-w/2, (size.height-h)/2), Size(w,h), CornerRadius(h/2))
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val active = index == selected
                    val displayLabel = if (fontScale > 1.3f) when (item.route) {
                        "history" -> "Saved"
                        "handoffs" -> "Swap"
                        else -> item.label
                    } else item.label
                    val onBlob = kotlin.math.abs(x-index) <= .36f * RiffMotion.widthAt(shapePhase.value)
                    val foreground by animateColorAsState(if (onBlob) Color(0xFF251B34) else colors.onSurfaceVariant,
                        tween(120), label = "Tab contrast")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .selectable(active, role = Role.Tab, onClick = { onSelect(item) })
                            .semantics { contentDescription = item.label }.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(item.icon), null, Modifier.size(24.dp),
                            tint = foreground)
                        Spacer(Modifier.height(4.dp))
                        Text(displayLabel, fontSize = 12.sp, lineHeight = 15.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = foreground)
                    }
                }
            }
        }
    }
}
@Composable
fun ThemeSwitch(dark: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val slide by animateFloatAsState(if (dark) 1f else 0f, tween(450), label = "Theme thumb")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.size(76.dp, 48.dp).clip(CircleShape)
        .toggleable(dark, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
        .semantics { contentDescription = "Dark theme"; stateDescription = if (dark) "On" else "Off" }) {
        drawRoundRect(track, Offset(4.dp.toPx(), 6.dp.toPx()),
            Size(size.width-8.dp.toPx(), size.height-12.dp.toPx()), CornerRadius(size.height/2))
        val r = 14.dp.toPx()
        val center = Offset(22.dp.toPx() + slide * 32.dp.toPx(), size.height/2)
        drawCircle(if (dark) Violet else MobileGold, r, center)
        if (slide > .5f) {
            drawCircle(track, r * .82f, center + Offset(r * .48f, -r * .34f))
            drawCircle(MobileGold, 1.4.dp.toPx(), Offset(23.dp.toPx(), 19.dp.toPx()))
            drawCircle(Violet, 1.dp.toPx(), Offset(33.dp.toPx(), 29.dp.toPx()))
        } else {
            drawCircle(Color(0xFFFFF4CD), r * .58f, center)
        }
    }
}
