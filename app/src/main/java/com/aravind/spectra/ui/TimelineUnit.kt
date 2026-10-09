package com.aravind.spectra.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.dsp.AnalysisResult
import com.aravind.spectra.ui.theme.SpectraColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

/** Short-term loudness across the track, with the seconds that contain clipping marked below. */
@Composable
fun TimelineUnit(a: AnalysisResult) {
    val series = a.loudnessSeries
    var clippedSeconds = 0
    for (c in a.clipSeconds) if (c > 0) clippedSeconds++

    RackPanel("Loudness over time", lamp = SpectraColors.Phosphor) {
        if (series.size < 2) {
            Text("Needs at least 4 seconds of audio for a timeline.", color = SpectraColors.Ink, fontSize = 13.sp)
        } else {
            GlassScreen { LoudnessPlot(a) }
            Spacer(Modifier.height(12.dp))
            val finite = series.filter { !it.isNaN() }
            val hi = finite.maxOrNull()
            val lo = finite.minOrNull()
            ReadoutGrid(
                listOf(
                    Readout("Loudest 3 s", fmtLufs(hi?.toDouble())),
                    Readout("Quietest 3 s", fmtLufs(lo?.toDouble())),
                    Readout("Spread", if (hi != null && lo != null) "${f1((hi - lo).toDouble())} LU" else "—"),
                    Readout(
                        "Seconds with clipping",
                        clippedSeconds.toString(),
                        if (clippedSeconds > 0) SpectraColors.Amber else SpectraColors.Phosphor
                    )
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Short-term loudness (3 s window). The amber line is the integrated loudness. " +
                    "Red ticks below mark seconds that contain clipped samples.",
                color = SpectraColors.InkSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LoudnessPlot(a: AnalysisResult) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 4.dp)) {
        val series = a.loudnessSeries
        val dur = a.durationSec
        val left = 40.dp.toPx()
        val top = 10.dp.toPx()
        val right = 8.dp.toPx()
        val stripH = 14.dp.toPx()
        val bottom = 18.dp.toPx() + stripH + 6.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom

        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in series) {
            if (!v.isNaN()) {
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
        }
        if (mn > mx) {
            mn = -30f
            mx = -10f
        }
        val lo = floor((mn - 2f) / 6f) * 6f
        var hi = ceil((mx + 2f) / 6f) * 6f
        if (hi - lo < 12f) hi = lo + 12f
        val hiF = hi
        fun yOf(db: Float): Float = top + ph * (1f - ((db - lo) / (hiF - lo)).coerceIn(0f, 1f))
        fun xOf(t: Double): Float = left + pw * (t / dur).toFloat().coerceIn(0f, 1f)

        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 9.sp.toPx()
        paint.color = SpectraColors.PhosphorDim.toArgb()
        paint.textAlign = Paint.Align.RIGHT
        val grid = SpectraColors.Phosphor.copy(alpha = 0.13f)
        var g = lo
        while (g <= hiF + 0.01f) {
            val gy = yOf(g)
            drawLine(grid, Offset(left, gy), Offset(left + pw, gy), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(g.roundToInt().toString(), left - 4.dp.toPx(), gy + 3.dp.toPx(), paint)
            g += 6f
        }

        val line = Path()
        var pen = false
        for (i in series.indices) {
            val v = series[i]
            if (v.isNaN()) {
                pen = false
                continue
            }
            val px = xOf(i + 1.5)
            val py = yOf(v)
            if (!pen) {
                line.moveTo(px, py)
                pen = true
            } else {
                line.lineTo(px, py)
            }
        }
        drawPath(line, SpectraColors.Phosphor, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))

        a.lufs?.let { l ->
            val iy = yOf(l.toFloat())
            drawLine(
                SpectraColors.Amber, Offset(left, iy), Offset(left + pw, iy), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
            paint.color = SpectraColors.Amber.toArgb()
            paint.textAlign = Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText("I ${f1(l)}", left + 4.dp.toPx(), iy - 3.dp.toPx(), paint)
        }

        val stripTop = top + ph + 6.dp.toPx()
        for (sec in a.clipSeconds.indices) {
            val c = a.clipSeconds[sec]
            if (c <= 0) continue
            val frac = (log10(1.0 + c) / 4.0).toFloat().coerceIn(0.2f, 1f)
            val bx = xOf(sec + 0.5)
            drawLine(
                SpectraColors.Red,
                Offset(bx, stripTop + stripH),
                Offset(bx, stripTop + stripH * (1f - frac)),
                2.dp.toPx()
            )
        }

        paint.color = SpectraColors.PhosphorDim.toArgb()
        for (i in 0..4) {
            paint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                4 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                fmtDurationSec(dur * i / 4.0),
                left + pw * i / 4f,
                size.height - 2.dp.toPx(),
                paint
            )
        }
    }
}
