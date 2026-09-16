package com.aravind.spectra.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.model.UiState
import com.aravind.spectra.ui.theme.SpectraColors

@Composable
fun AnalyzerScreen(viewModel: AnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val channelView by viewModel.channelView.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.analyzeFile(context, uri)
    }

    Surface(color = SpectraColors.Void, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Spectra", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SpectraColors.Text1)
            Text(
                "Upload a track. See exactly what's really in it.",
                color = SpectraColors.Text2,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is UiState.Idle -> IdleCard { pickFileLauncher.launch(arrayOf("audio/*")) }
                is UiState.Loading -> LoadingCard(s.step)
                is UiState.Error -> ErrorCard(s.message) { viewModel.reset() }
                is UiState.Success -> {
                    FileChip(s.fileName, s.fileSizeBytes) { viewModel.reset() }
                    MetadataCardContent(s)
                    QualityCardContent(s)
                    SpectrogramCardContent(s, channelView, viewModel::setChannelView)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun IdleCard(onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SpectraColors.Card,
        border = BorderStroke(1.5.dp, SpectraColors.Border)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\u25D0", fontSize = 28.sp, color = SpectraColors.Text2)
            Spacer(Modifier.height(8.dp))
            Text("Tap to choose an audio file", color = SpectraColors.Text1, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("FLAC · WAV · MP3 · AAC · OGG · Opus", color = SpectraColors.Text3, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun LoadingCard(step: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = SpectraColors.Accent,
            trackColor = SpectraColors.CardAlt
        )
        Spacer(Modifier.height(10.dp))
        Text(step, color = SpectraColors.Text2, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SpectraColors.WarnSoft),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Couldn't analyze this file", color = Color(0xFFFFC4C4), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Color(0xFFFFC4C4), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss) { Text("Try another file", color = SpectraColors.Accent) }
        }
    }
}

@Composable
private fun FileChip(name: String, size: Long, onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(SpectraColors.Card, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$name · ${fmtBytes(size)}",
            color = SpectraColors.Text2,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onReset) { Text("analyze another", fontSize = 12.sp, color = SpectraColors.Text3) }
    }
}

/** Standard two-pass downsampled bitmap decode. Some FLAC rips embed very
 *  large cover scans (several thousand px per side); decoding that at full
 *  resolution for a 72dp thumbnail can allocate tens of MB for one bitmap,
 *  which stacks with the audio already decoded in memory and can tip a
 *  device into OOM on a longer track. See CHANGELOG.md. */
private fun decodeSampledBitmap(bytes: ByteArray, reqSizePx: Int): android.graphics.Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    var sampleSize = 1
    var w = boundsOptions.outWidth
    var h = boundsOptions.outHeight
    while (w / 2 >= reqSizePx && h / 2 >= reqSizePx) {
        w /= 2; h /= 2; sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
}

@Composable
private fun MetadataCardContent(s: UiState.Success) {
    SectionCard("Metadata") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bytes = s.tags.coverArt
            if (bytes != null) {
                val bmp = remember(bytes) {
                    decodeSampledBitmap(bytes, reqSizePx = 200)?.asImageBitmap()
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Cover art",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
            Column {
                Text(
                    s.tags.title ?: s.fileName,
                    color = SpectraColors.Text1,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = s.tags.artist
                if (artist != null) {
                    Text(artist, color = SpectraColors.Text2, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        s.tags.album?.let { KeyValueRow("Album", it) }
        s.tags.trackNumber?.let { KeyValueRow("Track", it) }
        s.tags.discNumber?.let { KeyValueRow("Disc", it) }
        s.tags.durationMs?.let { KeyValueRow("Duration", fmtDurationMs(it)) }
        s.tags.year?.let { KeyValueRow("Release date", it) }
        s.tags.genre?.let { KeyValueRow("Genre", it) }
        s.tags.bitrateBps?.let { KeyValueRow("Tag bitrate", "${it / 1000} kbps") }
    }
}

@Composable
private fun QualityCardContent(s: UiState.Success) {
    val a = s.analysis
    val decoded = s.decoded

    SectionCard("Audio Quality Analysis") {
        StatGrid(
            listOf(
                "Codec" to (s.tags.mimeType ?: "—"),
                "Sample Rate" to fmtHz(decoded.sampleRate.toDouble()),
                "Channels" to decoded.channelCount.toString() + when (decoded.channelCount) {
                    2 -> " (stereo)"
                    1 -> " (mono)"
                    else -> ""
                },
                "Decoded Format" to "PCM16 (MediaCodec)",
                "Duration" to fmtDurationSec(a.totalSamples / decoded.sampleRate.toDouble()),
                "Nyquist" to fmtHz(decoded.sampleRate / 2.0),
                "Size" to fmtBytes(s.fileSizeBytes),
                "Samples" to a.totalSamples.toString(),
                "Dynamic Range" to fmtDb(a.overallStats.dr),
                "Peak" to fmtDb(a.overallStats.peakDb),
                "RMS" to fmtDb(a.overallStats.rmsDb),
                "Clipping" to if (a.overallStats.clipCount > 0) "${a.overallStats.clipCount} samples" else "No clipping",
                "Spectral Cutoff" to fmtHz(a.cutoff.cutoffHz)
            )
        )

        val nyquistRatio = a.cutoff.cutoffHz / (decoded.sampleRate / 2.0)
        val suspicious = a.cutoff.isSharp && nyquistRatio < 0.92
        FlagBanner(
            text = if (suspicious)
                "Spectrum cuts off sharply around ${fmtHz(a.cutoff.cutoffHz)}, well below the ${fmtHz(decoded.sampleRate / 2.0)} Nyquist limit — consistent with a lossy source that was upsampled or repackaged as lossless."
            else
                "Full bandwidth up to ${fmtHz(a.cutoff.cutoffHz)} — no sign of an artificial lossy cutoff.",
            isWarning = suspicious
        )

        Spacer(Modifier.height(10.dp))
        a.perChannelStats.forEachIndexed { i, stat ->
            KeyValueRow(
                "Ch ${i + 1}",
                "P ${"%.1f".format(stat.peakDb)} / R ${"%.1f".format(stat.rmsDb)} / DR ${"%.1f".format(stat.dr)}"
            )
        }
    }
}

@Composable
private fun SpectrogramCardContent(s: UiState.Success, channelView: String, onViewChange: (String) -> Unit) {
    SectionCard("Spectrogram") {
        val hasCh2 = s.analysis.views.containsKey("ch2")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelPill("Channels", channelView == "all") { onViewChange("all") }
            if (hasCh2) {
                ChannelPill("Ch 1", channelView == "ch1") { onViewChange("ch1") }
                ChannelPill("Ch 2", channelView == "ch2") { onViewChange("ch2") }
            }
        }
        Spacer(Modifier.height(12.dp))
        val spectrogram = s.analysis.views[channelView] ?: s.analysis.views.getValue("all")
        SpectrogramView(
            spectrogram,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Sample Rate: ${s.decoded.sampleRate} Hz",
                color = SpectraColors.Text3,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Nyquist: ${fmtHz(s.decoded.sampleRate / 2.0)}",
                color = SpectraColors.Text3,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ChannelPill(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (active) SpectraColors.AccentSoft else SpectraColors.CardAlt,
        border = BorderStroke(1.dp, if (active) SpectraColors.Accent else SpectraColors.Border)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (active) Color(0xFFD9D3FF) else SpectraColors.Text2,
            fontSize = 12.5.sp
        )
    }
}
