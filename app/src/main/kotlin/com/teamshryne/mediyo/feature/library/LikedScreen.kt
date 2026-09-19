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
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    val tracks = remember(liked) { liked.map { it.toTrack() } }
    val heroThumb = remember(liked) { liked.firstOrNull()?.artworkUrl?.upscaledThumbUrl() }
    val listState = rememberLazyListState()

    fun playAll(shuffled: Boolean) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffled) tracks.shuffled() else tracks
        player?.playTracks(ordered, 0, PlayOrigin.Liked(tracks.size))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "liked_hero") {
            CollectionHero(
                title = "Liked songs",
                subtitle = "Auto playlist",
                thumbUrl = heroThumb,
                countText = "${liked.size} songs",
                fallbackIcon = Icons.Filled.Favorite,
                onBack = { nav?.popBackStack() },
                topEnd = {
                    if (liked.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear liked songs",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                onPlay = { playAll(false) },
                onShuffle = { playAll(true) }
            )
        }
        if (liked.isEmpty()) {
            item(key = "liked_empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.FavoriteBorder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No liked songs yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap ♥ on any track or in the player to like it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(tracks, key = { _, t -> t.videoId ?: t.title }) { idx, t ->
                LocalTrackRow(
                    track = t,
                    isPlaying = playingId != null && playingId == t.videoId,
                    number = idx + 1,
                    showArtwork = true,
                    trailing = { TrackOverflowIcon(onClick = { menuTrack = t }) }
                ) {
                    player?.playTracks(tracks, idx, PlayOrigin.Liked(tracks.size))
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
            onShowArtist = { name, id ->
                menuScope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let { nav?.navigate("artist/$it") } }
            },
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
