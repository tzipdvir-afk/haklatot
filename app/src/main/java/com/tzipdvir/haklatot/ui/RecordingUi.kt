package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tzipdvir.haklatot.R
import com.tzipdvir.haklatot.data.Recording
import com.tzipdvir.haklatot.recording.RecordingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RecordRed = Navy.Rec

// ---------- small building blocks ----------

@Composable
fun AppChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Navy.Accent else Navy.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Navy.AccentInk else if (enabled) Navy.Ink else Navy.Mute,
        )
    }
}

@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Navy.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_gear), contentDescription = "הגדרות", colorFilter = ColorFilter.tint(Navy.Mute), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun appFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Navy.Surface,
    unfocusedContainerColor = Navy.Surface,
    focusedBorderColor = Navy.Accent,
    unfocusedBorderColor = Navy.Line,
    focusedPlaceholderColor = Navy.Mute,
    unfocusedPlaceholderColor = Navy.Mute,
    cursorColor = Navy.Accent,
)

// ---------- recording ----------

/** Moving bars that follow the loudness. Newest bar on the left (Hebrew reading direction). */
@Composable
fun LevelMeter(levels: List<Float>, active: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val count = RecordingState.LEVEL_HISTORY
        val step = size.width / count
        val barW = step * 0.58f
        val minH = 4.dp.toPx()
        for (age in 0 until count) {
            val idx = levels.size - 1 - age
            val level = if (active && idx >= 0) levels[idx] else 0f
            val h = (level * size.height).coerceAtLeast(minH)
            val x = age * step + (step - barW) / 2
            drawRoundRect(
                color = if (active) Navy.Rec else Navy.Line,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

/** Big round button: microphone to start, white square to stop. */
@Composable
fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(136.dp)
            .drawBehind {
                // soft ring around the button
                drawCircle(Navy.Rec.copy(alpha = 0.16f), radius = size.minDimension / 2)
            }
            .padding(12.dp)
            .clip(CircleShape)
            .background(Navy.Rec)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
        } else {
            Image(painterResource(R.drawable.ic_mic), contentDescription = "הקלט", colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(50.dp))
        }
    }
}

@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(2.dp, Navy.Line),
        modifier = Modifier.height(48.dp),
    ) {
        Text(if (paused) "▶  המשך הקלטה" else "❚❚  השהה", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy.Ink)
    }
}

/** Quick type buttons + optional free name, shown before recording starts. */
@Composable
fun NameChooser(type: String?, customName: String, onType: (String?) -> Unit, onCustomName: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("שיעור", "פגישה", "הרצאה").forEach { t ->
                AppChip(t, selected = type == t) { onType(if (type == t) null else t) }
            }
        }
        OutlinedTextField(
            value = customName,
            onValueChange = onCustomName,
            singleLine = true,
            placeholder = { Text("שם להקלטה (לא חובה)") },
            shape = RoundedCornerShape(12.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

// ---------- list ----------

@Composable
fun ListHeader(count: Int, totalBytes: Long, freeBytes: Long?) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            if (count == 0) "עדיין אין הקלטות" else "כל ההקלטות ($count)",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                formatSize(totalBytes) + (freeBytes?.let { " · ${formatSize(it)} פנויים" } ?: ""),
                fontSize = 14.sp,
                color = Navy.Mute,
            )
        }
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
            { TextButton(onClick = { onQuery("") }) { Text("נקה", color = Navy.Accent) } }
        } else null,
        shape = RoundedCornerShape(12.dp),
        colors = appFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

@Composable
fun RecordingRow(rec: Recording, onClick: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Navy.Surface)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Navy.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_play), contentDescription = null, colorFilter = ColorFilter.tint(Navy.Accent), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rec.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatDate(rec.dateMillis)} · ${formatSize(rec.sizeBytes)}", fontSize = 14.sp, color = Navy.Mute)
            if (rec.recovered) Text("שוחזרה אחרי קריסה", fontSize = 13.sp, color = Navy.Accent, fontWeight = FontWeight.SemiBold)
            rec.stopReason?.label?.let { Text(it, fontSize = 13.sp, color = Navy.Accent, fontWeight = FontWeight.SemiBold) }
            rec.noiseDb?.let {
                // Temporary diagnostic, for choosing the silence threshold.
                Text("רעש רקע: ${ltr("$it dB")}", fontSize = 12.sp, color = Navy.Mute.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(rec.durationMs), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy.Mute) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("שתף", fontSize = 17.sp) }, onClick = { menuOpen = false; onShare() })
                DropdownMenuItem(text = { Text("שנה שם", fontSize = 17.sp) }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("מחק", fontSize = 17.sp, color = Navy.Rec) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

// ---------- dialogs ----------

@Composable
fun RenameDialog(rec: Recording, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(rec.id) { mutableStateOf(rec.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי שם") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = appFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        },
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
                "Gmail מאפשר לצרף עד ${ltr("25 MB")}, ולכן ישלח את ההקלטה כקישור ל-Google Drive.\n\n" +
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
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Navy.Rec, contentColor = Color.White)) { Text("מחק") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

// ---------- formatting ----------

/** Keeps "26.1 MB" in the right order inside Hebrew text (left-to-right isolate). */
fun ltr(s: String) = "⁦$s⁩"

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return ltr(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s))
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return ltr(
        when {
            mb >= 1024 -> "%.1f GB".format(Locale.US, mb / 1024)
            mb >= 1 -> "%.1f MB".format(Locale.US, mb)
            else -> "%d KB".format(bytes / 1024)
        }
    )
}

fun formatDate(millis: Long): String = ltr(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis)))
