package com.aravind.spectra.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.dsp.AnalysisResult
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.dsp.Verdict
import com.aravind.spectra.dsp.VerdictEngine
import com.aravind.spectra.model.UiState
import com.aravind.spectra.ui.theme.SpectraColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun sevColor(s: Severity): Color = when (s) {
    Severity.GOOD -> SpectraColors.Green
    Severity.INFO -> SpectraColors.Blue
    Severity.WARN -> SpectraColors.Amber
    Severity.BAD -> SpectraColors.Red
}

/** Decodes cover art pre-downsampled so huge embedded scans can't blow up memory. */
    internal fun decodeSampledBitmap(bytes: ByteArray, reqSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    while (w / 2 >= reqSizePx && h / 2 >= reqSizePx) {
        w /= 2
        h /= 2
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

@Composable
fun SourceUnit(s: UiState.Success, onNew: () -> Unit, onShare: () -> Unit) {
    RackPanel("Source", lamp = SpectraColors.Green) {
        GlassScreen {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val bytes = s.tags.coverArt
                val bmp = remember(bytes) { bytes?.let { decodeSampledBitmap(it, 200)?.asImageBitmap() } }
                if (bmp != null) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(SpectraColors.MetalEdgeDark, SpectraColors.MetalLo)))
                            .padding(3.dp)
                    ) {
                        Image(
                            bmp,
                            "Cover art",
                            Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Lcd(s.tags.title ?: s.fileName, size = 16.sp, weight = FontWeight.Bold, maxLines = 2)
                    s.tags.artist?.let { Lcd(it, color = SpectraColors.Amber, size = 13.sp, maxLines = 1) }
                    s.tags.album?.let { Lcd(it, color = SpectraColors.PhosphorDim, size = 12.sp, maxLines = 1) }
                    Spacer(Modifier.height(4.dp))
                    Lcd(
                        "${codecName(s.info.codecMime)} · ${fmtBytes(s.fileSizeBytes)}",
                        color = SpectraColors.PhosphorDim,
                        size = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
        val items = buildList {
            s.tags.year?.let { add(Readout("Released", it)) }
            s.tags.genre?.let { add(Readout("Genre", it)) }
            s.tags.trackNumber?.let { add(Readout("Track", it)) }
            s.tags.discNumber?.let { add(Readout("Disc", it)) }
        }
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ReadoutGrid(items)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HwButton("EJECT", Modifier.weight(1f), onClick = onNew)
            HwButton("SHARE", Modifier.weight(1f), onClick = onShare)
        }
    }
}

@Composable
fun VerdictUnit(v: Verdict) {
    val tone = sevColor(v.severity)
    RackPanel("Authenticity", lamp = tone) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(tone, true, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Lcd(
                        v.headline.uppercase(),
                        Modifier.weight(1f),
                        color = tone,
                        size = 18.sp,
                        weight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    v.summary,
                    color = SpectraColors.Phosphor.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
                v.confidence?.let { c ->
                    Spacer(Modifier.height(12.dp))
                    Lcd("EVIDENCE $c%", color = tone, size = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LedLadder(c.toDouble(), 0.0, 100.0, segments = 20, tint = tone)
                }
                v.estimatedSource?.let {
                    Spacer(Modifier.height(10.dp))
                    Lcd("SOURCE: $it", color = SpectraColors.Amber, size = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        v.findings.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Led(sevColor(f.severity), true, 10.dp, Modifier.padding(top = 3.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Engraved(f.label.uppercase(), size = 11.sp, spacing = 1.5.sp)
                    Text(f.text, color = SpectraColors.Ink, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
fun SpectrogramUnit(
    a: AnalysisResult,
    view: String,
    onView: (String) -> Unit,
    logScale: Boolean,
    onLog: (Boolean) -> Unit,
    minDb: Float,
    onMinDb: (Float) -> Unit
) {
    RackPanel("Spectrogram", lamp = SpectraColors.Phosphor) {
        val names = listOf(
            "all" to (if (a.channelCount == 1) "MONO" else "L+R"),
            "ch1" to "L",
            "ch2" to "R",
            "mid" to "MID",
            "side" to "SIDE"
        ).filter { a.views.containsKey(it.first) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { (key, label) ->
                HwButton(label, Modifier.weight(1f), active = view == key, led = SpectraColors.Phosphor, height = 54.dp, compact = true) {
                    onView(key)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val spec = a.views[view] ?: a.views.getValue("all")
        GlassScreen {
            SpectrogramPane(spec, a.sampleRate / 2.0, a.durationSec, logScale, minDb, 0f)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Engraved("FREQ SCALE", Modifier.width(92.dp), size = 11.sp, spacing = 1.5.sp)
            HwButton("LIN", Modifier.weight(1f), active = !logScale, height = 36.dp) { onLog(false) }
            HwButton("LOG", Modifier.weight(1f), active = logScale, height = 36.dp) { onLog(true) }
        }
        Spacer(Modifier.height(8.dp))
        Engraved("FLOOR  ${minDb.roundToInt()} dB", size = 11.sp, spacing = 1.5.sp)
        HwFader(minDb, onMinDb, -130f..-40f)
    }
}

@Composable
fun SpectrumUnit(a: AnalysisResult) {
    RackPanel("Average Spectrum", lamp = SpectraColors.Phosphor) {
        GlassScreen {
            AvgSpectrumPlot(a.avgSpectrumDb, a.sampleRate / 2.0, a.cutoff.cutoffHz, a.cutoff.isSharp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "A brick-wall drop (amber line) well below Nyquist is the classic fingerprint of a lossy encode.",
            color = SpectraColors.InkSoft,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun LevelsUnit(a: AnalysisResult) {
    val over = a.truePeakDb > 0.0
    RackPanel("Loudness & Levels", lamp = if (over) SpectraColors.Red else SpectraColors.Green) {
        VuMeter(a.lufs?.toFloat(), -36f, 0f, -10f, "LUFS")
        Spacer(Modifier.height(12.dp))
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                a.channels.forEachIndexed { i, ch ->
                    val name = if (a.channelCount == 2) (if (i == 0) "L" else "R") else "CH${i + 1}"
                    Column {
                        Lcd(
                            "$name  PK ${f1(ch.peakDb)}  TP ${f1(ch.truePeakDb)}",
                            color = if (ch.truePeakDb > 0.0) SpectraColors.Red else SpectraColors.Phosphor,
                            size = 11.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        LedLadder(ch.peakDb, -60.0, 0.0)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val drTone = when {
            a.dr == null -> SpectraColors.Phosphor
            a.dr <= 7.0 -> SpectraColors.Amber
            else -> SpectraColors.Phosphor
        }
        val items = mutableListOf(
            Readout("Integrated", fmtLufs(a.lufs)),
            Readout("Loud. range", a.lra?.let { "${f1(it)} LU" } ?: "—"),
            Readout("Max momentary", fmtLufs(a.maxMomentary)),
            Readout("Dynamic range", a.dr?.let { "DR${it.roundToInt()}" } ?: "—", drTone),
            Readout("Sample peak", fmtDb(a.peakDb)),
            Readout("True peak", fmtDb(a.truePeakDb), if (over) SpectraColors.Red else SpectraColors.Phosphor),
            Readout("RMS", fmtDb(a.rmsDb)),
            Readout(
                "Clipping",
                when {
                    a.clipRuns > 0 -> "${a.clipRuns} runs"
                    a.clipSamples > 0 -> "${a.clipSamples} touches"
                    else -> "none"
                },
                if (a.clipRuns > 0) SpectraColors.Amber else SpectraColors.Phosphor
            )
        )
        a.stereo?.let { st ->
            items.add(Readout("Correlation", f2(st.correlation), if (st.correlation < 0.0) SpectraColors.Amber else SpectraColors.Phosphor))
            items.add(Readout("Side vs mid", if (st.dualMono) "mono" else "${f1(st.sideDb - st.midDb)} dB"))
            items.add(Readout("Balance L/R", "${f1(st.balanceDb)} dB"))
        }
        ReadoutGrid(items)
    }
}

@Composable
fun DetailsUnit(s: UiState.Success) {
    val a = s.analysis
    val lossless = VerdictEngine.isLossless(s.info.codecMime)
    RackPanel("Technical Data") {
        s.info.note?.let {
            Text("FFmpeg not used: $it", color = SpectraColors.Ink, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
        }
        ReadoutGrid(
            buildList {
                add(Readout("Codec", codecName(s.info.codecMime)))
                add(Readout("Sample rate", fmtHz(a.sampleRate.toDouble())))
                add(Readout("Channels", a.channelCount.toString()))
                add(Readout("Decoder", s.info.engine))
                add(Readout("Decoded as", s.info.encoding))
                add(
                    Readout(
                        "Bit depth",
                        if (!lossless) "n/a (lossy)" else when {
                            s.info.declaredBits != null && a.effectiveBits != null ->
                                "${s.info.declaredBits} (eff. ${a.effectiveBits})"
                            a.effectiveBits != null -> "${a.effectiveBits}-bit eff."
                            else -> "16-bit (decoder)"
                        }
                    )
                )
                add(Readout("Duration", fmtDurationSec(a.durationSec)))
                add(Readout("Nyquist", fmtHz(a.sampleRate / 2.0)))
                add(Readout("Cutoff edge", if (a.cutoff.hasEdge) fmtHz(a.cutoff.cutoffHz) else "none found"))
                if (a.cutoff.hasEdge) {
                add(Readout("Edge steepness", "${f1(a.cutoff.strengthDb)} dB/300Hz"))
                a.cutoff.lockRatio?.let { add(Readout("HF persistence", "${(it * 100).roundToInt()} %")) }
                }
                add(Readout("File size", fmtBytes(s.fileSizeBytes)))
                add(Readout("Frames", a.totalFrames.toString()))
                add(Readout("DC offset", f2(a.channels.maxOf { abs(it.dcOffset) } * 100) + " %"))
                s.tags.bitrateBps?.let { add(Readout("Tag bitrate", "${it / 1000} kbps")) }
                add(Readout("Analysis time", String.format(Locale.US, "%.1f s", s.tookMs / 1000.0)))
            }
        )
    }
}
