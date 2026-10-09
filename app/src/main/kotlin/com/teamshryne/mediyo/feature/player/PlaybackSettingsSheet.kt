package com.teamshryne.mediyo.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

/** Tolerance for matching slider-snapped values against literals. */
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
    // skipPartiallyExpanded: content height changes (reset row appearing,
    // pitch section enabling) must not yank the sheet back to half-height.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 0.05 granularity: speed 0.25–3.0 → 55 intervals; pitch 0.5–2.0 → 30 intervals.
    val speedSteps = remember { ((PlaybackPrefs.MAX_SPEED - PlaybackPrefs.MIN_SPEED) / 0.05f).roundToInt() - 1 }
    val pitchSteps = remember { ((PlaybackPrefs.MAX_PITCH - PlaybackPrefs.MIN_PITCH) / 0.05f).roundToInt() - 1 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Playback speed & pitch",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${formatRate(settings.effectivePitch)} · ${formatSemitones(pitchToSemitones(settings.effectivePitch))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Preserve pitch",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = settings.preservePitch, onCheckedChange = onSetPreservePitch)
            }

            if (!settings.isDefault) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
