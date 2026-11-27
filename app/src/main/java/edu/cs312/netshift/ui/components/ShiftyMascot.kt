package edu.cs312.netshift.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import edu.cs312.netshift.R
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.ui.theme.LocalNetShiftDark
import edu.cs312.netshift.ui.theme.LocalUiMotion
import edu.cs312.netshift.ui.theme.LocalShiftyTexture
import kotlin.math.*

/** One shared texture atlas; each anatomical part has its own pivot. No per-frame bitmap allocations. */
@Composable
fun ShiftyMascot(
    modifier: Modifier = Modifier,
    transport: NetworkTransport = NetworkTransport.WIFI,
    waving: Boolean = false,
) {
    val atlas = LocalShiftyTexture.current ?: ImageBitmap.imageResource(R.drawable.shifty_atlas)
    val dark = LocalNetShiftDark.current
    val motion = LocalUiMotion.current
    val loop = rememberInfiniteTransition(label = "Shifty breathing")
    val beat by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "Shifty cycle")
    val hue by animateFloatAsState(
        (if (transport == NetworkTransport.CELLULAR) -80f else if (transport == NetworkTransport.NONE) 90f else 30f) + if (dark) 15f else 0f,
        tween(650), label = "Shifty network color")
    val light by animateFloatAsState(if (dark) 1.06f else 0.9f, tween(650), label = "Shifty theme color")
    val tint = remember(hue, light) { ColorFilter.colorMatrix(hueMatrix(hue, light)) }
    val networkName = when (transport) {
        NetworkTransport.CELLULAR -> "mobile data"
        NetworkTransport.NONE -> "offline"
        else -> "Wi-Fi"
    }
    Canvas(modifier.semantics { contentDescription = "Shifty the chameleon, $networkName colors" }) {
        val scale = min(size.width, size.height) / 400f
        val phase = if (motion) sin(beat * 2f * PI.toFloat()) else 0f
        withTransform({
            translate((size.width - 400f * scale) / 2f, (size.height - 400f * scale) / 2f)
            scale(scale, scale, Offset.Zero)
            translate(0f, phase * 3f)
        }) {
            // Source anchors are opaque joint pixels in each 512 px cell. Every rotation is
            // about its attachment inside the torso, so motion cannot pull a limb away.
            fun part(cell: Int, anchor: Offset, joint: Offset, w: Int, h: Int, angle: Float = 0f, mirrored: Boolean = false) {
                val position = joint - Offset(anchor.x * w / 512f, anchor.y * h / 512f)
                withTransform({
                    if (mirrored) scale(-1f, 1f, joint)
                    rotate(angle, joint)
                }) {
                    drawImage(atlas, IntOffset((cell % 3) * 512, (cell / 3) * 512), IntSize(512, 512),
                        IntOffset(position.x.toInt(), position.y.toInt()), IntSize(w, h), colorFilter = tint, filterQuality = FilterQuality.High)
                }
            }
            part(2, Offset(440f, 90f), Offset(178f, 282f), 120, 120, phase * 4f)
            part(5, Offset(160f, 100f), Offset(182f, 303f), 86, 91, -phase * 3f)
            part(5, Offset(160f, 100f), Offset(247f, 304f), 90, 94, phase * 3f)
            part(3, Offset(110f, 410f), Offset(249f, 222f), 108, 129,
                if (waving) phase * 15f - 5f else phase * 4f)
            // The rear limbs attach under the torso. The resting arm is on the near side:
            // draw it in front so its shoulder, elbow and whole hand remain visible.
            part(0, Offset.Zero, Offset(140f, 170f), 143, 158)
            part(4, Offset(220f, 100f), Offset(181f, 224f), 72, 104, 10f + phase * 3f, mirrored = true)
            part(1, Offset(250f, 450f), Offset(214f, 194f), 171, 166, phase * 2f)
        }
    }
}
private fun hueMatrix(degrees: Float, light: Float): ColorMatrix {
    val c = cos(degrees * PI / 180).toFloat()
    val s = sin(degrees * PI / 180).toFloat()
    val r = .213f; val g = .715f; val b = .072f
    return ColorMatrix(floatArrayOf(
        (r+c*(1-r)-s*r)*light, (g-c*g-s*g)*light, (b-c*b+s*(1-b))*light,0f,0f,
        (r-c*r+s*.143f)*light,(g+c*(1-g)+s*.140f)*light,(b-c*b-s*.283f)*light,0f,0f,
        (r-c*r-s*(1-r))*light,(g-c*g+s*g)*light,(b+c*(1-b)+s*b)*light,0f,0f,
        0f,0f,0f,1f,0f))
}
