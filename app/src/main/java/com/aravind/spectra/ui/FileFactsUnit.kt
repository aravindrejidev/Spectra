package com.aravind.spectra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.model.UiState
import com.aravind.spectra.ui.theme.SpectraColors

/** What the file itself says about how it was made (container, profile, bitrate, encoder, tags). */
@Composable
fun FileFactsUnit(s: UiState.Success) {
    val f = s.info.facts
    RackPanel("File details") {
        if (f == null) {
            Text(
                "Not available: this file was decoded by the phone's own decoder instead of FFmpeg.",
                color = SpectraColors.Ink,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        } else {
            val rows = ArrayList<Pair<String, String>>()
            rows += "Container" to (f.formatLong ?: f.formatName ?: "unknown")
            rows += "Codec" to (f.codecLong ?: codecName(s.info.codecMime))
            f.profile?.let { rows += "Profile" to it }
            f.bitrateKbps?.let { rows += "Bitrate" to "$it kbps" }
            f.channelLayout?.let { rows += "Channel layout" to it }
            f.sampleFormat?.let { rows += "Sample format" to it }
            rows += "Streams" to (f.streamCount.toString() + if (f.hasCoverStream) " (incl. cover art)" else "")
            f.encoder?.let { rows += "Encoder" to it }

            GlassScreen {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, v) in rows) FactRow(k, v)
                }
            }

            val other = f.tags.filter { it.first != "encoder" }.take(40)
            if (other.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Engraved("TAGS IN THE FILE", size = 11.sp, spacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                GlassScreen {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, v) in other) FactRow(k, v)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Lcd(label.uppercase(), Modifier.weight(0.38f), color = SpectraColors.PhosphorDim, size = 10.sp, maxLines = 2)
        Lcd(value, Modifier.weight(0.62f), size = 12.sp, maxLines = 4)
    }
}
