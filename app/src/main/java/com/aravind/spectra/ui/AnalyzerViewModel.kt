package com.aravind.spectra.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aravind.spectra.decode.AudioDecoder
import com.aravind.spectra.dsp.StreamAnalyzer
import com.aravind.spectra.dsp.VerdictEngine
import com.aravind.spectra.metadata.MetadataReader
import com.aravind.spectra.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AnalyzerViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _view = MutableStateFlow("all")
    val view: StateFlow<String> = _view.asStateFlow()

    private val _logScale = MutableStateFlow(false)
    val logScale: StateFlow<Boolean> = _logScale.asStateFlow()

    private val _minDb = MutableStateFlow(-100f)
    val minDb: StateFlow<Float> = _minDb.asStateFlow()

    private var job: Job? = null

    fun setView(v: String) { _view.value = v }
    fun setLogScale(b: Boolean) { _logScale.value = b }
    fun setMinDb(v: Float) { _minDb.value = v }

    fun analyzeFile(context: Context, uri: Uri) {
        job?.cancel()
        _view.value = "all"
        val app = context.applicationContext
        job = viewModelScope.launch {
            _state.value = UiState.Loading("Reading tags", null)
            try {
                val (name, size) = withContext(Dispatchers.IO) { queryNameAndSize(app, uri) }
                val tags = withContext(Dispatchers.IO) {
                    runCatching { MetadataReader.read(app, uri) }.getOrElse { emptyTags() }
                }

                _state.value = UiState.Loading("Decoding & measuring", 0f)
                val t0 = SystemClock.elapsedRealtime()
                val result = withContext(Dispatchers.Default) {
                    val ctx = coroutineContext
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
                                    _state.value = UiState.Loading("Decoding & measuring", progress)
                                }
                            }
                        }
                    }) { ctx.ensureActive() }

                    _state.value = UiState.Loading("Finalizing", 1f)
                    val an = requireNotNull(analyzer) { "No audio could be decoded from this file" }.finish()
                    Triple(info, an, VerdictEngine.evaluate(an, info.codecMime, codecName(info.codecMime), info.declaredBits))
                }

                _state.value = UiState.Success(
                    fileName = name,
                    fileSizeBytes = size,
                    tags = tags,
                    info = result.first,
                    analysis = result.second,
                    verdict = result.third,
                    tookMs = SystemClock.elapsedRealtime() - t0
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _state.value = UiState.Error(
                    "Ran out of memory analyzing this file. Close other apps and try again."
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = UiState.Idle
    }

    fun reset() {
        job?.cancel()
        _state.value = UiState.Idle
        _view.value = "all"
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
