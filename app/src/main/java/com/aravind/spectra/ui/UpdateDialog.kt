package com.aravind.spectra.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.ui.theme.SpectraColors
import com.aravind.spectra.update.UpdateChecker
import com.aravind.spectra.update.UpdateState
import com.aravind.spectra.update.UpdateViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private fun titleOf(s: UpdateState): String = when (s) {
    is UpdateState.Available -> "Update available"
    is UpdateState.Downloading -> "Downloading"
    is UpdateState.Ready -> "Ready to install"
    is UpdateState.Failed -> "Update failed"
    is UpdateState.Welcome -> "Updated"
    is UpdateState.Hidden -> ""
}

private fun lampOf(s: UpdateState): Color = when (s) {
    is UpdateState.Available -> SpectraColors.Amber
    is UpdateState.Downloading -> SpectraColors.Blue
    is UpdateState.Ready -> SpectraColors.Green
    is UpdateState.Failed -> SpectraColors.Red
    is UpdateState.Welcome -> SpectraColors.Green
    is UpdateState.Hidden -> SpectraColors.Amber
}

private fun fmtMb(bytes: Double): String = String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)

private fun fmtEta(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

/** Themed "update" popup: offer, download, ready, failed, and "what's new" after updating. */
@Composable
fun UpdatePrompt(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val s = state
    if (s is UpdateState.Hidden) return

    val haptic = LocalHapticFeedback.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "pop")

    val failed = s is UpdateState.Failed
    val ready = s is UpdateState.Ready && !s.needsPermission
    val shake = remember { Animatable(0f) }
    LaunchedEffect(failed) {
        if (failed) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            repeat(3) {
                shake.animateTo(1f, tween(55))
                shake.animateTo(-1f, tween(55))
            }
            shake.animateTo(0f, tween(55))
        }
    }
    LaunchedEffect(ready) {
        if (ready) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Dialog(
        onDismissRequest = { if (s !is UpdateState.Downloading) vm.later() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .graphicsLayer {
                    val sc = 0.9f + 0.1f * pop
                    scaleX = sc
                    scaleY = sc
                    alpha = pop.coerceIn(0f, 1f)
                    translationX = shake.value * 14.dp.toPx()
                }
        ) {
            RackPanel(
                titleOf(s),
                lamp = lampOf(s),
                lampPulse = s is UpdateState.Downloading || s is UpdateState.Available
            ) {
                AnimatedContent(
                    targetState = s,
                    // progress ticks inside one state must not trigger a transition
                    contentKey = { it::class.java.name },
                    transitionSpec = {
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeIn(tween(300, delayMillis = 90))) togetherWith
                            (slideOutHorizontally(tween(240)) { -it / 4 } + fadeOut(tween(150)))
                    },
                    label = "updateContent"
                ) { st ->
                    Column(Modifier.fillMaxWidth()) { UpdateBody(st, vm) }
                }
            }
        }
    }
}

