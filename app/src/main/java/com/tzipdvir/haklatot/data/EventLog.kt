package com.tzipdvir.haklatot.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small diagnostic diary: times and events only (start, stop, pause, call, warnings, errors).
 * Never contains audio or recording names. Keeps only the most recent events.
 * Shared from Settings to help find the cause of a problem.
 */
object EventLog {
    private const val MAX_BYTES = 96 * 1024
    private const val KEEP_LINES = 600
    private var file: File? = null
    private val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "events.log")
    }

    @Synchronized
    fun add(text: String) {
        val f = file ?: return
        runCatching {
            f.appendText("${fmt.format(Date())}  $text\n")
            if (f.length() > MAX_BYTES) {
                val lines = f.readLines()
                f.writeText(lines.takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    @Synchronized
    fun read(): String = runCatching { file?.readText() }.getOrNull().orEmpty()

    @Synchronized
    fun clear() {
        runCatching { file?.writeText("") }
    }
}
