package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tzipdvir.haklatot.recording.RecorderSettings

/** Simple settings screen (visual design comes at the end of the project). */
@Composable
fun SettingsScreen(
    bitrate: Int,
    autoStop: Boolean,
    recording: Boolean,
    batteryOk: Boolean,
    onBitrate: (Int) -> Unit,
    onAutoStop: (Boolean) -> Unit,
    onBatteryRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShareLog: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("הגדרות", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("חזרה", fontSize = 17.sp) }
        }

        Text("איכות הקלטה", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (recording) Text("אפשר לשנות אחרי שההקלטה תסתיים.", fontSize = 14.sp)
        RecorderSettings.BITRATES.forEach { kbps ->
            val mbPerHour = kbps * 3600 / 8 / 1024
            val label = when (kbps) {
                32 -> "חסכונית – דיבור בלבד"
                64 -> "רגילה (מומלצת)"
                96 -> "גבוהה"
                else -> "גבוהה מאוד"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = kbps == bitrate, enabled = !recording) { onBitrate(kbps) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = kbps == bitrate, onClick = null, enabled = !recording)
                Column(Modifier.padding(start = 8.dp)) {
                    Text("$label · $kbps kbps", fontSize = 17.sp)
                    Text("כ-$mbPerHour MB לשעה", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                }
            }
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("עצירה אוטומטית אחרי שקט", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "אחרי 5 דקות בלי קול: התראה ורטט. אם לא לוחצים \"המשך להקליט\" – אחרי דקה נוספת ההקלטה נעצרת ונשמרת.",
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.padding(4.dp))
            Switch(checked = autoStop, onCheckedChange = onAutoStop)
        }

        HorizontalDivider()

        Text("סוללה (חשוב בסמסונג)", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (batteryOk) "✓ האפליקציה מורשית לפעול ברקע בלי הגבלת סוללה."
            else "סמסונג עלולה לעצור הקלטה ארוכה כדי לחסוך סוללה. מומלץ לאשר לאפליקציה לפעול ברקע.",
            fontSize = 15.sp,
        )
        if (!batteryOk) Button(onClick = onBatteryRequest) { Text("אשר פעולה ברקע") }
        Text(
            "בדיקה נוספת מומלצת: הגדרות הטלפון ← סוללה ← הגבלות שימוש ברקע. " +
                "ודא ש\"ההקלטות שלי\" לא מופיעה ב\"אפליקציות במצב שינה\" או ב\"אפליקציות בשינה עמוקה\". " +
                "אפשר גם להוסיף אותה ל\"אפליקציות שאף פעם לא נכנסות למצב שינה\".",
            fontSize = 14.sp,
        )
        OutlinedButton(onClick = onOpenAppSettings) { Text("פתח את הגדרות האפליקציה בטלפון") }

        HorizontalDivider()

        Text("יומן אירועים (לאבחון תקלות)", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "האפליקציה רושמת שעות ואירועים: התחלה, עצירה, השהיה, שיחות, אזהרות ותקלות. " +
                "בלי תוכן ההקלטות ובלי השמות שלהן. אם משהו לא עבד – שלח את היומן.",
            fontSize = 14.sp,
        )
        OutlinedButton(onClick = onShareLog) { Text("שלח יומן") }
        Spacer(Modifier.height(24.dp))
    }
}
