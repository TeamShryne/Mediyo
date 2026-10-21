package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.data.update.ChangelogEntry

/**
 * What changed between the build the user is running and the newest one.
 *
 * The notes are read, not skimmed: they are the words the person who made
 * the change wrote for the person using the app, and the screen gives them
 * room.
 */
@Composable
fun WhatsNewCard(
    state: UpdateViewModel.ChangelogState,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "What's new",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            when (state) {
                is UpdateViewModel.ChangelogState.Idle -> {
                    Text(
                        "What changed between the version you are running and the newest one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (BuildConfig.DEBUG) {
                        // A debug build never loads the notes, so a button
                        // that loads them would be a lie.
                        Text(
                            "The release notes are not loaded in a debug build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
                            Text("Show what changed")
                        }
                    }
                }

                is UpdateViewModel.ChangelogState.Loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                }

                is UpdateViewModel.ChangelogState.Failed -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onReload) { Text("Try again") }
                }

                is UpdateViewModel.ChangelogState.Ready -> {
                    val entries = state.entries
                    if (entries.isEmpty()) {
                        Text(
                            "Nothing new since v${BuildConfig.VERSION_NAME}. You are up to date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Since v${BuildConfig.VERSION_NAME}:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        entries.forEach { entry -> EntryNotes(entry) }
                        TextButton(onClick = onReload) { Text("Refresh") }
                    }
                }
            }
        }
    }
}

/** One release, and the notes it shipped. */
@Composable
private fun EntryNotes(entry: ChangelogEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "v${entry.version}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        entry.notes.forEach { note ->
            Text(
                noteText(note.text),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The notes are written in a small subset of markdown: a bold lead, and
 * italics for the name of a screen or a setting. That is the style they are
 * written in, and it is what makes them read like notes rather than like a
 * list of file names.
 */
fun noteText(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = rest.indexOf("**", 2)
                if (end < 0) {
                    append(rest)
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(rest.substring(2, end))
                    }
                    i += end + 2
                }
            }

            rest.startsWith("*") -> {
                val end = rest.indexOf('*', 1)
                if (end < 0) {
                    append(rest)
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(rest.substring(1, end))
                    }
                    i += end + 1
                }
            }

            else -> {
                append(rest[0])
                i += 1
            }
        }
    }
}
