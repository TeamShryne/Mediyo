package com.teamshryne.mediyo.feature.sleeptimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.data.sleeptimer.SleepMode
import com.teamshryne.mediyo.data.sleeptimer.SleepTimerState

private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onSetTimer: (Long) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onSetEndOfQueue: () -> Unit,
    onCancel: () -> Unit,
    onAddFive: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("30") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when (state.mode) {
                            SleepMode.OFF -> "Stop playback automatically"
                            SleepMode.TIMER -> "Playing until timer ends"
                            SleepMode.END_OF_TRACK -> "Stops after this track"
                            SleepMode.END_OF_QUEUE -> "Stops after queue ends"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }

            // Active banner
            if (state.isActive) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                when (state.mode) {
                                    SleepMode.TIMER -> Icons.Filled.Timer
                                    SleepMode.END_OF_TRACK -> Icons.Filled.MusicNote
                                    SleepMode.END_OF_QUEUE -> Icons.Filled.QueueMusic
                                    else -> Icons.Filled.Bedtime
                                }, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                when (state.mode) {
                                    SleepMode.TIMER -> formatRemaining(state.remainingMs)
                                    SleepMode.END_OF_TRACK -> "After this track"
                                    SleepMode.END_OF_QUEUE -> "After queue"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.weight(1f))
                            if (state.mode == SleepMode.TIMER) {
                                LinearProgressIndicator(
                                    progress = { 1f - state.progress },
                                    modifier = Modifier.width(48.dp).height(4.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            }
                        }
                        if (state.mode == SleepMode.TIMER) {
                            Text(
                                "Radio paused • ${formatRemaining(state.remainingMs)} left • fades out",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                "Radio paused • will pause when ${if (state.mode == SleepMode.END_OF_TRACK) "track ends" else "queue ends"} • fades out",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.mode == SleepMode.TIMER) {
                                OutlinedButton(onClick = onAddFive, shape = CircleShape) { Text("+5 min") }
                            }
                            Button(onClick = onCancel, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                Icon(Icons.Filled.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Cancel timer")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Presets
            Text("Timer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val presets = listOf(5, 10, 15, 30, 45, 60)
            // 3 columns
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in presets.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (m in row) {
                            val selected = state.mode == SleepMode.TIMER && state.totalMs == m * 60 * 1000L
                            val mod = Modifier.weight(1f)
                            if (selected) {
                                Button(onClick = { onSetTimer(m * 60 * 1000L) }, shape = RoundedCornerShape(12.dp), modifier = mod, contentPadding = PaddingValues(vertical = 10.dp)) {
                                    Icon(Icons.Filled.Check, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("${m}m")
                                }
                            } else {
                                FilledTonalButton(onClick = { onSetTimer(m * 60 * 1000L) }, shape = RoundedCornerShape(12.dp), modifier = mod, contentPadding = PaddingValues(vertical = 10.dp)) {
                                    Text("${m}m")
                                }
                            }
                        }
                        // fill if incomplete row
                        if (row.size < 3) {
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Custom row
            if (!showCustom) {
                OutlinedButton(onClick = { showCustom = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Custom minutes")
                }
            } else {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) customMinutes = it },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val v = customMinutes.toIntOrNull() ?: 0
                            if (v in 1..180) onSetTimer(v * 60 * 1000L)
                            showCustom = false
                        }, shape = CircleShape) { Text("Set") }
                        TextButton(onClick = { showCustom = false }) { Text("Cancel") }
                    }
                }
                Text("1–180 minutes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text("Or stop after", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val isEOT = state.mode == SleepMode.END_OF_TRACK
                if (isEOT) {
                    Button(onClick = onSetEndOfTrack, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("End of track")
                    }
                } else {
                    FilledTonalButton(onClick = onSetEndOfTrack, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("End of track")
                    }
                }
                val isEOQ = state.mode == SleepMode.END_OF_QUEUE
                if (isEOQ) {
                    Button(onClick = onSetEndOfQueue, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.QueueMusic, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("End of queue")
                    }
                } else {
                    FilledTonalButton(onClick = onSetEndOfQueue, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.QueueMusic, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("End of queue")
                    }
                }
            }
            Text(
                "Radio generation pauses while timer is active. Playback fades out over ~8s then pauses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
