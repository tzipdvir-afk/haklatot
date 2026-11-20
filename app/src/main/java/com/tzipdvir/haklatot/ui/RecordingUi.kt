package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tzipdvir.haklatot.data.Recording
import com.tzipdvir.haklatot.recording.RecordingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RecordRed = Color(0xFFD32F2F)

/** Moving bars that follow the loudness. Newest bar on the left (Hebrew reading direction). */
@Composable
fun LevelMeter(levels: List<Float>, active: Boolean, modifier: Modifier = Modifier) {
    val barColor = if (active) RecordRed else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val count = RecordingState.LEVEL_HISTORY
        val step = size.width / count
        val barW = step * 0.6f
        val minH = 3.dp.toPx()
        for (age in 0 until count) {
            val idx = levels.size - 1 - age
            val level = if (idx >= 0) levels[idx] else 0f
            val h = (level * size.height).coerceAtLeast(minH)
            val x = age * step + (step - barW) / 2
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(150.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (recording) RecordRed else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
    ) {
        Text(if (recording) "עצור\nושמור" else "הקלט", fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    }
}

@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.height(52.dp)) {
        Text(if (paused) "המשך הקלטה" else "השהה", fontSize = 18.sp)
    }
}

@Composable
fun RecordingRow(rec: Recording, onClick: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rec.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatDate(rec.dateMillis)}  ·  ${formatSize(rec.sizeBytes)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                if (rec.recovered) {
                    Text("שוחזרה אחרי קריסה", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
                rec.stopReason?.label?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
                rec.noiseDb?.let {
                    // Temporary diagnostic, for choosing the silence threshold.
                    Text("רעש רקע: $it dB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(formatDuration(rec.durationMs), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("שתף", fontSize = 17.sp) }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("שנה שם", fontSize = 17.sp) }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("מחק", fontSize = 17.sp, color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Quick type buttons + optional free name, shown before recording starts. */
@Composable
fun NameChooser(type: String?, customName: String, onType: (String?) -> Unit, onCustomName: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("שיעור", "פגישה", "הרצאה").forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { onType(if (type == t) null else t) },
                    label = { Text(t, fontSize = 16.sp) },
                )
            }
        }
        OutlinedTextField(
            value = customName,
            onValueChange = onCustomName,
            singleLine = true,
            placeholder = { Text("שם להקלטה (לא חובה)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("חיפוש לפי שם") },
        trailingIcon = if (query.isNotEmpty()) {
            { TextButton(onClick = { onQuery("") }) { Text("נקה") } }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RenameDialog(rec: Recording, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(rec.id) { mutableStateOf(rec.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי שם") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Shown before sharing a large file: Gmail limits attachments to 25 MB. */
@Composable
fun LargeShareDialog(rec: Recording, onShare: (dontAskAgain: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("קובץ גדול (${formatSize(rec.sizeBytes)})") },
        text = {
            Text(
                "Gmail מאפשר לצרף עד 25MB, ולכן ישלח את ההקלטה כקישור ל-Google Drive.\n\n" +
                    "וואטסאפ ו-Google Drive מקבלים אותה כרגיל."
            )
        },
        confirmButton = { Button(onClick = { onShare(false) }) { Text("המשך לשיתוף") } },
        dismissButton = { TextButton(onClick = { onShare(true) }) { Text("הבנתי, אל תציג שוב") } },
    )
}

@Composable
fun DeleteDialog(rec: Recording, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מחיקת הקלטה") },
        text = { Text("למחוק את \"${rec.title}\" (${formatDuration(rec.durationMs)})?\n\nהמחיקה סופית – אי אפשר לשחזר.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("מחק") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> "%.1f GB".format(Locale.US, mb / 1024)
        mb >= 1 -> "%.1f MB".format(Locale.US, mb)
        else -> "%d KB".format(bytes / 1024)
    }
}

fun formatDate(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis))
