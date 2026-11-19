package com.tzipdvir.haklatot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import com.tzipdvir.haklatot.recording.MicSource
import com.tzipdvir.haklatot.recording.RecorderSettings
import com.tzipdvir.haklatot.recording.RecordingService
import com.tzipdvir.haklatot.recording.RecordingState
import com.tzipdvir.haklatot.ui.HaklatotTheme
import com.tzipdvir.haklatot.ui.LevelMeter
import com.tzipdvir.haklatot.ui.MicSourceChooser
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
    private var micSource by mutableStateOf(MicSource.NORMAL)
    private var infoMessage by mutableStateOf<String?>(null)
    private var askMicPermission by mutableStateOf(false)
    private var confirmCrash by mutableStateOf(false)

    private lateinit var installer: UpdateInstaller
    private lateinit var store: RecordingsStore

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true || hasMic()) {
            RecordingService.start(this)
        } else {
            infoMessage = "בלי הרשאת מיקרופון אי אפשר להקליט. אפשר לאשר אותה בהגדרות הטלפון ← אפליקציות ← ההקלטות שלי ← הרשאות."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installer = UpdateInstaller(applicationContext)
        store = RecordingsStore(applicationContext)
        micSource = RecorderSettings.micSource(this)

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
                    HomeScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkForUpdate(force = false)
        refreshList()
    }

    private fun refreshList() {
        lifecycleScope.launch {
            recordings = withContext(Dispatchers.IO) { runCatching { store.list() }.getOrDefault(recordings) }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun onRecordPressed(recording: Boolean) {
        infoMessage = null
        if (recording) {
            RecordingService.stop(this)
        } else if (hasMic()) {
            requestNotificationsIfNeeded()
            RecordingService.start(this)
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

    private fun play(rec: Recording) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(rec.uri, "audio/mpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: ActivityNotFoundException) {
            infoMessage = "לא נמצא בטלפון נגן שיכול לפתוח את הקובץ."
        }
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
    private fun HomeScreen() {
        val status by RecordingState.status.collectAsState()

        LaunchedEffect(status.savedCount) { if (status.savedCount > 0) refreshList() }
        LaunchedEffect(status.error) { status.error?.let { infoMessage = "ההקלטה נעצרה: $it" } }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    update?.let { UpdateBanner(it, status.recording) }
                    Text(
                        "ההקלטות שלי",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        formatDuration(status.elapsedMs),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = if (status.recording) RecordRed else MaterialTheme.colorScheme.onBackground,
                    )
                    LevelMeter(status.levels, status.recording, Modifier.padding(vertical = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    RecordButton(status.recording) { onRecordPressed(status.recording) }
                    Spacer(Modifier.height(14.dp))
                    MicSourceChooser(micSource, enabled = !status.recording) {
                        micSource = it
                        RecorderSettings.setMicSource(this@MainActivity, it)
                    }
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
                    Spacer(Modifier.height(4.dp))
                }
            }

            items(recordings, key = { it.id }) { rec -> RecordingRow(rec) { play(rec) } }

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
}
