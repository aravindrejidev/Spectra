package com.aravind.spectra.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Hands out 0, 1, 2... to panels that appear together, so they slide in one after another. */
object Stagger {
    private var last = 0L
    private var n = 0

    fun next(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - last > 400) n = 0
        last = now
        return n++
    }
}

/** The text revealed one character at a time, like an LCD booting. */
@Composable
fun rememberTyped(text: String, msPerChar: Long = 26L): String {
    var n by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        n = 0
        while (n < text.length) {
            delay(msPerChar)
            n++
        }
    }
    return text.take(n)
}

/** Reveals the content left to right behind a glowing scan line. Restarts when [key] changes. */
@Composable
fun Modifier.revealWipe(durationMs: Int = 900, key: Any? = null): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return this.drawWithContent {
        val reveal = progress.value
        clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
        if (reveal < 1f) {
            val x = size.width * reveal
            drawLine(
                SpectraColors.Phosphor.copy(alpha = 0.85f),
                Offset(x, 0f), Offset(x, size.height),
                2.dp.toPx()
            )
        }
    }
}

/** A lamp whose glow breathes; used while something is running. */
@Composable
fun PulsingLed(color: Color, diameter: Dp = 10.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val glow by t.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Canvas(modifier.size(diameter)) {
        val r = this.size.minDimension / 2f
        val c = this.center
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.6f * glow), Color.Transparent), center = c, radius = r * 3f),
            r * 3f, c
        )
        val body = listOf(
            Color.White.copy(alpha = 0.5f + 0.45f * glow),
            color.shade(0.6f + 0.4f * glow),
            color.shade(0.4f + 0.2f * glow)
        )
        drawCircle(Brush.radialGradient(body, center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.3f), r, c)
        drawCircle(Color.Black.copy(alpha = 0.55f), r, c, style = Stroke(1.dp.toPx()))
    }
}

/** LED bar with a light sweeping back and forth: "working, no percentage known". */
@Composable
fun ScanBar(segments: Int = 30, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "scan")
    val pos by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pos"
    )
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2.dp.toPx()
        val segW = (this.size.width - gap * (segments - 1)) / segments
        val head = pos * (segments - 1)
        for (i in 0 until segments) {
            val a = (1f - abs(i - head) / 5f).coerceIn(0f, 1f)
            drawRoundRect(
                SpectraColors.Phosphor.shade(0.18f + 0.82f * a),
                Offset(i * (segW + gap), 0f),
                Size(segW, this.size.height),
                CornerRadius(2.dp.toPx())
            )
        }
    }
}

/** 0..1 sweep position for the metal shine, -1 while resting. Does nothing unless [enabled]. */
@Composable
fun rememberShine(enabled: Boolean): State<Float> {
    val sweep = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        delay(900)
        while (true) {
            animate(0f, 1f, animationSpec = tween(1500, easing = FastOutSlowInEasing)) { v, _ ->
                sweep.floatValue = v
            }
            sweep.floatValue = -1f
            delay(8000)
        }
    }
    return sweep
}
