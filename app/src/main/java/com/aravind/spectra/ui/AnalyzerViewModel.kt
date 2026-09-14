package com.aravind.spectra.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aravind.spectra.decode.AudioDecoder
import com.aravind.spectra.dsp.analyze
import com.aravind.spectra.metadata.MetadataReader
import com.aravind.spectra.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyzerViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _channelView = MutableStateFlow("all")
    val channelView: StateFlow<String> = _channelView.asStateFlow()

    fun setChannelView(view: String) {
        _channelView.value = view
    }

    fun analyzeFile(context: Context, uri: Uri) {
        _channelView.value = "all"
        viewModelScope.launch {
            _state.value = UiState.Loading("Reading metadata")
            try {
                val (name, size) = withContext(Dispatchers.IO) { queryNameAndSize(context, uri) }
                val tags = withContext(Dispatchers.IO) { MetadataReader.read(context, uri) }

                _state.value = UiState.Loading("Decoding audio")
                val decoded = withContext(Dispatchers.IO) { AudioDecoder.decode(context, uri) }

                _state.value = UiState.Loading("Computing spectrogram")
                val analysis = withContext(Dispatchers.Default) {
                    analyze(decoded.channels, decoded.sampleRate, targetCols = 360, targetRows = 200)
                }

                _state.value = UiState.Success(name, size, tags, decoded, analysis)
            } catch (e: OutOfMemoryError) {
                // Deliberately separate from the Exception branch below: OutOfMemoryError
                // extends Error, not Exception, so a plain "catch (e: Exception)" here
                // silently misses it — which is exactly why a memory-heavy file used to
                // close the whole app with no message instead of showing this card.
                _state.value = UiState.Error(
                    "Ran out of memory analyzing this file. Very long or very high-resolution " +
                        "tracks can need more RAM than is available right now — try a shorter " +
                        "file, or close other apps and retry."
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        _channelView.value = "all"
    }

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
