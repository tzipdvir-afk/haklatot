package com.tzipdvir.haklatot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import com.tzipdvir.haklatot.recording.RecorderSettings
import com.tzipdvir.haklatot.ui.PauseButton
import com.tzipdvir.haklatot.ui.NameChooser
import com.tzipdvir.haklatot.ui.SearchField
import com.tzipdvir.haklatot.ui.RenameDialog
import com.tzipdvir.haklatot.ui.DeleteDialog
import com.tzipdvir.haklatot.ui.LargeShareDialog
import android.content.ClipData
import com.tzipdvir.haklatot.ui.formatSize
import androidx.activity.result.IntentSenderRequest
import android.os.Environment
import android.os.StatFs
import com.tzipdvir.haklatot.ui.SettingsScreen
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tzipdvir.haklatot.data.Recording
import com.tzipdvir.haklatot.data.RecordingsStore
import com.tzipdvir.haklatot.recording.RecordingService
import com.tzipdvir.haklatot.recording.RecordingState
import com.tzipdvir.haklatot.ui.HaklatotTheme
import com.tzipdvir.haklatot.ui.LevelMeter
import com.tzipdvir.haklatot.ui.PlaybackScreen
import com.tzipdvir.haklatot.data.Waveforms
import com.tzipdvir.haklatot.ui.RecordButton
import com.tzipdvir.haklatot.ui.RecordRed
import com.tzipdvir.haklatot.ui.RecordingRow
import com.tzipdvir.haklatot.ui.formatDuration
import com.tzipdvir.haklatot.update.CheckResult
import com.tzipdvir.haklatot.update.DownloadState
import com.tzipdvir.haklatot.update.UpdateChecker
import com.tzipdvir.haklatot.update.UpdateInfo
import com.tzipdvir.haklatot.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Updates
    private var update by mutableStateOf<UpdateInfo?>(null)
    private var download by mutableStateOf<DownloadState>(DownloadState.Idle)
    private var checkMessage by mutableStateOf<String?>(null)
    private var askInstallPermission by mutableStateOf(false)

    // Recordings
    private var recordings by mutableStateOf<List<Recording>>(emptyList())

    // Built-in player
    private var player: MediaPlayer? = null
    private var nowPlaying by mutableStateOf<Recording?>(null)
    private var isPlaying by mutableStateOf(false)
    private var playPosMs by mutableStateOf(0L)
    private var playDurMs by mutableStateOf(0L)
    private var playSpeed by mutableStateOf(1f)
    private var waveform by mutableStateOf<ByteArray?>(null)
    private var infoMessage by mutableStateOf<String?>(null)
    private var askMicPermission by mutableStateOf(false)
    private var confirmCrash by mutableStateOf(false)

    // Names, search, list management
    private var nameType by mutableStateOf<String?>(null)
    private var customName by mutableStateOf("")
    private var query by mutableStateOf("")
    private var freeBytes by mutableStateOf<Long?>(null)
    private var renameTarget by mutableStateOf<Recording?>(null)
    private var deleteTarget by mutableStateOf<Recording?>(null)
    private var shareTarget by mutableStateOf<Recording?>(null)

    // Settings
    private var showSettings by mutableStateOf(false)
    private var bitrate by mutableStateOf(RecorderSettings.DEFAULT_BITRATE)
    private var autoStop by mutableStateOf(true)
    private var batteryOk by mutableStateOf(true)
    private var askBattery by mutableStateOf(false)

    private lateinit var installer: UpdateInstaller
    private lateinit var store: RecordingsStore
    private lateinit var settings: RecorderSettings

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true || hasMic()) {
            startRecording()
        } else {
            infoMessage = "בלי הרשאת מיקרופון אי אפשר להקליט. אפשר לאשר אותה בהגדרות הטלפון ← אפליקציות ← ההקלטות שלי ← הרשאות."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installer = UpdateInstaller(applicationContext)
        store = RecordingsStore(applicationContext)
        settings = RecorderSettings(applicationContext)
        bitrate = settings.bitrateKbps
        autoStop = settings.autoStopOnSilence
        playSpeed = getSharedPreferences("player", MODE_PRIVATE).getFloat("speed", 1f)

        // A recording that was cut off (crash, phone restart, forced stop) is closed and kept.
        if (!RecordingState.status.value.recording) {
            lifecycleScope.launch {
                val n = withContext(Dispatchers.IO) { runCatching { store.recoverUnfinished() }.getOrDefault(0) }
                if (n > 0) infoMessage = "הקלטה שנקטעה באמצע נשמרה עד נקודת העצירה. היא מסומנת ברשימה."
                refreshList()
            }
        }

        enableEdgeToEdge()
        setContent {
            HaklatotTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(isPlaying) {
                        while (isPlaying) {
                            player?.let { playPosMs = it.currentPosition.toLong() }
                            delay(200)
                        }
                    }
                    val open = nowPlaying
                    if (open != null) {
                        BackHandler { closePlayer() }
                        Column(Modifier.safeDrawingPadding()) {
                            PlaybackScreen(
                                rec = open,
                                waveform = waveform,
                                isPlaying = isPlaying,
                                positionMs = playPosMs,
                                durationMs = playDurMs,
                                speed = playSpeed,
                                onPlayPause = { togglePlay() },
                                onSeek = { seekTo(it) },
                                onSkip = { seekTo(playPosMs + it) },
                                onSpeed = { setSpeed(it) },
                                onShare = { requestShare(open) },
                                onRename = { renameTarget = open },
                                onDelete = { deleteTarget = open },
                                onBack = { closePlayer() },
                            )
                        }
                        ManageDialogs()
                    } else if (showSettings) {
                        BackHandler { showSettings = false }
                        val status by RecordingState.status.collectAsState()
                        Column(Modifier.safeDrawingPadding()) {
                            SettingsScreen(
                                bitrate = bitrate,
                                autoStop = autoStop,
                                recording = status.recording,
                                batteryOk = batteryOk,
                                onBitrate = { bitrate = it; settings.bitrateKbps = it },
                                onAutoStop = { autoStop = it; settings.autoStopOnSilence = it },
                                onBatteryRequest = { requestBatteryExemption() },
                                onOpenAppSettings = { openAppSettings() },
                                onBack = { showSettings = false },
                            )
                        }
                    } else {
                        HomeScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryOk = isBatteryOk()
        checkForUpdate(force = false)
        refreshList()
    }

    private fun refreshList() {
        lifecycleScope.launch {
            recordings = withContext(Dispatchers.IO) { runCatching { store.list() }.getOrDefault(recordings) }
            // Keep the open playback screen in sync (e.g. after renaming).
            nowPlaying?.let { cur -> recordings.firstOrNull { it.id == cur.id }?.let { nowPlaying = it } }
            freeBytes = withContext(Dispatchers.IO) {
                runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrNull()
            }
        }
    }

    private fun startRecording() {
        val name = customName.trim().ifBlank { nameType ?: "" }
        RecordingService.start(this, name)
        customName = ""
        nameType = null
    }

    // ---------- share ----------

    private fun requestShare(rec: Recording) {
        val skipNote = getSharedPreferences("player", MODE_PRIVATE).getBoolean("skip_large_note", false)
        if (rec.sizeBytes > LARGE_FILE_BYTES && !skipNote) shareTarget = rec else share(rec)
    }

    /** Opens Android's share sheet with the original file (no copy; temporary read access only). */
    private fun share(rec: Recording) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/mpeg")
            .putExtra(Intent.EXTRA_STREAM, rec.uri)
            .putExtra(Intent.EXTRA_SUBJECT, rec.title)
            .putExtra(Intent.EXTRA_TITLE, rec.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(rec.title, rec.uri)
        runCatching { startActivity(Intent.createChooser(send, "שיתוף: ${rec.title}")) }
            .onFailure { infoMessage = "לא נמצאה אפליקציה לשיתוף." }
    }

    // ---------- rename / delete ----------

    /** Runs after the user approves Android's consent dialog (only needed for files the app no longer "owns"). */
    private var afterConsent: (() -> Unit)? = null

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val action = afterConsent
        afterConsent = null
        if (r.resultCode == RESULT_OK) action?.invoke()
    }

    private fun askConsent(pending: android.app.PendingIntent?, then: () -> Unit) {
        if (pending == null) {
            infoMessage = "אנדרואיד לא מאפשר את הפעולה על הקובץ הזה."
            return
        }
        afterConsent = then
        consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }

    private fun doRename(rec: Recording, newTitle: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.rename(rec, newTitle) } }
            val error = result.exceptionOrNull()
            when {
                error is SecurityException -> askConsent(store.writeConsentRequest(rec)) { doRename(rec, newTitle) }
                error != null || result.getOrNull() != true -> infoMessage = "לא הצלחתי לשנות את השם."
                else -> refreshList()
            }
        }
    }

    private fun doDelete(rec: Recording) {
        if (nowPlaying?.id == rec.id) closePlayer()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.delete(rec) } }
            val error = result.exceptionOrNull()
            when {
                // The system dialog itself deletes the file when the user approves.
                error is SecurityException -> askConsent(store.deleteConsentRequest(rec)) {
                    store.forgetExtras(rec.id); refreshList()
                }
                error != null || result.getOrNull() != true -> infoMessage = "לא הצלחתי למחוק את ההקלטה."
                else -> refreshList()
            }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // ---------- settings & battery ----------

    private fun isBatteryOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife") // a personal recorder that must keep running for hours; user confirms
    private fun requestBatteryExemption() {
        settings.batteryExplained = true
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun onRecordPressed(recording: Boolean) {
        infoMessage = null
        if (recording) {
            RecordingService.stop(this)
        } else if (!batteryOk && !settings.batteryExplained) {
            askBattery = true
        } else if (hasMic()) {
            closePlayer()
            requestNotificationsIfNeeded()
            startRecording()
        } else {
            askMicPermission = true
        }
    }

    private fun askForPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        permissionRequest.launch(perms.toTypedArray())
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ---------- built-in player ----------

    private fun play(rec: Recording) {
        if (RecordingState.status.value.recording) {
            infoMessage = "אפשר להאזין אחרי שההקלטה תסתיים."
            return
        }
        closePlayer()
        nowPlaying = rec
        playPosMs = 0
        playDurMs = rec.durationMs
        waveform = null
        lifecycleScope.launch {
            val w = withContext(Dispatchers.Default) { runCatching { Waveforms.load(applicationContext, rec) }.getOrNull() }
            if (nowPlaying?.id == rec.id) waveform = w ?: ByteArray(0)
        }
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener {
                if (it.duration > 0) playDurMs = it.duration.toLong()
                // Continue where the user stopped last time (unless that was the very end).
                val saved = positions.getLong(rec.id.toString(), 0L)
                if (saved in 1 until playDurMs - 3000) {
                    it.seekTo(saved.toInt()); playPosMs = saved
                }
                startPlayback(it)
            }
            setOnCompletionListener { this@MainActivity.isPlaying = false; playPosMs = playDurMs }
            setOnErrorListener { _, _, _ ->
                infoMessage = "לא הצלחתי לנגן את ההקלטה."
                closePlayer()
                true
            }
            try {
                setDataSource(applicationContext, rec.uri)
                prepareAsync()
            } catch (e: Exception) {
                infoMessage = "לא הצלחתי לפתוח את ההקלטה."
                closePlayer()
            }
        }
    }

    /** Starts playing at the chosen speed (pitch stays natural). */
    private fun startPlayback(p: MediaPlayer) {
        if (playSpeed != 1f) {
            // Setting the speed also starts playback.
            runCatching { p.playbackParams = p.playbackParams.setSpeed(playSpeed).setPitch(1f) }
                .onFailure { p.start() }
        } else {
            p.start()
        }
        isPlaying = true
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause(); isPlaying = false
            savePosition()
        } else {
            if (playPosMs >= playDurMs - 300) { p.seekTo(0); playPosMs = 0 }
            startPlayback(p)
        }
    }

    private fun setSpeed(s: Float) {
        playSpeed = s
        getSharedPreferences("player", MODE_PRIVATE).edit().putFloat("speed", s).apply()
        val p = player ?: return
        if (p.isPlaying) runCatching { p.playbackParams = p.playbackParams.setSpeed(s).setPitch(1f) }
    }

    private val positions by lazy { getSharedPreferences("positions", MODE_PRIVATE) }

    private fun savePosition() {
        val rec = nowPlaying ?: return
        val pos = player?.currentPosition?.toLong() ?: playPosMs
        positions.edit().putLong(rec.id.toString(), if (pos >= playDurMs - 3000) 0L else pos).apply()
    }

    private fun seekTo(ms: Long) {
        val p = player ?: return
        val target = ms.coerceIn(0, playDurMs)
        p.seekTo(target.toInt())
        playPosMs = target
    }

    private fun closePlayer() {
        if (player != null) savePosition()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        nowPlaying = null
        isPlaying = false
        waveform = null
    }

    override fun onStop() {
        super.onStop()
        player?.let { if (it.isPlaying) { it.pause(); isPlaying = false } }
        savePosition()
    }

    override fun onDestroy() {
        closePlayer()
        super.onDestroy()
    }

    // ---------- updates ----------

    private fun checkForUpdate(force: Boolean) {
        lifecycleScope.launch {
            if (force) checkMessage = "בודק…"
            when (val r = UpdateChecker.check(applicationContext, force)) {
                is CheckResult.Available -> { update = r.info; checkMessage = null }
                CheckResult.UpToDate -> { update = null; if (force) checkMessage = "יש לך את הגרסה האחרונה" }
                CheckResult.Failed -> if (force) checkMessage = "אין חיבור לאינטרנט, נסה שוב מאוחר יותר"
            }
        }
    }

    private fun startUpdate(info: UpdateInfo) {
        if (RecordingState.status.value.recording) return
        if (!installer.canInstall()) {
            askInstallPermission = true
            return
        }
        lifecycleScope.launch {
            installer.downloadAndInstall(info) { download = it }
        }
    }

    // ---------- screen ----------

    @Composable
    private fun ManageDialogs() {
        renameTarget?.let { rec ->
            RenameDialog(rec, onConfirm = { renameTarget = null; doRename(rec, it) }, onDismiss = { renameTarget = null })
        }
        deleteTarget?.let { rec ->
            DeleteDialog(rec, onConfirm = { deleteTarget = null; doDelete(rec) }, onDismiss = { deleteTarget = null })
        }
        shareTarget?.let { rec ->
            LargeShareDialog(
                rec,
                onShare = { dontAsk ->
                    if (dontAsk) getSharedPreferences("player", MODE_PRIVATE).edit().putBoolean("skip_large_note", true).apply()
                    shareTarget = null
                    share(rec)
                },
                onDismiss = { shareTarget = null },
            )
        }
    }

    @Composable
    private fun HomeScreen() {
        val status by RecordingState.status.collectAsState()

        LaunchedEffect(status.savedCount) { if (status.savedCount > 0) refreshList() }
        LaunchedEffect(status.endMessage) { status.endMessage?.let { infoMessage = it } }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    update?.let { UpdateBanner(it, status.recording) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ההקלטות שלי",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showSettings = true }) { Text("הגדרות", fontSize = 17.sp) }
                    }
                    if (!batteryOk && !status.recording) {
                        Card(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("כדי שהקלטות ארוכות לא ייעצרו – מומלץ לאשר פעולה ברקע.", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { requestBatteryExemption() }) { Text("אשר") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        formatDuration(status.elapsedMs),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = if (status.recording && !status.paused) RecordRed else MaterialTheme.colorScheme.onBackground,
                    )
                    val stateLine = when {
                        !status.recording -> null
                        status.paused -> "ההקלטה מושהית"
                        status.silencedByCall -> "המיקרופון מושתק בגלל שיחה – ההקלטה תמשיך אוטומטית אחריה"
                        else -> "מקליט…"
                    }
                    stateLine?.let { Text(it, fontSize = 16.sp, textAlign = TextAlign.Center) }
                    LevelMeter(status.levels, status.recording && !status.paused, Modifier.padding(vertical = 8.dp))
                    if (status.silenceWarning) {
                        Card(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("לא נקלט קול 5 דקות. ההקלטה תיעצר ותישמר בעוד דקה.", fontSize = 16.sp, textAlign = TextAlign.Center)
                                Button(onClick = { RecordingService.keepGoing(this@MainActivity) }) { Text("המשך להקליט") }
                            }
                        }
                    }
                    status.warning?.let { if (status.recording) Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                    if (status.recording) {
                        Text("שם: ${status.name}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
                    } else {
                        NameChooser(nameType, customName, onType = { nameType = it }, onCustomName = { customName = it })
                    }
                    Spacer(Modifier.height(10.dp))
                    RecordButton(status.recording) { onRecordPressed(status.recording) }
                    if (status.recording) {
                        Spacer(Modifier.height(12.dp))
                        PauseButton(status.paused) {
                            if (status.paused) RecordingService.resume(this@MainActivity) else RecordingService.pause(this@MainActivity)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (status.recording) {
                        TextButton(onClick = { confirmCrash = true }) {
                            Text("סימולציית קריסה (בדיקה)", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    infoMessage?.let {
                        Card(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Text(it, Modifier.padding(14.dp), fontSize = 16.sp, textAlign = TextAlign.Start)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (recordings.isEmpty()) "עדיין אין הקלטות" else "כל ההקלטות (${recordings.size})",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (recordings.isNotEmpty()) {
                        Text(
                            "סה\"כ ${formatSize(recordings.sumOf { it.sizeBytes })}" +
                                (freeBytes?.let { " · נשארו ${formatSize(it)} פנויים בטלפון" } ?: ""),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        SearchField(query) { query = it }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            val shown = if (query.isBlank()) recordings
            else recordings.filter { it.title.contains(query.trim(), ignoreCase = true) }
            if (query.isNotBlank() && shown.isEmpty()) {
                item { Text("לא נמצאו הקלטות בשם הזה", fontSize = 16.sp, modifier = Modifier.padding(12.dp)) }
            }
            items(shown, key = { it.id }) { rec ->
                RecordingRow(
                    rec,
                    onClick = { play(rec) },
                    onShare = { requestShare(rec) },
                    onRename = { renameTarget = rec },
                    onDelete = { deleteTarget = rec },
                )
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 18.dp)) {
                    Text(
                        "גרסה ${BuildConfig.VERSION_NAME}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    TextButton(onClick = { checkForUpdate(force = true) }) { Text("בדוק עדכונים", fontSize = 16.sp) }
                    checkMessage?.let { Text(it, fontSize = 14.sp) }
                }
            }
        }
        }

        ManageDialogs()

        if (askBattery) {
            AlertDialog(
                onDismissRequest = { askBattery = false },
                title = { Text("לפני ההקלטה הראשונה") },
                text = {
                    Text(
                        "טלפונים של סמסונג חוסכים סוללה בכך שהם עוצרים אפליקציות שפועלות ברקע – " +
                            "וזה עלול לעצור הקלטה ארוכה כשהמסך כבוי.\n\n" +
                            "כדי למנוע את זה, אשר לאפליקציה לפעול ברקע. בחלון שייפתח לחץ \"אפשר\".\n\n" +
                            "אחר כך לחץ שוב \"הקלט\"."
                    )
                },
                confirmButton = {
                    Button(onClick = { askBattery = false; requestBatteryExemption() }) { Text("אשר עכשיו") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        askBattery = false
                        settings.batteryExplained = true
                        onRecordPressed(false)
                    }) { Text("לא עכשיו, הקלט") }
                },
            )
        }

        if (askMicPermission) {
            AlertDialog(
                onDismissRequest = { askMicPermission = false },
                title = { Text("גישה למיקרופון") },
                text = {
                    Text(
                        "כדי להקליט, האפליקציה צריכה גישה למיקרופון. " +
                            "היא תבקש גם אישור להציג התראה – כך תראה שההקלטה פועלת ותוכל לעצור אותה גם כשהמסך כבוי.\n\n" +
                            "ההקלטות נשמרות רק בטלפון שלך."
                    )
                },
                confirmButton = {
                    Button(onClick = { askMicPermission = false; askForPermissions() }) { Text("המשך") }
                },
                dismissButton = { TextButton(onClick = { askMicPermission = false }) { Text("ביטול") } },
            )
        }

        if (confirmCrash) {
            AlertDialog(
                onDismissRequest = { confirmCrash = false },
                title = { Text("סימולציית קריסה") },
                text = {
                    Text(
                        "האפליקציה תיסגר מיד, כאילו קרסה, באמצע ההקלטה.\n\n" +
                            "אחר כך פתח אותה שוב: ההקלטה אמורה להופיע ברשימה עם הסימון \"שוחזרה אחרי קריסה\", " +
                            "ולהישמע עד הרגע הזה."
                    )
                },
                confirmButton = {
                    Button(onClick = { Process.killProcess(Process.myPid()) }) { Text("סגור בכוח") }
                },
                dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("ביטול") } },
            )
        }

        if (askInstallPermission) {
            AlertDialog(
                onDismissRequest = { askInstallPermission = false },
                title = { Text("אישור חד-פעמי") },
                text = {
                    Text(
                        "כדי שהאפליקציה תוכל לעדכן את עצמה, אנדרואיד מבקש לאשר לה \"התקנת אפליקציות לא מוכרות\".\n\n" +
                            "במסך שייפתח – הפעל את המתג, חזור לכאן ולחץ שוב \"עדכן עכשיו\"."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        askInstallPermission = false
                        installer.openInstallPermissionSettings()
                    }) { Text("פתח הגדרות") }
                },
                dismissButton = { TextButton(onClick = { askInstallPermission = false }) { Text("לא עכשיו") } },
            )
        }
    }

    @Composable
    private fun UpdateBanner(info: UpdateInfo, recording: Boolean) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("גרסה ${info.versionName} מוכנה", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (info.notes.isNotBlank()) Text(info.notes, fontSize = 16.sp)
                if (recording) {
                    Text("אפשר לעדכן אחרי שההקלטה תסתיים.", fontSize = 16.sp)
                } else when (val d = download) {
                    is DownloadState.Downloading -> {
                        Text("מוריד… ${d.percent}%", fontSize = 16.sp)
                        LinearProgressIndicator(progress = { d.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    DownloadState.Failed -> {
                        Text("ההורדה לא הצליחה. אפשר להוריד מדף ההורדה בדפדפן.", fontSize = 16.sp)
                        Button(onClick = { installer.openDownloadPage() }) { Text("פתח דף הורדה") }
                        TextButton(onClick = { download = DownloadState.Idle; startUpdate(info) }) { Text("נסה שוב") }
                    }
                    DownloadState.Idle -> {
                        Button(onClick = { startUpdate(info) }) { Text("עדכן עכשיו", fontSize = 17.sp) }
                    }
                }
            }
        }
    }

    private companion object {
        /** Gmail's attachment limit. */
        const val LARGE_FILE_BYTES = 25L * 1000 * 1000
    }
}
