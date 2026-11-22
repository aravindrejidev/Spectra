package com.aravind.spectra.ui

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.dsp.Severity
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.ModernColors
import com.aravind.spectra.ui.theme.SpectraColors
import com.aravind.spectra.ui.theme.isModernUi
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ small helpers

/** Fades and lifts a piece of UI in, [index] steps after its neighbours. Does nothing with Reduce motion. */
@Composable
fun Modifier.popIn(index: Int = 0, dy: Dp = 10.dp): Modifier {
    if (AppSettings.reduceMotion) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(520L))
        p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * dy.toPx()
        val sc = 0.94f + 0.06f * p.value
        scaleX = sc
        scaleY = sc
    }
}

/** [popIn] in the Modern look, nothing in Classic. */
@Composable
fun Modifier.modernPopIn(index: Int = 0): Modifier = if (isModernUi) popIn(index) else this

private val KEEP_AS_IS = setOf(
    "L", "R", "C", "LFE", "LS", "RS", "LB", "RB", "MID", "SIDE", "MONO", "L+R", "LIN", "LOG", "PDF", "CSV", "DR", "LUFS"
)

/** "LOAD TRACK" becomes "Load Track"; channel names and acronyms stay as they are. */
internal fun prettyLabel(s: String): String {
    if (s != s.uppercase()) return s
    if (s in KEEP_AS_IS) return s
    if (s.length > 2 && s.startsWith("CH") && s[2].isDigit()) return s
    return s.split(' ').joinToString(" ") { w ->
        when (w) {
            "CSV", "PDF" -> w
            "FFMPEG" -> "FFmpeg"
            else -> w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

/** Classic displays shout in capitals; the Modern look keeps normal sentence case. */
internal fun lcdCase(text: String): String = if (isModernUi) text else text.uppercase()

internal fun sevLabel(s: Severity): String = when (s) {
    Severity.GOOD -> "No lossy sign"
    Severity.INFO -> "Inconclusive"
    Severity.WARN -> "Suspicious"
    Severity.BAD -> "Likely fake"
}

// ------------------------------------------------------------------ backdrop and cards

/** Near-black page with two soft red glows that breathe slowly. */
@Composable
fun ModernBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "mbg")
    val breathe by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val calm = AppSettings.reduceMotion
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ModernColors.BgTop, ModernColors.BgBottom)))
            .drawBehind {
                val k = if (calm) 0.5f else breathe
                drawRect(
                    Brush.radialGradient(
                        listOf(ModernColors.Accent.copy(alpha = 0.14f + 0.10f * k), Color.Transparent),
                        center = Offset(size.width * 0.96f, size.height * 0.02f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(ModernColors.AccentLo.copy(alpha = 0.08f + 0.07f * (1f - k)), Color.Transparent),
                        center = Offset(size.width * 0.04f, size.height * 0.98f),
                        radius = size.maxDimension * 0.5f
                    )
                )
            },
        content = content
    )
}

/** The glass surface shared by every card: gradient fill, soft red corner glow, gradient hairline. */
@Composable
internal fun ModernPanelBox(
    order: Int,
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        if (shown || AppSettings.reduceMotion) 1f else 0f,
        tween(560, delayMillis = (order * 70).coerceAtMost(560), easing = FastOutSlowInEasing),
        label = "panelEnter"
    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 40.dp.toPx()
                val sc = 0.96f + 0.04f * enter
                scaleX = sc
                scaleY = sc
            }
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = ModernColors.AccentLo)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ModernColors.CardTop, ModernColors.CardBottom)))
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(w * 0.65f, h * 0.65f)
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(ModernColors.Accent.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(w, h),
                        radius = min(w, h) * 0.95f + 40.dp.toPx()
                    )
                )
                val sw = 1.2.dp.toPx()
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.05f),
                            ModernColors.Accent.copy(alpha = 0.42f)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    ),
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(w - sw, h - sw),
                    cornerRadius = CornerRadius(radius.toPx() - sw / 2f),
                    style = Stroke(sw)
                )
            },
        content = content
    )
}

