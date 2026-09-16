package com.aravind.spectra.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-math DSP core. No Android dependencies here on purpose, so it is
 * plain-JVM testable and easy to reason about independently of
 * decode/UI concerns.
 *
 * This mirrors the worker.js implementation used in the web-app version of
 * Spectra, tested there against synthetic sine/noise signals, and
 * cross-checked here against the same test cases after the streaming
 * rewrite below (see CHANGELOG.md).
 */

private const val FFT_SIZE = 2048
private const val HOP = 1024

data class LevelStats(
    val peakDb: Double,
    val rmsDb: Double,
    val dr: Double,
    val clipCount: Int,
    val peakLinear: Double,
    val rmsLinear: Double
)

data class CutoffResult(
    val cutoffHz: Double,
    val nyquist: Double,
    val isSharp: Boolean,
    val dropPerKHz: Double
)

data class ChannelSpectrogram(
    // flat [col * rows + row] dB matrix, row 0 = lowest frequency
    val dbMatrix: FloatArray,
    val cols: Int,
    val rows: Int
)

data class AnalysisResult(
    val perChannelStats: List<LevelStats>,
    val overallStats: LevelStats,
    val cutoff: CutoffResult,
    val views: Map<String, ChannelSpectrogram>, // "all", "ch1", "ch2"
    val totalSamples: Int,
    val fftSize: Int
)

/** Iterative in-place radix-2 Cooley-Tukey FFT (re/im must be same pow2 length). */
private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (bit and j != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val half = len shr 1
        val ang = -2 * Math.PI / len
        val wr = cos(ang)
        val wi = sin(ang)
        var i = 0
        while (i < n) {
            var curWr = 1.0
            var curWi = 0.0
            for (k in 0 until half) {
                val a = i + k
                val b = i + k + half
                val vr = re[b] * curWr - im[b] * curWi
                val vi = re[b] * curWi + im[b] * curWr
                val ur = re[a]
                val ui = im[a]
                re[a] = ur + vr; im[a] = ui + vi
                re[b] = ur - vr; im[b] = ui - vi
                val nWr = curWr * wr - curWi * wi
                val nWi = curWr * wi + curWi * wr
                curWr = nWr; curWi = nWi
            }
            i += len
        }
        len = len shl 1
    }
}

private fun hannWindow(size: Int): DoubleArray {
    val w = DoubleArray(size)
    for (i in 0 until size) w[i] = 0.5 - 0.5 * cos(2 * Math.PI * i / (size - 1))
    return w
}
private val WINDOW = hannWindow(FFT_SIZE)

private fun magToDb(mag: Double): Double = 20 * log10(max(mag, 1e-9))

/** STFT computed as a single streaming pass: each frame's magnitude is folded
 *  immediately into the average-spectrum accumulator and the display-bucket
 *  accumulators, then discarded. Peak memory is O(FFT_SIZE + targetCols*targetRows),
 *  not O(numFrames * FFT_SIZE/2) — an earlier two-pass version held every frame
 *  of the whole track in memory at once, which is fine for a 3-minute song
 *  but crashes on long files (a 20+ minute track could need well over a
 *  gigabyte just for that intermediate array). See CHANGELOG.md. */
private class StreamingSpectrogram(private val numCh: Int, private val targetCols: Int, private val targetRows: Int) {
    val half = FFT_SIZE / 2
    val avgSpectrumAccum = DoubleArray(half)
    var frameCount = 0

    // one accumulator pair per view: "all" (power-combined) plus one per channel if stereo+
    val sumSq: Array<DoubleArray> = Array(if (numCh > 1) numCh + 1 else 1) { DoubleArray(targetCols * targetRows) }
    val counts: Array<IntArray> = Array(sumSq.size) { IntArray(targetCols * targetRows) }

    fun addFrame(col: Int, rowStep: Double, perChannelMag: Array<DoubleArray>) {
        val combined = DoubleArray(half)
        for (k in 0 until half) {
            var s = 0.0
            for (c in 0 until numCh) { val v = perChannelMag[c][k]; s += v * v }
            combined[k] = sqrt(s / numCh)
            avgSpectrumAccum[k] += combined[k]
        }
        frameCount++

        for (k in 0 until half) {
            val row = min(targetRows - 1, (k / rowStep).toInt())
            val bucket = col * targetRows + row
            val vAll = combined[k]
            sumSq[0][bucket] += vAll * vAll
            counts[0][bucket]++
            if (numCh > 1) {
                for (c in 0 until numCh) {
                    val v = perChannelMag[c][k]
                    sumSq[c + 1][bucket] += v * v
                    counts[c + 1][bucket]++
                }
            }
        }
    }

    fun viewMatrix(index: Int): FloatArray {
        val out = FloatArray(targetCols * targetRows)
        val sq = sumSq[index]
        val ct = counts[index]
        for (i in out.indices) {
            out[i] = magToDb(if (ct[i] > 0) sqrt(sq[i] / ct[i]) else 0.0).toFloat()
        }
        return out
    }
}

