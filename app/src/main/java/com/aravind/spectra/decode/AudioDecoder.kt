package com.aravind.spectra.decode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes an audio file to per-channel Float PCM using Android's built-in
 * MediaExtractor (demux) + MediaCodec (decode) — no native/FFmpeg
 * dependency. This covers every format Android guarantees platform
 * support for: AAC, MP3, FLAC, Vorbis, Opus, WAV/PCM.
 *
 * ALAC is NOT covered here — stock MediaCodec has no guaranteed ALAC
 * decoder; createDecoderByType() throws for it, which we surface as a
 * clear error rather than letting it crash the app.
 *
 * Samples are written directly into a growable per-channel float buffer
 * as MediaCodec produces them (see [GrowableFloatArray]) rather than
 * accumulating a List<ShortArray> and copying it into a second, separate
 * float array afterwards — that older approach briefly held two full
 * copies of the whole track's decoded audio in memory at once, which is
 * exactly the kind of thing that runs a long/high-res file out of heap.
 */
object AudioDecoder {

    /** Above this, we refuse rather than risk an OOM crash — see README. */
    const val MAX_DURATION_MS = 20 * 60 * 1000L // 20 minutes

    data class DecodedAudio(
        val sampleRate: Int,
        val channelCount: Int,
        val channels: List<FloatArray>
    )

    /** Simple growable float array — like ArrayList but for primitives, so
     *  we don't pay for the reallocate-and-copy that decoding first into
     *  a List<ShortArray> and then into fixed-size FloatArrays would need. */
    private class GrowableFloatArray(initialCapacity: Int) {
        var array = FloatArray(initialCapacity.coerceAtLeast(4096))
            private set
        var size = 0
            private set

        fun add(value: Float) {
            if (size >= array.size) {
                array = array.copyOf(array.size + array.size / 2 + 4096)
            }
            array[size++] = value
        }

        fun trimmed(): FloatArray = if (size == array.size) array else array.copyOf(size)
    }

    fun decode(context: Context, uri: Uri): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var trackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                inputFormat = f
                break
            }
        }
        val format = requireNotNull(inputFormat) { "No audio track found in this file" }
        extractor.selectTrack(trackIndex)

        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs > 0 && durationUs / 1000 > MAX_DURATION_MS) {
            extractor.release()
            val minutes = durationUs / 1_000_000 / 60
            throw IllegalStateException(
                "This file is about $minutes minutes long. Full-track analysis is currently capped at " +
                    "${MAX_DURATION_MS / 60_000} minutes to avoid running out of memory on-device — " +
                    "try a shorter file or a trimmed clip for now."
            )
        }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            throw IllegalStateException("No decoder available for $mime on this device (e.g. ALAC isn't supported yet — see README Phase 2).", e)
        }
        codec.configure(format, null, null, 0)
        codec.start()

        val estimatedFrames = if (durationUs > 0) {
            ((durationUs * sampleRate) / 1_000_000L).toInt() + sampleRate
        } else {
            sampleRate * 60 * 5 // fallback guess: ~5 minutes, grows automatically if wrong
        }
        // Created lazily on the first real output chunk rather than here, since
        // MediaCodec guarantees INFO_OUTPUT_FORMAT_CHANGED (which can revise
        // channelCount) fires before any actual decoded data — allocating
        // eagerly here risked sizing this array from a channel count the
        // codec was about to correct.
        var buffers: Array<GrowableFloatArray>? = null

        // Upfront duration check catches the common case, but only works if the
        // container reports KEY_DURATION accurately. This is a second, unconditional
        // safety net inside the loop itself: a hard sample-count ceiling means a
        // file with missing/wrong duration metadata — or a decoder that somehow
        // never signals end-of-stream — can't grow the output buffer without limit.
        val maxSamplesPerChannel = (MAX_DURATION_MS / 1000) * sampleRate

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = requireNotNull(codec.getInputBuffer(inIndex))
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        if (buffers == null) {
                            buffers = Array(channelCount) { GrowableFloatArray(estimatedFrames) }
                        }
                        val bufs = buffers!!
                        val outBuf = requireNotNull(codec.getOutputBuffer(outIndex))
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var i = 0
                        val remaining = shortBuf.remaining()
                        while (i + channelCount <= remaining) {
                            for (c in 0 until channelCount) {
                                bufs[c].add(shortBuf.get(i + c) / 32768f)
                            }
                            i += channelCount
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                // INFO_TRY_AGAIN_LATER: just loop again
            }

            if ((buffers?.get(0)?.size ?: 0) > maxSamplesPerChannel) {
                codec.stop(); codec.release(); extractor.release()
                throw IllegalStateException(
                    "Decoding passed the ${MAX_DURATION_MS / 60_000}-minute safety limit without " +
                        "finishing — either this file is longer than its tags say, or something in " +
                        "the file is confusing the decoder. Stopped before it could run out of memory."
                )
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val finalBuffers = buffers ?: Array(channelCount) { GrowableFloatArray(0) } // empty/silent file edge case
        return DecodedAudio(sampleRate, channelCount, finalBuffers.map { it.trimmed() })
    }
}
