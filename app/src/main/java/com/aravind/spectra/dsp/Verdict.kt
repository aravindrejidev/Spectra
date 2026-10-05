package com.aravind.spectra.dsp

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Severity { GOOD, INFO, WARN, BAD }

data class Finding(val label: String, val severity: Severity, val text: String)

/** confidence = strength of evidence for the stated call (rule-based score, not a probability). */
data class Verdict(
    val severity: Severity,
    val headline: String,
    val summary: String,
    val confidence: Int?,
    val estimatedSource: String?,
    val findings: List<Finding>
)

private data class Auth(
    val severity: Severity,
    val headline: String,
    val summary: String,
    val confidence: Int?,
    val source: String?
)

private fun khz(hz: Double): String = String.format(Locale.US, "%.1f kHz", hz / 1000.0)
private fun n1(x: Double): String = String.format(Locale.US, "%.1f", x)
private fun n2(x: Double): String = String.format(Locale.US, "%.2f", x)
private fun pct(x: Double): String = "${(x * 100).roundToInt()}%"

object VerdictEngine {
    private val LOSSLESS = setOf("audio/flac", "audio/raw", "audio/alac", "audio/x-wav")
    private val SIGNATURES = doubleArrayOf(15500.0, 16000.0, 17000.0, 17500.0, 18000.0, 19000.0)
    private val STANDARD_RATES = intArrayOf(32000, 44100, 48000, 88200, 96000, 176400, 192000)

    fun isLossless(mime: String?): Boolean = mime != null && mime.lowercase() in LOSSLESS

    fun guessLossyBitrate(hz: Double): String = when {
        hz < 12000 -> "≈ 64 kbps or lower"
        hz < 15000 -> "≈ 96 kbps"
        hz < 16800 -> "≈ 128 kbps"
        hz < 17800 -> "≈ 160 kbps"
        hz < 19000 -> "≈ 192 kbps"
        hz < 19900 -> "≈ 224–256 kbps"
        hz < 20700 -> "≈ 256–320 kbps"
        else -> "320 kbps or higher"
    }

    private fun sourceRate(cut: Double): Int {
        for (r in STANDARD_RATES) if (r / 2.0 >= cut * 0.985) return r
        return STANDARD_RATES.last()
    }

    private fun rateLabel(r: Int): String =
        if (r % 1000 == 0) "${r / 1000} kHz" else String.format(Locale.US, "%.1f kHz", r / 1000.0)

    private fun lossyContainer(c: CutoffResult, codecLabel: String): Auth {
        val extra = if (c.hasEdge) "Brick-wall edge at ${khz(c.cutoffHz)}." else "No brick-wall cutoff (gradual roll-off)."
        return Auth(
            Severity.INFO, "Lossy source",
            "$codecLabel is a lossy format, so a low-pass is expected. $extra",
            null, if (c.hasEdge) guessLossyBitrate(c.cutoffHz) else null
        )
    }

    private fun upsampled(a: AnalysisResult, c: CutoffResult): Auth {
        val src = sourceRate(c.cutoffHz)
        val bonus = max(0, min(12, ((c.strengthDb - 20.0) / 2.0).toInt()))
        return Auth(
            Severity.WARN, "Upsampled hi-res",
            "Content stops at ${khz(c.cutoffHz)}, only ${pct(c.cutoffHz / c.nyquist)} of the ${khz(c.nyquist)} limit. " +
                "Typical of a ${rateLabel(src)} master resampled to ${rateLabel(a.sampleRate)}.",
            min(95, 78 + bonus), "${rateLabel(src)} master"
        )
    }

    /** Hard edge between 19.5 and 21 kHz: CD-era anti-alias filters and 256-320 kbps encodes look alike. */
    private fun ambiguousEdge(c: CutoffResult): Auth {
        val lock = c.lockRatio
        var s = 30
        if (lock != null) s += when {
            lock < 0.5 -> 20
            lock < 0.8 -> 8
            else -> -10
        }
        s = s.coerceIn(10, 60)
        return if (s >= 50) {
            Auth(
                Severity.WARN, "Possibly lossy",
                "Hard low-pass at ${khz(c.cutoffHz)} and high frequencies don't persist up to the edge (${pct(lock ?: 0.0)}). " +
                    "Common in 256–320 kbps encodes, but anti-alias filtered masters can look similar.",
                s, null
            )
        } else {
            Auth(
                Severity.INFO, "Inconclusive low-pass",
                "A hard edge at ${khz(c.cutoffHz)} is typical of CD-era anti-alias filters, but 256–320 kbps lossy " +
                    "encodes look similar. Nothing else here points to a lossy origin.",
                null, null
            )
        }
    }

    /** Hard edge below 19.5 kHz: rare in genuine masters, typical of lossy encoders. */
    private fun lowEdge(c: CutoffResult): Auth {
        val lock = c.lockRatio
        var s = if (c.cutoffHz >= 12000.0) 62 else 50
        if (c.strengthDb >= 30.0) s += 4
        if (lock != null) {
            if (lock < 0.5) s += 10 else if (lock >= 0.8) s -= 12
        }
        if (SIGNATURES.any { abs(it - c.cutoffHz) < 250.0 }) s += 6
        s = s.coerceIn(40, 96)
        return when {
            s >= 70 -> Auth(
                Severity.BAD, "Likely fake lossless",
                "A brick-wall low-pass at ${khz(c.cutoffHz)}, far below Nyquist, is the fingerprint of a lossy encode " +
                    "(${guessLossyBitrate(c.cutoffHz)}).",
                s, guessLossyBitrate(c.cutoffHz) + " lossy file"
            )
            s >= 50 -> Auth(
                Severity.WARN, "Suspicious low-pass",
                "Hard cutoff at ${khz(c.cutoffHz)}. Could be a lossy origin or a naturally band-limited recording.",
                s, null
            )
            else -> Auth(
                Severity.INFO, "Inconclusive low-pass",
                "Hard cutoff at ${khz(c.cutoffHz)}, but high frequencies persist up to it like in a natural recording.",
                null, null
            )
        }
    }