/** A card with a red accent bar, a title and an optional status dot. */
@Composable
internal fun ModernCard(
    title: String,
    modifier: Modifier,
    lamp: Color?,
    lampOn: Boolean,
    lampPulse: Boolean,
    shine: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val order = remember { Stagger.next() }
    val bar = remember { Animatable(if (AppSettings.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(180L + (order * 70L).coerceAtMost(560L))
        bar.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val sweep by rememberShine(shine && AppSettings.headerShine && !AppSettings.reduceMotion)
    ModernPanelBox(order, modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 16.dp)
                        .graphicsLayer { scaleY = bar.value }
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(ModernColors.AccentHi, ModernColors.AccentLo)))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    Modifier.weight(1f),
                    color = ModernColors.TextHi,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lamp != null) {
                    if (lampPulse && !AppSettings.reduceMotion) ModernPulseDot(lamp, 9.dp) else ModernDot(lamp, lampOn, 9.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
        if (shine) {
            Canvas(Modifier.matchParentSize()) {
                if (sweep >= 0f) {
                    val cx = size.width * (sweep * 1.5f - 0.25f)
                    val half = 70.dp.toPx()
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(cx - half, 0f),
                            end = Offset(cx + half, size.height * 0.5f)
                        )
                    )
                }
            }
        }
    }
}

/** A darker inset area inside a card, for displays, plots and readouts. */
@Composable
internal fun ModernInset(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ModernColors.InsetTop, ModernColors.InsetBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                        endY = 14.dp.toPx()
                    )
                )
            },
        content = content
    )
}

// ------------------------------------------------------------------ dots

/** A flat glowing status dot. */
@Composable
internal fun ModernDot(color: Color, on: Boolean, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val c = center
        if (on) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.50f), Color.Transparent), center = c, radius = r * 3.2f),
                r * 3.2f,
                c
            )
            drawCircle(color, r, c)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.36f, c - Offset(r * 0.22f, r * 0.26f))
        } else {
            drawCircle(Color.White.copy(alpha = 0.16f), r, c)
        }
    }
}

/** A status dot with a ring that keeps spreading outward: "working". */
@Composable
internal fun ModernPulseDot(color: Color, diameter: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mpulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(color.copy(alpha = (1f - p) * 0.6f), r * (1f + 2.2f * p), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 3f),
            r * 3f,
            c
        )
        drawCircle(color, r, c)
        drawCircle(Color.White.copy(alpha = 0.5f), r * 0.36f, c - Offset(r * 0.22f, r * 0.26f))
    }
}

// ------------------------------------------------------------------ buttons

/**
 * The Modern button. [primary] is the red call-to-action with a glow and a light sweep; the others are
 * dark glass. [active] (a latching button) turns it red-tinted. Every press scales the button and spreads a
 * ripple from the finger.
 */
