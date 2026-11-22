package com.aravind.spectra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.model.UiState
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.ModernColors
import com.aravind.spectra.ui.theme.Pal
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ header

/** The app title card: equalizer logo, wordmark, status dot and the settings button. */
@Composable
fun ModernHeaderUnit(state: UiState, version: String, onSettings: () -> Unit) {
    val order = remember { Stagger.next() }
    val lamp = when (state) {
        is UiState.Idle -> ModernColors.Warn
        is UiState.Loading -> ModernColors.Info
        is UiState.Error -> ModernColors.Bad
        is UiState.Success -> ModernColors.Good
    }
    val running = state is UiState.Loading
    val calm = AppSettings.reduceMotion
    val sweep by rememberShine(AppSettings.headerShine && !calm)
    val t = rememberInfiniteTransition(label = "hdrBars")
    val phase by t.animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phase"
    )

    ModernPanelBox(order, radius = 28.dp) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val c = center
                    drawCircle(
                        Brush.radialGradient(
                            listOf(ModernColors.Accent.copy(alpha = 0.30f), Color.Transparent),
                            center = c,
                            radius = w * 0.75f
                        ),
                        w * 0.75f,
                        c
                    )
                    val base = floatArrayOf(0.30f, 0.55f, 0.85f, 0.60f, 0.95f, 0.50f, 0.35f)
                    val bw = w * 0.085f
                    val gap = w * 0.05f
                    val total = base.size * bw + (base.size - 1) * gap
                    var x = (w - total) / 2f
                    val speed = if (running) 2.6f else 1f
                    for (i in base.indices) {
                        val k = if (calm) 1f else 0.82f + 0.18f * sin(phase * speed + i * 0.9f)
                        val bh = h * 0.9f * base[i] * k
                        val top = c.y - bh / 2f
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(ModernColors.AccentHi, ModernColors.AccentLo),
                                startY = top,
                                endY = top + bh
                            ),
                            Offset(x, top),
                            Size(bw, bh),
                            CornerRadius(bw / 2f)
                        )
                        x += bw + gap
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd)) {
                    if (running && !calm) ModernPulseDot(lamp, 9.dp) else ModernDot(lamp, true, 9.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.10f)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ModernColors.TextHi)) { append("SPECT") }
                        withStyle(SpanStyle(color = ModernColors.Accent)) { append("RA") }
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    maxLines = 1
                )
                Text("Audio Forensics Analyzer", color = ModernColors.TextMid, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text("v$version", color = ModernColors.TextFaint, fontSize = 10.5.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            ModernIconButton(MIconKind.Gear, onSettings, 44.dp, spin = 90f)
        }
        Canvas(Modifier.matchParentSize()) {
            if (sweep >= 0f) {
                val cx = size.width * (sweep * 1.5f - 0.25f)
                val half = 70.dp.toPx()
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent),
                        start = Offset(cx - half, 0f),
                        end = Offset(cx + half, size.height * 0.5f)
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------------ input

/** The first card on the home screen: pick a track. */
@Composable
fun ModernInputCard(onLoad: () -> Unit) {
    RackPanel("Input", lamp = Pal.Amber) {
        ModernDropZone(onLoad)
        Spacer(Modifier.height(14.dp))
        HwButton(
            "LOAD TRACK",
            Modifier.fillMaxWidth(),
            height = 56.dp,
            primary = true,
            icon = MIconKind.Play,
            onClick = onLoad
        )
        Spacer(Modifier.height(14.dp))
        FormatChips()
    }
}

/** A dashed target with marching dashes, a bobbing folder and faint bars that dance behind it. */
@Composable
private fun ModernDropZone(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "drop")
    val dash by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "dash")
    val bob by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val phase by t.animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "phase"
    )
    val calm = AppSettings.reduceMotion
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dzScale"
    )
    val tint by animateFloatAsState(if (pressed) 1f else 0f, tween(140), label = "dzTint")
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.025f + 0.04f * tint))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .drawBehind {
                val w = size.width
                val h = size.height
                val n = 30
                val gap = 4.dp.toPx()
                val pad = 12.dp.toPx()
                val floor = h - 10.dp.toPx()
                val bw = (w - 2f * pad - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val k = if (calm) {
                        0.5f
                    } else {
                        val a = 0.5f + 0.5f * sin(phase + i * 0.45f)
                        val b = 0.5f + 0.5f * sin(phase * 2f + i * 1.1f)
                        a * 0.6f + b * 0.4f
                    }
                    val bh = h * (0.08f + 0.20f * k)
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(ModernColors.Accent.copy(alpha = 0.30f), ModernColors.Accent.copy(alpha = 0.05f)),
                            startY = floor - bh,
                            endY = floor
                        ),
                        Offset(pad + i * (bw + gap), floor - bh),
                        Size(bw, bh),
                        CornerRadius(bw / 2f)
                    )
                }
                val period = 18.dp.toPx()
                val sw = 1.5.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.22f + 0.20f * tint),
                    Offset(sw / 2f, sw / 2f),
                    Size(w - sw, h - sw),
                    CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        sw,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                            if (calm) 0f else (1f - dash) * period
                        )
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { translationY = if (calm) 0f else -bob * 5.dp.toPx() }) {
                MIcon(MIconKind.Folder, tint = ModernColors.TextMid, size = 40.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text("Choose an audio file", color = ModernColors.TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("or open one from another app", color = ModernColors.AccentHi, fontSize = 13.sp)
        }
    }
}

