package com.aravind.spectra.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.aravind.spectra.decode.AudioDecoder
import com.aravind.spectra.dsp.StreamAnalyzer
import com.aravind.spectra.dsp.VerdictEngine
import com.aravind.spectra.metadata.MetadataReader
import com.aravind.spectra.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Runs the full pipeline (tags, decode, analysis, verdict) for one file without touching any UI state. */
object AnalysisRunner {

    suspend fun run(context: Context, uri: Uri, onProgress: (String, Float?) -> Unit): UiState.Success =
        withContext(Dispatchers.Default) {
            val app = context.applicationContext
            val ctx = coroutineContext

            onProgress("Reading tags", null)
            val (name, size) = queryNameAndSize(app, uri)
            val tags = runCatching { MetadataReader.read(app, uri) }.getOrElse { emptyTags() }

            onProgress("Decoding & measuring", 0f)
            val t0 = SystemClock.elapsedRealtime()
            var analyzer: StreamAnalyzer? = null
            var lastPct = -1
            val info = AudioDecoder.decode(app, uri, object : AudioDecoder.Listener {
                override fun onStart(sampleRate: Int, channelCount: Int, expectedFrames: Long, floatOutput: Boolean) {
                    analyzer = StreamAnalyzer(sampleRate, channelCount, expectedFrames, floatOutput)
                }

                override fun onChunk(planar: Array<FloatArray>, frames: Int, progress: Float) {
                    analyzer?.feed(planar, frames)
                    if (progress >= 0f) {
                        val pct = (progress * 100).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress("Decoding & measuring · $pct%", progress)
                        }
                    }
                }
            }) { ctx.ensureActive() }

            onProgress("Finalizing", null)
            val an = requireNotNull(analyzer) { "No audio could be decoded from this file" }.finish()
            val verdict = VerdictEngine.evaluate(an, info.codecMime, codecName(info.codecMime), info.declaredBits)
            UiState.Success(
                fileName = name,
                fileSizeBytes = size,
                tags = tags,
                info = info,
                analysis = an,
                verdict = verdict,
                tookMs = SystemClock.elapsedRealtime() - t0
            )
        }

    private fun emptyTags() =
        MetadataReader.Tags(null, null, null, null, null, null, null, null, null, null, null)

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "unknown file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }
}
