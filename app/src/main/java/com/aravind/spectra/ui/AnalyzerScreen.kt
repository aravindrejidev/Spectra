package com.aravind.spectra.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aravind.spectra.model.UiState
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            HeaderUnit(state)
            if (batch.visible) {
                BatchScreen(batchVm) { uri ->
                    batchVm.hide()
                    viewModel.analyzeFile(context, uri)
                }
            } else when (val s = state) {
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
                    LevelsUnit(s.analysis)
                    DetailsUnit(s)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun shareReport(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Spectra report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "Share report"))
}

@Composable
private fun HeaderUnit(state: UiState) {
    val lamp = when (state) {
        is UiState.Idle -> SpectraColors.Amber
        is UiState.Loading -> SpectraColors.Blue
        is UiState.Error -> SpectraColors.Red
        is UiState.Success -> SpectraColors.Green
    }
    RackPanel("Spectra SA-1", lamp = lamp) {
        Engraved("SPECTRA", size = 34.sp, color = SpectraColors.Ink, spacing = 4.sp, weight = FontWeight.Black)
        Engraved("AUDIO FORENSICS ANALYZER", size = 10.sp, spacing = 2.sp)
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

    SelfTestUnit()

    RackPanel("Checks") {
        listOf(
            "Fake-lossless & upsample detection",
            "Loudness: LUFS, LRA, true peak",
            "Dynamic range (DR) & clipping",
            "Spectrogram with LIN / LOG, MID / SIDE",
            "Stereo field & effective bit depth",
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
    val p = s.progress ?: 0f
    RackPanel("Analyzing", lamp = SpectraColors.Blue) {
        GlassScreen {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Lcd(s.step.uppercase(), size = 14.sp)
                Spacer(Modifier.height(12.dp))
                LedLadder((p * 100f).toDouble(), 0.0, 100.0, segments = 30, tint = SpectraColors.Phosphor)
                Spacer(Modifier.height(8.dp))
                Lcd("${(p * 100f).roundToInt()} %", color = SpectraColors.PhosphorDim, size = 12.sp)
            }
        }
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
