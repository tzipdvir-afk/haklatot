package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple player shown at the bottom of the screen.
 * The time bar runs left-to-right like in common media players.
 */
@Composable
fun PlayerBar(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // While the finger drags the slider, show the dragged position instead of the live one.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1)
    val shownPos = dragging?.let { (it * total).toLong() } ?: positionMs

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("סגור") }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Slider(
                        value = (shownPos.toFloat() / total).coerceIn(0f, 1f),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { onSeek((it * total).toLong()) }
                            dragging = null
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(shownPos), fontSize = 13.sp)
                        Text(formatDuration(durationMs), fontSize = 13.sp)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { onSkip(-15_000) }) { Text("15 שנ׳ ↶", fontSize = 15.sp) }
                        FilledIconButton(
                            onClick = onPlayPause,
                            shape = CircleShape,
                            modifier = Modifier.padding(horizontal = 18.dp).size(64.dp),
                        ) {
                            PlayPauseIcon(isPlaying, Color.White)
                        }
                        OutlinedButton(onClick = { onSkip(15_000) }) { Text("↷ 15 שנ׳", fontSize = 15.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseIcon(playing: Boolean, color: Color) {
    Canvas(Modifier.size(26.dp)) {
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
