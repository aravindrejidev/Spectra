package com.aravind.spectra.dsp

import java.util.Arrays
import kotlin.math.*

private const val FFT_SIZE = 2048
private const val HOP = 1024
private const val BINS = FFT_SIZE / 2
private const val ROWS = 256 // BINS / 4 -> row = bin shr 2
private const val COLS = 480
private const val MAX_FFT_FRAMES = 16_000
private const val DR_BLOCK_SUBS = 30 // 3 s of 100 ms sub-blocks
private const val SCALE = 4.0 / FFT_SIZE // 0 dBFS sine == 0 dB
private const val SCALE_SQ = SCALE * SCALE

fun toDb(x: Double): Double = 20.0 * log10(max(x, 1e-9))

data class ChannelStats(
    val peakDb: Double,
    val rmsDb: Double,
    val truePeakDb: Double,
    val clipSamples: Long,
    val clipRuns: Int,
    val dcOffset: Double,
    val dr: Double?
)

data class StereoStats(
    val correlation: Double,
    val balanceDb: Double,
    val midDb: Double,
    val sideDb: Double,
    val dualMono: Boolean
)

data class CutoffResult(val cutoffHz: Double, val nyquist: Double, val isSharp: Boolean, val dropDb: Double)

/** dbMatrix is flat [col * rows + row], row 0 = lowest frequency. */
data class ChannelSpectrogram(val dbMatrix: FloatArray, val cols: Int, val rows: Int)

data class AnalysisResult(
    val sampleRate: Int,
    val channelCount: Int,
    val totalFrames: Long,
    val channels: List<ChannelStats>,
    val peakDb: Double,
    val truePeakDb: Double,
    val rmsDb: Double,
    val clipSamples: Long,
    val clipRuns: Int,
    val dr: Double?,
    val lufs: Double?,
    val lra: Double?,
    val maxMomentary: Double?,
    val effectiveBits: Int?,
    val stereo: StereoStats?,
    val cutoff: CutoffResult,
    val avgSpectrumDb: FloatArray,
    val views: Map<String, ChannelSpectrogram> // all, ch1, ch2, mid, side
) {
    val durationSec: Double get() = totalFrames.toDouble() / sampleRate
}

