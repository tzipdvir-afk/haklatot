package com.tzipdvir.haklatot.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tzipdvir.haklatot.MainActivity
import com.tzipdvir.haklatot.R
import com.tzipdvir.haklatot.data.RecordingsStore
import java.io.FileOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Foreground service of type "microphone": keeps recording with the screen off,
 * in the pocket, or after the app is swiped away. Shows a permanent notification.
 *
 * Pipeline (on its own thread): AudioRecord (16 kHz mono PCM) -> LAME -> MP3 file,
 * written continuously and forced to disk every few seconds, so a crash loses only seconds.
 */
class RecordingService : Service() {

    @Volatile private var running = false
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!running) start()
            ACTION_STOP -> stopRecording()
            else -> stopSelf() // restarted by the system without a request: never auto-record
        }
        // NOT_STICKY: Android must not restart us on its own (it may not record from the background anyway).
        return START_NOT_STICKY
    }

    private fun start() {
        val startWall = System.currentTimeMillis()
        val startElapsed = SystemClock.elapsedRealtime()

        // Must be called right away, while the app is visible (Android 14+ rule for microphone services).
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(startWall),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haklatot:recording")
            .apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) } // safety cap: 12 hours

        running = true
        RecordingState.update { RecorderStatus(recording = true, savedCount = it.savedCount) }
        val source = RecorderSettings.micSource(this)
        thread = Thread({ recordLoop(source, startWall) }, "recorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        Log.i(TAG, "Recording started (source=$source) at elapsed=$startElapsed")
    }

    @SuppressLint("MissingPermission") // permission is checked by the screen before starting
    private fun recordLoop(source: MicSource, startWall: Long) {
        val store = RecordingsStore(this)
        var uri: Uri? = null
        var errorMsg: String? = null
        try {
            uri = store.createPending(startWall, source.fileSuffix)
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(
                source.androidSource, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE * 2) * 2, // >= 2 s of buffer
            )
            check(record.state == AudioRecord.STATE_INITIALIZED) { "המיקרופון לא זמין" }

            val pcm = ShortArray(CHUNK_SAMPLES)
            Mp3Encoder(SAMPLE_RATE, RecordingsStore.DEFAULT_BITRATE_KBPS).use { encoder ->
                val mp3 = encoder.newOutputBuffer(CHUNK_SAMPLES)
                contentResolver.openFileDescriptor(uri, "w")!!.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        record.startRecording()
                        var totalSamples = 0L
                        var lastSync = SystemClock.elapsedRealtime()
                        val levels = ArrayDeque<Float>()
                        try {
                            while (running) {
                                val n = record.read(pcm, 0, pcm.size)
                                if (n < 0) throw IllegalStateException("שגיאת מיקרופון ($n)")
                                if (n == 0) continue
                                val bytes = encoder.encode(pcm, n, mp3)
                                if (bytes > 0) out.write(mp3, 0, bytes)
                                totalSamples += n

                                // Force data to disk every few seconds (crash safety).
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastSync >= SYNC_INTERVAL_MS) {
                                    out.flush(); pfd.fileDescriptor.sync(); lastSync = now
                                }

                                levels.addLast(level(pcm, n))
                                while (levels.size > RecordingState.LEVEL_HISTORY) levels.removeFirst()
                                val elapsed = totalSamples * 1000 / SAMPLE_RATE
                                val snapshot = levels.toList()
                                RecordingState.update { it.copy(elapsedMs = elapsed, levels = snapshot) }
                            }
                        } finally {
                            runCatching { record.stop() }
                            record.release()
                            val tail = encoder.flush(mp3)
                            if (tail > 0) out.write(mp3, 0, tail)
                            out.flush()
                            pfd.fileDescriptor.sync()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Recording failed", t)
            errorMsg = t.message ?: "שגיאה בהקלטה"
        } finally {
            uri?.let { runCatching { store.finish(it) } }
            RecordingState.update {
                RecorderStatus(recording = false, error = errorMsg, savedCount = it.savedCount + 1)
            }
            ContextCompat.getMainExecutor(this).execute { shutdown() }
        }
    }

    /** Sound level of one chunk, mapped from -60..0 dBFS to 0..1. */
    private fun level(pcm: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val v = pcm[i].toDouble(); sum += v * v }
        val rms = sqrt(sum / n) / 32768.0
        val db = if (rms > 0) 20 * log10(rms) else -100.0
        return ((db + 60) / 60).coerceIn(0.0, 1.0).toFloat()
    }

    private fun stopRecording() {
        if (!running) { shutdown(); return }
        running = false // the loop finishes the file and then calls shutdown()
    }

    private fun shutdown() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        thread?.join(3000)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun buildNotification(startWall: Long): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "הקלטה פעילה", NotificationManager.IMPORTANCE_LOW).apply {
                description = "מוצגת כל עוד ההקלטה פועלת"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("מקליט…")
            .setContentText("לחץ כדי לחזור לאפליקציה")
            .setWhen(startWall)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "עצור ושמור", stop)
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10 // 100 ms
        private const val SYNC_INTERVAL_MS = 3_000L
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RecordingService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
