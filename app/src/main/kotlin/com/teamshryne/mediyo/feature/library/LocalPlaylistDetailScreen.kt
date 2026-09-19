package com.teamshryne.mediyo.feature.library

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
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistDetailVm @Inject constructor(
    private val repo: PlaylistRepository,
    private val likeRepo: LikeRepository
) : ViewModel() {
    var title by mutableStateOf("")
    var playlistId by mutableStateOf("")

    fun load(id: String) {
        playlistId = id
        viewModelScope.launch {
            val p = repo.getPlaylist(id)
            title = p?.title ?: ""
        }
    }
    fun flowEntries(id: String) = repo.flowEntries(id)
    fun flowPlaylist(id: String) = repo.flowPlaylist(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun isLiked(videoId: String): Boolean = likeRepo.isLiked(videoId)
    fun toggleLike(track: Track) { viewModelScope.launch { likeRepo.toggle(track) } }
    fun remove(entryId: String) { viewModelScope.launch { repo.removeTrack(playlistId, entryId) } }
    fun reorder(from: Int, to: Int) { viewModelScope.launch { repo.reorder(playlistId, from, to) } }
    fun clear() { viewModelScope.launch { repo.clear(playlistId) } }
    fun delete(onDone: () -> Unit) { viewModelScope.launch { repo.delete(playlistId); onDone() } }
}

@Composable
fun LocalPlaylistDetailScreen(
    playlistId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: LocalPlaylistDetailVm = hiltViewModel()
) {
    LaunchedEffect(playlistId) { vm.load(playlistId) }
    val playlist by vm.flowPlaylist(playlistId).collectAsState(initial = null)
    val entries by vm.flowEntries(playlistId).collectAsState(initial = emptyList())
    val playingId by player?.state?.collectAsState()?.let { remember { it } } ?: remember { mutableStateOf(com.teamshryne.mediyo.feature.player.PlayerState()) }

    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    val title = playlist?.title ?: vm.title.ifEmpty { "Playlist" }
    val description = playlist?.description
    val tracks = remember(entries) { entries.map { it.toTrack() } }
    val heroThumb = remember(entries) { entries.firstOrNull()?.artworkUrl?.upscaledThumbUrl() }
    val listState = rememberLazyListState()

    fun playAll(shuffled: Boolean) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffled) tracks.shuffled() else tracks
        player?.playTracks(ordered, 0, PlayOrigin.LocalPlaylist(playlistId, title))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "local_hero") {
            CollectionHero(
                title = title,
                subtitle = description?.takeIf { it.isNotBlank() } ?: "Local playlist",
                thumbUrl = heroThumb,
                countText = "${entries.size} songs",
                fallbackIcon = Icons.Filled.PlaylistPlay,
                onBack = { nav?.popBackStack() },
                topEnd = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                onPlay = { playAll(false) },
                onShuffle = { playAll(true) }
            )
        }
        if (entries.isEmpty()) {
            item(key = "local_empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No songs yet", style = MaterialTheme.typography.titleMedium)
                        Text("Add songs from any track's ••• menu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(tracks, key = { idx, _ -> entries.getOrNull(idx)?.id ?: "track_$idx" }) { idx, t ->
                LocalTrackRow(
                    track = t,
                    isPlaying = playingId.videoId == t.videoId,
                    number = idx + 1,
                    showArtwork = true,
                    trailing = {
                        TrackOverflowIcon(onClick = {
                            menuTrack = t; menuEntryId = entries.getOrNull(idx)?.id
                        })
                    }
                ) {
                    player?.playTracks(tracks, idx, PlayOrigin.LocalPlaylist(playlistId, title))
                }
            }
        }
    }

    menuTrack?.let { t ->
        var liked by remember { mutableStateOf(false) }
        LaunchedEffect(t.videoId) { liked = t.videoId?.let { vm.isLiked(it) } ?: false }
        TrackMenuSheet(
            track = t, show = true, onDismiss = { menuTrack = null; menuEntryId = null },
            isLiked = liked,
            onLike = { vm.toggleLike(t) },
            onAddToPlaylist = { /* already in playlist, could add to another */ },
            onPlayNext = { player?.addNext(t) },
            onAddToQueue = { player?.addToQueue(t) },
            onShowArtist = { name, id ->
                menuScope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let { nav?.navigate("artist/$it") } }
            },
            onComments = { nav?.navigate("comments/${t.videoId}") },
            onRemove = { menuEntryId?.let { vm.remove(it) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete playlist?") },
            text = { Text("This will remove the playlist and all its entries. Cannot be undone.") },
            confirmButton = { Button(onClick = { vm.delete { nav?.popBackStack() } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

private fun LocalPlaylistEntryEntity.toTrack() = Track(
    videoId = trackVideoId, browseId = null, playlistId = null,
    title = title, artists = if (artist.isBlank()) emptyList() else artist.split(",").map { it.trim() },
    album = album, artworkUrl = artworkUrl.upscaledThumbUrl(), duration = duration, category = category
)
