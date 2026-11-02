package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.data.update.UpdateChannel

/**
 * The update channel picker.
 *
 * The two tracks are deliberately not two equal buttons. Stable is the
 * default and stays the obvious, one-tap choice; Nightly is something a
 * person asks for. So the controls differ: a radio for the default, a
 * switch for the opt-in, and the switch asks before it turns on.
 *
 * Two rules hold the rest of the design together:
 *
 * - turning nightly on takes a second, deliberate tap, because it changes
 *   what arrives on the phone from then on;
 * - turning it off takes none, because going back to the safer default
 *   must never be the harder of the two.
 */
@Composable
fun ChannelPicker(
    channel: UpdateChannel,
    onSelect: (UpdateChannel) -> Unit,
    onRequestNightly: () -> Unit,
    onLeaveNightly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StableCard(
            selected = channel == UpdateChannel.STABLE,
            onClick = { onSelect(UpdateChannel.STABLE) },
        )
        NightlyCard(
            enabled = channel == UpdateChannel.NIGHTLY,
            onToggle = {
                if (channel == UpdateChannel.NIGHTLY) onLeaveNightly() else onRequestNightly()
            },
        )
    }
}

/**
 * The default track.
 *
 * It says "Recommended" whichever track is on: it is the one a person who
 * has not thought about this would want, and labelling it only while it is
 * selected would make it look like a fallback.
 */
@Composable
private fun StableCard(selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Stable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        text = "Recommended",
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text(
                    "Signed releases, published when they are ready. Every release is " +
                        "tested before it goes out, so this is the app that will not " +
                        "surprise you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The opt-in track.
 *
 * Off, it is one line: what it gives you and that you can have it. On, it
 * says what is different about it, because that is the moment the words
 * matter -- and it stays short enough to be read.
 */
@Composable
private fun NightlyCard(enabled: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = if (enabled) 1.5.dp else 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            // Only the switch turns the channel off. The card is a way in,
            // not a way out: an accidental tap on the text should not
            // quietly move somebody back to a channel they chose to leave.
            Modifier
                .clickable(enabled = !enabled, onClick = onToggle)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nightly",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        text = "Early access",
                        content = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        container = if (enabled) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text(
                    if (enabled) {
                        "A fresh build every day, cut from main. New features and fixes " +
                            "reach you days before a release does. The builds are tested, but " +
                            "not as thoroughly as a release, so the occasional rough edge may " +
                            "slip through. Switch back to Stable whenever you like."
                    } else {
                        "Get new features and fixes days before anyone else. Daily builds, " +
                            "cut from main. Opt in to follow this channel."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled) {
                    Text(
                        "One build at a time, and only when you say yes to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "Nightly builds" },
            )
        }
    }
}

/** A small tag that names what a row is. */
@Composable
private fun Badge(text: String, content: Color, container: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * Shown while the stored choice is still being read.
 *
 * A guess would be wrong for half the people who use this screen, and a
 * wrong selection that changes under the finger is worse than a moment of
 * waiting.
 */
@Composable
fun ChannelPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Reading your update channel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The last word before the nightly channel is switched on.
 *
 * It says plainly what nightly is and what it costs, in a few sentences,
 * and the way out is one tap. A person who reads this and does not want
 * it has lost nothing but a few seconds.
 */
@Composable
fun NightlyOptInDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
        },
        title = { Text("Turn on nightly builds?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Nightly is a build of the app cut from main and published every day. " +
                        "New features and fixes reach you days before a release, one build " +
                        "at a time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "The builds are tested, but not as thoroughly as a release: the " +
                        "occasional rough edge may slip through. Nothing is installed " +
                        "without you saying yes, and you can go back to Stable any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Turn on") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
