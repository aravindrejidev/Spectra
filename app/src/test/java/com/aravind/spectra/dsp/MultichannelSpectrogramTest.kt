package com.aravind.spectra.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every channel carries its own tone; each channel's spectrogram view must show only that tone. */
class MultichannelSpectrogramTest {

    private fun check(numCh: Int) {
        val sr = 48_000
        val frames = sr * 4
        val freqs = DoubleArray(numCh) { 1000.0 * (it + 1) }
        val an = StreamAnalyzer(sr, numCh, frames.toLong(), false, exactTruePeak = false, clipThreshold = 0.999)
        var pos = 0
        while (pos < frames) {
            val n = minOf(4800, frames - pos)
            val data = Array(numCh) { c ->
                FloatArray(n) { i -> (0.5 * sin(2.0 * PI * freqs[c] * (pos + i) / sr)).toFloat() }
            }
            an.feed(data, n)
            pos += n
        }
        val result = an.finish()
        for (c in 0 until numCh) {
            val key = "ch${c + 1}"
            val view = result.views[key] ?: error("missing view $key for $numCh channels")
            val perRow = DoubleArray(view.rows)
            for (col in 0 until view.cols) {
                for (r in 0 until view.rows) perRow[r] += view.dbMatrix[col * view.rows + r] / view.cols
            }
            val top = perRow.indices.maxByOrNull { perRow[it] }!!
            val expected = (freqs[c] / (sr / 2.0) * view.rows).toInt()
            assertTrue("$numCh ch: $key peaks at row $top, expected about $expected", abs(top - expected) <= 2)
            for (o in 0 until numCh) {
                if (o == c) continue
                val row = (freqs[o] / (sr / 2.0) * view.rows).toInt()
                assertTrue("$numCh ch: $key shows the tone of ch${o + 1}", perRow[row] < perRow[top] - 40.0)
            }
        }
    }

    @Test fun threeChannels() = check(3)
    @Test fun fiveOneChannels() = check(6)
    @Test fun sevenOneChannels() = check(8)
}