@Composable
internal fun ModernButton(
    label: String,
    modifier: Modifier,
    active: Boolean,
    led: Color?,
    height: Dp,
    compact: Boolean,
    primary: Boolean,
    icon: MIconKind?,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "mbtnScale"
    )
    val sel by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "mbtnSel")
    val glow by animateDpAsState(
        if (!primary) 0.dp else if (pressed) 4.dp else 14.dp,
        tween(140),
        label = "mbtnGlow"
    )
    val half = height / 2
    val cap = if (compact) 16.dp else 26.dp
    val shape = RoundedCornerShape(if (half < cap) half else cap)
    val shine by rememberShine(primary && !AppSettings.reduceMotion)
    val ripple = remember { Animatable(0f) }
    var ripplePos by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(src) {
        src.interactions.collect { i ->
            if (i is PressInteraction.Press) {
                ripplePos = i.pressPosition
                launch {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    val faceTop = if (primary) {
        if (pressed) ModernColors.Accent else ModernColors.AccentHi
    } else {
        lerp(Color(0xFF22232B), Color(0xFF4A1A22), sel)
    }
    val faceBottom = if (primary) {
        if (pressed) ModernColors.AccentDeep else ModernColors.AccentLo
    } else {
        lerp(Color(0xFF14151A), Color(0xFF2A1015), sel)
    }
    val rimTop = if (primary) {
        Color.White.copy(alpha = 0.45f)
    } else {
        lerp(Color.White.copy(alpha = 0.16f), ModernColors.AccentHi.copy(alpha = 0.85f), sel)
    }
    val rimBottom = if (primary) {
        Color.White.copy(alpha = 0.06f)
    } else {
        lerp(Color.White.copy(alpha = 0.05f), ModernColors.Accent.copy(alpha = 0.45f), sel)
    }
    val ink = if (primary) Color.White else lerp(Color(0xFFD5D7E0), ModernColors.TextHi, sel)

    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(glow, shape, ambientColor = ModernColors.Accent, spotColor = ModernColors.Accent)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(faceTop, faceBottom)))
            .border(1.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
            .clickable(interactionSource = src, indication = null) {
                if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .drawWithContent {
                drawContent()
                val r = ripple.value
                if (r > 0f && r < 1f) {
                    drawCircle(Color.White.copy(alpha = 0.22f * (1f - r)), size.maxDimension * r, ripplePos)
                }
                if (primary && shine >= 0f) {
                    val cx = size.width * (shine * 1.6f - 0.3f)
                    val bandHalf = 46.dp.toPx()
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                            start = Offset(cx - bandHalf, 0f),
                            end = Offset(cx + bandHalf, size.height * 0.6f)
                        )
                    )
                }
            }
            .padding(horizontal = if (compact) 4.dp else if (primary) 18.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val text = prettyLabel(label)
        if (compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (led != null) {
                    ModernDot(led, active, 7.dp)
                    Spacer(Modifier.height(5.dp))
                }
                Text(
                    text,
                    color = ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    MIcon(icon, tint = ink, size = if (primary) 20.dp else 18.dp)
                    Spacer(Modifier.width(if (primary) 10.dp else 8.dp))
                }
                Text(
                    text,
                    color = ink,
                    fontSize = if (primary) 16.sp else 14.sp,
                    fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A round glass button with an icon. [spin] is how far the icon turns while the button is held. */
@Composable
internal fun ModernIconButton(
    kind: MIconKind,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    spin: Float = 0f,
    tint: Color = ModernColors.TextHi
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "mibScale"
    )
    val rot by animateFloatAsState(
        if (pressed) spin else 0f,
        spring(dampingRatio = 0.5f, stiffness = 220f),
        label = "mibRot"
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF24252D), Color(0xFF15161B))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))),
                CircleShape
            )
            .clickable(interactionSource = src, indication = null) {
                if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        MIcon(kind, Modifier.graphicsLayer { rotationZ = rot }, tint, size * 0.5f)
    }
}

// ------------------------------------------------------------------ readouts

/** Two columns of small stat tiles: the label on top, the value below. */
@Composable
internal fun ModernReadoutGrid(items: List<Readout>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, cell ->
                    ModernTile(cell, rowIndex * 2 + colIndex, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModernTile(cell: Readout, index: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .popIn(index)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            cell.label.uppercase(),
            color = ModernColors.TextLow,
            fontSize = 9.5.sp,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            cell.value,
            color = if (cell.tone == SpectraColors.Phosphor) ModernColors.TextHi else cell.tone,
            fontSize = if (cell.value.length > 13) 13.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3
        )
    }
}

