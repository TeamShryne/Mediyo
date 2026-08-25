package com.teamshryne.mediyo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LikedVm @Inject constructor(private val repo: LikeRepository) : ViewModel() {
    val liked = repo.flowLiked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun remove(videoId: String) { viewModelScope.launch { repo.unlike(videoId) } }
    fun clearAll() { viewModelScope.launch { repo.clearAll() } }
}

@Composable
fun LikedScreen(
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: LikedVm = hiltViewModel()
) {
    val liked by vm.liked.collectAsState()
    val playingId = player?.state?.collectAsState()?.value?.videoId
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav?.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Liked songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (liked.isNotEmpty()) TextButton(onClick = { showClearConfirm = true }) { Text("Clear") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${liked.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = {
                    val tracks = liked.map { it.toTrack() }
                    if (tracks.isNotEmpty()) player?.playTracks(tracks, 0, PlayOrigin.Liked(tracks.size))
                }, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Filled.PlayArrow, null) }
                FilledTonalIconButton(onClick = {
                    val tracks = liked.map { it.toTrack() }.shuffled()
                    if (tracks.isNotEmpty()) player?.playTracks(tracks, 0, PlayOrigin.Liked(tracks.size))
                }, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Filled.Shuffle, null) }
            }
        }
        HorizontalDivider()
        if (liked.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.FavoriteBorder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No liked songs yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap ♥ on any track or in the player to like it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(liked, key = { _, e -> e.videoId }) { idx, e ->
                    val isPlaying = playingId == e.videoId
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val tracks = liked.map { it.toTrack() }
                            player?.playTracks(tracks, idx, PlayOrigin.Liked(tracks.size))
                        }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = e.artworkUrl.upscaledThumbUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(e.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (isPlaying) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        TrackOverflowIcon(onClick = { menuTrack = e.toTrack() })
                    }
                }
            }
        }
    }

    menuTrack?.let { t ->
        TrackMenuSheet(
            track = t, show = true, onDismiss = { menuTrack = null },
            isLiked = true,
            onLike = { vm.remove(t.videoId ?: ""); menuTrack = null },
            onAddToPlaylist = { showAddSheet = t; menuTrack = null },
            onPlayNext = { player?.addNext(t) },
            onAddToQueue = { player?.addToQueue(t) },
            onComments = { nav?.navigate("comments/${t.videoId}") }
        )
    }
    showAddSheet?.let { t ->
        AddToPlaylistSheet(track = t, onDismiss = { showAddSheet = null })
    }
    if (showClearConfirm) {
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear liked songs?") }, confirmButton = { Button(onClick = { vm.clearAll(); showClearConfirm = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } })
    }
}

private fun LikedTrackEntity.toTrack() = Track(videoId = videoId, title = title, artists = if (artist.isBlank()) emptyList() else artist.split(",").map { it.trim() }, artworkUrl = artworkUrl.upscaledThumbUrl(), album = album, duration = duration, category = category)
