package com.aravind.spectra.decode

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Streaming decoder: MediaExtractor + MediaCodec -> planar float chunks pushed to a
 * [Listener]. Nothing is accumulated here, so memory stays flat for any track length.
 * Float PCM output is requested; if the decoder refuses, it falls back to PCM16.
 */
object AudioDecoder {

    interface Listener {
        fun onStart(sampleRate: Int, channelCount: Int, expectedFrames: Long, floatOutput: Boolean)
        fun onChunk(planar: Array<FloatArray>, frames: Int, progress: Float)
    }

    data class DecodeInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val codecMime: String,
        val floatOutput: Boolean,
        val durationUs: Long
    )

    fun decode(context: Context, uri: Uri, listener: Listener, checkCancelled: () -> Unit): DecodeInfo {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val m = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    trackIndex = i
                    mime = m
                    break
                }
            }
            require(trackIndex >= 0) { "No audio track found in this file" }
            extractor.selectTrack(trackIndex)

            val inFormat = extractor.getTrackFormat(trackIndex)
            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs =
                if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val expectedFrames = if (durationUs > 0) durationUs * sampleRate / 1_000_000L else 0L

            val c = openCodec(extractor, trackIndex, mime)
            codec = c
            c.start()

            var floatOut = false
            var started = false
            var planar: Array<FloatArray> = emptyArray()
            val bufInfo = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            var stall = 0

            while (!outEos) {
                checkCancelled()

                if (!inEos) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val inBuf = requireNotNull(c.getInputBuffer(ii))
                        val n = extractor.readSampleData(inBuf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val oi = c.dequeueOutputBuffer(bufInfo, 10_000)
                when {
                    oi >= 0 -> {
                        stall = 0
                        if (bufInfo.size > 0) {
                            val out = requireNotNull(c.getOutputBuffer(oi))
                            out.position(bufInfo.offset)
                            out.limit(bufInfo.offset + bufInfo.size)
                            val bb = out.order(ByteOrder.LITTLE_ENDIAN)
                            if (!started) {
                                started = true
                                listener.onStart(sampleRate, channelCount, expectedFrames, floatOut)
                            }
                            val frames: Int
                            if (floatOut) {
                                val fb = bb.asFloatBuffer()
                                frames = fb.remaining() / channelCount
                                if (planar.size != channelCount || planar[0].size < frames) {
                                    planar = Array(channelCount) { FloatArray(frames + 1024) }
                                }
                                var p = 0
                                for (f in 0 until frames) {
                                    for (ch in 0 until channelCount) planar[ch][f] = fb.get(p++)
                                }
                            } else {
                                val sb = bb.asShortBuffer()
                                frames = sb.remaining() / channelCount
                                if (planar.size != channelCount || planar[0].size < frames) {
                                    planar = Array(channelCount) { FloatArray(frames + 1024) }
                                }
                                var p = 0
                                for (f in 0 until frames) {
                                    for (ch in 0 until channelCount) planar[ch][f] = sb.get(p++) / 32768f
                                }
                            }
                            val progress =
                                if (durationUs > 0) (bufInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f) else -1f
                            listener.onChunk(planar, frames, progress)
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = c.outputFormat
                        sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatOut = nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    oi == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inEos) {
                            stall++
                            if (stall > 300) outEos = true
                        }
                    }
                }
            }
            return DecodeInfo(sampleRate, channelCount, mime, floatOut, durationUs)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun openCodec(extractor: MediaExtractor, track: Int, mime: String): MediaCodec {
        var c = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            throw IllegalStateException("No decoder available for $mime on this device (ALAC isn't supported yet).", e)
        }
        try {
            val f = extractor.getTrackFormat(track)
            f.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            c.configure(f, null, null, 0)
        } catch (e: Exception) {
            runCatching { c.release() }
            c = MediaCodec.createDecoderByType(mime)
            c.configure(extractor.getTrackFormat(track), null, null, 0)
        }
        return c
    }
}
