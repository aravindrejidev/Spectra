package com.aravind.spectra.dsp

import kotlin.math.*

internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

/** ITU-R BS.1770 K-weighting (high-shelf + RLB high-pass), valid for any sample rate. */
internal fun makeKWeighting(fs: Double): Array<Biquad> {
    var f0 = 1681.974450955533
    val g = 3.999843853973347
    var q = 0.7071752369554196
    var k = tan(PI * f0 / fs)
    val vh = 10.0.pow(g / 20.0)
    val vb = vh.pow(0.4996667741545416)
    var a0 = 1.0 + k / q + k * k
    val shelf = Biquad(
        (vh + vb * k / q + k * k) / a0,
        2.0 * (k * k - vh) / a0,
        (vh - vb * k / q + k * k) / a0,
        2.0 * (k * k - 1.0) / a0,
        (1.0 - k / q + k * k) / a0
    )
    f0 = 38.13547087602444
    q = 0.5003270373238773
    k = tan(PI * f0 / fs)
    a0 = 1.0 + k / q + k * k
    val highPass = Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
    return arrayOf(shelf, highPass)
}

/**
 * True-peak meter: 4x oversampling where each interpolated point (1/4, 1/2, 3/4 between two
 * samples) is a 48-tap Kaiser-windowed sinc. Point 0 is the sample itself (covered by the sample
 * peak). Every sample is evaluated, nothing is gated or skipped.
 * Measured against an ideal FFT reconstruction: mean error ~0.03 dB, worst case ~0.1 dB.
 */
internal class TruePeakMeter(private val channels: Int) {

    companion object {
        private const val K = 24
        private const val TAPS = 2 * K
        private const val TAIL = TAPS - 1
        private const val BETA = 10.0

        private fun bessel0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val q = x * x / 4.0
            var k = 1
            while (k < 60) {
                term *= q / (k.toDouble() * k)
                sum += term
                if (term < 1e-14 * sum) break
                k++
            }
            return sum
        }

        private fun buildCoef(): DoubleArray {
            val out = DoubleArray(3 * TAPS)
            val w = K + 0.5
            val i0b = bessel0(BETA)
            for (p in 1..3) {
                val f = p / 4.0
                val row = DoubleArray(TAPS)
                var sum = 0.0
                for (t in 0 until TAPS) {
                    val u = (t - (K - 1)) - f
                    val s = if (abs(u) < 1e-12) 1.0 else sin(PI * u) / (PI * u)
                    val win = if (abs(u) < w) bessel0(BETA * sqrt(max(0.0, 1.0 - (u / w) * (u / w)))) / i0b else 0.0
                    row[t] = s * win
                    sum += row[t]
                }
                for (t in 0 until TAPS) out[(p - 1) * TAPS + t] = row[t] / sum
            }
            return out
        }

        private val coef: DoubleArray = buildCoef()
    }

    /** Highest interpolated peak per channel (linear). */
    val peaks = DoubleArray(channels)
    private val tails = Array(channels) { FloatArray(TAIL) }
    private var ext = FloatArray(0)

    fun process(data: Array<FloatArray>, frames: Int) {
        if (frames <= 0) return
        val need = frames + TAIL
        if (ext.size < need) ext = FloatArray(need)
        val e = ext
        val cf = coef
        for (c in 0 until channels) {
            System.arraycopy(tails[c], 0, e, 0, TAIL)
            System.arraycopy(data[c], 0, e, TAIL, frames)
            var tp = peaks[c]
            for (n in TAIL until need) {
                val base = n - TAIL
                var o = 0
                for (p in 0 until 3) {
                    var s = 0.0
                    for (t in 0 until TAPS) s += e[base + t] * cf[o + t]
                    val v = abs(s)
                    if (v > tp) tp = v
                    o += TAPS
                }
            }
            peaks[c] = tp
            System.arraycopy(e, frames, tails[c], 0, TAIL)
        }
    }
}

internal class DoubleList {
    var data = DoubleArray(1024)
        private set
    var size = 0
        private set

    fun add(v: Double) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Double = data[i]
}

internal data class LoudnessResult(val lufs: Double?, val lra: Double?, val maxMomentary: Double?)

/** [subs] = K-weighted mean-square (summed over channels) of consecutive 100 ms blocks. */
internal fun computeLoudness(subs: DoubleList): LoudnessResult {
    val n = subs.size
    if (n < 4) return LoudnessResult(null, null, null)
    val pre = DoubleArray(n + 1)
    for (i in 0 until n) pre[i + 1] = pre[i] + subs[i]
    fun win(i: Int, len: Int): Double = (pre[i + len] - pre[i]) / len
    fun lk(ms: Double): Double = -0.691 + 10.0 * log10(ms)

    var sum = 0.0
    var cnt = 0
    var maxM = Double.NEGATIVE_INFINITY
    for (i in 0..n - 4) {
        val v = win(i, 4)
        if (v <= 0.0) continue
        val l = lk(v)
        if (l > maxM) maxM = l
        if (l > -70.0) {
            sum += v
            cnt++
        }
    }
    if (cnt == 0) return LoudnessResult(null, null, null)
    val gate = max(lk(sum / cnt) - 10.0, -70.0)
    var s2 = 0.0
    var c2 = 0
    for (i in 0..n - 4) {
        val v = win(i, 4)
        if (v > 0.0 && lk(v) > gate) {
            s2 += v
            c2++
        }
    }
    val lufs = if (c2 > 0) lk(s2 / c2) else null

    var lra: Double? = null
    if (n >= 30) {
        val st = ArrayList<Double>()
        var sSum = 0.0
        for (i in 0..n - 30) {
            val v = win(i, 30)
            if (v > 0.0 && lk(v) > -70.0) {
                st.add(v)
                sSum += v
            }
        }
        if (st.size > 1) {
            val g2 = max(lk(sSum / st.size) - 20.0, -70.0)
            val ls = st.map { lk(it) }.filter { it > g2 }.sorted()
            if (ls.size > 1) {
                val lo = ls[((ls.size - 1) * 0.10).roundToInt()]
                val hi = ls[((ls.size - 1) * 0.95).roundToInt()]
                lra = hi - lo
            }
        }
    }
    return LoudnessResult(lufs, lra, if (maxM.isFinite()) maxM else null)
}
