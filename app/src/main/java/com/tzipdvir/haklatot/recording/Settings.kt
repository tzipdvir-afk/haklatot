package com.tzipdvir.haklatot.recording

import android.content.Context
import android.media.MediaRecorder

/** User settings + fixed recording constants. */
class RecorderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** MP3 quality in kbps: 32 / 64 / 96 / 128. */
    var bitrateKbps: Int
        get() = prefs.getInt(KEY_BITRATE, DEFAULT_BITRATE).takeIf { it in BITRATES } ?: DEFAULT_BITRATE
        set(v) { prefs.edit().putInt(KEY_BITRATE, v).apply() }

    /** Stop automatically after a long silence (warning after 5 min, stop after 6). */
    var autoStopOnSilence: Boolean
        get() = prefs.getBoolean(KEY_AUTO_STOP, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO_STOP, v).apply() }

    /** Whether we already explained the battery setting once. */
    var batteryExplained: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_EXPLAINED, false)
        set(v) { prefs.edit().putBoolean(KEY_BATTERY_EXPLAINED, v).apply() }

    companion object {
        private const val KEY_BITRATE = "bitrate"
        private const val KEY_AUTO_STOP = "auto_stop_silence"
        private const val KEY_BATTERY_EXPLAINED = "battery_explained"

        val BITRATES = listOf(32, 64, 96, 128)
        const val DEFAULT_BITRATE = 64

        /**
         * Microphone source, chosen after a real test on the Galaxy A26 (stage 1):
         * VOICE_RECOGNITION gave clearer speech with less background noise than MIC.
         */
        const val MIC_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION

        /**
         * A second counts as "sound" if its loudest 100 ms is above this level (dBFS).
         * TEMPORARY value – to be set from real classroom measurements (see noise level in the list).
         */
        const val SILENCE_THRESHOLD_DB = -45.0
        const val SILENCE_WARN_MS = 5 * 60 * 1000L
        const val SILENCE_STOP_MS = 6 * 60 * 1000L

        /** Battery: stop and save at this level when not charging. */
        const val BATTERY_STOP_PERCENT = 3

        /** Free storage: warn below, stop and save below. */
        const val STORAGE_WARN_BYTES = 300L * 1024 * 1024
        const val STORAGE_STOP_BYTES = 50L * 1024 * 1024
    }
}
