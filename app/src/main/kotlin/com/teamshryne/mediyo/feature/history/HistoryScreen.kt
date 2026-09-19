package com.teamshryne.mediyo.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.CollectionHero
import com.teamshryne.mediyo.core.design.LocalTrackRow
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackMenuSheet
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class HistoryVm @Inject constructor(
    private val repo: HistoryRepository,
    private val likeRepo: LikeRepository
) : ViewModel() {
    val history = repo.flowHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun remove(videoId: String) { viewModelScope.launch { repo.remove(videoId) } }
    fun clearAll() { viewModelScope.launch { repo.clearAll() } }
    suspend fun isLiked(videoId: String) = likeRepo.isLiked(videoId)
    fun toggleLike(track: Track) { viewModelScope.launch { likeRepo.toggle(track) } }
}

@Composable
fun HistoryScreen(
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: HistoryVm = hiltViewModel()
) {
    val history by vm.history.collectAsState()
    val playingId = player?.state?.collectAsState()?.value?.videoId
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }
    var showClear by remember { mutableStateOf(false) }
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    val grouped = remember(history) { groupByDate(history) }
    val heroThumb = remember(history) { history.firstOrNull()?.artworkUrl?.upscaledThumbUrl() }
    val allTracks = remember(history) { history.map { it.toTrack() } }
    val listState = rememberLazyListState()

    fun playAll(shuffled: Boolean) {
        if (allTracks.isEmpty()) return
        val ordered = if (shuffled) allTracks.shuffled() else allTracks
        player?.playTracks(ordered, 0, PlayOrigin.History("All"))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "history_hero") {
            CollectionHero(
                title = "History",
                subtitle = "Auto playlist",
                thumbUrl = heroThumb,
                countText = "${history.size} plays",
                fallbackIcon = Icons.Filled.History,
                onBack = { nav?.popBackStack() },
                topEnd = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClear = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear history",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                onPlay = { playAll(false) },
                onShuffle = { playAll(true) }
            )
        }
        if (history.isEmpty()) {
            item(key = "history_empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No history yet", style = MaterialTheme.typography.titleMedium)
                        Text("Songs you play will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            grouped.forEach { (label, items) ->
                item(key = "header_$label") {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                itemsIndexed(items, key = { _, e -> e.videoId + e.lastPlayedAt }) { _, e ->
                    val track = remember(e.videoId, e.lastPlayedAt) { e.toTrack() }
                    LocalTrackRow(
                        track = track,
                        isPlaying = playingId == e.videoId,
                        showArtwork = true,
                        trailing = { TrackOverflowIcon(onClick = { menuTrack = track }) }
                    ) {
                        val sectionTracks = items.map { it.toTrack() }
                        val pos = items.indexOf(e).coerceAtLeast(0)
                        player?.playTracks(sectionTracks, pos, PlayOrigin.History(label))
                    }
                }
            }
        }
    }

    menuTrack?.let { t ->
        var liked by remember { mutableStateOf(false) }
        LaunchedEffect(t.videoId) { liked = t.videoId?.let { vm.isLiked(it) } ?: false }
        TrackMenuSheet(
            track = t, show = true, onDismiss = { menuTrack = null },
            isLiked = liked,
            onLike = { vm.toggleLike(t) },
            onAddToPlaylist = { showAddSheet = t; menuTrack = null },
            onPlayNext = { player?.addNext(t) },
            onAddToQueue = { player?.addToQueue(t) },
            onShowArtist = { name, id ->
                menuScope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let { nav?.navigate("artist/$it") } }
            },
            onComments = { nav?.navigate("comments/${t.videoId}") },
            onRemove = { vm.remove(t.videoId ?: ""); menuTrack = null }
        )
    }
    showAddSheet?.let { t -> AddToPlaylistSheet(track = t, onDismiss = { showAddSheet = null }) }
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("Clear history?") }, confirmButton = { Button(onClick = { vm.clearAll(); showClear = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } })
}

private fun HistoryEntryEntity.toTrack() = Track(videoId = videoId, title = title, artists = if (artist.isBlank()) emptyList() else artist.split(",").map { it.trim() }, artworkUrl = artworkUrl.upscaledThumbUrl(), album = album, duration = duration, category = category)

private fun groupByDate(list: List<HistoryEntryEntity>): List<Pair<String, List<HistoryEntryEntity>>> {
    val now = Calendar.getInstance()
    val fmt = SimpleDateFormat("MMM dd", Locale.getDefault())
    val groups = linkedMapOf<String, MutableList<HistoryEntryEntity>>()
    for (e in list) {
        val cal = Calendar.getInstance().apply { timeInMillis = e.lastPlayedAt }
        val label = when {
            isSameDay(cal, now) -> "Today"
            isYesterday(cal, now) -> "Yesterday"
            isSameWeek(cal, now) -> "This week"
            else -> fmt.format(Date(e.lastPlayedAt))
        }
        groups.getOrPut(label) { mutableListOf() }.add(e)
    }
    return groups.toList()
}
private fun isSameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR)==b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)
private fun isYesterday(a: Calendar, now: Calendar): Boolean {
    val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(a, y)
}
private fun isSameWeek(a: Calendar, b: Calendar) = a.get(Calendar.WEEK_OF_YEAR)==b.get(Calendar.WEEK_OF_YEAR) && a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
