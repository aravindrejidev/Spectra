package com.aravind.spectra.dsp

import java.util.Locale
import java.util.Random
import kotlin.math.*

data class TestResult(val name: String, val expected: String, val measured: String, val pass: Boolean)

/**
 * On-device accuracy self-test: synthetic signals with analytically known answers go through
 * the real StreamAnalyzer and each measurement is compared with its expected value.
 */
object SelfTest {

    private class Check(val expected: String, val measured: String, val pass: Boolean)
    private class Case(val name: String, val run: () -> Check)

    val count: Int get() = cases.size

    fun run(onProgress: (Int, String) -> Unit): List<TestResult> {
        val out = ArrayList<TestResult>()
        for ((i, c) in cases.withIndex()) {
            onProgress(i, c.name)
            val r = try {
                c.run()
            } catch (e: Exception) {
                Check("no error", "${e.javaClass.simpleName}: ${e.message}", false)
            }
            out += TestResult(c.name, r.expected, r.measured, r.pass)
        }
        onProgress(cases.size, "")
        return out
    }

    private val cases: List<Case> = listOf(
        Case("Loudness: −23 dBFS sine, 44.1 kHz") { loudness(44100) },
        Case("Loudness: −23 dBFS sine, 48 kHz") { loudness(48000) },
        Case("Loudness: −23 dBFS sine, 96 kHz") { loudness(96000) },
        Case("Loudness: mono −20 dBFS sine") {
            val x = sine(48000, 20.0, 997.0, amp(-20.0))
            near(-23.0, 0.1, analyze(48000, arrayOf(x)).lufs, "LUFS")
        },
        Case("Gating: −36 / −23 / −36 dBFS") {
            val sr = 48000
            val x = concat(
                sine(sr, 10.0, 997.0, amp(-36.0)),
                sine(sr, 30.0, 997.0, amp(-23.0)),
                sine(sr, 10.0, 997.0, amp(-36.0))
            )
            near(-23.0, 0.1, analyze(sr, arrayOf(x, x)).lufs, "LUFS")
        },
        Case("Loudness range: −20 / −30 LUFS") {
            val sr = 48000
            val x = concat(sine(sr, 30.0, 997.0, amp(-20.0)), sine(sr, 30.0, 997.0, amp(-30.0)))
            near(10.0, 0.5, analyze(sr, arrayOf(x, x)).lra, "LU")
        },
        Case("True peak: fs/4 sine at 45°") {
            val sr = 48000
            val x = sine(sr, 2.0, sr / 4.0, 1.0, PI / 4)
            val a = analyze(sr, arrayOf(x, x))
            val ok = a.truePeakDb in -0.15..0.25 && abs(a.peakDb + 3.01) < 0.05
            Check("TP about +0.1 dBTP, sample peak −3.01 dB", "TP ${f2(a.truePeakDb)} dBTP, peak ${f2(a.peakDb)} dB", ok)
        },
        Case("True peak: 1 kHz at −6 dBFS") {
            val sr = 48000
            val x = sine(sr, 2.0, 1000.0, 0.5)
            near(-6.02, 0.05, analyze(sr, arrayOf(x, x)).truePeakDb, "dBTP")
        },
        Case("Clipping: hard-clipped sine") {
            val sr = 48000
            val x = FloatArray(sr * 5) { (1.5 * sin(2.0 * PI * 997.0 * it / sr)).coerceIn(-1.0, 1.0).toFloat() }
            near(9970.0, 10.0, analyze(sr, arrayOf(x)).clipRuns.toDouble(), "runs", 0)
        },
        Case("Dynamic range: full-scale sine") {
            val sr = 48000
            val x = sine(sr, 20.0, 997.0, 1.0)
            near(0.0, 0.3, analyze(sr, arrayOf(x, x)).dr, "DR")
        },
        Case("Stereo: identical channels") {
            val sr = 48000
            val rnd = Random(1)
            val l = FloatArray(sr * 5) { (rnd.nextGaussian() * 0.1).toFloat() }
            val st = analyze(sr, arrayOf(l, l)).stereo
            if (st == null) {
                Check("stereo statistics", "missing", false)
            } else {
                Check("correlation +1.00, dual mono", "r = ${f2(st.correlation)}, dual mono ${st.dualMono}",
                    st.correlation > 0.999 && st.dualMono)
            }
        },
        Case("Stereo: inverted channels") {
            val sr = 48000
            val rnd = Random(1)
            val l = FloatArray(sr * 5) { (rnd.nextGaussian() * 0.1).toFloat() }
            val r = FloatArray(l.size) { -l[it] }
            near(-1.0, 0.01, analyze(sr, arrayOf(l, r)).stereo?.correlation, "r")
        },
        Case("Stereo: independent noise") {
            val sr = 48000
            val r1 = Random(2)
            val r2 = Random(3)
            val l = FloatArray(sr * 5) { (r1.nextGaussian() * 0.1).toFloat() }
            val r = FloatArray(sr * 5) { (r2.nextGaussian() * 0.1).toFloat() }
            near(0.0, 0.1, analyze(sr, arrayOf(l, r)).stereo?.correlation, "r")
        },
        Case("Stereo FFT: L and R spectra stay separate") {
            val sr = 44100
            val n = sr * 5
            val l = FloatArray(n) { (0.5 * sin(2.0 * PI * 200.0 * it / 2048.0)).toFloat() }
            val r = FloatArray(n) { (0.25 * sin(2.0 * PI * 400.0 * it / 2048.0)).toFloat() }
            val a = analyze(sr, arrayOf(l, r))
            val s1 = a.views["ch1"]
            val s2 = a.views["ch2"]
            if (s1 == null || s2 == null) {
                Check("L and R views", "missing", false)
            } else {
                val col = s1.cols / 2
                val l50 = s1.dbMatrix[col * s1.rows + 50].toDouble()
                val l100 = s1.dbMatrix[col * s1.rows + 100].toDouble()
                val r100 = s2.dbMatrix[col * s2.rows + 100].toDouble()
                val r50 = s2.dbMatrix[col * s2.rows + 50].toDouble()
                val leak = min(l50 - l100, r100 - r50)
                val ok = abs(l50 + 11.08) < 1.0 && abs(r100 + 17.10) < 1.0 && leak > 80.0
                Check("L −11.1 dB, R −17.1 dB, leak over 80 dB down",
                    "L ${f1(l50)} dB, R ${f1(r100)} dB, leak ${f1(leak)} dB", ok)
            }
        },
        Case("Bit depth: 16-bit data") {
            val sr = 48000
            val x = FloatArray(sr * 3) { (Math.round(0.5 * sin(2.0 * PI * 997.0 * it / sr) * 32768.0) / 32768.0).toFloat() }
            near(16.0, 0.0, analyze(sr, arrayOf(x, x), trackBits = true).effectiveBits?.toDouble(), "bit", 0)
        },
        Case("Bit depth: 24-bit data") {
            val sr = 48000
            val x = FloatArray(sr * 3) { (Math.round(0.5 * sin(2.0 * PI * 997.0 * it / sr) * 8388608.0) / 8388608.0).toFloat() }
            near(24.0, 0.0, analyze(sr, arrayOf(x, x), trackBits = true).effectiveBits?.toDouble(), "bit", 0)
        },
        Case("DC offset: +0.10") {
            val sr = 48000
            val x = FloatArray(sr * 2) { (0.3 * sin(2.0 * PI * 997.0 * it / sr) + 0.1).toFloat() }
            near(0.1, 0.003, analyze(sr, arrayOf(x, x)).channels[0].dcOffset, "", 3)
        },
        Case("Cutoff edge: 16 kHz low-pass") {
            val sr = 44100
            val y = lowpassNoise(sr, 6.0, 16000.0, 7L)
            val c = analyze(sr, arrayOf(y, y)).cutoff
            val ok = c.hasEdge && abs(c.cutoffHz - 16000.0) <= 400.0 && c.strengthDb >= 30.0
            Check(
                "edge at 16.0 kHz ± 0.4, steepness 30 dB or more",
                if (c.hasEdge) "edge ${f1(c.cutoffHz / 1000.0)} kHz, ${f1(c.strengthDb)} dB" else "no edge found",
                ok
            )
        },
        Case("Cutoff edge: none in full-band noise") {
            val sr = 44100
            val rnd = Random(9)
            val y = FloatArray(sr * 6) { (rnd.nextGaussian() * 0.1).toFloat() }
            val c = analyze(sr, arrayOf(y, y)).cutoff
            Check("no edge", if (c.hasEdge) "edge ${f1(c.cutoffHz / 1000.0)} kHz" else "no edge", !c.hasEdge)
        }
    )