private fun runStreamingStft(
    channels: List<FloatArray>,
    targetCols: Int,
    targetRows: Int,
    onProgress: ((Double) -> Unit)?
): StreamingSpectrogram {
    val numCh = channels.size
    val totalSamples = channels[0].size
    val numFrames = max(1, (totalSamples - FFT_SIZE) / HOP + 1)
    val half = FFT_SIZE / 2
    val colStep = numFrames.toDouble() / targetCols
    val rowStep = half.toDouble() / targetRows

    val acc = StreamingSpectrogram(numCh, targetCols, targetRows)
    val re = DoubleArray(FFT_SIZE)
    val im = DoubleArray(FFT_SIZE)
    val perChannelMag = Array(numCh) { DoubleArray(half) }

    for (f in 0 until numFrames) {
        val start = f * HOP
        for (c in 0 until numCh) {
            val samples = channels[c]
            for (i in 0 until FFT_SIZE) {
                val idx = start + i
                re[i] = (if (idx < samples.size) samples[idx].toDouble() else 0.0) * WINDOW[i]
                im[i] = 0.0
            }
            fft(re, im)
            val mag = perChannelMag[c]
            for (k in 0 until half) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) / FFT_SIZE
        }
        val col = min(targetCols - 1, (f / colStep).toInt())
        acc.addFrame(col, rowStep, perChannelMag)
        if (onProgress != null && (f and 127) == 0) onProgress(f.toDouble() / numFrames)
    }
    return acc
}

private fun detectSpectralCutoff(avgLinearSpectrum: DoubleArray, sampleRate: Int): CutoffResult {
    val half = avgLinearSpectrum.size
    val binHz = (sampleRate / 2.0) / half
    val dbs = DoubleArray(half) { magToDb(avgLinearSpectrum[it]) }
    var peakDb = Double.NEGATIVE_INFINITY
    for (v in dbs) if (v > peakDb) peakDb = v

    val topStart = (half * 0.9).toInt()
    val topSorted = dbs.copyOfRange(topStart, half).sorted()
    val noiseFloor = if (topSorted.isNotEmpty()) topSorted[topSorted.size / 2] else -100.0
    val threshold = max(noiseFloor + 8, peakDb - 65)

    var cutoffBin = half - 1
    for (k in half - 1 downTo 1) {
        if (dbs[k] > threshold && dbs[k - 1] > threshold) { cutoffBin = k; break }
    }
    val cutoffHz = cutoffBin * binHz

    val bandBins = max(1, round(1000 / binHz).toInt())
    val beforeIdx = max(0, cutoffBin - round(200 / binHz).toInt())
    val afterIdx = min(half - 1, cutoffBin + bandBins)
    val dropPerKHz = dbs[beforeIdx] - dbs[afterIdx]
    val isSharp = dropPerKHz > 25

    return CutoffResult(cutoffHz, sampleRate / 2.0, isSharp, dropPerKHz)
}

private fun levelStats(samples: FloatArray): LevelStats {
    var peak = 0.0
    var sumSq = 0.0
    var clipCount = 0
    for (s in samples) {
        val a = abs(s.toDouble())
        if (a > peak) peak = a
        sumSq += s.toDouble() * s.toDouble()
        if (a >= 0.999) clipCount++
    }
    val rms = sqrt(sumSq / samples.size)
    val peakDb = 20 * log10(max(peak, 1e-9))
    val rmsDb = 20 * log10(max(rms, 1e-9))
    return LevelStats(peakDb, rmsDb, peakDb - rmsDb, clipCount, peak, rms)
}

/**
 * Runs the full analysis pipeline. Call off the main thread (e.g. from a
 * coroutine on Dispatchers.Default) — this is CPU-bound and can take a
 * few seconds on a multi-minute track.
 */
fun analyze(
    channels: List<FloatArray>,
    sampleRate: Int,
    targetCols: Int,
    targetRows: Int,
    onProgress: ((Double) -> Unit)? = null
): AnalysisResult {
    val numCh = channels.size
    val perChannelStats = channels.map { levelStats(it) }

    val overallStats: LevelStats = run {
        var peakLinear = 0.0
        var sumSqRms = 0.0
        var clipCount = 0
        for (s in perChannelStats) {
            if (s.peakLinear > peakLinear) peakLinear = s.peakLinear
            sumSqRms += s.rmsLinear * s.rmsLinear
            if (s.clipCount > clipCount) clipCount = s.clipCount
        }
        val rmsLinear = sqrt(sumSqRms / numCh)
        val peakDb = 20 * log10(max(peakLinear, 1e-9))
        val rmsDb = 20 * log10(max(rmsLinear, 1e-9))
        LevelStats(peakDb, rmsDb, peakDb - rmsDb, clipCount, peakLinear, rmsLinear)
    }

    val streaming = runStreamingStft(channels, targetCols, targetRows) { p -> onProgress?.invoke(p) }

    for (k in 0 until streaming.half) streaming.avgSpectrumAccum[k] = streaming.avgSpectrumAccum[k] / streaming.frameCount
    val cutoff = detectSpectralCutoff(streaming.avgSpectrumAccum, sampleRate)

    val views = mutableMapOf("all" to ChannelSpectrogram(streaming.viewMatrix(0), targetCols, targetRows))
    if (numCh > 1) {
        views["ch1"] = ChannelSpectrogram(streaming.viewMatrix(1), targetCols, targetRows)
        views["ch2"] = ChannelSpectrogram(streaming.viewMatrix(2), targetCols, targetRows)
    }

    return AnalysisResult(perChannelStats, overallStats, cutoff, views, channels[0].size, FFT_SIZE)
}
