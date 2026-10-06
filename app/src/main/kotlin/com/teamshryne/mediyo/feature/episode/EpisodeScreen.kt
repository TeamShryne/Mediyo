package com.teamshryne.mediyo.feature.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ActionPill
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.mediyo.FfiEpisodePage
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class EpisodeVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var page by mutableStateOf<FfiEpisodePage?>(null)
    fun load(id: String) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                page = bridge.episode(id)
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }

    fun trackOf(p: FfiEpisodePage): Track = Track(
        videoId = p.videoId,
        browseId = null,
        playlistId = null,
        title = p.title,
        artists = listOfNotNull(p.showName?.takeIf { it.isNotBlank() }),
        album = p.showName,
        artworkUrl = p.artworkUrl,
        duration = p.duration,
        category = "Episode"
    )

    /** Play the episode, starting at [startMs] (chapter jumps). */
    fun playFrom(track: Track, startMs: Long, showId: String?, player: com.teamshryne.mediyo.feature.player.PlayerViewModel) {
        val cur = player.state.value
        if (cur.videoId == track.videoId && cur.title.isNotEmpty()) {
            player.seekToMs(startMs)
            return
        }
        player.playTrack(track, PlayOrigin.Podcast(showId ?: track.videoId.orEmpty()))
        if (startMs <= 0) return
        viewModelScope.launch {
            val ready = try {
                kotlinx.coroutines.withTimeout(15000) {
                    player.state.first { it.videoId == track.videoId && it.durationMs > 0 }
                }
                true
            } catch (_: Throwable) { false }
            if (ready) player.seekToMs(startMs)
        }
    }
}

private fun fmtChapter(s: Long): String =
    if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    episodeId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: EpisodeVm = hiltViewModel()
) {
    LaunchedEffect(episodeId) { vm.load(episodeId) }
    val p = vm.page

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null || p == null -> ErrorState(vm.error ?: "Failed to load") { vm.load(episodeId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val track = remember(p) { vm.trackOf(p) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val listSearch = com.teamshryne.mediyo.core.design.rememberListSearchUiState()
            val sq = listSearch.query.trim()
            val matchChapters = remember(p.chapters, sq) {
                if (sq.isBlank()) p.chapters
                else p.chapters.filter { c ->
                    com.teamshryne.mediyo.core.design.matchesQuery(sq, c.text)
                }
            }
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)) {
                if (sq.isBlank()) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth()) {
                        com.teamshryne.mediyo.core.design.HeroSearchTopRow(
                            state = listSearch,
                            placeholder = "Search chapters",
                            onBack = { nav?.popBackStack() }
                        )
                        AsyncImage(
                            model = p.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        p.showName?.takeIf { it.isNotBlank() }?.let { show ->
                            Text(
                                show,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp)
                                    .clickable(enabled = p.showId != null) {
                                        p.showId?.let { nav?.navigate("podcast/$it") }
                                    }
                            )
                        }
                        Text(
                            listOfNotNull(
                                p.date?.takeIf { it.isNotBlank() },
                                p.stats?.takeIf { it.isNotBlank() },
                                p.duration?.takeIf { it.isNotBlank() }
                            ).joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ActionPill(
                                text = if (playingId != null && playingId == p.videoId) "Playing" else "Play",
                                icon = Icons.Filled.PlayArrow,
                                filled = true
                            ) {
                                if (playingId != null && playingId == p.videoId) player?.toggle()
                                else player?.let { vm.playFrom(track, 0, p.showId, it) }
                            }
                        }
                    }
                }
                }
                stickyHeader(key = "search_header") {
                    com.teamshryne.mediyo.core.design.DetailSearchHeader(
                        listState = listState,
                        title = p.title,
                        search = listSearch,
                        searching = false,
                        resultCount = matchChapters.size,
                        onBack = { nav?.popBackStack() },
                        placeholder = "Search chapters"
                    )
                }
                if (sq.isBlank()) {
                if (p.chapters.isNotEmpty()) {
                    item(key = "chapters_header") {
                        SectionHeader("Chapters", Modifier.padding(top = 22.dp))
                    }
                    items(p.chapters.size, key = { "ch_$it" }) { i ->
                        val c = p.chapters[i]
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { player?.let { vm.playFrom(track, c.startSeconds * 1000, p.showId, it) } }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                fmtChapter(c.startSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(56.dp)
                            )
                            Text(
                                c.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                p.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    item(key = "episode_desc") {
                        Column(Modifier.padding(top = 8.dp)) {
                            SectionHeader("About this episode", Modifier.padding(top = 14.dp))
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
                            )
                        }
                    }
                }
                } else {
                items(matchChapters.size, key = { "match_$it" }) { i ->
                    val c = matchChapters[i]
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { player?.let { vm.playFrom(track, c.startSeconds * 1000, p.showId, it) } }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fmtChapter(c.startSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(56.dp)
                        )
                        Text(
                            c.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (matchChapters.isEmpty()) {
                    item(key = "no_match") {
                        com.teamshryne.mediyo.core.design.EmptyState(
                            "No chapters match \"$sq\"",
                            "Try different keywords"
                        )
                    }
                }
                }
            }
            }
        }
    }
}