    // ------------------------------------------------------------ helpers

    private fun loudness(sr: Int): Check {
        val x = sine(sr, 20.0, 997.0, amp(-23.0))
        return near(-23.0, 0.1, analyze(sr, arrayOf(x, x)).lufs, "LUFS")
    }

    private fun analyze(sr: Int, ch: Array<FloatArray>, trackBits: Boolean = false): AnalysisResult {
        val frames = ch[0].size
        val an = StreamAnalyzer(sr, ch.size, frames.toLong(), trackBits)
        val chunk = 8192
        val buf = Array(ch.size) { FloatArray(chunk) }
        var pos = 0
        while (pos < frames) {
            val n = min(chunk, frames - pos)
            for (c in ch.indices) System.arraycopy(ch[c], pos, buf[c], 0, n)
            an.feed(buf, n)
            pos += n
        }
        return an.finish()
    }

    private fun near(expected: Double, tol: Double, measured: Double?, unit: String, digits: Int = 2): Check {
        val ok = measured != null && measured.isFinite() && abs(measured - expected) <= tol
        val f = "%.${digits}f"
        return Check(
            String.format(Locale.US, "$f ± $f $unit", expected, tol).trim(),
            if (measured == null) "—" else String.format(Locale.US, "$f $unit", measured).trim(),
            ok
        )
    }

