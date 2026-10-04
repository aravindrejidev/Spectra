package com.aravind.spectra.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.dsp.ChannelSpectrogram
import com.aravind.spectra.ui.theme.SpectraColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BMP_H = 320

/** 0.0 = bottom of the display, 1.0 = top. */
fun freqToFrac(freq: Double, nyquist: Double, log: Boolean): Double =
    if (!log) (freq / nyquist).coerceIn(0.0, 1.0)
    else (ln(max(freq, 20.0) / 20.0) / ln(nyquist / 20.0)).coerceIn(0.0, 1.0)

fun fracToFreq(frac: Double, nyquist: Double, log: Boolean): Double =
    if (!log) frac * nyquist else 20.0 * (nyquist / 20.0).pow(frac)

private fun shortHz(f: Double): String {
    if (f < 1000) return f.roundToInt().toString()
    val k = f / 1000.0
    return if (abs(k - k.roundToInt()) < 0.05) "${k.roundToInt()}k" else String.format(Locale.US, "%.1fk", k)
}

fun buildSpectrogramBitmap(
    spec: ChannelSpectrogram,
    nyquist: Double,
    log: Boolean,
    minDb: Float,
    maxDb: Float
): ImageBitmap {
    val w = spec.cols
    val h = BMP_H
    val rowFor = IntArray(h) { y ->
        val frac = 1.0 - (y + 0.5) / h
        ((fracToFreq(frac, nyquist, log) / nyquist) * spec.rows).toInt().coerceIn(0, spec.rows - 1)
    }
    val lut = SpectrogramPalette.lut
    val range = max(maxDb - minDb, 1f)
    val px = IntArray(w * h)
    for (c in 0 until w) {
        val colBase = c * spec.rows
        for (y in 0 until h) {
            val db = spec.dbMatrix[colBase + rowFor[y]]
            val t = (((db - minDb) / range) * 255f).toInt().coerceIn(0, 255)
            px[y * w + c] = lut[t]
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

@Composable
fun SpectrogramPane(
    spec: ChannelSpectrogram,
    nyquist: Double,
    durationSec: Double,
    logScale: Boolean,
    minDb: Float,
    maxDb: Float
) {
    var cursor by remember(spec) { mutableStateOf<Offset?>(null) }
    val bitmap = remember(spec, logScale, minDb, maxDb) {
        buildSpectrogramBitmap(spec, nyquist, logScale, minDb, maxDb)
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(240.dp)) {
            Canvas(Modifier.width(34.dp).fillMaxHeight()) {
                paint.color = SpectraColors.PhosphorDim.toArgb()
                paint.textSize = 9.sp.toPx()
                paint.textAlign = android.graphics.Paint.Align.RIGHT
                paint.typeface = Typeface.MONOSPACE
                val marks = if (logScale) listOf(20.0, 100.0, 1000.0, 10000.0, nyquist)
                else listOf(0.0, 0.25, 0.5, 0.75, 1.0).map { it * nyquist }
                for (f in marks) {
                    if (f > nyquist + 1) continue
                    val y = (size.height * (1.0 - freqToFrac(f, nyquist, logScale))).toFloat()
                        .coerceIn(9.sp.toPx(), size.height - 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(shortHz(f), size.width - 4.dp.toPx(), y, paint)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(spec) {
                        detectTapGestures { o ->
                            cursor = Offset(
                                (o.x / size.width).coerceIn(0f, 1f),
                                (o.y / size.height).coerceIn(0f, 1f)
                            )
                        }
                    }
                    .pointerInput(spec) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            cursor = Offset(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f)
                            )
                        }
                    }
            ) {
                Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low
                )
                cursor?.let { cur ->
                    Canvas(Modifier.fillMaxSize()) {
                        val x = cur.x * size.width
                        val y = cur.y * size.height
                        val col = SpectraColors.Phosphor.copy(alpha = 0.9f)
                        drawLine(col, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        drawLine(col, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        drawCircle(col, 5.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 34.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0..4) {
                Text(
                    fmtDurationSec(durationSec * i / 4.0),
                    color = SpectraColors.PhosphorDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Canvas(Modifier.fillMaxWidth().padding(start = 34.dp, top = 8.dp).height(6.dp)) {
            drawRect(Brush.horizontalGradient(*SpectrogramPalette.legendStops))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${minDb.roundToInt()} dB", color = SpectraColors.PhosphorDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("${maxDb.roundToInt()} dB", color = SpectraColors.PhosphorDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        val cur = cursor
        val readout = if (cur == null) {
            "TAP / DRAG THE DISPLAY TO PROBE"
        } else {
            val col = (cur.x * spec.cols).toInt().coerceIn(0, spec.cols - 1)
            val freq = fracToFreq(1.0 - cur.y, nyquist, logScale)
            val row = ((freq / nyquist) * spec.rows).toInt().coerceIn(0, spec.rows - 1)
            val db = spec.dbMatrix[col * spec.rows + row]
            val dbText = if (db <= -170f) "—" else f1(db.toDouble()) + " dB"
            "T ${fmtDurationSec(durationSec * cur.x)}   F ${fmtHz(freq)}   $dbText"
        }
        Lcd(
            readout,
            Modifier.padding(start = 34.dp, top = 8.dp),
            color = if (cur == null) SpectraColors.PhosphorDim else SpectraColors.Phosphor,
            size = 12.sp,
            maxLines = 1
        )
    }
}

/** Average spectrum curve with the detected cutoff marked. */
@Composable
fun AvgSpectrumPlot(avgDb: FloatArray, nyquist: Double, cutoffHz: Double, sharp: Boolean) {
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 4.dp)) {
        val left = 34.dp.toPx()
        val bottom = 18.dp.toPx()
        val top = 8.dp.toPx()
        val right = 8.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom

        var mx = -200f
        for (v in avgDb) if (v > mx) mx = v
        val hi = ceil(mx / 10f) * 10f
        val lo = hi - 110f
        fun yOf(db: Float): Float = top + ph * (1f - ((db - lo) / (hi - lo)).coerceIn(0f, 1f))
        fun xOf(i: Int): Float = left + pw * i / (avgDb.size - 1).toFloat()

        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 9.sp.toPx()
        paint.color = SpectraColors.PhosphorDim.toArgb()
        val grid = SpectraColors.Phosphor.copy(alpha = 0.13f)

        paint.textAlign = android.graphics.Paint.Align.RIGHT
        var g = hi
        while (g >= lo) {
            val y = yOf(g)
            drawLine(grid, Offset(left, y), Offset(left + pw, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(g.roundToInt().toString(), left - 4.dp.toPx(), y + 3.dp.toPx(), paint)
            g -= 20f
        }

        paint.textAlign = android.graphics.Paint.Align.CENTER
        var fk = 0.0
        while (fk <= nyquist) {
            val x = left + pw * (fk / nyquist).toFloat()
            drawLine(grid, Offset(x, top), Offset(x, top + ph), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(shortHz(fk), x, size.height - 4.dp.toPx(), paint)
            fk += 4000.0
        }

        val line = Path()
        val fill = Path()
        for (i in avgDb.indices) {
            val x = xOf(i)
            val y = yOf(avgDb[i])
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, top + ph)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xOf(avgDb.size - 1), top + ph)
        fill.close()
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(SpectraColors.Phosphor.copy(alpha = 0.35f), Color.Transparent),
                startY = top,
                endY = top + ph
            )
        )
        drawPath(line, SpectraColors.Phosphor, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))

        val cx = left + pw * (cutoffHz / nyquist).toFloat()
        val mark = if (sharp) SpectraColors.Amber else SpectraColors.PhosphorDim
        drawLine(
            mark,
            Offset(cx, top),
            Offset(cx, top + ph),
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
        paint.color = mark.toArgb()
        paint.textAlign = if (cx > size.width * 0.7f) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(
            "CUT " + fmtHz(cutoffHz),
            if (cx > size.width * 0.7f) cx - 4.dp.toPx() else cx + 4.dp.toPx(),
            top + 11.dp.toPx(),
            paint
        )
    }
}
