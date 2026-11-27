package edu.cs312.netshift.ui.components

import androidx.compose.animation.core.Easing

/** Riff 32 first transition, sampled from the supplied Figma motion context.
 * The original 6 s demo is driven by user selection here; the 1133 ms move keeps its stretch and overshoot. */
internal object RiffMotion {
    const val TAB_MS = 1133
    private val position = arrayOf(
    floatArrayOf(0.088228f, 0.000000f),
    floatArrayOf(0.117354f, 0.229034f),
    floatArrayOf(0.147381f, 0.438308f),
    floatArrayOf(0.176508f, 0.617376f),
    floatArrayOf(0.205635f, 0.762248f),
    floatArrayOf(0.235662f, 0.873342f),
    floatArrayOf(0.264788f, 0.953791f),
    floatArrayOf(0.293915f, 1.008179f),
    floatArrayOf(0.323889f, 1.041585f),
    floatArrayOf(0.353016f, 1.058996f),
    floatArrayOf(0.382143f, 1.064889f),
    floatArrayOf(0.412170f, 1.063056f),
    floatArrayOf(0.441296f, 1.056538f),
    floatArrayOf(0.470423f, 1.047637f),
    floatArrayOf(0.500450f, 1.038000f),
    floatArrayOf(0.529577f, 1.028718f),
    floatArrayOf(0.558704f, 1.020453f),
    floatArrayOf(0.588678f, 1.013530f),
    floatArrayOf(0.617804f, 1.008034f),
    floatArrayOf(0.646931f, 1.003906f),
    floatArrayOf(0.676958f, 1.000983f),
    floatArrayOf(0.706085f, 0.999068f),
    floatArrayOf(0.735212f, 0.997944f),
    floatArrayOf(0.765239f, 0.997415f),
    floatArrayOf(0.794365f, 0.997303f),
    floatArrayOf(0.823492f, 0.997466f),
    floatArrayOf(0.853466f, 0.997782f),
    floatArrayOf(0.882593f, 0.998167f),
    floatArrayOf(0.911720f, 0.998564f),
    floatArrayOf(0.941747f, 0.998940f),
    floatArrayOf(0.970873f, 0.999261f),
    floatArrayOf(1.000000f, 1.000000f))
    private val width = arrayOf(
    floatArrayOf(0.000000f, 1.000000f),
    floatArrayOf(0.029127f, 1.304688f),
    floatArrayOf(0.059101f, 1.611563f),
    floatArrayOf(0.088228f, 1.887119f),
    floatArrayOf(0.117354f, 1.808460f),
    floatArrayOf(0.147381f, 1.699756f),
    floatArrayOf(0.176508f, 1.576614f),
    floatArrayOf(0.205635f, 1.451273f),
    floatArrayOf(0.235662f, 1.332767f),
    floatArrayOf(0.264788f, 1.227199f),
    floatArrayOf(0.293915f, 1.138108f),
    floatArrayOf(0.323889f, 1.066920f),
    floatArrayOf(0.353016f, 1.013381f),
    floatArrayOf(0.382143f, 0.976045f),
    floatArrayOf(0.412170f, 0.952705f),
    floatArrayOf(0.441296f, 0.940756f),
    floatArrayOf(0.470423f, 0.937534f),
    floatArrayOf(0.500450f, 0.940489f),
    floatArrayOf(0.529577f, 0.947398f),
    floatArrayOf(0.558704f, 0.956403f),
    floatArrayOf(0.588678f, 0.966074f),
    floatArrayOf(0.617804f, 0.975381f),
    floatArrayOf(0.646931f, 0.983653f),
    floatArrayOf(0.676958f, 0.990534f),
    floatArrayOf(0.706085f, 0.995892f),
    floatArrayOf(0.735212f, 0.999778f),
    floatArrayOf(0.765239f, 1.002352f),
    floatArrayOf(0.794365f, 1.003824f),
    floatArrayOf(0.823492f, 1.004449f),
    floatArrayOf(0.853466f, 1.004455f),
    floatArrayOf(0.882593f, 1.004051f),
    floatArrayOf(0.911720f, 1.003426f),
    floatArrayOf(0.941747f, 1.002705f),
    floatArrayOf(0.970873f, 1.001994f),
    floatArrayOf(1.000000f, 1.000000f))
    private val height = arrayOf(
    floatArrayOf(0.000000f, 1.000000f),
    floatArrayOf(0.029127f, 0.948743f),
    floatArrayOf(0.059101f, 0.897110f),
    floatArrayOf(0.088228f, 0.850757f),
    floatArrayOf(0.117354f, 0.863985f),
    floatArrayOf(0.147381f, 0.882279f),
    floatArrayOf(0.176508f, 0.902993f),
    floatArrayOf(0.205635f, 0.924081f),
    floatArrayOf(0.235662f, 0.944015f),
    floatArrayOf(0.264788f, 0.961779f),
    floatArrayOf(0.293915f, 0.976765f),
    floatArrayOf(0.323889f, 0.988743f),
    floatArrayOf(0.353016f, 0.997750f),
    floatArrayOf(0.382143f, 1.000000f),
    floatArrayOf(0.735212f, 1.000000f),
    floatArrayOf(0.765239f, 0.999603f),
    floatArrayOf(0.794365f, 0.999353f),
    floatArrayOf(0.823492f, 0.999250f),
    floatArrayOf(0.853466f, 0.999250f),
    floatArrayOf(0.882593f, 0.999316f),
    floatArrayOf(0.911720f, 0.999426f),
    floatArrayOf(0.941747f, 0.999544f),
    floatArrayOf(0.970873f, 0.999662f),
    floatArrayOf(1.000000f, 1.000000f))
    val positionEasing = Easing { sample(position, it) }
    fun widthAt(t: Float) = sample(width, t)
    fun heightAt(t: Float) = sample(height, t)
    private fun sample(points: Array<FloatArray>, fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        val next = points.indexOfFirst { it[0] >= t }
        if (next <= 0) return points[if (next < 0) points.lastIndex else 0][1]
        val a = points[next-1]; val b = points[next]
        return a[1] + (b[1]-a[1]) * ((t-a[0])/(b[0]-a[0]))
    }
}