/** A half-circle gauge: an arc that fills up to the value, with a glowing knob and a count-up number. */
@Composable
internal fun ModernVuMeter(
    value: Float?,
    minV: Float,
    maxV: Float,
    redFrom: Float,
    title: String,
    modifier: Modifier = Modifier
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val target = if (armed && value != null) value.coerceIn(minV, maxV) else minV
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = 60f), label = "mvu")
    val number by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "mvuNum")
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.85f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ModernColors.InsetTop, ModernColors.InsetBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeW = 10.dp.toPx()
            val radius = min(w * 0.40f, h * 0.74f)
            val cx = w / 2f
            val cy = h * 0.84f
            val topLeft = Offset(cx - radius, cy - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            fun frac(v: Float): Float = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)

            fun pointAt(f: Float, r: Float): Offset {
                val a = Math.toRadians(180.0 + 180.0 * f)
                return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            }

            drawArc(
                Color.White.copy(alpha = 0.07f), 180f, 180f, false, topLeft, arcSize,
                style = Stroke(strokeW, cap = StrokeCap.Round)
            )
            val rf = frac(redFrom)
            drawArc(
                ModernColors.AccentLo.copy(alpha = 0.40f), 180f + 180f * rf, 180f * (1f - rf), false, topLeft, arcSize,
                style = Stroke(strokeW)
            )
            val vf = frac(shown)
            val soft = Color(0xFFE9EAF0)
            if (vf > 0.01f) {
                val safe = min(vf, rf)
                if (safe > 0.01f) {
                    drawArc(
                        soft.copy(alpha = 0.18f), 180f, 180f * safe, false, topLeft, arcSize,
                        style = Stroke(strokeW * 1.9f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        soft, 180f, 180f * safe, false, topLeft, arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }
                if (vf > rf) {
                    drawArc(
                        ModernColors.Accent.copy(alpha = 0.25f), 180f + 180f * rf, 180f * (vf - rf), false, topLeft, arcSize,
                        style = Stroke(strokeW * 1.9f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        ModernColors.Accent, 180f + 180f * rf, 180f * (vf - rf), false, topLeft, arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            paint.color = ModernColors.TextLow.toArgb()
            paint.textSize = 9.sp.toPx()
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT
            val n = ((maxV - minV) / 2f).roundToInt()
            for (i in 0..n) {
                val v = minV + 2f * i
                val f = frac(v)
                val major = i % 3 == 0
                val outer = radius - strokeW / 2f - 3.dp.toPx()
                val inner = outer - (if (major) 9 else 5).dp.toPx()
                val tickColor = if (v >= redFrom) {
                    ModernColors.AccentHi.copy(alpha = 0.75f)
                } else {
                    Color.White.copy(alpha = if (major) 0.45f else 0.2f)
                }
                drawLine(tickColor, pointAt(f, inner), pointAt(f, outer), if (major) 1.6.dp.toPx() else 1.dp.toPx())
                if (major) {
                    val p = pointAt(f, inner - 9.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(), p.x, p.y + 3.dp.toPx(), paint)
                }
            }

            val tip = pointAt(vf, radius)
            val tipColor = if (vf > rf) ModernColors.Accent else ModernColors.TextMid
            drawCircle(
                Brush.radialGradient(listOf(tipColor.copy(alpha = 0.55f), Color.Transparent), center = tip, radius = strokeW * 2.2f),
                strokeW * 2.2f,
                tip
            )
            drawCircle(Color.White, strokeW * 0.62f, tip)
            drawCircle(tipColor, strokeW * 0.62f, tip, style = Stroke(2.dp.toPx()))

            paint.color = ModernColors.TextHi.toArgb()
            paint.textSize = 30.sp.toPx()
            paint.typeface = Typeface.DEFAULT_BOLD
            val txt = if (value == null) "—" else String.format(Locale.US, "%.1f", number)
            drawContext.canvas.nativeCanvas.drawText(txt, cx, cy - radius * 0.10f, paint)
            paint.color = ModernColors.TextLow.toArgb()
            paint.textSize = 12.sp.toPx()
            paint.typeface = Typeface.DEFAULT
            drawContext.canvas.nativeCanvas.drawText(title, cx, cy + 12.dp.toPx(), paint)
        }
    }
}

/** A smooth level bar that fills in on first draw. Green, amber and red zones unless [tint] is given. */
@Composable
internal fun ModernLadder(
    valueDb: Double,
    minDb: Double,
    maxDb: Double,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val frac = ((valueDb - minDb) / (maxDb - minDb)).toFloat().coerceIn(0f, 1f)
    val anim by animateFloatAsState(if (armed) frac else 0f, tween(900, easing = FastOutSlowInEasing), label = "mladder")
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val barH = 8.dp.toPx()
        val y = (size.height - barH) / 2f
        val r = barH / 2f
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, barH), CornerRadius(r))
        val fillW = size.width * anim
        if (fillW > 1f) {
            val brush = if (tint != null) {
                Brush.horizontalGradient(
                    0f to tint.copy(alpha = 0.55f),
                    1f to tint,
                    startX = 0f,
                    endX = max(fillW, 2f)
                )
            } else {
                Brush.horizontalGradient(
                    0f to ModernColors.Good,
                    0.62f to ModernColors.Good,
                    0.85f to ModernColors.Warn,
                    1f to ModernColors.Bad,
                    startX = 0f,
                    endX = size.width
                )
            }
            drawRoundRect(brush, Offset(0f, y), Size(fillW, barH), CornerRadius(r))
            if (fillW > barH) {
                val glowColor = tint ?: Color.White
                drawCircle(
                    Brush.radialGradient(
                        listOf(glowColor.copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(fillW - r, y + r),
                        radius = barH * 1.6f
                    ),
                    barH * 1.6f,
                    Offset(fillW - r, y + r)
                )
            }
        }
    }
}

/** An indeterminate bar: a bright comet slides back and forth: "working, no percentage known". */
@Composable
internal fun ModernScanBar(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mscan")
    val pos by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pos"
    )
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val barH = 8.dp.toPx()
        val y = (size.height - barH) / 2f
        val r = barH / 2f
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, barH), CornerRadius(r))
        val cometW = size.width * 0.34f
        val x0 = (size.width - cometW) * pos
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, ModernColors.Accent, ModernColors.AccentHi),
                startX = x0,
                endX = x0 + cometW
            ),
            Offset(x0, y),
            Size(cometW, barH),
            CornerRadius(r)
        )
    }
}

