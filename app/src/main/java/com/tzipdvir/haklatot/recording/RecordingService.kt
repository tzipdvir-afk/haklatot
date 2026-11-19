package com.tzipdvir.haklatot.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tzipdvir.haklatot.MainActivity
import com.tzipdvir.haklatot.R
import com.tzipdvir.haklatot.data.RecordingMeta
import com.tzipdvir.haklatot.data.RecordingMetaStore
import com.tzipdvir.haklatot.data.RecordingsStore
import com.tzipdvir.haklatot.data.StopReason
import com.tzipdvir.haklatot.data.Waveforms
import java.io.FileOutputStream
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Foreground service of type "microphone": keeps recording with the screen off,
 * in the pocket, or after the app is swiped away. Shows a permanent notification.
 *
 * Pipeline (own thread): AudioRecord (16 kHz mono PCM) -> LAME -> MP3 file,
 * written continuously and forced to disk every few seconds, so a crash loses only seconds.
 * Also: pause/resume, call-mute detection, silence auto-stop, battery/storage/shutdown safety.
 */
class RecordingService : Service() {

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var silencedByCall = false
    @Volatile private var resetSilence = false
    @Volatile private var stopReason = StopReason.USER
    @Volatile private var audioSessionId = 0
    @Volatile private var elapsedMs = 0L

    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryWarned = false
    private var storageWarned = false

    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!running) start()
            ACTION_STOP -> requestStop(StopReason.USER)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_KEEP_GOING -> keepGoing()
            else -> if (!running) stopSelf() // restarted by the system: never record on its own
        }
        // NOT_STICKY: Android must not restart us by itself (it may not record from the background anyway).
        return START_NOT_STICKY
    }

    // ---------- lifecycle ----------

    private fun start() {
        createChannels()
        elapsedMs = 0
        // Must happen right away, while the app is visible (Android 14+ rule for microphone services).
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildOngoingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haklatot:recording")
            .apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) } // safety cap: 12 hours

        running = true
        paused = false
        silencedByCall = false
        stopReason = StopReason.USER
        batteryWarned = false
        storageWarned = false
        RecordingState.update { RecorderStatus(recording = true, savedCount = it.savedCount) }

        registerSystemEvents()
        val bitrate = RecorderSettings(this).bitrateKbps
        thread = Thread({ recordLoop(bitrate) }, "recorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun requestStop(reason: StopReason) {
        if (!running) { shutdown(); return }
        stopReason = reason
        running = false // the loop finishes the file and then calls shutdown()
    }

    private fun setPaused(p: Boolean) {
        if (!running || paused == p) return
        paused = p
        resetSilence = true
        RecordingState.update { it.copy(paused = p, silenceWarning = false) }
        nm.cancel(ALERT_ID)
        refreshOngoingNotification()
    }

    private fun keepGoing() {
        resetSilence = true
        RecordingState.update { it.copy(silenceWarning = false) }
        nm.cancel(ALERT_ID)
    }

    private fun shutdown() {
        unregisterSystemEvents()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        thread?.join(3000)
        unregisterSystemEvents()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ---------- the recording loop ----------

    @SuppressLint("MissingPermission") // permission is checked by the screen before starting
    private fun recordLoop(bitrate: Int) {
        val store = RecordingsStore(this)
        val metaStore = RecordingMetaStore(this)
        val settings = RecorderSettings(this)
        var uri: Uri? = null
        var id = -1L
        var errorText: String? = null
        val noiseHistogram = IntArray(101) // per-second level in dBFS, index = -dB (0..100)

        try {
            uri = store.createPending(System.currentTimeMillis())
            id = ContentUris.parseId(uri)
            metaStore.put(id, RecordingMeta(bitrateKbps = bitrate)) // known even if we crash

            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(
                RecorderSettings.MIC_SOURCE, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE * 2) * 2, // >= 2 s of buffer
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("המיקרופון לא זמין")
            }
            audioSessionId = record.audioSessionId

            val pcm = ShortArray(CHUNK_SAMPLES)
            Mp3Encoder(SAMPLE_RATE, bitrate).use { encoder ->
                val mp3 = encoder.newOutputBuffer(CHUNK_SAMPLES)
                val waveOut = Waveforms.openForWrite(this, id)
                contentResolver.openFileDescriptor(uri, "w")!!.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        record.startRecording()
                        var totalSamples = 0L
                        var lastSync = SystemClock.elapsedRealtime()
                        var lastStorageCheck = 0L
                        val levels = ArrayDeque<Float>()
                        // Silence detection works in 1-second blocks.
                        var blockSamples = 0
                        var blockMaxDb = -100.0
                        var silentMs = 0L
                        var warned = false
                        try {
                            while (running) {
                                val n = record.read(pcm, 0, pcm.size)
                                if (n < 0) throw IllegalStateException("שגיאת מיקרופון ($n)")
                                if (n == 0) continue
                                val now = SystemClock.elapsedRealtime()

                                if (resetSilence) {
                                    resetSilence = false; silentMs = 0; warned = false
                                }

                                if (paused) {
                                    // Microphone stays open but nothing is written; the clock does not run.
                                    pushLevel(levels, 0f)
                                    val snap = levels.toList()
                                    RecordingState.update { it.copy(levels = snap) }
                                    continue
                                }

                                val bytes = encoder.encode(pcm, n, mp3)
                                if (bytes > 0) out.write(mp3, 0, bytes)
                                totalSamples += n
                                elapsedMs = totalSamples * 1000 / SAMPLE_RATE

                                val db = levelDb(pcm, n)
                                val level = ((db + 60) / 60).coerceIn(0.0, 1.0).toFloat()
                                waveOut.write((level * 255).roundToInt())
                                pushLevel(levels, level)

                                // Force data to disk every few seconds (crash safety).
                                if (now - lastSync >= SYNC_INTERVAL_MS) {
                                    out.flush(); pfd.fileDescriptor.sync(); waveOut.flush(); lastSync = now
                                }

                                // --- silence detection (per second) ---
                                blockSamples += n
                                if (db > blockMaxDb) blockMaxDb = db
                                if (blockSamples >= SAMPLE_RATE) {
                                    noiseHistogram[(-blockMaxDb).roundToInt().coerceIn(0, 100)]++
                                    val loud = blockMaxDb > RecorderSettings.SILENCE_THRESHOLD_DB
                                    if (loud || silencedByCall || !settings.autoStopOnSilence) {
                                        silentMs = 0
                                        if (warned) { warned = false; clearSilenceWarning() }
                                    } else {
                                        silentMs += 1000
                                        if (!warned && silentMs >= RecorderSettings.SILENCE_WARN_MS) {
                                            warned = true; showSilenceWarning()
                                        }
                                        if (silentMs >= RecorderSettings.SILENCE_STOP_MS) {
                                            stopReason = StopReason.SILENCE; running = false
                                        }
                                    }
                                    blockSamples = 0; blockMaxDb = -100.0
                                }

                                // --- free storage, every 30 s ---
                                if (now - lastStorageCheck >= 30_000) {
                                    lastStorageCheck = now
                                    checkStorage()
                                }

                                val snapshot = levels.toList()
                                val e = elapsedMs
                                RecordingState.update { it.copy(elapsedMs = e, levels = snapshot) }
                            }
                        } finally {
                            runCatching { record.stop() }
                            record.release()
                            audioSessionId = 0
                            val tail = encoder.flush(mp3)
                            if (tail > 0) out.write(mp3, 0, tail)
                            out.flush()
                            pfd.fileDescriptor.sync()
                            runCatching { waveOut.close() }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Recording failed", t)
            errorText = t.message ?: "שגיאה בהקלטה"
            stopReason = StopReason.ERROR
        } finally {
            uri?.let { runCatching { store.finish(it) } }
            val reason = stopReason
            if (id >= 0) {
                runCatching {
                    metaStore.put(id, RecordingMeta(bitrateKbps = bitrate, noiseDb = noiseFloor(noiseHistogram), stopReason = reason))
                }
            }
            val message = when {
                errorText != null -> "ההקלטה נעצרה בגלל תקלה: $errorText. מה שהוקלט עד אז נשמר."
                reason != StopReason.USER -> "${reason.label}. ההקלטה נשמרה."
                else -> null
            }
            nm.cancel(ALERT_ID)
            if (message != null) showAlert(message, withKeepGoing = false)
            RecordingState.update {
                RecorderStatus(recording = false, endMessage = message, savedCount = it.savedCount + 1)
            }
            ContextCompat.getMainExecutor(this).execute { shutdown() }
        }
    }

    private fun pushLevel(levels: ArrayDeque<Float>, v: Float) {
        levels.addLast(v)
        while (levels.size > RecordingState.LEVEL_HISTORY) levels.removeFirst()
    }

    /** Loudness of one chunk in dBFS (-100..0). */
    private fun levelDb(pcm: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) { val v = pcm[i].toDouble(); sum += v * v }
        val rms = sqrt(sum / n) / 32768.0
        return if (rms > 0) (20 * log10(rms)).coerceAtLeast(-100.0) else -100.0
    }

    /** Background noise = the level that 10% of the seconds were quieter than. */
    private fun noiseFloor(hist: IntArray): Int? {
        val total = hist.sum()
        if (total < 10) return null
        var acc = 0
        for (neg in 100 downTo 0) { // from quietest (-100 dB) to loudest (0 dB)
            acc += hist[neg]
            if (acc >= total * 0.1) return -neg
        }
        return null
    }

    // ---------- warnings ----------

    private fun showSilenceWarning() {
        RecordingState.update { it.copy(silenceWarning = true) }
        showAlert("לא נקלט קול 5 דקות. ההקלטה תיעצר ותישמר בעוד דקה.", withKeepGoing = true)
    }

    private fun clearSilenceWarning() {
        RecordingState.update { it.copy(silenceWarning = false) }
        nm.cancel(ALERT_ID)
    }

    private fun checkStorage() {
        val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrNull() ?: return
        when {
            free < RecorderSettings.STORAGE_STOP_BYTES -> {
                stopReason = StopReason.STORAGE; running = false
            }
            free < RecorderSettings.STORAGE_WARN_BYTES && !storageWarned -> {
                storageWarned = true
                val msg = "נשאר מעט מקום בטלפון. אם ייגמר, ההקלטה תיעצר ותישמר."
                RecordingState.update { it.copy(warning = msg) }
                showAlert(msg, withKeepGoing = false)
            }
        }
    }

    // ---------- system events: calls, battery, shutdown ----------

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> requestStop(StopReason.SHUTDOWN)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    if (level < 0 || scale <= 0 || plugged) return
                    val pct = level * 100 / scale
                    if (pct <= RecorderSettings.BATTERY_STOP_PERCENT) {
                        requestStop(StopReason.BATTERY)
                    } else if (pct <= 15 && !batteryWarned) {
                        batteryWarned = true
                        val msg = "הסוללה חלשה ($pct%). ההקלטה ממשיכה; ב-${RecorderSettings.BATTERY_STOP_PERCENT}% היא תיעצר ותישמר אוטומטית."
                        RecordingState.update { it.copy(warning = msg) }
                        showAlert(msg, withKeepGoing = false)
                    }
                }
            }
        }
    }

    private val callCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val session = audioSessionId
            if (session == 0) return
            val mine = configs.firstOrNull { it.clientAudioSessionId == session } ?: return
            val silenced = mine.isClientSilenced
            if (silenced != silencedByCall) {
                silencedByCall = silenced
                RecordingState.update { it.copy(silencedByCall = silenced) }
                refreshOngoingNotification()
            }
        }
    }

    private var eventsRegistered = false

    private fun registerSystemEvents() {
        if (eventsRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        audioManager.registerAudioRecordingCallback(callCallback, mainHandler)
        eventsRegistered = true
    }

    private fun unregisterSystemEvents() {
        if (!eventsRegistered) return
        runCatching { unregisterReceiver(systemReceiver) }
        runCatching { audioManager.unregisterAudioRecordingCallback(callCallback) }
        eventsRegistered = false
    }

    // ---------- notifications ----------

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "הקלטה פעילה", NotificationManager.IMPORTANCE_LOW).apply {
                description = "מוצגת כל עוד ההקלטה פועלת"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "התראות הקלטה", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "שקט ממושך, סוללה חלשה, מקום שנגמר, הקלטה שנעצרה"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }

    private fun refreshOngoingNotification() {
        if (running) nm.notify(NOTIFICATION_ID, buildOngoingNotification())
    }

    private fun buildOngoingNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)

        when {
            paused -> b.setContentTitle("ההקלטה מושהית")
                .setContentText("נשמר עד כה: ${formatClock(elapsedMs)}")
                .setUsesChronometer(false).setShowWhen(false)
                .addAction(0, "המשך", servicePending(ACTION_RESUME, 2))
            silencedByCall -> b.setContentTitle("המיקרופון מושתק – שיחה")
                .setContentText("ההקלטה תמשיך אוטומטית כשהשיחה תסתיים")
                .setWhen(System.currentTimeMillis() - elapsedMs).setUsesChronometer(true).setShowWhen(true)
                .addAction(0, "השהה", servicePending(ACTION_PAUSE, 3))
            else -> b.setContentTitle("מקליט…")
                .setContentText("לחץ כדי לחזור לאפליקציה")
                .setWhen(System.currentTimeMillis() - elapsedMs).setUsesChronometer(true).setShowWhen(true)
                .addAction(0, "השהה", servicePending(ACTION_PAUSE, 3))
        }
        b.addAction(0, "עצור ושמור", servicePending(ACTION_STOP, 1))
        return b.build()
    }

    private fun showAlert(text: String, withKeepGoing: Boolean) {
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("ההקלטות שלי")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (withKeepGoing) b.addAction(0, "המשך להקליט", servicePending(ACTION_KEEP_GOING, 4))
        runCatching { nm.notify(ALERT_ID, b.build()) }
    }

    private fun servicePending(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun formatClock(ms: Long): String {
        val t = ms / 1000
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    companion object {
        private const val TAG = "RecordingService"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10 // 100 ms
        private const val SYNC_INTERVAL_MS = 3_000L
        private const val CHANNEL_ONGOING = "recording"
        private const val CHANNEL_ALERTS = "recording_alerts"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_ID = 2
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_KEEP_GOING = "keep_going"

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, RecordingService::class.java).setAction(action))
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RecordingService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) = send(context, ACTION_STOP)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun keepGoing(context: Context) = send(context, ACTION_KEEP_GOING)
    }
}
