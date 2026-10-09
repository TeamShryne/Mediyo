package com.teamshryne.mediyo.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.teamshryne.mediyo.data.local.DownloadedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.player.PlayerViewModel

/**
 * Offline library: every downloaded song, playable without network.
 * Tapping a row plays it (bytes come from the permanent store, no URL
 * needed); the trailing button cancels an in-flight download or removes a
 * finished one. Failed rows tap-to-retry via the overflow row below them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    nav: androidx.navigation.NavController? = null,
    player: PlayerViewModel,
    vm: DownloadVm = hiltViewModel()
) {
    val tracks by vm.tracks.collectAsState(initial = emptyList())
    val states by vm.uiStates.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = { nav?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = { vm.removeAll() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Remove all downloads")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Use Download on any song to keep it offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp
                )
            ) {
                items(tracks, key = { it.videoId }) { entity ->
                    DownloadRow(
                        entity = entity,
                        state = states[entity.videoId] ?: DownloadUiState.Completed,
                        onPlay = {
                            player.playTrack(
                                Track(
                                    videoId = entity.videoId,
                                    title = entity.title,
                                    artists = entity.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    artworkUrl = entity.artworkUrl
                                )
                            )
                        },
                        onCancelOrRemove = { vm.remove(entity.videoId) },
                        onRetry = {
                            vm.download(
                                Track(
                                    videoId = entity.videoId,
                                    title = entity.title,
                                    artists = entity.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    artworkUrl = entity.artworkUrl
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entity: DownloadedTrackEntity,
    state: DownloadUiState,
    onPlay: () -> Unit,
    onCancelOrRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val statusText = when (state) {
        DownloadUiState.Completed -> "Downloaded"
        is DownloadUiState.Downloading ->
            if (state.percent >= 0) "Downloading • ${state.percent}%" else "Downloading…"
        DownloadUiState.Queued -> "Queued"
        DownloadUiState.Failed -> "Failed • tap to retry"
        DownloadUiState.NotDownloaded -> "Downloaded"
    }
    val failed = state is DownloadUiState.Failed
    val inFlight = state is DownloadUiState.Downloading || state is DownloadUiState.Queued

    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = { if (failed) onRetry() else onPlay() })
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = entity.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(
                entity.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                (entity.artist.ifBlank { "Unknown artist" }) + "  •  $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (inFlight) {
            val pct = (state as? DownloadUiState.Downloading)?.percent ?: -1
            if (pct >= 0) {
                CircularProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onCancelOrRemove) {
                Icon(Icons.Filled.Close, "Cancel download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!failed) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCancelOrRemove) {
                Icon(Icons.Filled.Close, "Remove download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Failed: row tap or button retries.
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Downloading, "Retry download", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