/** A slider: red filled track and a round knob that grows while it is dragged. */
@Composable
internal fun ModernFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val cb by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(
        if (dragging) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "faderGrow"
    )
    val margin = 14.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(range) {
                detectTapGestures { o ->
                    val m = margin.toPx()
                    val f = ((o.x - m) / (size.width - 2f * m)).coerceIn(0f, 1f)
                    cb(range.start + f * (range.endInclusive - range.start))
                }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    val m = margin.toPx()
                    val f = ((change.position.x - m) / (size.width - 2f * m)).coerceIn(0f, 1f)
                    cb(range.start + f * (range.endInclusive - range.start))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val m = margin.toPx()
            val gy = size.height / 2f
            val gh = 6.dp.toPx()
            val trackW = size.width - 2f * m
            val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val cx = m + trackW * f
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(m, gy - gh / 2f), Size(trackW, gh), CornerRadius(gh / 2f))
            for (i in 0..10) {
                val x = m + trackW * i / 10f
                val len = (if (i % 5 == 0) 5 else 3).dp.toPx()
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, gy + gh + 2.dp.toPx()), Offset(x, gy + gh + 2.dp.toPx() + len), 1.dp.toPx())
            }
            if (cx - m > 1f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(ModernColors.AccentLo, ModernColors.AccentHi), startX = m, endX = max(cx, m + 2f)),
                    Offset(m, gy - gh / 2f),
                    Size(cx - m, gh),
                    CornerRadius(gh / 2f)
                )
            }
            val kr = 10.dp.toPx() + 3.dp.toPx() * grow
            drawCircle(
                Brush.radialGradient(
                    listOf(ModernColors.Accent.copy(alpha = 0.30f + 0.25f * grow), Color.Transparent),
                    center = Offset(cx, gy),
                    radius = kr * 2.2f
                ),
                kr * 2.2f,
                Offset(cx, gy)
            )
            drawCircle(Color.Black.copy(alpha = 0.35f), kr, Offset(cx, gy + 1.5.dp.toPx()))
            drawCircle(Color(0xFFF4F5F8), kr, Offset(cx, gy))
            drawCircle(ModernColors.Accent, kr * 0.42f, Offset(cx, gy))
        }
    }
}

