package com.aravind.spectra.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.aravind.spectra.dsp.ChannelSpectrogram

/**
 * Draws a [ChannelSpectrogram] as a single bitmap (built once via
 * Bitmap.setPixels rather than one draw call per cell — cheap and avoids
 * per-frame recomposition cost for what is otherwise a static image).
 */
@Composable
fun SpectrogramView(
    spectrogram: ChannelSpectrogram,
    modifier: Modifier = Modifier,
    minDb: Float = -100f,
    maxDb: Float = -5f
) {
    val imageBitmap = remember(spectrogram) {
        val cols = spectrogram.cols
        val rows = spectrogram.rows
        val pixels = IntArray(cols * rows)
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val db = spectrogram.dbMatrix[c * rows + r]
                val color = dbToColor(db, minDb, maxDb)
                val y = rows - 1 - r // low frequency at the bottom of the image
                pixels[y * cols + c] = color.toArgb()
            }
        }
        val bmp = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        bmp.asImageBitmap()
    }

    Image(
        bitmap = imageBitmap,
        contentDescription = "Spectrogram",
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None
    )
}
