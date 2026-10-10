package com.aravind.spectra.decode

/** Facts read from the file's container and audio stream by ffprobe (FFmpeg decoder only). */
data class FileFacts(
    val formatName: String?,
    val formatLong: String?,
    val codecLong: String?,
    val profile: String?,
    val channelLayout: String?,
    val sampleFormat: String?,
    val bitrateKbps: Int?, // audio stream bitrate when known, else the container's
    val streamCount: Int,
    val hasCoverStream: Boolean,
    val encoder: String?,
    val tags: List<Pair<String, String>> // lower-case tag name -> value
)
