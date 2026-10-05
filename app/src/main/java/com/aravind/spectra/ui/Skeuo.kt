package com.aravind.spectra.ui

import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun Color.shade(f: Float): Color = Color(red * f, green * f, blue * f, alpha)

data class Readout(val label: String, val value: String, val tone: Color = SpectraColors.Phosphor)

/** Dark walnut backdrop with faint grain and a vignette. */
@Composable
fun SkeuoBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SpectraColors.WoodTop, SpectraColors.WoodBottom)))
            .drawBehind {
                val step = 5.dp.toPx()
                var y = 0f
                var i = 0
                while (y < size.height) {
                    val a = 0.015f + ((i * 7919) % 11) / 11f * 0.05f
                    drawLine(Color(0xFF8A5E3C).copy(alpha = a), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                    y += step
                    i++
                }
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color(0xAA000000)),
                        center = Offset(size.width / 2f, size.height * 0.4f),
                        radius = size.maxDimension * 0.8f
                    )
                )
            },
        content = content
    )
}

private fun DrawScope.drawScrew(c: Offset, r: Float, angle: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), r + 1.2.dp.toPx(), c + Offset(0f, 1.2.dp.toPx()))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFAFAFA), Color(0xFF9FA3AB), Color(0xFF4F535B)),
            center = c - Offset(r * 0.3f, r * 0.3f),
            radius = r * 1.5f
        ),
        r,
        c
    )
    drawCircle(Color(0xFF3C3F46), r, c, style = Stroke(1.dp.toPx()))
    val t = Math.toRadians(angle.toDouble())
    val d = Offset((cos(t) * r * 0.7).toFloat(), (sin(t) * r * 0.7).toFloat())
    drawLine(Color(0xFF2B2E34), c - d, c + d, 1.6.dp.toPx(), StrokeCap.Round)
}

@Composable
fun Engraved(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.sp,
    color: Color = SpectraColors.InkSoft,
    weight: FontWeight = FontWeight.Bold,
    spacing: TextUnit = 1.sp,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = spacing,
        maxLines = maxLines,
        style = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.95f), Offset(0f, 1.5f), 0f))
    )
}

@Composable
fun Lcd(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SpectraColors.Phosphor,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Medium,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = size,
        fontFamily = FontFamily.Monospace,
        fontWeight = weight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(shadow = Shadow(color.copy(alpha = 0.7f), Offset.Zero, 14f))
    )
}

/** A brushed-aluminium rack unit with engraved title, power lamp and corner screws. */
@Composable
fun RackPanel(
    title: String,
    modifier: Modifier = Modifier,
    lamp: Color? = null,
    lampOn: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(SpectraColors.MetalHi, SpectraColors.MetalMid, SpectraColors.MetalLo))
            )
            .drawBehind {
                val step = 3.dp.toPx()
                var y = step
                var i = 0
                while (y < size.height) {
                    drawLine(
                        if (i % 2 == 0) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.035f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        1.dp.toPx()
                    )
                    y += step
                    i++
                }
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.9f), Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(2.dp.toPx())
                )
            }
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Engraved(title.uppercase(), Modifier.weight(1f), size = 12.sp, spacing = 2.sp)
                if (lamp != null) Led(lamp, lampOn, 10.dp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
        Canvas(Modifier.matchParentSize()) {
            val inset = 11.dp.toPx()
            val r = 4.5.dp.toPx()
            drawScrew(Offset(inset, inset), r, 35f)
            drawScrew(Offset(size.width - inset, inset), r, 110f)
            drawScrew(Offset(inset, size.height - inset), r, 75f)
            drawScrew(Offset(size.width - inset, size.height - inset), r, 160f)
        }
    }
}

/** Recessed dark glass display with bezel, inner shadow and glare. */
@Composable
fun GlassScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(SpectraColors.MetalEdgeDark, Color(0xFFC3C7CF))))
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(SpectraColors.ScreenTop, SpectraColors.ScreenBottom)))
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                            endY = 16.dp.toPx()
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.7f, size.height * 0.55f)
                        )
                    )
                },
            content = content
        )
    }
}

