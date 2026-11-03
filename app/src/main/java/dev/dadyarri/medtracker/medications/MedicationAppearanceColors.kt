package dev.dadyarri.medtracker.medications

import kotlin.math.abs
import kotlin.math.roundToInt

/** Preserve hue while keeping custom colors in the same soft range as the palette. */
internal fun pastelMedicationColor(hex: String): Long? {
    val text = hex.trim().removePrefix("#")
    if (!text.matches(Regex("([0-9a-fA-F]{3}|[0-9a-fA-F]{6})"))) return null
    val expanded = if (text.length == 3) text.map { "$it$it" }.joinToString("") else text
    val rgb = expanded.toLong(16)
    val r = ((rgb shr 16) and 255) / 255.0
    val g = ((rgb shr 8) and 255) / 255.0
    val b = (rgb and 255) / 255.0
    val hi = maxOf(r, g, b)
    val lo = minOf(r, g, b)
    val delta = hi - lo
    val originalLightness = (hi + lo) / 2
    val hue = when {
        delta == 0.0 -> 0.0
        hi == r -> ((g - b) / delta % 6 + 6) % 6 * 60
        hi == g -> ((b - r) / delta + 2) * 60
        else -> ((r - g) / delta + 4) * 60
    }
    val saturation = if (delta == 0.0) 0.0 else minOf(.55, delta / (1 - abs(2 * originalLightness - 1)))
    val lightness = if (delta == 0.0) maxOf(.84, originalLightness) else originalLightness.coerceIn(.84, .90)
    val c = (1 - abs(2 * lightness - 1)) * saturation
    val x = c * (1 - abs(hue / 60 % 2 - 1))
    val m = lightness - c / 2
    val channels = when {
        hue < 60 -> doubleArrayOf(c, x, 0.0)
        hue < 120 -> doubleArrayOf(x, c, 0.0)
        hue < 180 -> doubleArrayOf(0.0, c, x)
        hue < 240 -> doubleArrayOf(0.0, x, c)
        hue < 300 -> doubleArrayOf(x, 0.0, c)
        else -> doubleArrayOf(c, 0.0, x)
    }.map { ((it + m) * 255).roundToInt().coerceIn(0, 255).toLong() }
    return 0xFF000000 or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}
