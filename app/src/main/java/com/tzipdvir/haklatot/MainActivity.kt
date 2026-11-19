package com.tzipdvir.haklatot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tzipdvir.haklatot.ui.HaklatotTheme
import com.tzipdvir.haklatot.update.CheckResult
import com.tzipdvir.haklatot.update.DownloadState
import com.tzipdvir.haklatot.update.UpdateChecker
import com.tzipdvir.haklatot.update.UpdateInfo
import com.tzipdvir.haklatot.update.UpdateInstaller
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var update by mutableStateOf<UpdateInfo?>(null)
    private var download by mutableStateOf<DownloadState>(DownloadState.Idle)
    private var checkMessage by mutableStateOf<String?>(null)
    private var askInstallPermission by mutableStateOf(false)

    private lateinit var installer: UpdateInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installer = UpdateInstaller(applicationContext)
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
    }

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
        if (!installer.canInstall()) {
            askInstallPermission = true
            return
        }
        lifecycleScope.launch {
            installer.downloadAndInstall(info) { download = it }
        }
    }

    @Composable
    private fun HomeScreen() {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            update?.let { UpdateBanner(it) }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .size(140.dp)
                    .clip(CircleShape),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = 1.45f, scaleY = 1.45f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "ההקלטות שלי",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "שלום! זו גרסת בדיקה של התשתית.\nבשלב הבא יופיע כאן כפתור ההקלטה.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.weight(1f))

            Text(
                "גרסה ${BuildConfig.VERSION_NAME}",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            TextButton(onClick = { checkForUpdate(force = true) }) {
                Text("בדוק עדכונים", fontSize = 17.sp)
            }
            checkMessage?.let {
                Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            }
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
                dismissButton = {
                    TextButton(onClick = { askInstallPermission = false }) { Text("לא עכשיו") }
                },
            )
        }
    }

    @Composable
    private fun UpdateBanner(info: UpdateInfo) {
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
                when (val d = download) {
                    is DownloadState.Downloading -> {
                        Text("מוריד… ${d.percent}%", fontSize = 16.sp)
                        LinearProgressIndicator(
                            progress = { d.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
