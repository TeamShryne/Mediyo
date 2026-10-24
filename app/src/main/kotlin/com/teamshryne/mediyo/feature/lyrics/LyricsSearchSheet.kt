package com.teamshryne.mediyo.feature.lyrics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.domain.model.Track

/**
 * Search one provider by hand.
 *
 * The automatic lookup guesses the match from the song's metadata. When that
 * guess is wrong — a remix, a live version, a title the provider spells
 * differently — the user needs to say the words themselves, and choose which
 * of the five sources to ask. The result is pinned to the playing track's
 * cache, so it is still there the next time the song opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchSheet(
    track: Track,
    durationMs: Long?,
    vm: LyricsViewModel,
    onDismiss: () -> Unit,
    navBarInset: Dp = 0.dp
) {
    val search by vm.search.collectAsState()
    val order by vm.searchOrder.collectAsState(initial = LyricsSource.defaultOrder)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Seed from the playing song: the failed words are usually the words.
    LaunchedEffect(track, durationMs) { vm.openSearch(track, durationMs) }
    // The sheet must never collapse to an empty frame if anything clears the
    // search session while it is open: seed it again from its own track.
    LaunchedEffect(search == null) {
        if (search == null) vm.openSearch(track, durationMs)
    }
    // A hit already replaced [LyricsUiState]; close onto it. A stale
    // applied=true from the previous open must not close the sheet before the
    // seed above has run, so only dismiss after this session actually searched.
    var sawRunning by remember { mutableStateOf(false) }
    LaunchedEffect(search?.running) {
        if (search?.running == true) sawRunning = true
    }
    LaunchedEffect(search?.applied) {
        if (search?.applied == true && sawRunning) {
            onDismiss()
        }
    }

    // A null session is a frame between clearing and re-seeding; render the
    // sheet's own track rather than letting it vanish.
    val s = search ?: LyricsSearchState(
        source = LyricsSource.defaultOrder.first(),
        title = track.title,
        artist = track.artists.firstOrNull() ?: track.album.orEmpty()
    )

    ModalBottomSheet(
        onDismissRequest = { vm.closeSearch(); onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        // Matches the nav-bar strip SheetStatusBar paints while this is open.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = navBarInset + 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Search lyrics", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pick one source and adjust the words if the match was wrong.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Source picker ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                order.forEach { source ->
                    FilterChip(
                        selected = source == s.source,
                        onClick = { vm.setSearchSource(source) },
                        label = { Text(source.label) }
                    )
                }
            }
            Text(
                s.source.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── The words to search for ────────────────────────────────────
            OutlinedTextField(
                value = s.title,
                onValueChange = vm::setSearchTitle,
                label = { Text("Song") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.artist,
                onValueChange = vm::setSearchArtist,
                label = { Text("Artist") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }),
                modifier = Modifier.fillMaxWidth()
            )

            s.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = vm::runSearch,
                enabled = !s.running && s.title.isNotBlank() && s.artist.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (s.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(if (s.running) "Searching…" else "Search ${s.source.label}")
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}