private val FORMATS = listOf(
    "FLAC" to MIconKind.Waveform,
    "WAV" to MIconKind.Waveform,
    "MP3" to MIconKind.Music,
    "AAC" to MIconKind.Music,
    "OGG" to MIconKind.Music,
    "OPUS" to MIconKind.Music
)

@Composable
private fun FormatChips() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FORMATS.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { c, item ->
                    FormatChip(item.first, item.second, Modifier.weight(1f).popIn(r * 3 + c))
                }
            }
        }
    }
}

@Composable
private fun FormatChip(name: String, icon: MIconKind, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MIcon(icon, tint = ModernColors.TextMid, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(name, color = ModernColors.TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

/** The feature list at the bottom of the home screen; each box ticks itself in. */
@Composable
fun ModernChecksCard(items: List<String>) {
    RackPanel("Checks") {
        items.forEachIndexed { i, text -> ModernCheckRow(text, i) }
    }
}

// ------------------------------------------------------------------ loading and error

/** A progress ring (or a spinning arc when the percentage is not known) with the percentage inside. */
@Composable
fun ModernRing(progress: Float?, modifier: Modifier = Modifier, ringSize: Dp = 84.dp) {
    val t = rememberInfiniteTransition(label = "ring")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin")
    val shown by animateFloatAsState(progress ?: 0f, tween(400, easing = FastOutSlowInEasing), label = "ringP")
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val d = size.minDimension - sw
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arc = Size(d, d)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arc, style = Stroke(sw))
            val brush = Brush.sweepGradient(
                listOf(ModernColors.AccentLo, ModernColors.AccentHi, ModernColors.Accent),
                center = center
            )
            if (progress == null) {
                rotate(spin - 90f, center) {
                    drawArc(brush, 0f, 110f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
            } else {
                rotate(-90f, center) {
                    drawArc(
                        ModernColors.Accent.copy(alpha = 0.22f), 0f, 360f * shown, false, topLeft, arc,
                        style = Stroke(sw * 1.8f, cap = StrokeCap.Round)
                    )
                    drawArc(brush, 0f, 360f * shown, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
        if (progress == null) {
            MIcon(MIconKind.Pulse, tint = ModernColors.AccentHi, size = 26.dp)
        } else {
            Text(
                "${(shown * 100f).roundToInt()}%",
                color = ModernColors.TextHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AnimatedDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dp")
    Canvas(Modifier.size(width = 26.dp, height = 18.dp)) {
        for (i in 0..2) {
            val d = ((p - i) % 3f + 3f) % 3f
            val a = 0.2f + 0.8f * (1f - d / 3f)
            drawCircle(
                ModernColors.TextHi.copy(alpha = a),
                2.dp.toPx(),
                Offset(4.dp.toPx() + i * 8.dp.toPx(), size.height - 5.dp.toPx())
            )
        }
    }
}

/** A heartbeat line with a bright pulse travelling along it. */
@Composable
private fun ModernPulseLine(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ecg")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "p")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pts = floatArrayOf(0f, 0.5f, 0.22f, 0.5f, 0.34f, 0.08f, 0.5f, 0.95f, 0.62f, 0.5f, 1f, 0.5f)
        val path = Path()
        var len = 0f
        var px = 0f
        var py = 0f
        var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i] * w
            val y = pts[i + 1] * h
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                len += hypot(x - px, y - py)
            }
            px = x
            py = y
            i += 2
        }
        val sw = 1.8.dp.toPx()
        drawPath(
            path,
            ModernColors.Accent.copy(alpha = 0.25f),
            style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val dash = len * 0.35f
        drawPath(
            path,
            ModernColors.AccentHi,
            style = Stroke(
                sw,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, len), (1f - p) * (len + dash))
            )
        )
    }
}

/** "Analyzing": progress ring, the current step and a bar. */
@Composable
fun ModernLoadingUnit(s: UiState.Loading, onAbort: () -> Unit) {
    val p = s.progress
    RackPanel("Analyzing", lamp = Pal.Blue, lampPulse = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModernRing(p, ringSize = 84.dp)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(1.dp).height(64.dp).background(Color.White.copy(alpha = 0.10f)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Analyzing", color = ModernColors.TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    AnimatedDots()
                    Spacer(Modifier.weight(1f))
                    ModernPulseLine(Modifier.size(width = 40.dp, height = 24.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(rememberTyped(s.step), color = ModernColors.TextMid, fontSize = 13.sp, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                if (p == null) {
                    ModernScanBar()
                } else {
                    ModernLadder((p * 100f).toDouble(), 0.0, 100.0, tint = ModernColors.Accent)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TipLine()
        Spacer(Modifier.height(14.dp))
        HwButton("ABORT", Modifier.fillMaxWidth(), onClick = onAbort)
    }
}

/** "Fault": a red notice that shakes once, and a way to try again. */
@Composable
fun ModernErrorUnit(message: String, onRetry: () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!AppSettings.reduceMotion) {
            delay(350)
            repeat(3) {
                shake.animateTo(1f, tween(55))
                shake.animateTo(-1f, tween(55))
            }
            shake.animateTo(0f, tween(55))
        }
    }
    RackPanel("Fault", lamp = Pal.Red) {
        Box(Modifier.graphicsLayer { translationX = shake.value * 10.dp.toPx() }) {
            ModernNotice("Could not analyze this file", message, ModernColors.Bad, MIconKind.Alert)
        }
        Spacer(Modifier.height(14.dp))
        HwButton("TRY ANOTHER FILE", Modifier.fillMaxWidth(), height = 52.dp, primary = true, onClick = onRetry)
    }
}

// ------------------------------------------------------------------ style picker

/** Two preview tiles in Settings > Appearance: tap one to switch the look of the whole app. */
@Composable
fun StylePicker() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StyleTile("Modern", "Dark glass, red accent", AppSettings.STYLE_MODERN, Modifier.weight(1f))
        StyleTile("Classic", "Brushed-metal rack unit", AppSettings.STYLE_CLASSIC, Modifier.weight(1f))
    }
}

@Composable
private fun StyleTile(name: String, hint: String, style: Int, modifier: Modifier) {
    val selected = AppSettings.uiStyle == style
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "styleScale"
    )
    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "styleSel")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Pal.Ink.copy(alpha = 0.05f))
            .border(
                1.5.dp,
                lerp(Pal.InkSoft.copy(alpha = 0.30f), ModernColors.Accent, sel),
                shape
            )
            .clickable(interactionSource = src, indication = null) {
                if (!selected) {
                    if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    AppSettings.uiStyle = style
                    AppSettings.save()
                }
            }
            .padding(10.dp)
    ) {
        Box {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(12.dp))) {
                drawStylePreview(style)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = sel
                        scaleY = sel
                        alpha = sel
                    }
                    .clip(CircleShape)
                    .background(ModernColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                MIcon(MIconKind.Check, tint = Color.White, size = 14.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = Pal.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = Pal.InkSoft, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStylePreview(style: Int) {
    val w = size.width
    val h = size.height
    if (style == AppSettings.STYLE_MODERN) {
        drawRect(Brush.verticalGradient(listOf(ModernColors.BgTop, ModernColors.BgBottom)))
        drawRect(
            Brush.radialGradient(
                listOf(ModernColors.Accent.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w, 0f),
                radius = w * 0.8f
            )
        )
        val cardTop = h * 0.12f
        drawRoundRect(
            Brush.verticalGradient(listOf(ModernColors.CardTop, ModernColors.CardBottom)),
            Offset(w * 0.08f, cardTop),
            Size(w * 0.84f, h * 0.46f),
            CornerRadius(9.dp.toPx())
        )
        drawRoundRect(
            ModernColors.Accent,
            Offset(w * 0.14f, cardTop + h * 0.07f),
            Size(3.dp.toPx(), h * 0.09f),
            CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.6f),
            Offset(w * 0.22f, cardTop + h * 0.09f),
            Size(w * 0.30f, 4.dp.toPx()),
            CornerRadius(2.dp.toPx())
        )
        val bw = w * 0.035f
        for (i in 0 until 12) {
            val bh = h * (0.05f + 0.10f * (0.5f + 0.5f * sin(i * 0.9f)))
            drawRoundRect(
                ModernColors.Accent.copy(alpha = 0.85f),
                Offset(w * 0.14f + i * (bw + w * 0.016f), cardTop + h * 0.42f - bh),
                Size(bw, bh),
                CornerRadius(bw / 2f)
            )
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(ModernColors.AccentHi, ModernColors.AccentLo)),
            Offset(w * 0.08f, h * 0.68f),
            Size(w * 0.84f, h * 0.2f),
            CornerRadius(h * 0.1f)
        )
    } else {
        drawRect(Brush.verticalGradient(listOf(SpectraColors.WoodTop, SpectraColors.WoodBottom)))
        drawRoundRect(
            Brush.verticalGradient(listOf(SpectraColors.MetalHi, SpectraColors.MetalMid, SpectraColors.MetalLo)),
            Offset(w * 0.08f, h * 0.10f),
            Size(w * 0.84f, h * 0.52f),
            CornerRadius(7.dp.toPx())
        )
        val sr = 2.dp.toPx()
        val sx = w * 0.12f
        val sy = h * 0.16f
        drawCircle(Color(0xFF6B707A), sr, Offset(sx, sy))
        drawCircle(Color(0xFF6B707A), sr, Offset(w - sx, sy))
        drawCircle(Color(0xFF6B707A), sr, Offset(sx, h * 0.56f))
        drawCircle(Color(0xFF6B707A), sr, Offset(w - sx, h * 0.56f))
        drawRoundRect(
            Brush.verticalGradient(listOf(SpectraColors.ScreenTop, SpectraColors.ScreenBottom)),
            Offset(w * 0.17f, h * 0.20f),
            Size(w * 0.66f, h * 0.34f),
            CornerRadius(5.dp.toPx())
        )
        val bw = w * 0.035f
        for (i in 0 until 12) {
            val bh = h * (0.05f + 0.12f * (0.5f + 0.5f * sin(i * 0.9f)))
            drawRoundRect(
                SpectraColors.Phosphor,
                Offset(w * 0.21f + i * (bw + w * 0.013f), h * 0.50f - bh),
                Size(bw, bh),
                CornerRadius(1.dp.toPx())
            )
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFFAFAFB), Color(0xFFB6BBC4))),
            Offset(w * 0.08f, h * 0.70f),
            Size(w * 0.84f, h * 0.18f),
            CornerRadius(6.dp.toPx())
        )
    }
}
