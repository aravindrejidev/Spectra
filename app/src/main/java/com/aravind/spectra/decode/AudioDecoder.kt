package com.aravind.spectra.decode

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/**
 * Streaming decoder pushing planar float chunks to a [Listener]; nothing is accumulated here.
 * FFmpeg (bit-exact) is tried first; if it can't handle the file, the phone's MediaCodec is used.
 */
object AudioDecoder {

    interface Listener {
        /** floatOutput = decoder delivers more than 16 bits, so bit-depth detection is meaningful. */
        fun onStart(sampleRate: Int, channelCount: Int, expectedFrames: Long, floatOutput: Boolean)
        fun onChunk(planar: Array<FloatArray>, frames: Int, progress: Float)
    }

    data class DecodeInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val codecMime: String,
        val floatOutput: Boolean,
        val durationUs: Long,
        val encoding: String,
        val declaredBits: Int? = null,
        val engine: String = "System",
        val note: String? = null,
        val facts: FileFacts? = null
    )

    fun decode(context: Context, uri: Uri, listener: Listener, checkCancelled: () -> Unit): DecodeInfo {
        try {
            val r = FfmpegDecoder.decode(context, uri, listener, checkCancelled)
            if (r != null) return r
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // FFmpeg could not decode this file: fall back to the system decoder
        } catch (e: LinkageError) {
            // native FFmpeg library unavailable: fall back
        }
        return decodeWithMediaCodec(context, uri, listener, checkCancelled).copy(note = FfmpegDecoder.lastFailure)
    }

    private fun decodeWithMediaCodec(
        context: Context,
        uri: Uri,
        listener: Listener,
        checkCancelled: () -> Unit
    ): DecodeInfo {
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

            val c = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "No decoder available for $mime on this device.", e
                )
            }
            codec = c
            c.configure(inFormat, null, null, 0)
            c.start()

            var encoding = AudioFormat.ENCODING_PCM_16BIT
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
                                listener.onStart(sampleRate, channelCount, expectedFrames, isHighDepth(encoding))
                            }
                            val frames = bb.remaining() / (bytesPer(encoding) * channelCount)
                            if (planar.size != channelCount || planar[0].size < frames) {
                                planar = Array(channelCount) { FloatArray(frames + 1024) }
                            }
                            convert(bb, encoding, channelCount, frames, planar)
                            val progress =
                                if (durationUs > 0) (bufInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                else -1f
                            listener.onChunk(planar, frames, progress)
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = c.outputFormat
                        sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding =
                            if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING)) nf.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else AudioFormat.ENCODING_PCM_16BIT
                    }
                    oi == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inEos) {
                            stall++
                            if (stall > 300) outEos = true
                        }
                    }
                }
            }
            return DecodeInfo(sampleRate, channelCount, mime, isHighDepth(encoding), durationUs, label(encoding))
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun bytesPer(enc: Int): Int = when (enc) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        else -> 2
    }

    private fun isHighDepth(enc: Int): Boolean =
        enc == AudioFormat.ENCODING_PCM_FLOAT ||
            enc == AudioFormat.ENCODING_PCM_32BIT ||
            enc == AudioFormat.ENCODING_PCM_24BIT_PACKED

    private fun label(enc: Int): String = when (enc) {
        AudioFormat.ENCODING_PCM_FLOAT -> "Float32"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
        AudioFormat.ENCODING_PCM_8BIT -> "PCM8"
        else -> "PCM16"
    }

    private fun convert(bb: ByteBuffer, enc: Int, ch: Int, frames: Int, out: Array<FloatArray>) {
        when (enc) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = bb.asFloatBuffer()
                var p = 0
                for (f in 0 until frames) {
                    for (c in 0 until ch) {
                        val v = fb.get(p++)
                        out[c][f] = if (v.isNaN() || v.isInfinite()) 0f else v
                    }
                }
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                val ib = bb.asIntBuffer()
                var p = 0
                for (f in 0 until frames) {
                    for (c in 0 until ch) out[c][f] = (ib.get(p++) / 2147483648.0).toFloat()
                }
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                var o = bb.position()
                for (f in 0 until frames) {
                    for (c in 0 until ch) {
                        val b0 = bb.get(o).toInt() and 0xFF
                        val b1 = bb.get(o + 1).toInt() and 0xFF
                        val b2 = bb.get(o + 2).toInt()
                        out[c][f] = ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                        o += 3
                    }
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                var o = bb.position()
                for (f in 0 until frames) {
                    for (c in 0 until ch) {
                        out[c][f] = ((bb.get(o).toInt() and 0xFF) - 128) / 128f
                        o++
                    }
                }
            }
            else -> {
                val sb = bb.asShortBuffer()
                var p = 0
                for (f in 0 until frames) {
                    for (c in 0 until ch) out[c][f] = sb.get(p++) / 32768f
                }
            }
        }
    }
}
