package com.aravind.spectra.model

import com.aravind.spectra.decode.AudioDecoder
import com.aravind.spectra.dsp.AnalysisResult
import com.aravind.spectra.dsp.Verdict
import com.aravind.spectra.metadata.MetadataReader

sealed class UiState {
    object Idle : UiState()

    data class Loading(val step: String, val progress: Float?) : UiState()

    data class Error(val message: String) : UiState()

    data class Success(
        val fileName: String,
        val fileSizeBytes: Long,
        val tags: MetadataReader.Tags,
        val info: AudioDecoder.DecodeInfo,
        val analysis: AnalysisResult,
        val verdict: Verdict,
        val tookMs: Long
    ) : UiState()
}
