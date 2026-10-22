package com.aravind.spectra.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.delay

private val TIPS = listOf(
    "In a spectrogram, dark means quiet and bright means loud. Up is a higher pitch, right is later in the track.",
    "CD audio takes 44,100 samples a second, so the highest frequency it can hold is 22.05 kHz: half the sample rate.",
    "A flat horizontal wall around 16 kHz is the classic sign of a 128 kbps MP3. Real recordings usually fade out gradually instead.",
    "Converting an MP3 to FLAC does not bring the lost detail back. It only stores the already damaged audio without losing more.",
    "LUFS measures how loud something sounds, not how high its peaks are. Many streaming services turn loud tracks down to roughly -14 LUFS.",
    "True peak can be higher than the highest sample, because the reconstructed wave swings between samples. Mastering engineers often leave about -1 dBTP of headroom.",
    "DR (dynamic range) compares the loud parts with the average. Around DR14 or more is very dynamic, DR6 or less is heavily compressed.",
    "A 24-bit file whose lowest 8 bits are always zero is really 16-bit audio in a bigger box. Spectra's effective bits shows that.",
    "Correlation +1 means both channels match (mono), 0 means they are unrelated, and below 0 means they partly cancel on a single speaker.",
    "In the MID view you see what is centred in a mix, often vocals and bass. The SIDE view shows what is spread wide, like reverb and stereo effects.",
    "Hearing usually fades above 16 kHz as we age, which is part of why encoders can throw away the top of the spectrum.",
    "A pure sine wave at full volume has a DR of 0, because its peaks are only 3 dB above its average level.",
    "Clipping turns smooth wave peaks into flat tops. That adds harsh harmonics, which show up as bright streaks in a spectrogram.",
    "A 96 kHz file whose spectrum stops near 22 kHz was most likely upsampled from a 44.1 kHz source.",
    "Opus audio is decoded at 48 kHz no matter what the original sample rate was.",
    "Loud modern masters often measure around -8 LUFS, while classical recordings can sit near -20 LUFS or lower.",
    "LRA (loudness range) shows how much the loudness moves during a track. Classical can reach 15 LU or more, many pop tracks stay below 8 LU.",
    "DC offset is a constant shift of the whole waveform. It wastes headroom and can cause clicks when a track starts or stops.",
    "FLAC and ALAC usually shrink audio by roughly 30 to 60 percent without changing a single sample.",
    "Resampling between 44.1 and 48 kHz uses a steep filter, so even a genuine recording can show a hard edge near 20 kHz. That is why an edge there alone is not proof of a fake.",
    "Each spectrogram frame is a 2048-sample FFT with a Hann window, and Spectra measures every one of them."
)

private fun startIndex(): Int = ((System.nanoTime() / 1_000_000L) % TIPS.size).toInt()

/** Home-screen card: one audio fact at a time, typed out on the LCD. */
@Composable
fun TipsUnit() {
    var index by remember { mutableIntStateOf(startIndex()) }
    LaunchedEffect(index) {
        delay(12_000)
        index = (index + 1) % TIPS.size
    }
    RackPanel("Did you know?", lamp = SpectraColors.Phosphor) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 120.dp)) {
                Text(
                    rememberTyped(TIPS[index], 14L),
                    color = SpectraColors.Phosphor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HwButton("NEXT TIP", Modifier.fillMaxWidth(), height = 40.dp) { index = (index + 1) % TIPS.size }
    }
}

/** A smaller version shown while an analysis runs, so the wait is not empty. */
@Composable
fun TipLine() {
    if (!AppSettings.showTips) return
    var index by remember { mutableIntStateOf(startIndex()) }
    LaunchedEffect(index) {
        delay(9_000)
        index = (index + 1) % TIPS.size
    }
    GlassScreen {
        Column(Modifier.fillMaxWidth().padding(14.dp).heightIn(min = 110.dp)) {
            Lcd("DID YOU KNOW?", color = SpectraColors.PhosphorDim, size = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                rememberTyped(TIPS[index], 14L),
                color = SpectraColors.Phosphor.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
