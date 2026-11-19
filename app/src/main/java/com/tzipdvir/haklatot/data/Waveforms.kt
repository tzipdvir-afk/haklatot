package com.tzipdvir.haklatot.data

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * "Shape of the sound" for each recording: one byte (0..255 loudness) per 100 ms,
 * stored privately next to the app (1 hour = 36 KB). Written while recording,
 * used later by the playback screen (stage 4).
 */
object Waveforms {
    private fun dir(context: Context) = File(context.filesDir, "waveforms").apply { mkdirs() }

    fun file(context: Context, recordingId: Long) = File(dir(context), "$recordingId.lvl")

    fun openForWrite(context: Context, recordingId: Long) =
        BufferedOutputStream(FileOutputStream(file(context, recordingId), true), 4096)

    fun read(context: Context, recordingId: Long): ByteArray? =
        file(context, recordingId).takeIf { it.exists() }?.readBytes()
}