    fun evaluate(a: AnalysisResult, codecMime: String?, codecLabel: String): Verdict {
        val c = a.cutoff
        val ratio = c.cutoffHz / c.nyquist
        val lossless = isLossless(codecMime)

        val auth: Auth = when {
            !lossless -> lossyContainer(c, codecLabel)
            !c.hasEdge -> Auth(
                Severity.GOOD, "No lossy signature",
                "No brick-wall cutoff found; the spectrum reaches the top of the band smoothly. " +
                    "This test looks for common lossy fingerprints, so it can't prove a file is lossless.",
                null, null
            )
            a.sampleRate >= 88200 && ratio <= 0.56 && c.cutoffHz >= 18500.0 -> upsampled(a, c)
            c.nyquist < 20000.0 -> if (ratio >= 0.9) {
                Auth(Severity.GOOD, "No lossy signature", "Edge at ${khz(c.cutoffHz)} is close to Nyquist.", null, null)
            } else {
                Auth(Severity.INFO, "Band-limited", "Hard edge at ${khz(c.cutoffHz)} in a low sample-rate file; can't judge.", null, null)
            }
            c.cutoffHz >= 21000.0 -> Auth(
                Severity.GOOD, "No lossy signature",
                "The edge at ${khz(c.cutoffHz)} is a normal anti-alias filter. No lossy fingerprint found.",
                null, null
            )
            c.cutoffHz >= 19500.0 -> ambiguousEdge(c)
            else -> lowEdge(c)
        }

        val f = ArrayList<Finding>()
        f += Finding(
            "Bandwidth", auth.severity,
            if (c.hasEdge) "Hard edge at ${khz(c.cutoffHz)} (${pct(ratio)} of Nyquist)"
            else "No brick-wall cutoff, spectrum is smooth up to Nyquist"
        )
        if (c.hasEdge) {
            val lockTxt = c.lockRatio?.let { ", HF persistence ${pct(it)}" } ?: ""
            f += Finding("Edge", Severity.INFO, "Steepness ${n1(c.strengthDb)} dB per 300 Hz, drop ${n1(c.dropDb)} dB$lockTxt")
        }

        val runs = a.clipRuns
        f += when {
            runs > 500 -> Finding("Clipping", Severity.BAD, "$runs clipped runs (${a.clipSamples} samples at full scale), distortion likely")
            runs > 0 -> Finding("Clipping", Severity.WARN, "$runs clipped runs, ${a.clipSamples} samples at full scale")
            a.clipSamples > 0 -> Finding("Clipping", Severity.INFO, "${a.clipSamples} samples touch 0 dBFS, no sustained clipping")
            else -> Finding("Clipping", Severity.GOOD, "No clipping detected")
        }

        f += if (a.truePeakDb > 0.0) {
            Finding("True peak", Severity.WARN, "Inter-sample peaks reach +${n1(a.truePeakDb)} dBTP, may distort after lossy encoding or on a DAC")
        } else {
            Finding("True peak", Severity.GOOD, "${n1(a.truePeakDb)} dBTP, headroom OK")
        }

        a.dr?.takeIf { it.isFinite() }?.let { dr ->
            val d = dr.roundToInt()
            f += when {
                d <= 4 -> Finding("Dynamics", Severity.BAD, "DR$d, extremely compressed (loudness-war master)")
                d <= 7 -> Finding("Dynamics", Severity.WARN, "DR$d, heavily compressed")
                d <= 11 -> Finding("Dynamics", Severity.INFO, "DR$d, typical modern master")
                else -> Finding("Dynamics", Severity.GOOD, "DR$d, dynamics well preserved")
            }
        }

        a.lufs?.let { l ->
            val lra = a.lra?.let { " · LRA ${n1(it)} LU" } ?: ""
            f += Finding("Loudness", if (l > -8.0) Severity.WARN else Severity.INFO, "${n1(l)} LUFS integrated$lra")
        }

        a.stereo?.let { st ->
            f += when {
                st.dualMono -> Finding("Stereo", Severity.INFO, "Both channels are identical (dual mono)")
                st.correlation < 0.0 -> Finding("Stereo", Severity.WARN, "Out-of-phase content (correlation ${n2(st.correlation)}), poor mono compatibility")
                else -> Finding("Stereo", Severity.GOOD, "Correlation ${n2(st.correlation)}, side ${n1(st.sideDb - st.midDb)} dB vs mid")
            }
        }

        if (lossless) {
            a.effectiveBits?.let { b ->
                f += if (b <= 16) {
                    Finding("Bit depth", Severity.INFO, "$b effective bits. If this file is labeled 24-bit, it was padded from 16-bit")
                } else {
                    Finding("Bit depth", Severity.GOOD, "$b effective bits")
                }
            }
        }

        val dc = a.channels.maxOf { abs(it.dcOffset) }
        if (dc > 0.002) f += Finding("DC offset", Severity.WARN, "${n2(dc * 100)}% offset on at least one channel")

        return Verdict(auth.severity, auth.headline, auth.summary, auth.confidence, auth.source, f)
    }
}