private object Fft {
    val cosT = DoubleArray(FFT_SIZE / 2) { cos(2.0 * PI * it / FFT_SIZE) }
    val sinT = DoubleArray(FFT_SIZE / 2) { -sin(2.0 * PI * it / FFT_SIZE) }
    val rev = IntArray(FFT_SIZE).also { r ->
        val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
        for (i in 0 until FFT_SIZE) r[i] = Integer.reverse(i) ushr (32 - bits)
    }
    val hann = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }

    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = FFT_SIZE
        for (i in 0 until n) {
            val j = rev[i]
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                var t = 0
                for (k in 0 until half) {
                    val wr = cosT[t]
                    val wi = sinT[t]
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] += vr
                    im[a] += vi
                    t += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * One-pass streaming analyzer. feed() chunks as they are decoded, then finish().
 * Memory is constant: spectrogram columns are merged pairwise whenever the fixed
 * column budget fills up, so any track length ends up in <= COLS columns.
 */
class StreamAnalyzer(
    private val sampleRate: Int,
    private val numCh: Int,
    expectedFrames: Long,
    private val trackBits: Boolean
) {
    private val specCh = min(numCh, 2)
    private val stride =
        if (expectedFrames > 0) max(1, ceil(expectedFrames.toDouble() / HOP / MAX_FFT_FRAMES).toInt()) else 1

    // levels
    private val peak = DoubleArray(numCh)
    private val sumSq = DoubleArray(numCh)
    private val dcSum = DoubleArray(numCh)
    private val clipSamples = LongArray(numCh)
    private val clipRuns = IntArray(numCh)
    private val runLen = IntArray(numCh)
    private val truePeak = DoubleArray(numCh)
    private val tpTail = Array(numCh) { FloatArray(11) }
    private var tpExt = FloatArray(0)
    private var bitOr = 0L
    private var totalFrames = 0L

    // loudness + dynamic range
    private val kw = Array(numCh) { makeKWeighting(sampleRate.toDouble()) }
    private val subLen = max(1, (sampleRate * 0.1).roundToInt())
    private val subAcc = DoubleArray(numCh)
    private var subFill = 0
    private val subs = DoubleList()
    private val drSumSq = DoubleArray(numCh)
    private val drPeak = DoubleArray(numCh)
    private var drSubs = 0
    private val drRms2 = Array(numCh) { DoubleList() }
    private val drPk = Array(numCh) { DoubleList() }

    // stereo
    private var sLR = 0.0
    private var sLL = 0.0
    private var sRR = 0.0
    private var sMid = 0.0
    private var sSide = 0.0

    // STFT
    private val winBuf = Array(specCh) { FloatArray(FFT_SIZE) }
    private var fill = 0
    private var frameIndex = 0
    private var frameCount = 0
    private var hopsPerCol = 1
    private var usedCols = 0
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val numViews = if (specCh == 2) 5 else 1
    private val sumP = Array(numViews) { DoubleArray(COLS * ROWS) }
    private val cnt = IntArray(COLS * ROWS)
    private val avgAccum = DoubleArray(BINS)

    fun feed(data: Array<FloatArray>, frames: Int) {
        if (frames <= 0) return
        var pos = 0
        while (pos < frames) {
            val n = min(subLen - subFill, frames - pos)
            processSlice(data, pos, n)
            subFill += n
            if (subFill >= subLen) flushSub()
            pos += n
        }
        updateTruePeak(data, frames)
        if (numCh >= 2) updateStereo(data[0], data[1], frames)
        feedStft(data, frames)
        totalFrames += frames
    }

    private fun processSlice(data: Array<FloatArray>, start: Int, n: Int) {
        val end = start + n
        for (c in 0 until numCh) {
            val x = data[c]
            val f0 = kw[c][0]
            val f1 = kw[c][1]
            var pk = peak[c]
            var blockPk = drPeak[c]
            var sq = 0.0
            var ksq = 0.0
            var dc = 0.0
            var cs = clipSamples[c]
            var runs = clipRuns[c]
            var run = runLen[c]
            var bits = 0L
            for (i in start until end) {
                val s = x[i].toDouble()
                val a = abs(s)
                if (a > pk) pk = a
                if (a > blockPk) blockPk = a
                sq += s * s
                dc += s
                if (a >= 0.999) {
                    cs++
                    run++
                    if (run == 3) runs++
                } else {
                    run = 0
                }
                val y = f1.process(f0.process(s))
                ksq += y * y
                if (trackBits) bits = bits or (a * 8388608.0).roundToLong()
            }
            peak[c] = pk
            drPeak[c] = blockPk
            sumSq[c] += sq
            drSumSq[c] += sq
            dcSum[c] += dc
            clipSamples[c] = cs
            clipRuns[c] = runs
            runLen[c] = run
            subAcc[c] += ksq
            bitOr = bitOr or bits
        }
    }

    private fun flushSub() {
        var total = 0.0
        for (c in 0 until numCh) {
            total += subAcc[c] / subLen
            subAcc[c] = 0.0
        }
        subs.add(total)
        subFill = 0
        drSubs++
        if (drSubs == DR_BLOCK_SUBS) {
            val blockLen = DR_BLOCK_SUBS.toDouble() * subLen
            for (c in 0 until numCh) {
                drRms2[c].add(drSumSq[c] / blockLen)
                drPk[c].add(drPeak[c])
                drSumSq[c] = 0.0
                drPeak[c] = 0.0
            }
            drSubs = 0
        }
    }

    /** Estimates inter-sample peaks; only evaluated where the signal is above -6 dBFS. */
    private fun updateTruePeak(data: Array<FloatArray>, frames: Int) {
        val need = frames + 11
        if (tpExt.size < need) tpExt = FloatArray(need)
        val ext = tpExt
        val cf = TruePeak.coef
        for (c in 0 until numCh) {
            System.arraycopy(tpTail[c], 0, ext, 0, 11)
            System.arraycopy(data[c], 0, ext, 11, frames)
            var tp = truePeak[c]
            for (n in 11 until need) {
                val m = n - 6
                if (abs(ext[m]) > 0.5f || abs(ext[m + 1]) > 0.5f) {
                    val base = n - 11
                    for (p in 1..3) {
                        val k = cf[p]
                        var s = 0.0
                        for (t in 0 until 12) s += ext[base + t] * k[t]
                        val v = abs(s)
                        if (v > tp) tp = v
                    }
                }
            }
            truePeak[c] = tp
            System.arraycopy(ext, frames, tpTail[c], 0, 11)
        }
    }

    private fun updateStereo(l: FloatArray, r: FloatArray, frames: Int) {
        var lr = 0.0
        var ll = 0.0
        var rr = 0.0
        var mid = 0.0
        var side = 0.0
        for (i in 0 until frames) {
            val a = l[i].toDouble()
            val b = r[i].toDouble()
            lr += a * b
            ll += a * a
            rr += b * b
            val m = (a + b) * 0.5
            val s = (a - b) * 0.5
            mid += m * m
            side += s * s
        }
        sLR += lr
        sLL += ll
        sRR += rr
        sMid += mid
        sSide += side
    }

    private fun feedStft(data: Array<FloatArray>, frames: Int) {
        var pos = 0
        while (pos < frames) {
            val n = min(FFT_SIZE - fill, frames - pos)
            for (c in 0 until specCh) System.arraycopy(data[c], pos, winBuf[c], fill, n)
            fill += n
            pos += n
            if (fill == FFT_SIZE) {
                onFrame()
                for (c in 0 until specCh) System.arraycopy(winBuf[c], HOP, winBuf[c], 0, HOP)
                fill = HOP
            }
        }
    }

    private fun onFrame() {
        val f = frameIndex++
        if (f % stride != 0) return
        var col = f / hopsPerCol
        while (col >= COLS) {
            mergeColumns()
            col = f / hopsPerCol
        }
        if (col + 1 > usedCols) usedCols = col + 1

        val w = Fft.hann
        val w0 = winBuf[0]
        if (specCh == 2) {
            val w1 = winBuf[1]
            for (i in 0 until FFT_SIZE) {
                re[i] = w0[i] * w[i]
                im[i] = w1[i] * w[i]
            }
        } else {
            for (i in 0 until FFT_SIZE) {
                re[i] = w0[i] * w[i]
                im[i] = 0.0
            }
        }
        Fft.transform(re, im)

        val base = col * ROWS
        if (specCh == 2) {
            // two real channels in one complex FFT: separate L and R, then derive mid/side
            val s0 = sumP[0]
            val s1 = sumP[1]
            val s2 = sumP[2]
            val s3 = sumP[3]
            val s4 = sumP[4]
            for (k in 0 until BINS) {
                val idx = base + (k shr 2)
                cnt[idx]++
                val nk = (FFT_SIZE - k) and (FFT_SIZE - 1)
                val a = re[k]
                val b = im[k]
                val c = re[nk]
                val d = im[nk]
                val lr = (a + c) * 0.5
                val li = (b - d) * 0.5
                val rr = (b + d) * 0.5
                val ri = (c - a) * 0.5
                val pl = lr * lr + li * li
                val pr = rr * rr + ri * ri
                val mr = (lr + rr) * 0.5
                val mi = (li + ri) * 0.5
                val sr = (lr - rr) * 0.5
                val si = (li - ri) * 0.5
                val pa = (pl + pr) * 0.5
                s0[idx] += pa
                s1[idx] += pl
                s2[idx] += pr
                s3[idx] += mr * mr + mi * mi
                s4[idx] += sr * sr + si * si
                avgAccum[k] += sqrt(pa) * SCALE
            }
        } else {
            val s0 = sumP[0]
            for (k in 0 until BINS) {
                val idx = base + (k shr 2)
                cnt[idx]++
                val p = re[k] * re[k] + im[k] * im[k]
                s0[idx] += p
                avgAccum[k] += sqrt(p) * SCALE
            }
        }
        frameCount++
    }

    private fun mergeColumns() {
        val half = COLS / 2
        for (j in 0 until half) {
            val d = j * ROWS
            val a = 2 * j * ROWS
            val b = a + ROWS
            for (r in 0 until ROWS) {
                for (v in 0 until numViews) sumP[v][d + r] = sumP[v][a + r] + sumP[v][b + r]
                cnt[d + r] = cnt[a + r] + cnt[b + r]
            }
        }
        for (v in 0 until numViews) Arrays.fill(sumP[v], half * ROWS, COLS * ROWS, 0.0)
        Arrays.fill(cnt, half * ROWS, COLS * ROWS, 0)
        hopsPerCol *= 2
        usedCols = (usedCols + 1) / 2
    }

    private fun drFor(c: Int): Double? {
        val n = drRms2[c].size
        val tf = totalFrames.toDouble()
        if (n == 0) { // shorter than 3 s: whole file is one block
            val r2 = sumSq[c] / tf
            if (r2 <= 0.0 || peak[c] <= 0.0) return null
            return toDb(peak[c]) - toDb(sqrt(2.0 * r2))
        }
        val rms2 = DoubleArray(n) { drRms2[c][it] }
        val pks = DoubleArray(n) { drPk[c][it] }
        rms2.sort()
        pks.sort()
        val top = max(1, (n * 0.2).toInt())
        var s = 0.0
        for (i in n - top until n) s += rms2[i]
        val rmsTop = sqrt(2.0 * s / top) // +3 dB like the DR meter
        val p2 = if (n >= 2) pks[n - 2] else pks[n - 1]
        if (rmsTop <= 0.0 || p2 <= 0.0) return null
        return toDb(p2) - toDb(rmsTop)
    }

    private fun detectCutoff(avg: DoubleArray, sr: Int): CutoffResult {
        val n = avg.size
        val binHz = (sr / 2.0) / n
        val raw = DoubleArray(n) { toDb(avg[it]) }
        val dbs = DoubleArray(n) { k ->
            var s = 0.0
            var c = 0
            for (j in max(0, k - 2)..min(n - 1, k + 2)) {
                s += raw[j]
                c++
            }
            s / c
        }
        var peakDb = Double.NEGATIVE_INFINITY
        for (v in dbs) if (v > peakDb) peakDb = v
        val top = dbs.copyOfRange((n * 0.9).toInt(), n).sorted()
        val noise = top[top.size / 2]
        val thr = max(noise + 8.0, peakDb - 65.0)
        var cb = n - 1
        for (k in n - 1 downTo 1) {
            if (dbs[k] > thr && dbs[k - 1] > thr) {
                cb = k
                break
            }
        }
        val before = max(0, cb - (200 / binHz).roundToInt())
        val after = min(n - 1, cb + max(1, (1000 / binHz).roundToInt()))
        val drop = dbs[before] - dbs[after]
        return CutoffResult(cb * binHz, sr / 2.0, drop > 25.0, drop)
    }

    fun finish(): AnalysisResult {
        if (frameIndex == 0 && fill > 0) { // very short file: zero-pad one frame
            for (c in 0 until specCh) Arrays.fill(winBuf[c], fill, FFT_SIZE, 0f)
            onFrame()
        }
        check(totalFrames > 0 && frameCount > 0) { "Not enough audio data to analyze" }

        val tf = totalFrames.toDouble()
        val chStats = (0 until numCh).map { c ->
            ChannelStats(
                peakDb = toDb(peak[c]),
                rmsDb = toDb(sqrt(sumSq[c] / tf)),
                truePeakDb = toDb(max(truePeak[c], peak[c])),
                clipSamples = clipSamples[c],
                clipRuns = clipRuns[c],
                dcOffset = dcSum[c] / tf,
                dr = drFor(c)
            )
        }
        val peakLin = peak.maxOrNull() ?: 0.0
        val tpLin = max(truePeak.maxOrNull() ?: 0.0, peakLin)
        val rmsLin = sqrt(sumSq.sum() / (tf * numCh))
        val drs = chStats.mapNotNull { it.dr }
        val loud = computeLoudness(subs)
        val bits =
            if (trackBits && bitOr != 0L) (24 - java.lang.Long.numberOfTrailingZeros(bitOr)).coerceIn(1, 24) else null

        val stereo = if (numCh >= 2 && sLL > 0.0 && sRR > 0.0) {
            StereoStats(
                correlation = sLR / sqrt(sLL * sRR),
                balanceDb = 10.0 * log10(sLL / sRR),
                midDb = 10.0 * log10(max(sMid / tf, 1e-18)),
                sideDb = 10.0 * log10(max(sSide / tf, 1e-18)),
                dualMono = sSide <= sMid * 1e-9
            )
        } else null

        val fc = frameCount.toDouble()
        val avg = DoubleArray(BINS) { avgAccum[it] / fc }
        val cutoff = detectCutoff(avg, sampleRate)
        val avgDb = FloatArray(ROWS) { r ->
            var s = 0.0
            for (j in 0 until 4) s += avg[r * 4 + j]
            toDb(s / 4.0).toFloat()
        }

        val cols = usedCols
        fun matrix(v: Int): ChannelSpectrogram {
            val out = FloatArray(cols * ROWS)
            val sp = sumP[v]
            for (i in out.indices) {
                val n = cnt[i]
                out[i] = if (n > 0) (10.0 * log10(max(sp[i] * SCALE_SQ / n, 1e-18))).toFloat() else -180f
            }
            return ChannelSpectrogram(out, cols, ROWS)
        }
        val views = linkedMapOf("all" to matrix(0))
        if (specCh == 2) {
            views["ch1"] = matrix(1)
            views["ch2"] = matrix(2)
            views["mid"] = matrix(3)
            views["side"] = matrix(4)
        }

        return AnalysisResult(
            sampleRate = sampleRate,
            channelCount = numCh,
            totalFrames = totalFrames,
            channels = chStats,
            peakDb = toDb(peakLin),
            truePeakDb = toDb(tpLin),
            rmsDb = toDb(rmsLin),
            clipSamples = clipSamples.sum(),
            clipRuns = clipRuns.sum(),
            dr = if (drs.isEmpty()) null else drs.average(),
            lufs = loud.lufs,
            lra = loud.lra,
            maxMomentary = loud.maxMomentary,
            effectiveBits = bits,
            stereo = stereo,
            cutoff = cutoff,
            avgSpectrumDb = avgDb,
            views = views
        )
    }
}
