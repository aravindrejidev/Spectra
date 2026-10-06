package com.aravind.spectra.decode

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bit-exact decoding through FFmpeg, independent of the phone's own codecs.
 * Output is raw float32 streamed through a named pipe, so memory stays flat.
 * Returns null when FFmpeg can't handle the file so the caller can fall back.
 */
internal object FfmpegDecoder {

    private class Probe(
        val sampleRate: Int,
        val channels: Int,
        val codec: String,
        val declaredBits: Int?,
        val durationSec: Double
    )

    private fun mimeFor(codec: String): String = when (codec) {
        "flac" -> "audio/flac"
        "alac" -> "audio/alac"
        "mp3", "mp2" -> "audio/mpeg"
        "aac" -> "audio/mp4a-latm"
        "vorbis" -> "audio/vorbis"
        "opus" -> "audio/opus"
        "wavpack" -> "audio/x-wavpack"
        "ape" -> "audio/x-ape"
        "tta" -> "audio/x-tta"
        "tak" -> "audio/x-tak"
        "wmalossless" -> "audio/x-wmalossless"
        else -> if (codec.startsWith("pcm_")) "audio/raw" else "audio/x-$codec"
    }

    private fun probe(path: String): Probe? {
        val session = FFprobeKit.getMediaInformation(path)
        val info = session.mediaInformation ?: return null
        val streams = info.streams ?: return null
        val s = streams.firstOrNull { it.getStringProperty("codec_type") == "audio" } ?: return null

        val sr = s.getStringProperty("sample_rate")?.toIntOrNull() ?: return null
        val ch = s.getStringProperty("channels")?.toIntOrNull() ?: return null
        val codec = s.getStringProperty("codec_name") ?: return null
        val fmt = s.getStringProperty("sample_fmt") ?: ""
        val raw = s.getStringProperty("bits_per_raw_sample")?.toIntOrNull()?.takeIf { it > 0 }

        val declared: Int? = when {
            fmt.startsWith("flt") || fmt.startsWith("dbl") -> null // floating point: no integer depth
            raw != null -> raw
            fmt.startsWith("s16") -> 16
            fmt.startsWith("u8") -> 8
            else -> null
        }
        val dur = info.duration?.toDoubleOrNull()
            ?: s.getStringProperty("duration")?.toDoubleOrNull()
            ?: 0.0
        return Probe(sr, ch, codec, declared, dur)
    }

    fun decode(
        context: Context,
        uri: Uri,
        listener: AudioDecoder.Listener,
        checkCancelled: () -> Unit
    ): AudioDecoder.DecodeInfo? {
        // each saf: parameter is single-use, so probing and decoding get their own
        val probeInput = FFmpegKitConfig.getSafParameterForRead(context, uri) ?: return null
        val p = probe(probeInput) ?: return null
        if (p.sampleRate <= 0 || p.channels <= 0 || p.channels > 16) return null

        val input = FFmpegKitConfig.getSafParameterForRead(context, uri) ?: return null
        val pipe = FFmpegKitConfig.registerNewFFmpegPipe(context) ?: return null

        val done = AtomicBoolean(false)
        val ok = AtomicBoolean(false)
        val opened = AtomicBoolean(false)

        val command = "-hide_banner -nostdin -loglevel error -i $input -map 0:a:0 -f f32le -acodec pcm_f32le $pipe"
        val session = FFmpegKit.executeAsync(command) { s ->
            ok.set(ReturnCode.isSuccess(s.returnCode))
            done.set(true)
        }

        // If FFmpeg fails before it ever opens the pipe, our blocking open() would hang: release it.
        val watchdog = Thread {
            try {
                while (!opened.get() && !done.get()) Thread.sleep(40)
                if (!opened.get()) FileOutputStream(pipe).close()
            } catch (e: Exception) {
                // ignore
            }
        }
        watchdog.isDaemon = true
        watchdog.start()

        var stream: FileInputStream? = null
        var total = 0L
        try {
            stream = FileInputStream(pipe)
            opened.set(true)

            val frameBytes = p.channels * 4
            val expected = if (p.durationSec > 0) (p.durationSec * p.sampleRate).toLong() else 0L
            val buf = ByteArray(1 shl 18)
            var have = 0
            var planar: Array<FloatArray> = emptyArray()
            var started = false

            while (true) {
                checkCancelled()
                val n = stream.read(buf, have, buf.size - have)
                if (n < 0) break
                have += n
                val usable = have - have % frameBytes
                if (usable == 0) continue
                val frames = usable / frameBytes

                if (!started) {
                    started = true
                    listener.onStart(p.sampleRate, p.channels, expected, true)
                }
                if (planar.size != p.channels || planar[0].size < frames) {
                    planar = Array(p.channels) { FloatArray(frames + 1024) }
                }
                val fb = ByteBuffer.wrap(buf, 0, usable).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                var idx = 0
                for (f in 0 until frames) {
                    for (c in 0 until p.channels) {
                        val v = fb.get(idx++)
                        planar[c][f] = if (v.isNaN() || v.isInfinite()) 0f else v
                    }
                }
                total += frames
                val progress = if (expected > 0) (total.toFloat() / expected).coerceIn(0f, 1f) else -1f
                listener.onChunk(planar, frames, progress)

                System.arraycopy(buf, usable, buf, 0, have - usable)
                have -= usable
            }

            var waited = 0
            while (!done.get() && waited < 3000) {
                Thread.sleep(20)
                waited += 20
            }
        } finally {
            if (!done.get()) FFmpegKit.cancel(session.sessionId)
            runCatching { stream?.close() }
            runCatching { FFmpegKitConfig.closeFFmpegPipe(pipe) }
        }

        if (total == 0L) return null
        check(ok.get()) { "FFmpeg stopped before the end of the file" }

        return AudioDecoder.DecodeInfo(
            sampleRate = p.sampleRate,
            channelCount = p.channels,
            codecMime = mimeFor(p.codec),
            floatOutput = true,
            durationUs = (p.durationSec * 1_000_000.0).toLong(),
            encoding = "FFmpeg Float32",
            declaredBits = p.declaredBits,
            engine = "FFmpeg"
        )
    }
}
