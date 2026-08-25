package com.teamshryne.mediyo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackMenuSheet
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
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

    Column(Modifier.fillMaxSize()) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav?.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(playlist?.title ?: vm.title.ifEmpty { "Playlist" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, null) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${entries.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = {
                    val tracks = entries.map { it.toTrack() }
                    if (tracks.isNotEmpty()) player?.playTracks(tracks, 0, PlayOrigin.LocalPlaylist(playlistId, playlist?.title ?: "Playlist"))
                }, shape = CircleShape, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.PlayArrow, null) }
                FilledTonalIconButton(onClick = {
                    val tracks = entries.map { it.toTrack() }.shuffled()
                    if (tracks.isNotEmpty()) player?.playTracks(tracks, 0, PlayOrigin.LocalPlaylist(playlistId, playlist?.title ?: ""))
                }, shape = CircleShape, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Shuffle, null) }
            }
        }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No songs yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add songs from any track's ••• menu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(entries, key = { _, e -> e.id }) { idx, e ->
                    val isPlaying = playingId.videoId == e.trackVideoId
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val tracks = entries.map { it.toTrack() }
                            player?.playTracks(tracks, idx, PlayOrigin.LocalPlaylist(playlistId, playlist?.title ?: ""))
                        }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                        AsyncImage(model = e.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(e.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (isPlaying) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        TrackOverflowIcon(onClick = {
                            menuTrack = e.toTrack(); menuEntryId = e.id
                        })
                    }
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
    album = album, artworkUrl = artworkUrl, duration = duration, category = category
)
