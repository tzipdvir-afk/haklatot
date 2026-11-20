package com.tzipdvir.haklatot

import android.app.Application
import android.os.Build
import com.tzipdvir.haklatot.data.EventLog

/** App entry point: sets up the event log and records unexpected crashes in it. */
class HaklatotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        EventLog.add("האפליקציה עלתה – גרסה ${BuildConfig.VERSION_NAME}, אנדרואיד ${Build.VERSION.RELEASE}")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            EventLog.add("קריסה (${thread.name}): ${error.javaClass.simpleName}: ${error.message}")
            error.stackTrace.take(6).forEach { EventLog.add("    at $it") }
            previous?.uncaughtException(thread, error)
        }
    }
}
