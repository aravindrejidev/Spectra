package com.aravind.spectra.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object SpectrogramPalette {
    private val stops = listOf(
        0.00f to intArrayOf(8, 8, 22),
        0.16f to intArrayOf(32, 20, 95),
        0.36f to intArrayOf(115, 25, 135),
        0.56f to intArrayOf(205, 35, 70),
        0.74f to intArrayOf(238, 105, 20),
        0.88f to intArrayOf(251, 193, 45),
        1.00f to intArrayOf(255, 251, 232)
    )

    /** 256-step ARGB lookup table. */
    val lut: IntArray = IntArray(256) { i ->
        val t = i / 255f
        var r = 255
        var g = 251
        var b = 232
        for (k in 0 until stops.size - 1) {
            val (t0, c0) = stops[k]
            val (t1, c1) = stops[k + 1]
            if (t >= t0 && t <= t1) {
                val f = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
                r = (c0[0] + (c1[0] - c0[0]) * f).roundToInt()
                g = (c0[1] + (c1[1] - c0[1]) * f).roundToInt()
                b = (c0[2] + (c1[2] - c0[2]) * f).roundToInt()
                break
            }
        }
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    val legendStops: Array<Pair<Float, Color>> =
        stops.map { (t, c) -> t to Color(c[0] / 255f, c[1] / 255f, c[2] / 255f) }.toTypedArray()
}
