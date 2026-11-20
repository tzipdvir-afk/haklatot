package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tzipdvir.haklatot.data.Recording
import java.util.Locale

val SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

/** Full playback screen. Time runs left-to-right, like in common media players. */
@Composable
fun PlaybackScreen(
    rec: Recording,
    waveform: ByteArray?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    // While the finger drags on the waveform, show the dragged position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1)
    val shownPos = dragFraction?.let { (it * total).toLong() } ?: positionMs

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("→ חזרה לרשימה", fontSize = 17.sp) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onShare) { Text("שתף") }
            TextButton(onClick = onRename) { Text("שנה שם") }
            TextButton(onClick = onDelete) { Text("מחק", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
        Text(rec.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatDate(rec.dateMillis)}  ·  ${formatDuration(durationMs)}  ·  ${formatSize(rec.sizeBytes)}",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(28.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                    if (waveform == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Text("מחשב את צורת הקול…", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Waveform(
                            data = waveform,
                            progress = (shownPos.toFloat() / total).coerceIn(0f, 1f),
                            onDrag = { dragFraction = it },
                            onDragEnd = { dragFraction?.let { onSeek((it * total).toLong()) }; dragFraction = null },
                            onTap = { onSeek((it * total).toLong()) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(shownPos), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(formatDuration(durationMs), fontSize = 16.sp)
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onSkip(-15_000) }) { Text("15 שנ׳ ↶", fontSize = 16.sp) }
                    FilledIconButton(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        modifier = Modifier.padding(horizontal = 20.dp).size(80.dp),
                    ) { PlayPauseIcon(isPlaying, Color.White) }
                    OutlinedButton(onClick = { onSkip(15_000) }) { Text("↷ 15 שנ׳", fontSize = 16.sp) }
                }
                Spacer(Modifier.height(28.dp))
                Text("מהירות", fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPEEDS.forEach { s ->
                        FilterChip(
                            selected = s == speed,
                            onClick = { onSpeed(s) },
                            label = { Text(formatSpeed(s), fontSize = 16.sp) },
                        )
                    }
                }
            }
        }
    }
}

fun formatSpeed(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else String.format(Locale.US, "%sx", s.toString().trimEnd('0'))

/** Loudness bars of the whole recording; tap or drag to jump. Played part is colored. */
@Composable
private fun Waveform(
    data: ByteArray,
    progress: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Float) -> Unit,
) {
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    val cursor = MaterialTheme.colorScheme.secondary
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures { onTap((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragStart = { onDrag((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, _ -> onDrag((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val bars = 90
        val step = size.width / bars
        val barW = step * 0.62f
        val minH = 2.dp.toPx()
        for (b in 0 until bars) {
            // Loudest value in this slice of the recording.
            var peak = 0
            if (data.isNotEmpty()) {
                val from = (b.toLong() * data.size / bars).toInt()
                val to = (((b + 1).toLong() * data.size / bars).toInt()).coerceAtLeast(from + 1).coerceAtMost(data.size)
                for (i in from until to) {
                    val v = data[i].toInt() and 0xFF
                    if (v > peak) peak = v
                }
            }
            val h = (peak / 255f * size.height).coerceAtLeast(minH)
            val x = b * step + (step - barW) / 2
            drawRoundRect(
                color = if ((b + 0.5f) / bars <= progress) played else rest,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
        val cx = progress * size.width
        drawRect(cursor, topLeft = Offset(cx - 1.5.dp.toPx(), 0f), size = Size(3.dp.toPx(), size.height))
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, color: Color) {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        if (playing) {
            val barW = w * 0.28f
            drawRect(color, Offset(w * 0.12f, 0f), Size(barW, h))
            drawRect(color, Offset(w * 0.6f, 0f), Size(barW, h))
        } else {
            val p = Path().apply {
                moveTo(w * 0.18f, 0f)
                lineTo(w, h / 2)
                lineTo(w * 0.18f, h)
                close()
            }
            drawPath(p, color)
        }
    }
}