@Composable
private fun UpdateBody(st: UpdateState, vm: UpdateViewModel) {
    when (st) {
        is UpdateState.Available -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpdateBadge()
                Spacer(Modifier.width(14.dp))
                Column {
                    Lcd(rememberTyped("NEW VERSION READY"), color = SpectraColors.Amber, size = 15.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Spectra v${st.info.version} is out.", color = SpectraColors.InkSoft, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            VersionPlate(vm.currentVersion, st.info.version)
            Spacer(Modifier.height(12.dp))
            NotesPlate(st.info.notes)
            if (st.info.sizeBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Engraved("DOWNLOAD SIZE  ${fmtBytes(st.info.sizeBytes)}", size = 11.sp, spacing = 1.sp)
            }
            Spacer(Modifier.height(16.dp))
            GlowFrame {
                HwButton("DOWNLOAD & INSTALL", Modifier.fillMaxWidth(), height = 52.dp, onClick = vm::startDownload)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HwButton("DON'T REMIND", Modifier.weight(1f), height = 40.dp, onClick = vm::skipVersion)
                HwButton("LATER", Modifier.weight(1f), height = 40.dp, onClick = vm::later)
            }
        }
        is UpdateState.Downloading -> DownloadBody(st, vm)
        is UpdateState.Ready -> {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SuccessPulse(color = if (st.needsPermission) SpectraColors.Amber else SpectraColors.Green)
                Spacer(Modifier.height(6.dp))
                Lcd(
                    rememberTyped(if (st.needsPermission) "ALLOW INSTALLS FIRST" else "READY TO INSTALL"),
                    color = if (st.needsPermission) SpectraColors.Amber else SpectraColors.Green,
                    size = 16.sp,
                    weight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (st.needsPermission) {
                        "Settings just opened. Turn on the switch for Spectra, come back here and tap INSTALL."
                    } else {
                        "Android's installer should open now. If it didn't, tap INSTALL."
                    },
                    color = SpectraColors.InkSoft,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
            GlowFrame {
                HwButton("INSTALL", Modifier.fillMaxWidth(), height = 52.dp, onClick = vm::installAgain)
            }
            Spacer(Modifier.height(10.dp))
            HwButton("LATER", Modifier.fillMaxWidth(), height = 40.dp, onClick = vm::later)
        }
        is UpdateState.Failed -> {
            GlassScreen {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Lcd("UPDATE FAILED", color = SpectraColors.Red, size = 15.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Lcd(st.message, color = SpectraColors.Amber, size = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HwButton("RETRY", Modifier.weight(1f), onClick = vm::startDownload)
                HwButton("LATER", Modifier.weight(1f), onClick = vm::later)
            }
        }
        is UpdateState.Welcome -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpdateBadge(color = SpectraColors.Green)
                Spacer(Modifier.width(14.dp))
                Column {
                    Lcd(rememberTyped("UPDATED TO v${st.info.version}"), color = SpectraColors.Green, size = 15.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Here is what changed in this version.", color = SpectraColors.InkSoft, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            NotesPlate(st.info.notes)
            Spacer(Modifier.height(16.dp))
            HwButton("NICE", Modifier.fillMaxWidth(), height = 50.dp, onClick = vm::later)
        }
        is UpdateState.Hidden -> Unit
    }
}

/** Smoothed download speed in bytes per second, from the progress ticks. */
@Composable
private fun rememberSpeed(bytesDone: Double): State<Double> {
    val speed = remember { mutableDoubleStateOf(0.0) }
    val last = remember { doubleArrayOf(0.0, 0.0) } // time in ms, bytes
    LaunchedEffect(bytesDone) {
        val now = SystemClock.elapsedRealtime().toDouble()
        if (last[0] > 0.0 && now > last[0]) {
            val instant = (bytesDone - last[1]) / ((now - last[0]) / 1000.0)
            if (instant >= 0.0) {
                speed.doubleValue = if (speed.doubleValue == 0.0) instant else speed.doubleValue * 0.8 + instant * 0.2
            }
        }
        last[0] = now
        last[1] = bytesDone
    }
    return speed
}

@Composable
private fun DownloadBody(st: UpdateState.Downloading, vm: UpdateViewModel) {
    val total = st.info.sizeBytes.toDouble()
    val done = st.progress * total
    val speed by rememberSpeed(done)
    val pct by animateFloatAsState(st.progress, tween(300), label = "pct")

    GlassScreen {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Lcd("DOWNLOADING v${st.info.version}", Modifier.weight(1f), size = 13.sp)
                Lcd("${(pct * 100f).roundToInt()}%", color = SpectraColors.Amber, size = 26.sp, weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            ChargeBar(st.progress)
            if (total > 0.0) {
                Spacer(Modifier.height(10.dp))
                val left = if (speed > 1.0) ((total - done) / speed).roundToInt() else -1
                Row(Modifier.fillMaxWidth()) {
                    Lcd("${fmtMb(done)} / ${fmtMb(total)}", Modifier.weight(1f), color = SpectraColors.PhosphorDim, size = 11.sp)
                    Lcd(
                        if (left >= 0) "${fmtMb(speed)}/s · ${fmtEta(left)} left" else "measuring speed…",
                        color = SpectraColors.PhosphorDim,
                        size = 11.sp
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    HwButton("CANCEL", Modifier.fillMaxWidth(), onClick = vm::cancelDownload)
}

@Composable
private fun VersionPlate(current: String, latest: String) {
    val t = rememberInfiniteTransition(label = "arrow")
    val dx by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dx"
    )
    GlassScreen {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Lcd("CURRENT", color = SpectraColors.PhosphorDim, size = 10.sp)
                Lcd("v$current", size = 18.sp, weight = FontWeight.Bold)
            }
            Box(Modifier.graphicsLayer { translationX = dx * 8.dp.toPx() }) {
                Lcd("→", color = SpectraColors.Amber, size = 26.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Lcd("NEW", color = SpectraColors.PhosphorDim, size = 10.sp)
                Lcd(rememberTyped("v$latest", 60L), color = SpectraColors.Amber, size = 18.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotesPlate(notes: String) {
    val lines = remember(notes) {
        UpdateChecker.cleanNotes(notes).lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    Engraved("WHAT'S NEW", size = 11.sp, spacing = 1.5.sp)
    Spacer(Modifier.height(6.dp))
    GlassScreen {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (lines.isEmpty()) Lcd("No release notes.", color = SpectraColors.PhosphorDim, size = 12.sp)
            lines.forEachIndexed { i, line -> NoteLine(line, i) }
        }
    }
}

/** One line of release notes, sliding in from the right a moment after the previous one. */
@Composable
private fun NoteLine(line: String, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(260L + (index * 70L).coerceAtMost(900L))
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "note")
    val bullet = line.startsWith("•")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = a
                translationX = (1f - a) * 28.dp.toPx()
            }
    ) {
        if (bullet) {
            Lcd("•", color = SpectraColors.Amber, size = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                line.removePrefix("•").trim(),
                modifier = Modifier.weight(1f),
                color = SpectraColors.Phosphor.copy(alpha = 0.92f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Lcd(line.uppercase(), color = SpectraColors.Amber, size = 12.sp, weight = FontWeight.Bold)
        }
    }
}
