package com.aravind.spectra.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.engage.Trophies
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.SpectraColors
import com.aravind.spectra.update.UpdateViewModel
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private const val REPO_URL = "https://github.com/aravindrejidev/Spectra"
private const val DEV_URL = "https://github.com/aravindrejidev"
private const val DEV_NAME = "aravindrejidev"

private enum class Page { HOME, APPEARANCE, ANALYSIS, NOTIFICATIONS, UPDATES, DATA, ABOUT, MEET }

private fun depthOf(p: Page): Int = when (p) {
    Page.HOME -> 0
    Page.MEET -> 2
    else -> 1
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun appVersion(context: Context): Pair<String, Long> {
    return try {
        @Suppress("DEPRECATION")
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        (pi.versionName ?: "?") to PackageInfoCompat.getLongVersionCode(pi)
    } catch (e: Exception) {
        "?" to 0L
    }
}

private fun updateFilesSize(context: Context): Long =
    File(context.cacheDir, "updates").listFiles()?.sumOf { it.length() } ?: 0L

/** Settings: a list of sections, each opening its own page. Back goes one page up. */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    BackHandler {
        when (page) {
            Page.HOME -> onClose()
            Page.MEET -> page = Page.ABOUT
            else -> page = Page.HOME
        }
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = depthOf(targetState) > depthOf(initialState)
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) it / 3 else -it / 3 } +
                fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
        },
        label = "settingsPage"
    ) { p ->
        Column(Modifier.fillMaxWidth()) {
            when (p) {
                Page.HOME -> HomePage(onClose) { page = it }
                Page.APPEARANCE -> AppearancePage { page = Page.HOME }
                Page.ANALYSIS -> AnalysisPage { page = Page.HOME }
                Page.NOTIFICATIONS -> NotificationsPage { page = Page.HOME }
                Page.UPDATES -> UpdatesPage { page = Page.HOME }
                Page.DATA -> DataPage { page = Page.HOME }
                Page.ABOUT -> AboutPage({ page = Page.HOME }) { page = it }
                Page.MEET -> MeetPage { page = Page.ABOUT }
            }
        }
    }
}

