package com.teamshryne.mediyo.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class QueueVm @Inject constructor(private val manager: PlaybackQueueManager) : ViewModel() {
    val state = manager.state
    fun removeAt(i: Int) = manager.removeAt(i)
    fun move(from: Int, to: Int) = manager.move(from, to)
    fun playAt(i: Int) = manager.setIndex(i)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    player: PlayerViewModel,
    vm: QueueVm = hiltViewModel()
) {
    val qs by vm.state.collectAsState()
    val currentIdx = qs.index
    val playerState by player.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    Text(qs.origin.label() + " • ${qs.entries.size} songs" + if (qs.isFetchingRadio) " • updating radio…" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }
            HorizontalDivider()
            if (qs.entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                    itemsIndexed(qs.entries, key = { idx, t -> "${t.videoId}_$idx" }) { idx, t ->
                        QueueRow(
                            track = t,
                            isCurrent = idx == currentIdx,
                            isPlaying = idx == currentIdx && playerState.isPlaying,
                            onClick = {
                                vm.playAt(idx)
                                // need to trigger player load
                                player.playAt(idx)
                            },
                            onRemove = { vm.removeAt(idx) }
                        )
                    }
                    if (qs.isFetchingRadio) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
                    } else if (qs.entries.size >= 3) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                Text("— Autoplay (radio) will add similar songs —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp)
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = track.artworkUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(track.artists.joinToString(", ").ifEmpty { track.album ?: "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (isCurrent && isPlaying) {
            Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}
