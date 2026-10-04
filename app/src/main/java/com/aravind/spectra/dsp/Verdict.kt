package com.aravind.spectra.dsp

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Severity { GOOD, INFO, WARN, BAD }

data class Finding(val label: String, val severity: Severity, val text: String)

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

object VerdictEngine {
    private val LOSSLESS = setOf("audio/flac", "audio/raw", "audio/alac", "audio/x-wav")
    private val KNOWN_LOWPASS = doubleArrayOf(15500.0, 16000.0, 17000.0, 17500.0, 18000.0, 19000.0, 19500.0, 20000.0)

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

    fun evaluate(a: AnalysisResult, codecMime: String?, codecLabel: String): Verdict {
        val nyq = a.sampleRate / 2.0
        val cut = a.cutoff.cutoffHz
        val ratio = cut / nyq
        val bandLimited = ratio < 0.92 && a.cutoff.isSharp
        val lossless = isLossless(codecMime)
        val matchesLowpass = KNOWN_LOWPASS.any { abs(it - cut) < 350.0 }
        val dropBonus = min(25.0, max(0.0, a.cutoff.dropDb - 25.0)).toInt()
        val fakeConf = min(98, 58 + dropBonus + (if (matchesLowpass) 15 else 0))

        val auth: Auth = when {
            lossless && bandLimited && a.sampleRate >= 88200 && cut in 20500.0..24500.0 -> Auth(
                Severity.WARN, "Upsampled hi-res",
                "Real content stops at ${khz(cut)}, far below the ${khz(nyq)} Nyquist limit. " +
                    "Looks like a 44.1 / 48 kHz master resampled to ${khz(a.sampleRate.toDouble())}.",
                min(95, 62 + dropBonus), "44.1 / 48 kHz master"
            )
            lossless && bandLimited -> Auth(
                Severity.BAD, "Fake lossless",
                "The spectrum falls off a cliff at ${khz(cut)} (${n1(a.cutoff.dropDb)} dB drop) while the file claims " +
                    "${khz(nyq)} of bandwidth. Typical of a lossy encode converted to lossless.",
                fakeConf, guessLossyBitrate(cut) + " lossy file"
            )
            lossless && ratio < 0.92 -> Auth(
                Severity.INFO, "Band-limited",
                "Energy fades smoothly above ${khz(cut)}. No encoder-style brick wall, so this is likely natural " +
                    "to the recording or mastering.",
                null, null
            )
            lossless -> Auth(
                Severity.GOOD, "Authentic lossless",
                "Content reaches ${khz(cut)} with no brick-wall cutoff. Nothing here suggests a lossy origin.",
                null, null
            )
            else -> Auth(
                Severity.INFO, "Lossy source",
                "$codecLabel is a lossy format, so a low-pass is expected. Detected bandwidth: ${khz(cut)}.",
                null, if (bandLimited) guessLossyBitrate(cut) else null
            )
        }

        val f = ArrayList<Finding>()
        f += Finding("Bandwidth", auth.severity, "Cutoff ${khz(cut)} · ${(ratio * 100).roundToInt()}% of Nyquist")

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

        a.dr?.let { dr ->
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
