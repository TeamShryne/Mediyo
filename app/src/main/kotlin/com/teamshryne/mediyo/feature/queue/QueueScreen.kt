package com.teamshryne.mediyo.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun joinByBullet(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" • ")

private fun formatDurationFromTracks(tracks: List<Track>): String {
    // duration strings like "3:21" -> sum approx
    var totalSec = 0
    for (t in tracks) {
        val d = t.duration ?: continue
        val parts = d.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size == 2) totalSec += parts[0] * 60 + parts[1]
        else if (parts.size == 3) totalSec += parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var shuffleEnabled by remember { mutableStateOf(playerState.shuffle) }
    LaunchedEffect(playerState.shuffle) { shuffleEnabled = playerState.shuffle }
    var lockQueue by remember { mutableStateOf(false) }

    // auto scroll to current
    LaunchedEffect(currentIdx) {
        if (currentIdx in qs.entries.indices) {
            try { listState.animateScrollToItem((currentIdx - 2).coerceAtLeast(0)) } catch (_: Throwable) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
            // ── Header (like reference) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            qs.origin.label().ifEmpty { "Queue" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            joinByBullet(
                                "${qs.entries.size} songs",
                                formatDurationFromTracks(qs.entries),
                                if (qs.isFetchingRadio) "updating radio…" else ""
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)

            // ── Queue list ──
            if (qs.entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Queue is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add songs via ••• menu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 520.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)
                ) {
                    itemsIndexed(qs.entries, key = { idx, t -> "${t.videoId}_${idx}_${t.title.hashCode()}" }) { idx, t ->
                        val isActive = idx == currentIdx
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.4f },
                            confirmValueChange = { value ->
                                if (!lockQueue && (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart)) {
                                    vm.removeAt(idx)
                                }
                                false // we handle removal via vm, not auto dismiss
                            }
                        )
                        val row = @Composable {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (idx == currentIdx) player.toggle()
                                            else {
                                                vm.playAt(idx)
                                                player.playAt(idx)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            // could enter select mode — for now just vibrate
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = t.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        (t.artists.joinToString(", ").ifEmpty { t.album ?: "" }) + (t.duration?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isActive && playerState.isPlaying) {
                                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                                } else if (isActive) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    // more menu could be shown — for now just toggle like/play next
                                    // use simple sheet: we reuse TrackOverflow later, but here just add to queue next?
                                }, modifier = Modifier.size(36.dp)) {
                                    // placeholder for future menu — keep drag handle
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                if (!lockQueue) {
                                    Icon(Icons.Filled.DragHandle, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp).padding(start = 2.dp))
                                }
                            }
                        }
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                            if (!lockQueue) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.CenterStart) {
                                            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                                Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    },
                                    content = { row() }
                                )
                            } else {
                                row()
                            }
                        }
                    }
                    if (qs.isFetchingRadio) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                    } else {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Autoplay • similar songs will be added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Bottom bar (reference style with our Mediyo colors) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = {
                        scope.launch {
                            // animate to current before shuffling, like reference
                            try { listState.animateScrollToItem((currentIdx - 1).coerceAtLeast(0)) } catch (_: Throwable) {}
                        }
                        player.toggleShuffle()
                    }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Collapse")
                    }
                    IconButton(onClick = { lockQueue = !lockQueue }) {
                        Icon(
                            if (lockQueue) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (lockQueue) "Unlock queue" else "Lock queue",
                            tint = if (lockQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