    private fun f1(x: Double): String = String.format(Locale.US, "%.1f", x)
    private fun f2(x: Double): String = String.format(Locale.US, "%.2f", x)

    private fun amp(db: Double): Double = 10.0.pow(db / 20.0)

    private fun sine(sr: Int, seconds: Double, freq: Double, amp: Double, phase: Double = 0.0): FloatArray {
        val n = (sr * seconds).toInt()
        return FloatArray(n) { (amp * sin(2.0 * PI * freq * it / sr + phase)).toFloat() }
    }

    private fun concat(vararg parts: FloatArray): FloatArray {
        var n = 0
        for (p in parts) n += p.size
        val out = FloatArray(n)
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

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

    /** White noise through an 801-tap Kaiser (beta 12) low-pass: a clean brick-wall at [cutoffHz]. */
    private fun lowpassNoise(sr: Int, seconds: Double, cutoffHz: Double, seed: Long): FloatArray {
        val n = (sr * seconds).toInt()
        val rnd = Random(seed)
        val noise = DoubleArray(n) { rnd.nextGaussian() * 0.1 }
        val taps = 801
        val m = taps - 1
        val i0b = bessel0(12.0)
        val fc = cutoffHz / sr
        val h = DoubleArray(taps)
        var sum = 0.0
        for (i in 0 until taps) {
            val t = i - m / 2.0
            val sinc = if (abs(t) < 1e-12) 2.0 * fc else sin(2.0 * PI * fc * t) / (PI * t)
            val r = 2.0 * i / m - 1.0
            val w = bessel0(12.0 * sqrt(max(0.0, 1.0 - r * r))) / i0b
            h[i] = sinc * w
            sum += h[i]
        }
        for (i in 0 until taps) h[i] /= sum
        val out = FloatArray(n)
        for (i in 0 until n) {
            var s = 0.0
            val kmax = min(i, m)
            for (k in 0..kmax) s += h[k] * noise[i - k]
            out[i] = s.toFloat()
        }
        return out
    }
}
