package com.aravind.spectra.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.model.UiState
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.SpectraColors
import com.aravind.spectra.update.UpdateChecker
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** What is on screen: Settings, the batch list, or one of the single-track states. */
private data class ScreenState(val settings: Boolean, val batch: Boolean, val ui: UiState)

@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel = viewModel(),
    incomingUri: Uri? = null,
    onIncomingHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val view by viewModel.view.collectAsState()
    val logScale by viewModel.logScale.collectAsState()
    val minDb by viewModel.minDb.collectAsState()
    val batchVm: BatchViewModel = viewModel()
    val batch by batchVm.state.collectAsState()
    var showShare by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.analyzeFile(context, uri)
    }
    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            viewModel.analyzeFile(context, incomingUri)
            onIncomingHandled()
        }
    }

    UpdatePrompt()
    NotificationPermissionRequest()

    SkeuoBackground {
        TrophyToastHost(
            Modifier.align(Alignment.TopCenter).zIndex(10f).statusBarsPadding().padding(horizontal = 14.dp)
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            if (!showSettings) {
                Spacer(Modifier.height(4.dp))
                HeaderUnit(state) { showSettings = true }
            }

            AnimatedContent(
                targetState = ScreenState(showSettings, batch.visible, state),
                // progress updates inside one screen must not trigger a transition
                contentKey = { if (it.settings) "settings" else if (it.batch) "batch" else it.ui::class.java.name },
                transitionSpec = { fadeIn(tween(320, delayMillis = 90)) togetherWith fadeOut(tween(140)) },
                label = "screen"
            ) { screen ->
                Column(Modifier.fillMaxWidth()) {
                    if (screen.settings) {
                        SettingsScreen(onClose = {
                            showSettings = false
                            viewModel.applyDefaults()
                        })
                    } else if (screen.batch) {
                        BatchScreen(batchVm) { uri ->
                            batchVm.hide()
                            viewModel.analyzeFile(context, uri)
                        }
                    } else when (val s = screen.ui) {
                        is UiState.Idle -> IdleUnits { picker.launch(arrayOf("audio/*")) }
                        is UiState.Loading -> LoadingUnit(s) { viewModel.cancel() }
                        is UiState.Error -> ErrorUnit(s.message) { viewModel.reset() }
                        is UiState.Success -> {
                            SourceUnit(s, onNew = { viewModel.reset() }, onShare = { showShare = true })
                            if (showShare) ShareDialog(s, view, logScale, minDb) { showShare = false }
                            VerdictUnit(s.verdict)
                            SpectrogramUnit(
                                s.analysis, view, viewModel::setView,
                                logScale, viewModel::setLogScale,
                                minDb, viewModel::setMinDb
                            )
                            SpectrumUnit(s.analysis)
                            TimelineUnit(s.analysis)
                            LevelsUnit(s.analysis)
                            FileFactsUnit(s)
                            DetailsUnit(s)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HeaderUnit(state: UiState, onSettings: () -> Unit) {
    val context = LocalContext.current
    val version = remember { UpdateChecker.currentVersion(context) }
    val lamp = when (state) {
        is UiState.Idle -> SpectraColors.Amber
        is UiState.Loading -> SpectraColors.Blue
        is UiState.Error -> SpectraColors.Red
        is UiState.Success -> SpectraColors.Green
    }
    RackPanel("Spectra SA-1", lamp = lamp, lampPulse = state is UiState.Loading, shine = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Engraved(
                "SPECTRA", Modifier.weight(1f),
                size = 34.sp, color = SpectraColors.Ink, spacing = 4.sp, weight = FontWeight.Black
            )
            GearButton(onSettings)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Engraved("AUDIO FORENSICS ANALYZER", Modifier.weight(1f), size = 10.sp, spacing = 2.sp)
            Engraved("v$version", size = 10.sp, spacing = 1.sp)
        }
    }
}

@Composable
private fun IdleDisplay(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "idle")
    val phase by t.animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "phase"
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)) {
            val n = 36
            val gap = 3.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val a = 0.5f + 0.5f * sin(phase + i * 0.45f)
                val b = 0.5f + 0.5f * sin(phase * 2f + i * 1.1f)
                val h = size.height * (0.10f + 0.17f * a + 0.09f * b)
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(SpectraColors.Phosphor, SpectraColors.PhosphorDim),
                        startY = size.height - h,
                        endY = size.height
                    ),
                    Offset(i * (bw + gap), size.height - h),
                    Size(bw, h),
                    CornerRadius(2.dp.toPx())
                )
            }
        }
        Lcd(
            "NO SIGNAL",
            Modifier.align(Alignment.TopStart).padding(10.dp),
            color = SpectraColors.PhosphorDim,
            size = 11.sp
        )
    }
}

@Composable
private fun IdleUnits(onLoad: () -> Unit) {
    RackPanel("Input", lamp = SpectraColors.Amber) {
        GlassScreen { IdleDisplay(Modifier.fillMaxWidth().height(130.dp)) }
        Spacer(Modifier.height(16.dp))
        HwButton("LOAD TRACK", Modifier.fillMaxWidth(), height = 54.dp, onClick = onLoad)
        Spacer(Modifier.height(12.dp))
        Engraved("FLAC · WAV · MP3 · AAC · OGG · OPUS", size = 11.sp, spacing = 1.5.sp)
    }

    BatchLauncherUnit()

    if (AppSettings.showTrophies) TrophyShelfUnit()
    if (AppSettings.showGame) SpotGameUnit()
    if (AppSettings.showTips) TipsUnit()
    if (AppSettings.showSelfTest) SelfTestUnit()

    RackPanel("Checks") {
        listOf(
            "Fake-lossless & upsample detection",
            "Inflated-bitrate and encoder-tag evidence",
            "Loudness: LUFS, LRA, true peak, over time",
            "Dynamic range (DR) & clipping timeline",
            "Spectrogram with LIN / LOG, MID / SIDE",
            "Stereo field & effective bit depth",
            "Batch scan, PDF and CSV export",
            "100% on-device analysis (network: update check only)"
        ).forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Led(SpectraColors.Green, true, 9.dp)
                Spacer(Modifier.width(12.dp))
                Engraved(it, size = 13.sp, spacing = 0.3.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoadingUnit(s: UiState.Loading, onAbort: () -> Unit) {
    val p = s.progress
    RackPanel("Analyzing", lamp = SpectraColors.Blue, lampPulse = true) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Lcd(rememberTyped(s.step.uppercase()), size = 14.sp)
                Spacer(Modifier.height(12.dp))
                if (p == null) {
                    ScanBar()
                } else {
                    LedLadder((p * 100f).toDouble(), 0.0, 100.0, segments = 30, tint = SpectraColors.Phosphor)
                }
                Spacer(Modifier.height(8.dp))
                Lcd(
                    if (p == null) "WORKING…" else "${(p * 100f).roundToInt()} %",
                    color = SpectraColors.PhosphorDim,
                    size = 12.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        TipLine()
        Spacer(Modifier.height(14.dp))
        HwButton("ABORT", Modifier.fillMaxWidth(), onClick = onAbort)
    }
}

@Composable
private fun ErrorUnit(message: String, onRetry: () -> Unit) {
    RackPanel("Fault", lamp = SpectraColors.Red) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Lcd("COULD NOT ANALYZE THIS FILE", color = SpectraColors.Red, size = 13.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Lcd(message, color = SpectraColors.Amber, size = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        HwButton("TRY ANOTHER FILE", Modifier.fillMaxWidth(), onClick = onRetry)
    }
}
