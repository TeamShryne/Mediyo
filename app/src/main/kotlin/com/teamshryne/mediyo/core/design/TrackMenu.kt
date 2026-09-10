package com.teamshryne.mediyo.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOverflowIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    show: Boolean,
    onDismiss: () -> Unit,
    isLiked: Boolean = false,
    onLike: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onGoToAlbum: (() -> Unit)? = null,
    onShowArtist: ((name: String, id: String?) -> Unit)? = null,
    onOpenChannel: (() -> Unit)? = null,
    onComments: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onLyricsSettings: (() -> Unit)? = null
) {
    if (!show) return
    // One entry point for artists; multi-artist tracks swap the sheet
    // to a picker listing every artist (single artist opens directly).
    val artistEntries = remember(track) {
        track.artists.mapIndexedNotNull { i, n ->
            n.takeIf { it.isNotBlank() }?.let { name ->
                name to track.artistIds.getOrNull(i)?.takeIf { it.isNotBlank() }
            }
        }
    }
    var showArtists by remember(track) { mutableStateOf(false) }
    BackHandler(enabled = showArtists) { showArtists = false }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showArtists && artistEntries.size > 1) {
                // ── Artist picker ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { showArtists = false }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Artists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                artistEntries.forEach { (name, id) ->
                    MenuItem(icon = Icons.Filled.Person, label = name, onClick = { onDismiss(); onShowArtist?.invoke(name, id) })
                }
            } else {
            // header
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(track.artists.joinToString(", ").ifEmpty { track.category }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Lyrics section — only when lyrics mode is active (caller passes non-null refetch)
            if (onRefetchLyrics != null || onLyricsSettings != null) {
                if (onRefetchLyrics != null) MenuItem(icon = Icons.Filled.Refresh, label = "Refetch lyrics", onClick = { onDismiss(); onRefetchLyrics() })
                if (onLyricsSettings != null) MenuItem(icon = Icons.Filled.Settings, label = "Lyrics settings", onClick = { onDismiss(); onLyricsSettings() })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            MenuItem(icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, label = if (isLiked) "Remove from Liked" else "Add to Liked", onClick = { onDismiss(); onLike() })
            MenuItem(icon = Icons.Filled.PlaylistAdd, label = "Add to playlist", onClick = { onDismiss(); onAddToPlaylist() })
            MenuItem(icon = Icons.Filled.QueueMusic, label = "Play next", onClick = { onDismiss(); onPlayNext() })
            MenuItem(icon = Icons.Filled.PlaylistPlay, label = "Add to queue", onClick = { onDismiss(); onAddToQueue() })
            if (onGoToAlbum != null) MenuItem(icon = Icons.Filled.Album, label = "Go to album", onClick = { onDismiss(); onGoToAlbum() })
            if (onOpenChannel != null && !track.channelId.isNullOrBlank()) MenuItem(icon = Icons.Filled.OpenInNew, label = "Open channel${track.channelName?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}", onClick = { onDismiss(); onOpenChannel() })
            if (onShowArtist != null && artistEntries.isNotEmpty()) {
                if (artistEntries.size == 1) {
                    val (name, id) = artistEntries[0]
                    MenuItem(icon = Icons.Filled.Person, label = "Show artist", onClick = { onDismiss(); onShowArtist(name, id) })
                } else {
                    MenuItem(icon = Icons.Filled.Person, label = "Show artists", onClick = { showArtists = true })
                }
            }
            if (onComments != null) MenuItem(icon = Icons.Filled.Comment, label = "Comments", onClick = { onDismiss(); onComments() })
            if (onRemove != null) MenuItem(icon = Icons.Filled.Delete, label = "Remove from playlist", onClick = { onDismiss(); onRemove() })
            if (onShare != null) MenuItem(icon = Icons.Filled.Share, label = "Share", onClick = { onDismiss(); onShare() })
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}

// Overload for FfiSearchResult convenience
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FfiTrackMenuSheet(
    item: uniffi.mediyo_ffi.FfiSearchResult,
    show: Boolean,
    onDismiss: () -> Unit,
    isLiked: Boolean = false,
    onLike: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onGoToAlbum: (() -> Unit)? = null,
    onShowArtist: ((name: String, id: String?) -> Unit)? = null,
    onOpenChannel: (() -> Unit)? = null,
    onComments: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onLyricsSettings: (() -> Unit)? = null
) {
    val track = remember(item.videoId, item.title) {
        Track(
            videoId = item.videoId, browseId = item.browseId, playlistId = item.playlistId,
            title = item.title, artists = item.artists, artistIds = item.artistIds,
            album = item.album, albumId = item.albumId,
            channelName = item.channelName, channelId = item.channelId,
            artworkUrl = item.thumbnails.bestThumbUrl(), duration = item.duration,
            category = item.category, year = item.year
        )
    }
    TrackMenuSheet(track, show, onDismiss, isLiked, onLike, onAddToPlaylist, onPlayNext, onAddToQueue, onGoToAlbum, onShowArtist, onOpenChannel, onComments, onRemove, onRefetchLyrics, onLyricsSettings)
}
