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
fun RecordingRow(rec: Recording, onClick: () -> Unit) {
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
            }
            Spacer(Modifier.width(10.dp))
            Text(formatDuration(rec.durationMs), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
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
    return if (mb >= 1) "%.1f MB".format(Locale.US, mb) else "%d KB".format(bytes / 1024)
}

fun formatDate(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis))
