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
    onComments: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onLyricsSettings: (() -> Unit)? = null
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
            if (onShowArtist != null) {
                // One row per artist: single artist → plain entry, 2–6+ → named entries.
                val entries = remember(track) {
                    track.artists.mapIndexedNotNull { i, n ->
                        n.takeIf { it.isNotBlank() }?.let { name ->
                            name to track.artistIds.getOrNull(i)?.takeIf { it.isNotBlank() }
                        }
                    }
                }
                if (entries.size == 1) {
                    val (name, id) = entries[0]
                    MenuItem(icon = Icons.Filled.Person, label = "Show artist", onClick = { onDismiss(); onShowArtist(name, id) })
                } else {
                    entries.forEach { (name, id) ->
                        MenuItem(icon = Icons.Filled.Person, label = "Show $name", onClick = { onDismiss(); onShowArtist(name, id) })
                    }
                }
            }
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
            artworkUrl = item.thumbnails.bestThumbUrl(), duration = item.duration,
            category = item.category, year = item.year
        )
    }
    TrackMenuSheet(track, show, onDismiss, isLiked, onLike, onAddToPlaylist, onPlayNext, onAddToQueue, onGoToAlbum, onShowArtist, onComments, onRemove, onRefetchLyrics, onLyricsSettings)
}
