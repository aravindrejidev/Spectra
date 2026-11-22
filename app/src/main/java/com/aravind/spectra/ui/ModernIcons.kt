package com.aravind.spectra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aravind.spectra.ui.theme.Pal
import kotlin.math.cos
import kotlin.math.sin

/** The small icon set of the Modern look. Drawn with paths, so no icon library is needed. */
enum class MIconKind {
    Folder, Play, Gear, Back, Chevron, Close, Check, Info, Warning, Alert,
    Pulse, Waveform, Music, Trophy, Share, Eject, File, Download
}

@Composable
fun MIcon(kind: MIconKind, modifier: Modifier = Modifier, tint: Color = Pal.Ink, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawMIcon(kind, tint) }
}

/** Draws [kind] filling the current draw area (a 24 x 24 grid scaled to the smaller side). */
fun DrawScope.drawMIcon(kind: MIconKind, tint: Color, strokeUnits: Float = 1.8f) {
    val s = size.minDimension / 24f
    val sw = strokeUnits * s
    val line = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun o(x: Float, y: Float): Offset = Offset(x * s, y * s)

    fun poly(vararg pts: Float, closed: Boolean = false): Path {
        val p = Path()
        p.moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i] * s, pts[i + 1] * s)
            i += 2
        }
        if (closed) p.close()
        return p
    }

    when (kind) {
        MIconKind.Folder -> {
            drawRoundRect(tint, o(3f, 8.5f), Size(18f * s, 11f * s), CornerRadius(2.5f * s), style = line)
            drawPath(poly(3f, 9f, 3f, 6.5f, 9f, 6.5f, 11f, 8.5f), tint, style = line)
        }
        MIconKind.Play -> {
            val p = poly(7.5f, 4.8f, 19f, 12f, 7.5f, 19.2f, closed = true)
            drawPath(p, tint)
            drawPath(p, tint, style = Stroke(1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        MIconKind.Gear -> {
            val c = o(12f, 12f)
            drawCircle(tint, 6.1f * s, c, style = line)
            drawCircle(tint, 2.3f * s, c, style = line)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(
                    tint,
                    o(12f + 7.2f * ca, 12f + 7.2f * sa),
                    o(12f + 9.5f * ca, 12f + 9.5f * sa),
                    2.6f * s,
                    StrokeCap.Round
                )
            }
        }
        MIconKind.Back -> drawPath(poly(14.5f, 5.5f, 8f, 12f, 14.5f, 18.5f), tint, style = line)
        MIconKind.Chevron -> drawPath(poly(9.5f, 5.5f, 16f, 12f, 9.5f, 18.5f), tint, style = line)
        MIconKind.Close -> {
            drawLine(tint, o(6f, 6f), o(18f, 18f), sw, StrokeCap.Round)
            drawLine(tint, o(18f, 6f), o(6f, 18f), sw, StrokeCap.Round)
        }
        MIconKind.Check -> drawPath(poly(5f, 12.5f, 10f, 17.5f, 19f, 7f), tint, style = line)
        MIconKind.Info -> {
            drawCircle(tint, 9.3f * s, o(12f, 12f), style = line)
            drawCircle(tint, 1.15f * s, o(12f, 7.9f))
            drawLine(tint, o(12f, 11f), o(12f, 16.6f), sw, StrokeCap.Round)
        }
        MIconKind.Warning -> {
            drawPath(poly(12f, 4f, 21f, 19.5f, 3f, 19.5f, closed = true), tint, style = line)
            drawLine(tint, o(12f, 9.8f), o(12f, 14f), sw, StrokeCap.Round)
            drawCircle(tint, 1.05f * s, o(12f, 16.9f))
        }
        MIconKind.Alert -> {
            drawLine(tint, o(12f, 5f), o(12f, 14.5f), 2.8f * s, StrokeCap.Round)
            drawCircle(tint, 1.6f * s, o(12f, 19f))
        }
        MIconKind.Pulse -> drawPath(
            poly(2f, 12f, 7f, 12f, 9.5f, 5f, 13f, 19f, 15.5f, 12f, 22f, 12f),
            tint,
            style = line
        )
        MIconKind.Waveform -> {
            val xs = floatArrayOf(4f, 8f, 12f, 16f, 20f)
            val hs = floatArrayOf(3f, 6f, 9.5f, 5.5f, 2.5f)
            for (i in xs.indices) {
                drawLine(tint, o(xs[i], 12f - hs[i]), o(xs[i], 12f + hs[i]), 2.4f * s, StrokeCap.Round)
            }
        }
        MIconKind.Music -> {
            drawLine(tint, o(8.6f, 17f), o(8.6f, 6.8f), sw, StrokeCap.Round)
            drawLine(tint, o(17.4f, 15f), o(17.4f, 4.8f), sw, StrokeCap.Round)
            drawLine(tint, o(8.6f, 6.8f), o(17.4f, 4.8f), sw, StrokeCap.Round)
            drawCircle(tint, 2.4f * s, o(6.3f, 17.4f))
            drawCircle(tint, 2.4f * s, o(15.1f, 15.4f))
        }
        MIconKind.Trophy -> {
            drawPath(poly(7f, 4.5f, 17f, 4.5f, 17f, 10f, 14.5f, 13f, 9.5f, 13f, 7f, 10f, closed = true), tint, style = line)
            drawPath(poly(7f, 6.5f, 4f, 6.5f, 4f, 9f, 7.2f, 10.8f), tint, style = line)
            drawPath(poly(17f, 6.5f, 20f, 6.5f, 20f, 9f, 16.8f, 10.8f), tint, style = line)
            drawLine(tint, o(12f, 13f), o(12f, 17.2f), sw, StrokeCap.Round)
            drawLine(tint, o(8f, 19.5f), o(16f, 19.5f), sw, StrokeCap.Round)
        }
        MIconKind.Share -> {
            drawCircle(tint, 2.5f * s, o(6f, 12f), style = line)
            drawCircle(tint, 2.5f * s, o(18f, 6f), style = line)
            drawCircle(tint, 2.5f * s, o(18f, 18f), style = line)
            drawLine(tint, o(8.2f, 10.8f), o(15.8f, 7.2f), sw, StrokeCap.Round)
            drawLine(tint, o(8.2f, 13.2f), o(15.8f, 16.8f), sw, StrokeCap.Round)
        }
        MIconKind.Eject -> {
            val p = poly(12f, 5f, 19f, 14f, 5f, 14f, closed = true)
            drawPath(p, tint)
            drawPath(p, tint, style = Stroke(1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(tint, o(5.5f, 18.5f), o(18.5f, 18.5f), sw, StrokeCap.Round)
        }
        MIconKind.File -> {
            drawPath(poly(6f, 3.5f, 14f, 3.5f, 19f, 8.5f, 19f, 20.5f, 6f, 20.5f, closed = true), tint, style = line)
            drawPath(poly(14f, 3.5f, 14f, 8.5f, 19f, 8.5f), tint, style = line)
        }
        MIconKind.Download -> {
            drawLine(tint, o(12f, 4f), o(12f, 15f), sw, StrokeCap.Round)
            drawPath(poly(7f, 10.5f, 12f, 15.5f, 17f, 10.5f), tint, style = line)
            drawLine(tint, o(5f, 19.5f), o(19f, 19.5f), sw, StrokeCap.Round)
        }
    }
}

/**
 * A tick that draws itself: [progress] 0 = nothing, 1 = complete. Used by the animated check rows
 * and by switches. The tick is laid out inside the box from [topLeft] with side [box].
 */
fun DrawScope.drawProgressCheck(
    topLeft: Offset,
    box: Float,
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    val a = Offset(topLeft.x + box * 0.24f, topLeft.y + box * 0.53f)
    val b = Offset(topLeft.x + box * 0.43f, topLeft.y + box * 0.71f)
    val c = Offset(topLeft.x + box * 0.77f, topLeft.y + box * 0.31f)
    val l1 = (b - a).getDistance()
    val l2 = (c - b).getDistance()
    val d = progress.coerceIn(0f, 1f) * (l1 + l2)
    if (d <= 0f) return
    if (d <= l1) {
        drawLine(color, a, a + (b - a) * (d / l1), strokeWidth, StrokeCap.Round)
    } else {
        drawLine(color, a, b, strokeWidth, StrokeCap.Round)
        drawLine(color, b, b + (c - b) * ((d - l1) / l2), strokeWidth, StrokeCap.Round)
    }
}
