package com.tzipdvir.haklatot.recording

import android.content.Context
import android.media.MediaRecorder

/** Temporary (stage 1) choice of microphone source, to compare quality on the real phone. */
enum class MicSource(val androidSource: Int, val label: String, val fileSuffix: String) {
    NORMAL(MediaRecorder.AudioSource.MIC, "רגיל", ""),
    SPEECH(MediaRecorder.AudioSource.VOICE_RECOGNITION, "זיהוי דיבור", " (זיהוי דיבור)"),
}

object RecorderSettings {
    private const val PREFS = "settings"
    private const val KEY_SOURCE = "mic_source"

    fun micSource(context: Context): MicSource =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOURCE, null)
            ?.let { runCatching { MicSource.valueOf(it) }.getOrNull() } ?: MicSource.NORMAL

    fun setMicSource(context: Context, source: MicSource) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SOURCE, source.name).apply()
    }
}
