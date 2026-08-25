package com.teamshryne.mediyo.core.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.domain.model.Track

@Composable
fun TrackOverflowIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
    onGoToArtist: (() -> Unit)? = null,
    onComments: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    if (!show) return
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // header
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(track.artists.joinToString(", ").ifEmpty { track.category }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MenuItem(icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, label = if (isLiked) "Remove from Liked" else "Add to Liked", onClick = { onDismiss(); onLike() })
            MenuItem(icon = Icons.Filled.PlaylistAdd, label = "Add to playlist", onClick = { onDismiss(); onAddToPlaylist() })
            MenuItem(icon = Icons.Filled.QueueMusic, label = "Play next", onClick = { onDismiss(); onPlayNext() })
            MenuItem(icon = Icons.Filled.PlaylistPlay, label = "Add to queue", onClick = { onDismiss(); onAddToQueue() })
            if (onGoToAlbum != null) MenuItem(icon = Icons.Filled.Album, label = "Go to album", onClick = { onDismiss(); onGoToAlbum() })
            if (onGoToArtist != null) MenuItem(icon = Icons.Filled.Person, label = "Go to artist", onClick = { onDismiss(); onGoToArtist() })
            if (onComments != null) MenuItem(icon = Icons.Filled.Comment, label = "Comments", onClick = { onDismiss(); onComments() })
            if (onRemove != null) MenuItem(icon = Icons.Filled.Delete, label = "Remove from playlist", onClick = { onDismiss(); onRemove() })
            if (onShare != null) MenuItem(icon = Icons.Filled.Share, label = "Share", onClick = { onDismiss(); onShare() })
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
    onGoToArtist: (() -> Unit)? = null,
    onComments: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val track = remember(item.videoId, item.title) {
        Track(
            videoId = item.videoId, browseId = item.browseId, playlistId = item.playlistId,
            title = item.title, artists = item.artists, album = item.album,
            artworkUrl = item.thumbnails.firstOrNull()?.url, duration = item.duration,
            category = item.category, year = item.year
        )
    }
    TrackMenuSheet(track, show, onDismiss, isLiked, onLike, onAddToPlaylist, onPlayNext, onAddToQueue, onGoToAlbum, onGoToArtist, onComments, onRemove)
}
