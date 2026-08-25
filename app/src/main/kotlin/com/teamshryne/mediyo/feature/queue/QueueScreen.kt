@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.teamshryne.mediyo.feature.queue

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.teamshryne.mediyo.data.queue.QueuePrefs
import com.teamshryne.mediyo.domain.model.ART_ROW_PX
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.thumbSized
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun joinByBullet(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" • ")

private fun formatDurationFromTracks(tracks: List<Track>): String {
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
class QueueVm @Inject constructor(
    private val manager: PlaybackQueueManager,
    private val prefs: QueuePrefs
) : ViewModel() {
    val state = manager.state
    val lockFlow = prefs.lockFlow
    fun removeAt(i: Int) = manager.removeAt(i)
    fun move(from: Int, to: Int) = manager.move(from, to)
    fun playAt(i: Int) = manager.setIndex(i)
    suspend fun setLock(v: Boolean) = prefs.setLock(v)
}

// ── Full Screen Queue ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    player: PlayerViewModel? = null,
    onClose: () -> Unit = {},
    onShowComments: (String) -> Unit = {},
    vm: QueueVm = hiltViewModel()
) {
    val qs by vm.state.collectAsState()
    val currentIdx = qs.index
    val playerState = player?.state?.collectAsState()?.value
    val lockQueue by vm.lockFlow.collectAsState(initial = false)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuIdx by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(currentIdx) {
        if (currentIdx in qs.entries.indices) {
            try { listState.animateScrollToItem((currentIdx - 2).coerceAtLeast(0)) } catch (_: Throwable) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(qs.origin.label().ifEmpty { "Queue" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { vm.setLock(!lockQueue) } }) {
                        Icon(
                            if (lockQueue) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (lockQueue) "Unlock" else "Lock",
                            tint = if (lockQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { player?.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { player?.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, null, tint = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", color = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = onClose, shape = CircleShape) {
                        Icon(Icons.Filled.ExpandMore, null); Spacer(Modifier.width(6.dp)); Text("Close")
                    }
                    TextButton(onClick = { scope.launch { vm.setLock(!lockQueue) } }) {
                        Icon(if (lockQueue) Icons.Filled.Lock else Icons.Filled.LockOpen, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (lockQueue) "Unlock" else "Lock")
                    }
                }
            }
        }
    ) { pad ->
        if (qs.entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.QueueMusic, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Queue is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add songs via ••• menu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                modifier = Modifier.fillMaxSize().padding(pad)
            ) {
                itemsIndexed(qs.entries, key = { idx, t -> "${t.videoId}_${idx}_${t.title.hashCode()}" }) { idx, t ->
                    val isActive = idx == currentIdx
                    val isPlaying = isActive && (playerState?.isPlaying == true)
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { it * 0.4f },
                        confirmValueChange = { value ->
                            if (!lockQueue && (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart)) {
                                vm.removeAt(idx)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            false
                        }
                    )
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
                                content = {
                                    QueueRowContent(
                                        track = t,
                                        isActive = isActive,
                                        isPlaying = isPlaying,
                                        lockQueue = lockQueue,
                                        showReorder = true,
                                        onClick = { if (idx == currentIdx) player?.toggle() else { vm.playAt(idx); player?.playAt(idx) } },
                                        onMore = { menuTrack = t; menuIdx = idx },
                                        onMoveUp = { if (idx > 0) vm.move(idx, idx - 1) },
                                        onMoveDown = { if (idx < qs.entries.lastIndex) vm.move(idx, idx + 1) }
                                    )
                                }
                            )
                        } else {
                            QueueRowContent(
                                track = t,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                lockQueue = true,
                                showReorder = false,
                                onClick = { if (idx == currentIdx) player?.toggle() else { vm.playAt(idx); player?.playAt(idx) } },
                                onMore = { menuTrack = t; menuIdx = idx },
                                onMoveUp = {},
                                onMoveDown = {}
                            )
                        }
                    }
                }
                if (qs.isFetchingRadio) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Autoplay • similar songs will be added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    menuTrack?.let { track ->
        val idx = menuIdx
        com.teamshryne.mediyo.core.design.TrackMenuSheet(
            track = track,
            show = true,
            onDismiss = { menuTrack = null; menuIdx = null },
            isLiked = false,
            onLike = { player?.toggleLike(track) },
            onAddToPlaylist = { showAddSheet = track },
            onPlayNext = { player?.addNext(track) },
            onAddToQueue = { player?.addToQueue(track) },
            onComments = { track.videoId?.let(onShowComments) },
            onRemove = idx?.let { { vm.removeAt(it) } }
        )
    }
    showAddSheet?.let { t ->
        com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddSheet = null })
    }
}

@Composable
private fun QueueRowContent(
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    lockQueue: Boolean,
    showReorder: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = track.artworkUrl.thumbSized(ART_ROW_PX), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((track.artists.joinToString(", ").ifEmpty { track.album ?: "" }) + (track.duration?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isActive && isPlaying) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        else if (isActive) Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        if (showReorder && !lockQueue) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) { Icon(Icons.Filled.KeyboardArrowUp, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f)) }
                Icon(Icons.Filled.DragHandle, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(14.dp))
                IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) { Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f)) }
            }
        } else if (lockQueue) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp).padding(start = 4.dp))
        }
    }
}
