package com.aravind.spectra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.PI
import kotlin.math.sin

/** Metal badge with two chevrons that float upward and a breathing glow. */
@Composable
fun UpdateBadge(modifier: Modifier = Modifier, color: Color = SpectraColors.Green) {
    val t = rememberInfiniteTransition(label = "badge")
    val float by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val glow by t.animateFloat(
        0.3f, 0.9f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glow"
    )
    Canvas(modifier.size(64.dp)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.5f * glow), Color.Transparent), center = c, radius = r * 1.5f),
            r * 1.5f, c
        )
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFF1F2F5), Color(0xFF8F95A0)), start = c - Offset(r, r), end = c + Offset(r, r)),
            r * 0.92f, c
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF14201B), Color(0xFF050807)), center = c, radius = r),
            r * 0.72f, c
        )
        val lift = (float - 0.5f) * r * 0.22f
        for (k in 0..1) {
            val y = c.y + r * 0.2f - k * r * 0.34f + lift
            val chevron = Path().apply {
                moveTo(c.x - r * 0.32f, y + r * 0.12f)
                lineTo(c.x, y - r * 0.14f)
                lineTo(c.x + r * 0.32f, y + r * 0.12f)
            }
            drawPath(
                chevron,
                color.copy(alpha = if (k == 0) 0.95f else 0.5f),
                style = Stroke(r * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        drawCircle(Color.Black.copy(alpha = 0.5f), r * 0.92f, c, style = Stroke(1.dp.toPx()))
    }
}

/** A progress bar of LED segments with a light wave travelling through the lit part. */
@Composable
fun ChargeBar(progress: Float, segments: Int = 32, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350, easing = FastOutSlowInEasing), label = "charge")
    val t = rememberInfiniteTransition(label = "chargeWave")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "wave")
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (segments - 1)) / segments
        val lit = shown * segments
        for (i in 0 until segments) {
            val fill = (lit - i).coerceIn(0f, 1f)
            val travelling = 0.5f + 0.5f * sin(((i.toFloat() / segments) - wave) * 2f * PI.toFloat() * 1.5f)
            val brightness = if (fill > 0f) (0.55f + 0.45f * travelling) * (0.4f + 0.6f * fill) else 0.16f
            val x = i * (segW + gap)
            drawRoundRect(
                SpectraColors.Phosphor.shade(brightness),
                Offset(x, 0f), Size(segW, size.height), CornerRadius(3.dp.toPx())
            )
            if (fill > 0.5f) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
                    Offset(x, 0f), Size(segW, size.height * 0.4f), CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

/** A glowing LED with a tick and rings spreading outward: "done". */
@Composable
fun SuccessPulse(modifier: Modifier = Modifier, color: Color = SpectraColors.Green) {
    val t = rememberInfiniteTransition(label = "ripple")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "p")
    Canvas(modifier.size(110.dp)) {
        val c = center
        val maxR = size.minDimension / 2f
        for (k in 0..2) {
            val f = (p + k / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - f) * 0.55f), maxR * (0.35f + 0.65f * f), c, style = Stroke(2.dp.toPx()))
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f), color, color.shade(0.5f)),
                center = c - Offset(maxR * 0.08f, maxR * 0.1f),
                radius = maxR * 0.45f
            ),
            maxR * 0.34f, c
        )
        val tick = Path().apply {
            moveTo(c.x - maxR * 0.14f, c.y)
            lineTo(c.x - maxR * 0.03f, c.y + maxR * 0.11f)
            lineTo(c.x + maxR * 0.17f, c.y - maxR * 0.11f)
        }
        drawPath(tick, Color(0xFF053B22), style = Stroke(maxR * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A breathing halo around the main call-to-action button. */
@Composable
fun GlowFrame(color: Color = SpectraColors.Green, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "glowFrame")
    val g by t.animateFloat(
        0.12f, 0.6f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )
    Box(
        Modifier.fillMaxWidth().drawBehind {
            val pad = 5.dp.toPx()
            drawRoundRect(
                color.copy(alpha = g * 0.28f),
                Offset(-pad, -pad), Size(size.width + 2f * pad, size.height + 2f * pad), CornerRadius(16.dp.toPx())
            )
            drawRoundRect(
                color.copy(alpha = g),
                Offset(-pad / 2f, -pad / 2f), Size(size.width + pad, size.height + pad), CornerRadius(14.dp.toPx()),
                style = Stroke(2.dp.toPx())
            )
        }
    ) { content() }
}
