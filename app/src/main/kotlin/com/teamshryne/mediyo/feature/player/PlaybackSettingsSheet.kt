package com.teamshryne.mediyo.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.data.playback.PlaybackPrefs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

private fun formatRate(v: Float): String {
    val r = (v * 100).roundToInt() / 100f
    return if (r == r.roundToInt().toFloat()) "${r.roundToInt()}×" else "%.2f×".format(Locale.US, r)
}

/** Tolerance for matching slider-snapped values against preset literals. */
private fun Float.isNear(other: Float) = abs(this - other) < 0.001f

/** Pitch multiplier → semitone offset (12-TET): +12 st = octave up. */
private fun pitchToSemitones(pitch: Float): Int =
    (12f * log2(pitch.coerceAtLeast(0.01f))).roundToInt()

private fun formatSemitones(st: Int): String = when {
    st > 0 -> "+$st st"
    st < 0 -> "$st st"
    else -> "±0 st"
}

/** Short overflow-menu suffix, shown only when off-normal (e.g. "1.25× · +3 st"). */
fun playbackMenuLabel(s: PlaybackSettings): String? {
    if (s.isDefault) return null
    val parts = mutableListOf<String>()
    if (!s.speed.isNear(1f)) parts += formatRate(s.speed)
    if (abs(s.effectivePitch - 1f) >= 0.005f) {
        parts += formatRate(s.effectivePitch)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsSheet(
    settings: PlaybackSettings,
    onPreviewSpeed: (Float) -> Unit,
    onCommitSpeed: (Float) -> Unit,
    onPreviewPitch: (Float) -> Unit,
    onCommitPitch: (Float) -> Unit,
    onSetPreservePitch: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    // 0.05 granularity: speed 0.25–3.0 → 55 intervals; pitch 0.5–2.0 → 30 intervals.
    val speedSteps = remember { ((PlaybackPrefs.MAX_SPEED - PlaybackPrefs.MIN_SPEED) / 0.05f).roundToInt() - 1 }
    val pitchSteps = remember { ((PlaybackPrefs.MAX_PITCH - PlaybackPrefs.MIN_PITCH) / 0.05f).roundToInt() - 1 }
    val speedPresets = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
    val pitchPresets = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
    val pitchSt = pitchToSemitones(settings.effectivePitch)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Playback speed & pitch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.isDefault) "Normal playback" else "Custom rate — stays on until reset",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }

            // live status
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isDefault)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            formatRate(settings.speed),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (settings.isDefault) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Speed",
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (settings.isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.8f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            formatRate(settings.effectivePitch),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (settings.isDefault) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Pitch · ${formatSemitones(pitchSt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (settings.isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Speed ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    formatRate(settings.speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.speed,
                onValueChange = onPreviewSpeed,
                onValueChangeFinished = { onCommitSpeed(settings.speed) },
                valueRange = PlaybackPrefs.MIN_SPEED..PlaybackPrefs.MAX_SPEED,
                steps = speedSteps,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatRate(PlaybackPrefs.MIN_SPEED), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onCommitSpeed(1f) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Reset to 1×", style = MaterialTheme.typography.labelSmall)
                }
                Text(formatRate(PlaybackPrefs.MAX_SPEED), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in speedPresets.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (p in row) {
                            val selected = settings.speed.isNear(p)
                            val mod = Modifier.weight(1f)
                            if (selected) {
                                Button(
                                    onClick = { onCommitSpeed(p) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = mod,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text(formatRate(p)) }
                            } else {
                                FilledTonalButton(
                                    onClick = { onCommitSpeed(p) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = mod,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text(formatRate(p)) }
                            }
                        }
                        if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Pitch ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${formatRate(settings.effectivePitch)} · ${formatSemitones(pitchSt)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Preserve pitch", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (settings.preservePitch) "Speeding up keeps voices natural"
                        else "Pitch follows the slider below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.preservePitch, onCheckedChange = onSetPreservePitch)
            }
            Slider(
                value = settings.effectivePitch,
                onValueChange = onPreviewPitch,
                onValueChangeFinished = { onCommitPitch(settings.effectivePitch) },
                valueRange = PlaybackPrefs.MIN_PITCH..PlaybackPrefs.MAX_PITCH,
                steps = pitchSteps,
                enabled = !settings.preservePitch,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−12 st", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatSemitones(pitchSt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("+12 st", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!settings.preservePitch) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in pitchPresets.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (p in row) {
                                val selected = settings.effectivePitch.isNear(p)
                                val mod = Modifier.weight(1f)
                                if (selected) {
                                    Button(
                                        onClick = { onCommitPitch(p) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = mod,
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) { Text(formatRate(p)) }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onCommitPitch(p) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = mod,
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) { Text(formatRate(p)) }
                                }
                            }
                            if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            if (!settings.isDefault) {
                Button(
                    onClick = onReset,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset speed & pitch") }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Applies to songs, videos and podcasts, survives track changes and restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Keep the last line off the gesture bar.
            Spacer(Modifier.width(1.dp))
        }
    }
}
