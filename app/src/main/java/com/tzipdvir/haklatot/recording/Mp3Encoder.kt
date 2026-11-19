package com.tzipdvir.haklatot.recording

import java.io.Closeable

/**
 * Kotlin side of the LAME bridge (mp3_encoder_jni.c).
 * Not thread-safe: use from the recording thread only.
 */
class Mp3Encoder(sampleRate: Int, bitrateKbps: Int) : Closeable {

    private var handle: Long = nativeInit(sampleRate, bitrateKbps)

    init {
        check(handle != 0L) { "LAME init failed (rate=$sampleRate, bitrate=$bitrateKbps)" }
    }

    /** Output buffer large enough for [samples] input samples (LAME's documented worst case). */
    fun newOutputBuffer(samples: Int) = ByteArray((1.25 * samples + 7200).toInt())

    /** Encodes [samples] PCM samples; returns how many MP3 bytes were written into [out]. */
    fun encode(pcm: ShortArray, samples: Int, out: ByteArray): Int {
        val n = nativeEncode(handle, pcm, samples, out)
        check(n >= 0) { "LAME encode error $n" }
        return n
    }

    /** Emits the last buffered frames. Call once at the end. */
    fun flush(out: ByteArray): Int = nativeFlush(handle, out).coerceAtLeast(0)

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private companion object {
        init {
            System.loadLibrary("mp3encoder")
        }

        @JvmStatic external fun nativeInit(sampleRate: Int, bitrateKbps: Int): Long
        @JvmStatic external fun nativeEncode(handle: Long, pcm: ShortArray, samples: Int, out: ByteArray): Int
        @JvmStatic external fun nativeFlush(handle: Long, out: ByteArray): Int
        @JvmStatic external fun nativeClose(handle: Long)
    }
}
