package com.tzipdvir.haklatot.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * "Shape of the sound" for each recording: one byte (0..255 loudness) per 100 ms,
 * stored privately next to the app (1 hour = 36 KB). Written while recording;
 * for older recordings it is computed once from the MP3 file.
 */
object Waveforms {
    private fun dir(context: Context) = File(context.filesDir, "waveforms").apply { mkdirs() }

    fun file(context: Context, recordingId: Long) = File(dir(context), "$recordingId.lvl")

    fun openForWrite(context: Context, recordingId: Long) =
        BufferedOutputStream(FileOutputStream(file(context, recordingId), true), 4096)

    /**
     * Returns the waveform, computing it from the audio if missing or clearly incomplete
     * (e.g. a recording cut off by a crash). Slow for long files: call off the main thread.
     */
    fun load(context: Context, rec: Recording): ByteArray {
        val f = file(context, rec.id)
        val expected = rec.durationMs / 100
        if (f.exists() && f.length() >= expected * 0.95) return f.readBytes()
        val built = buildFromAudio(context, rec.uri)
        runCatching { f.writeBytes(built) }
        return built
    }

    /** Same loudness scale as the recorder: -60..0 dBFS mapped to 0..255. */
    fun levelByte(sumSquares: Double, count: Int): Int {
        val rms = sqrt(sumSquares / count) / 32768.0
        val db = if (rms > 0) 20 * log10(rms) else -100.0
        return (((db + 60) / 60).coerceIn(0.0, 1.0) * 255).roundToInt()
    }

    /** Decodes the MP3 with Android's decoder and measures loudness every 100 ms. */
    private fun buildFromAudio(context: Context, uri: Uri): ByteArray {
        val out = ByteArrayOutputStream()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val window = rate / 10 * channels

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var sum = 0.0
            var count = 0
            while (true) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val b = c.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    val shorts = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shorts.hasRemaining()) {
                        val v = shorts.get().toDouble()
                        sum += v * v
                        if (++count >= window) {
                            out.write(levelByte(sum, count)); sum = 0.0; count = 0
                        }
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
        return out.toByteArray()
    }
}
