package com.tzipdvir.haklatot.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live state of the recorder, shared between the service and the screen (same process). */
data class RecorderStatus(
    val recording: Boolean = false,
    /** Name chosen for the current recording (date/time are added to the file name). */
    val name: String = "",
    val paused: Boolean = false,
    /** Android muted our microphone (phone / video call). Recording continues with silence. */
    val silencedByCall: Boolean = false,
    /** 5 minutes of silence: recording will stop in a minute unless the user taps "keep recording". */
    val silenceWarning: Boolean = false,
    val elapsedMs: Long = 0,
    /** Recent sound levels 0..1, oldest first, for the moving bars. */
    val levels: List<Float> = emptyList(),
    /** Warning shown while recording (low battery / low storage). */
    val warning: String? = null,
    /** Shown after a recording ended on its own (silence, battery, error...). */
    val endMessage: String? = null,
    /** Increases every time a recording is saved, so the list knows to refresh. */
    val savedCount: Int = 0,
)

object RecordingState {
    const val LEVEL_HISTORY = 60

    private val _status = MutableStateFlow(RecorderStatus())
    val status: StateFlow<RecorderStatus> = _status.asStateFlow()

    fun update(transform: (RecorderStatus) -> RecorderStatus) {
        while (true) {
            val old = _status.value
            if (_status.compareAndSet(old, transform(old))) return
        }
    }
}