@Composable
fun ReadoutGrid(items: List<Readout>) {
    GlassScreen {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            for (row in items.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    for (cell in row) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                cell.label.uppercase(),
                                color = SpectraColors.PhosphorDim,
                                fontSize = 9.5.sp,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Lcd(cell.value, color = cell.tone, size = 15.sp, maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun Led(color: Color, on: Boolean, diameter: Dp = 10.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        val r = this.size.minDimension / 2f
        val c = this.center
        if (on) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 2.8f),
                r * 2.8f,
                c
            )
        }
        val body = if (on) listOf(Color.White.copy(alpha = 0.95f), color, color.shade(0.55f))
        else listOf(Color(0xFF6C717A), Color(0xFF30343B), Color(0xFF16181C))
        drawCircle(Brush.radialGradient(body, center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.3f), r, c)
        drawCircle(Color.Black.copy(alpha = 0.55f), r, c, style = Stroke(1.dp.toPx()))
    }
}

/** Latching push button: raised when idle, pressed-in when active/held. */
@Composable
fun HwButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    led: Color? = null,
    height: Dp = 44.dp,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val down = pressed || active
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(11.dp)
    val face = if (down) listOf(Color(0xFFA7ACB5), Color(0xFFC9CDD4))
    else listOf(Color(0xFFFAFAFB), Color(0xFFB6BBC4))
    val rim = if (down) listOf(Color(0xFF6B707A), Color(0xFFEDEEF1))
    else listOf(Color.White, Color(0xFF6B707A))
    val ink = if (down) SpectraColors.Ink else SpectraColors.InkSoft
    Box(
        modifier
            .height(height)
            .shadow(if (down) 1.dp else 7.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(face))
            .border(1.dp, Brush.verticalGradient(rim), shape)
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = if (compact) 2.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (led != null) {
                    Led(led, active, 8.dp)
                    Spacer(Modifier.height(4.dp))
                }
                Engraved(label, size = 11.sp, spacing = 0.5.sp, color = ink, maxLines = 1)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (led != null) {
                    Led(led, active, 9.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Engraved(label, size = 12.sp, spacing = 1.5.sp, color = ink)
            }
        }
    }
}

/** Vintage analog VU meter with a spring-damped needle. */
@Composable
fun VuMeter(
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
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.38f, stiffness = 40f), label = "vu")
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.85f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(SpectraColors.MetalEdgeDark, Color(0xFFB4B9C2))))
            .padding(4.dp)
    ) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
            val w = this.size.width
            val h = this.size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFBF1CF), Color(0xFFE4CB8C))))
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color(0x33401C00)),
                    center = Offset(w / 2f, h * 0.9f),
                    radius = w * 0.75f
                )
            )
            val cx = w / 2f
            val cy = h * 1.12f
            val r = h
            fun ang(v: Float): Float = -50f + (v - minV) / (maxV - minV) * 100f
            fun pt(a: Float, rad: Float): Offset {
                val t = Math.toRadians(a.toDouble())
                return Offset(cx + (rad * sin(t)).toFloat(), cy - (rad * cos(t)).toFloat())
            }

            val arcR = r * 0.80f
            val arcTopLeft = Offset(cx - arcR, cy - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            drawArc(
                Color(0xFF2A2A2A), -90f + ang(minV), ang(redFrom) - ang(minV), false,
                arcTopLeft, arcSize, style = Stroke(1.5.dp.toPx())
            )
            drawArc(
                Color(0xFFD1432D), -90f + ang(redFrom), ang(maxV) - ang(redFrom), false,
                arcTopLeft, arcSize, style = Stroke(4.dp.toPx())
            )

            paint.color = SpectraColors.Ink.toArgb()
            paint.textSize = 10.sp.toPx()
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            val n = ((maxV - minV) / 2f).roundToInt()
            for (i in 0..n) {
                val v = minV + 2f * i
                val major = i % 3 == 0
                val a = ang(v)
                drawLine(
                    if (v >= redFrom) Color(0xFFB02A18) else Color(0xFF2A2A2A),
                    pt(a, arcR - (if (major) 11 else 6).dp.toPx()),
                    pt(a, arcR),
                    if (major) 2.dp.toPx() else 1.dp.toPx()
                )
                if (major) {
                    val p = pt(a, arcR - 22.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(), p.x, p.y + 4.dp.toPx(), paint)
                }
            }
            paint.textSize = 13.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(title, cx, cy - r * 0.42f, paint)

            val a = ang(shown)
            val tip = pt(a, r * 0.95f)
            val base = pt(a, r * 0.16f)
            val sh = Offset(3.dp.toPx(), 3.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.22f), base + sh, tip + sh, 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF1A1A1A), base, tip, 2.dp.toPx(), StrokeCap.Round)
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF3A2A1E), Color(0xFF140E0A)), startY = h * 0.92f, endY = h),
                topLeft = Offset(0f, h * 0.92f),
                size = Size(w, h * 0.08f + 2f)
            )
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = h * 0.5f))
        }
    }
}