// ------------------------------------------------------------------ switch, check, chips

/** On/off switch: the knob springs across, the track turns red and a tick fades in. */
@Composable
fun ModernSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "mswitch"
    )
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val grow by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "mswitchGrow")
    val haptic = LocalHapticFeedback.current
    Canvas(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clickable(interactionSource = src, indication = null) {
                if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(!checked)
            }
    ) {
        val w = size.width
        val h = size.height
        val xc = x.coerceIn(0f, 1f)
        drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = CornerRadius(h / 2f))
        drawRoundRect(
            Brush.horizontalGradient(listOf(ModernColors.AccentLo, ModernColors.AccentHi)),
            cornerRadius = CornerRadius(h / 2f),
            alpha = xc
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.14f * (1f - xc)),
            cornerRadius = CornerRadius(h / 2f),
            style = Stroke(1.dp.toPx())
        )
        val r = h / 2f - 3.dp.toPx() + 1.5.dp.toPx() * grow
        val cx = h / 2f + xc * (w - h)
        val cy = h / 2f
        drawCircle(Color.Black.copy(alpha = 0.30f), r, Offset(cx, cy + 1.5.dp.toPx()))
        drawCircle(Color(0xFFF4F5F8), r, Offset(cx, cy))
        if (xc > 0.05f) {
            drawProgressCheck(
                Offset(cx - r, cy - r),
                r * 2f,
                xc,
                ModernColors.Accent.copy(alpha = xc),
                1.8.dp.toPx()
            )
        }
    }
}

/** A list row with a box that ticks itself in, one row after the other. */
@Composable
fun ModernCheckRow(text: String, index: Int) {
    val p = remember { Animatable(if (AppSettings.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(300L + index * 110L)
        p.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(22.dp)) {
            val v = p.value
            val rr = CornerRadius(7.dp.toPx())
            drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = rr)
            drawRoundRect(
                Brush.linearGradient(listOf(ModernColors.AccentHi, ModernColors.AccentLo)),
                cornerRadius = rr,
                alpha = v
            )
            drawRoundRect(Color.White.copy(alpha = 0.16f * (1f - v)), cornerRadius = rr, style = Stroke(1.dp.toPx()))
            drawProgressCheck(Offset.Zero, size.minDimension, v, Color.White, 2.dp.toPx())
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            Modifier.weight(1f),
            color = ModernColors.TextHi.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
    }
}

/** A coloured pill with the verdict of a file: "No lossy sign", "Suspicious", "Likely fake"... */
@Composable
fun StatusChip(severity: Severity, modifier: Modifier = Modifier, label: String = sevLabel(severity)) {
    val tone = sevColor(severity)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (severity) {
            Severity.GOOD -> ModernDot(tone, true, 8.dp)
            Severity.INFO -> MIcon(MIconKind.Info, tint = tone, size = 15.dp)
            Severity.WARN -> MIcon(MIconKind.Warning, tint = tone, size = 15.dp)
            Severity.BAD -> MIcon(MIconKind.Alert, tint = tone, size = 15.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = tone, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A banner with a round icon, a title and a message, tinted by [tone]. */
@Composable
fun ModernNotice(
    title: String,
    message: String,
    tone: Color,
    icon: MIconKind,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .popIn()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.16f), tone.copy(alpha = 0.04f))))
            .border(1.dp, tone.copy(alpha = 0.40f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tone),
            contentAlignment = Alignment.Center
        ) {
            MIcon(icon, tint = Color.White, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ModernColors.TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(message, color = ModernColors.TextMid, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
