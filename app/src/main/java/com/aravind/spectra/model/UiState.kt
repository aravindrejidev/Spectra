package com.aravind.spectra.model

import com.aravind.spectra.decode.AudioDecoder
import com.aravind.spectra.dsp.AnalysisResult
import com.aravind.spectra.metadata.MetadataReader

sealed class UiState {
    object Idle : UiState()

    data class Loading(val step: String) : UiState()

    data class Error(val message: String) : UiState()

    data class Success(
        val fileName: String,
        val fileSizeBytes: Long,
        val tags: MetadataReader.Tags,
        val decoded: AudioDecoder.DecodedAudio,
        val analysis: AnalysisResult
    ) : UiState()
}