/** Segmented LED bar meter (green -> amber -> red), sweeps in on first draw. */
@Composable
fun LedLadder(
    valueDb: Double,
    minDb: Double,
    maxDb: Double,
    modifier: Modifier = Modifier,
    segments: Int = 28,
    tint: Color? = null
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val frac = ((valueDb - minDb) / (maxDb - minDb)).toFloat().coerceIn(0f, 1f)
    val anim by animateFloatAsState(if (armed) frac else 0f, tween(900, easing = FastOutSlowInEasing), label = "ladder")
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2.dp.toPx()
        val segW = (this.size.width - gap * (segments - 1)) / segments
        val lit = (anim * segments).roundToInt()
        for (i in 0 until segments) {
            val z = i.toFloat() / segments
            val base = tint ?: when {
                z < 0.62f -> SpectraColors.Green
                z < 0.85f -> SpectraColors.Amber
                else -> SpectraColors.Red
            }
            val x = i * (segW + gap)
            val on = i < lit
            drawRoundRect(
                if (on) base else base.shade(0.2f),
                Offset(x, 0f),
                Size(segW, this.size.height),
                CornerRadius(2.dp.toPx())
            )
            if (on) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x, 0f),
                    Size(segW, this.size.height * 0.4f),
                    CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/** Horizontal fader with a metal cap. */
@Composable
fun HwFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val cb by rememberUpdatedState(onValueChange)
    val capW = 26.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(range) {
                detectTapGestures { o ->
                    val cw = capW.toPx()
                    val f = ((o.x - cw / 2f) / (size.width - cw)).coerceIn(0f, 1f)
                    cb(range.start + f * (range.endInclusive - range.start))
                }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val cw = capW.toPx()
                    val f = ((change.position.x - cw / 2f) / (size.width - cw)).coerceIn(0f, 1f)
                    cb(range.start + f * (range.endInclusive - range.start))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = capW.toPx()
            val ch = 30.dp.toPx()
            val gy = this.size.height / 2f
            val gh = 8.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF3A3D44), Color(0xFFD7DAE0)),
                    startY = gy - gh / 2f,
                    endY = gy + gh / 2f
                ),
                Offset(cw / 2f, gy - gh / 2f),
                Size(this.size.width - cw, gh),
                CornerRadius(gh / 2f)
            )
            for (i in 0..10) {
                val x = cw / 2f + (this.size.width - cw) * i / 10f
                val len = (if (i % 5 == 0) 6 else 3).dp.toPx()
                drawLine(SpectraColors.Ink.copy(alpha = 0.5f), Offset(x, gy + gh), Offset(x, gy + gh + len), 1.dp.toPx())
            }
            val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val cx = cw / 2f + (this.size.width - cw) * f
            drawRoundRect(
                Color.Black.copy(alpha = 0.3f),
                Offset(cx - cw / 2f + 2.dp.toPx(), gy - ch / 2f + 3.dp.toPx()),
                Size(cw, ch),
                CornerRadius(5.dp.toPx())
            )
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color(0xFFB8BCC4), Color(0xFFFAFAFA), Color(0xFFB8BCC4)),
                    startX = cx - cw / 2f,
                    endX = cx + cw / 2f
                ),
                Offset(cx - cw / 2f, gy - ch / 2f),
                Size(cw, ch),
                CornerRadius(5.dp.toPx())
            )
            drawLine(
                Color(0xFF2B2E33),
                Offset(cx, gy - ch / 2f + 4.dp.toPx()),
                Offset(cx, gy + ch / 2f - 4.dp.toPx()),
                2.dp.toPx()
            )
        }
    }
}
