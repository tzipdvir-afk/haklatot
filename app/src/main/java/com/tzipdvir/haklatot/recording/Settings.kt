package com.tzipdvir.haklatot.recording

import android.media.MediaRecorder

/**
 * Microphone source. Chosen after a real test on the Galaxy A26 (stage 1):
 * VOICE_RECOGNITION gave clearer speech with less background noise than MIC.
 */
object RecorderSettings {
    const val MIC_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
}