@Composable
private fun HomePage(onClose: () -> Unit, go: (Page) -> Unit) {
    PageHeader("Settings", onClose)
    RackPanel("Preferences") {
        NavRow("Appearance", "Motion, haptics and what the home screen shows") { go(Page.APPEARANCE) }
        RowDivider()
        NavRow("Analysis", "Decoder, true peak, clipping and spectrogram defaults") { go(Page.ANALYSIS) }
        RowDivider()
        NavRow("Notifications", "Finished and new-version alerts") { go(Page.NOTIFICATIONS) }
        RowDivider()
        NavRow("Updates", "Version checks and downloads") { go(Page.UPDATES) }
        RowDivider()
        NavRow("Data & privacy", "Reset trophies, clear downloaded files") { go(Page.DATA) }
    }
    RackPanel("Spectra") {
        NavRow("About", "Version, links and credits") { go(Page.ABOUT) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AppearancePage(back: () -> Unit) {
    PageHeader("Appearance", back)
    RackPanel("Motion & feedback") {
        SwitchRow("Reduce motion", "Turns off sliding panels, header shine, pulsing lamps and scan effects.", AppSettings.reduceMotion) {
            AppSettings.reduceMotion = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Typewriter text", "Headlines and tips are typed out letter by letter.", AppSettings.typewriter) {
            AppSettings.typewriter = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Header shine", "A light sweep across the metal header every few seconds.", AppSettings.headerShine) {
            AppSettings.headerShine = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Button haptics", "A small tick when you press a button or switch.", AppSettings.haptics) {
            AppSettings.haptics = it
            AppSettings.save()
        }
    }
    RackPanel("Home screen") {
        SwitchRow("Trophies", "Medals and your lifetime numbers.", AppSettings.showTrophies) {
            AppSettings.showTrophies = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Spot the fake", "The spectrogram mini-game.", AppSettings.showGame) {
            AppSettings.showGame = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Did you know?", "Audio facts on the home screen and while an analysis runs.", AppSettings.showTips) {
            AppSettings.showTips = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Self-test", "The accuracy self-test panel.", AppSettings.showSelfTest) {
            AppSettings.showSelfTest = it
            AppSettings.save()
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AnalysisPage(back: () -> Unit) {
    PageHeader("Analysis", back)
    RackPanel("Decoding") {
        ChoiceRow(
            "Decoder",
            "Auto uses FFmpeg and falls back to the phone's own decoder. Pick one to test it on its own.",
            listOf("AUTO", "FFMPEG", "SYSTEM"),
            AppSettings.decoderMode
        ) {
            AppSettings.decoderMode = it
            AppSettings.save()
        }
    }
    RackPanel("Measurements") {
        SwitchRow(
            "Exact true peak",
            "Checks every sample with 4x oversampling. Slower on long files. When off, true peak shows the sample peak.",
            AppSettings.exactTruePeak
        ) {
            AppSettings.exactTruePeak = it
            AppSettings.save()
        }
        RowDivider()
        ChoiceRow(
            "Clipping detection",
            "Counts samples within 0.09, 0.009 or 0.0009 dB of full scale.",
            listOf("SENSITIVE", "NORMAL", "STRICT"),
            AppSettings.clipMode
        ) {
            AppSettings.clipMode = it
            AppSettings.save()
        }
    }
    RackPanel("Spectrogram") {
        ChoiceRow(
            "Default frequency scale",
            "Used when a result opens. You can still switch it on the result.",
            listOf("LINEAR", "LOGARITHMIC"),
            if (AppSettings.defaultLogScale) 1 else 0
        ) {
            AppSettings.defaultLogScale = it == 1
            AppSettings.save()
        }
        RowDivider()
        SliderRow(
            "Default floor",
            "${AppSettings.defaultFloorDb.roundToInt()} dB",
            AppSettings.defaultFloorDb,
            -130f..-40f
        ) {
            AppSettings.defaultFloorDb = it
            AppSettings.save()
        }
    }
    RackPanel("Batch scan") {
        SwitchRow("Include sub-folders", "Scan the whole folder tree, not just the top level.", AppSettings.scanSubfolders) {
            AppSettings.scanSubfolders = it
            AppSettings.save()
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NotificationsPage(back: () -> Unit) {
    val context = LocalContext.current
    PageHeader("Notifications", back)
    RackPanel("Alerts") {
        SwitchRow(
            "Finished alerts",
            "A notification when an analysis or batch scan finishes while the app is in the background.",
            AppSettings.notifyResults
        ) {
            AppSettings.notifyResults = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("New version alerts", "A notification when a newer Spectra release is available.", AppSettings.notifyUpdates) {
            AppSettings.notifyUpdates = it
            AppSettings.save()
        }
    }
    RackPanel("Progress") {
        Text(
            "While a track is analyzed or an update downloads, Android requires an ongoing progress notification so the " +
                "work isn't stopped. It disappears when the work is done.",
            color = SpectraColors.InkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        HwButton("SYSTEM NOTIFICATION SETTINGS", Modifier.fillMaxWidth(), height = 44.dp) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open the system settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun UpdatesPage(back: () -> Unit) {
    val context = LocalContext.current
    val updateVm: UpdateViewModel = viewModel()
    PageHeader("Updates", back)
    RackPanel("Checking") {
        SwitchRow("Check when the app opens", "Looks for a newer release on GitHub and shows the update popup.", AppSettings.checkOnLaunch) {
            AppSettings.checkOnLaunch = it
            AppSettings.save()
        }
        RowDivider()
        SwitchRow("Daily background check", "Once a day, even when the app is closed.", AppSettings.dailyCheck) {
            AppSettings.dailyCheck = it
            AppSettings.save()
        }
        RowDivider()
        ActionRow("Check for updates now", "Current version: v${updateVm.currentVersion}", "CHECK NOW") {
            Toast.makeText(context, "Checking…", Toast.LENGTH_SHORT).show()
            updateVm.checkNow { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        }
    }
    RackPanel("Reminders") {
        ActionRow(
            "Forget \"Don't remind\"",
            "Versions you chose not to be reminded about will be offered again.",
            "FORGET CHOICES"
        ) {
            updateVm.forgetSkippedVersion()
            Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DataPage(back: () -> Unit) {
    val context = LocalContext.current
    var filesSize by remember { mutableStateOf(updateFilesSize(context)) }
    var confirm by remember { mutableStateOf(false) }
    PageHeader("Data & privacy", back)
    RackPanel("Privacy") {
        Text(
            "Spectra analyzes audio on your phone. The network is used only to check GitHub for new versions and to " +
                "download them. Trophies, stats and settings never leave the device.",
            color = SpectraColors.InkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
    RackPanel("Storage") {
        ActionRow(
            "Downloaded update files",
            String.format(Locale.US, "%.1f MB stored in the cache", filesSize / 1_000_000.0),
            "CLEAR"
        ) {
            File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
            filesSize = updateFilesSize(context)
            Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show()
        }
    }
    RackPanel("Reset") {
        ActionRow(
            "Trophies and stats",
            "Locks every medal again and zeroes your numbers. This can't be undone.",
            if (confirm) "TAP AGAIN TO CONFIRM" else "RESET TROPHIES & STATS"
        ) {
            if (!confirm) {
                confirm = true
            } else {
                Trophies.reset(context)
                confirm = false
                Toast.makeText(context, "Trophies and stats reset", Toast.LENGTH_SHORT).show()
            }
        }
        RowDivider()
        ActionRow("Spot the fake best score", null, "RESET BEST SCORE") {
            context.getSharedPreferences("spectra_game", Context.MODE_PRIVATE).edit().remove("best").apply()
            Toast.makeText(context, "Best score reset", Toast.LENGTH_SHORT).show()
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- About

@Composable
private fun AppLogo() {
    val t = rememberInfiniteTransition(label = "logo")
    val glow by t.animateFloat(
        0.35f, 0.9f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Canvas(Modifier.size(104.dp)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(
            Brush.radialGradient(listOf(SpectraColors.Green.copy(alpha = 0.35f * glow), Color.Transparent), center = c, radius = r * 1.35f),
            r * 1.35f, c
        )
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFF1F2F5), Color(0xFF7E848F)), start = c - Offset(r, r), end = c + Offset(r, r)),
            r * 0.95f, c
        )
        drawCircle(Brush.radialGradient(listOf(Color(0xFF14201B), Color(0xFF040605)), center = c, radius = r), r * 0.78f, c)
        val heights = floatArrayOf(0.3f, 0.55f, 0.85f, 0.65f, 0.95f, 0.5f, 0.35f)
        val bw = r * 0.15f
        val gap = r * 0.06f
        var x = c.x - (heights.size * bw + (heights.size - 1) * gap) / 2f
        for (h in heights) {
            val bh = r * h
            drawRoundRect(
                SpectraColors.Phosphor.copy(alpha = 0.6f + 0.4f * glow),
                Offset(x, c.y - bh / 2f), Size(bw, bh), CornerRadius(bw / 2f)
            )
            x += bw + gap
        }
    }
}

@Composable
private fun VersionChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1411), Color(0xFF030504))))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) { Lcd(text, size = 13.sp) }
}

@Composable
private fun Avatar(letter: String) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFF1F2F5), Color(0xFF8F95A0)))),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = SpectraColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    NavRow(title, subtitle, onClick)
}

@Composable
private fun AboutPage(back: () -> Unit, go: (Page) -> Unit) {
    val context = LocalContext.current
    val (version, build) = remember { appVersion(context) }
    PageHeader("About", back)

    RackPanel("Spectra") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo()
            Spacer(Modifier.height(10.dp))
            Engraved("SPECTRA", size = 28.sp, color = SpectraColors.Ink, spacing = 3.sp, weight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            VersionChip("v$version")
            Spacer(Modifier.height(12.dp))
            Text(
                "Audio forensics for your phone: spectrogram, loudness, true peak and fake-lossless detection, all on the device.",
                color = SpectraColors.InkSoft,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    RackPanel("Story") {
        NavRow("Meet Spectra", null) { go(Page.MEET) }
    }

    RackPanel("Contributors") {
        Row(
            Modifier.fillMaxWidth().clickable { openUrl(context, DEV_URL) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(DEV_NAME.take(1).uppercase())
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                RowTitle(DEV_NAME)
                RowSub("Developer")
            }
            Chevron()
        }
    }

    RackPanel("Links") {
        LinkRow("Source code", "github.com/aravindrejidev/Spectra") { openUrl(context, REPO_URL) }
        RowDivider()
        LinkRow("Releases", "Download older or newer versions") { openUrl(context, "$REPO_URL/releases") }
        RowDivider()
        LinkRow("Report an issue", "Something wrong? Tell us on GitHub") { openUrl(context, "$REPO_URL/issues") }
    }

    RackPanel("App") {
        InfoRow("Version", "v$version (build $build)")
    }

    Text(
        "© ${Calendar.getInstance().get(Calendar.YEAR)} $DEV_NAME · build $build",
        color = Color(0xFFB9A58F),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
    )
}

@Composable
private fun MeetPage(back: () -> Unit) {
    PageHeader("Meet Spectra", back)
    RackPanel("What it does") {
        listOf(
            "Spectrogram with LIN / LOG, MID / SIDE views",
            "Fake-lossless and upsample detection, with the evidence shown",
            "Inflated-bitrate and encoder-tag checks",
            "Loudness (LUFS, LRA), true peak and a loudness timeline",
            "Dynamic range, clipping and DC offset",
            "Stereo field and effective bit depth",
            "Batch scan of whole folders, PDF and CSV export",
            "Reports as text, image or PDF"
        ).forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Led(SpectraColors.Green, true, 9.dp)
                Spacer(Modifier.width(12.dp))
                Text(it, color = SpectraColors.Ink, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            }
        }
    }
    RackPanel("How it works") {
        Text(
            "Spectra decodes your track with FFmpeg and measures it in a single pass: an FFT for the spectrogram, " +
                "ITU-R BS.1770 loudness, 4x oversampled true peak, clipping and dynamic range. Everything stays on your phone.",
            color = SpectraColors.InkSoft,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "The authenticity check looks for the fingerprints lossy encoders leave behind, such as a hard low-pass edge, " +
                "and says how strong the evidence is. It can show that a file looks lossy, but it can't prove a file is genuine.",
            color = SpectraColors.InkSoft,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
    Spacer(Modifier.height(8.dp))
}
