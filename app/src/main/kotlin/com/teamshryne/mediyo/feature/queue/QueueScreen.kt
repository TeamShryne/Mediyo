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
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
    nav: androidx.navigation.NavController? = null,
    player: PlayerViewModel? = null,
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

    // reorderable state
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!lockQueue) {
            vm.move(from.index, to.index)
        }
    }

    // auto scroll to current
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
                    IconButton(onClick = { nav?.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { vm.setLock(!lockQueue) }
                    }) {
                        Icon(
                            if (lockQueue) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (lockQueue) "Unlock" else "Lock",
                            tint = if (lockQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { player?.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // bottom bar like reference: shuffle + collapse + lock already in top, but keep extra bottom for collapse
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { player?.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, null, tint = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", color = if (playerState?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { nav?.popBackStack() }, shape = CircleShape) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                itemsIndexed(qs.entries, key = { idx, t -> "${t.videoId}_${idx}_${t.title.hashCode()}" }) { idx, t ->
                    val isActive = idx == currentIdx
                    val isPlaying = isActive && (playerState?.isPlaying == true)
                    ReorderableItem(reorderableState, key = "${t.videoId}_${idx}_${t.title.hashCode()}") { isDragging ->
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
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .then(if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)) else Modifier)
                        ) {
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
                                            onClick = {
                                                if (idx == currentIdx) player?.toggle() else { vm.playAt(idx); player?.playAt(idx) }
                                            },
                                            onMore = { menuTrack = t; menuIdx = idx },
                                            reorderableState = reorderableState
                                        )
                                    }
                                )
                            } else {
                                QueueRowContent(
                                    track = t,
                                    isActive = isActive,
                                    isPlaying = isPlaying,
                                    lockQueue = true,
                                    onClick = {
                                        if (idx == currentIdx) player?.toggle() else { vm.playAt(idx); player?.playAt(idx) }
                                    },
                                    onMore = { menuTrack = t; menuIdx = idx },
                                    reorderableState = null
                                )
                            }
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

    // 3-dot menu
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
            onComments = { track.videoId?.let { nav?.navigate("comments/$it") } },
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
    onClick: () -> Unit,
    onMore: () -> Unit,
    reorderableState: sh.calvin.reorderable.ReorderableLazyListState? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (track.artists.joinToString(", ").ifEmpty { track.album ?: "" }) + (track.duration?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isActive && isPlaying) {
            Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        } else if (isActive) {
            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (!lockQueue && reorderableState != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .then(
                        try {
                            with(reorderableState) { Modifier.draggableHandle() }
                        } catch (_: Throwable) { Modifier }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        } else if (lockQueue) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp).padding(start = 4.dp))
        }
    }
}

// ── Legacy Sheet wrapper (kept for overlay fallback, now delegates to screen navigation) ──
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    player: PlayerViewModel,
    vm: QueueVm = hiltViewModel()
) {
    // For backwards compat, just show the screen as a sheet; but spec wants screen, so we delegate to QueueScreen via sheet height
    // Keep simple wrap to avoid breaking existing call sites; will be removed once all callers use nav.
    QueueSheetContent(onDismiss = onDismiss, player = player, vm = vm)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueSheetContent(
    onDismiss: () -> Unit,
    player: PlayerViewModel,
    vm: QueueVm
) {
    val qs by vm.state.collectAsState()
    val currentIdx = qs.index
    val playerState by player.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lockQueue by vm.lockFlow.collectAsState(initial = false)
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuIdx by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }

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
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(qs.origin.label().ifEmpty { "Queue" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(joinByBullet("${qs.entries.size} songs", formatDurationFromTracks(qs.entries), if (qs.isFetchingRadio) "updating radio…" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            if (qs.entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.QueueMusic, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Queue is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp).fillMaxWidth(), contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)) {
                    itemsIndexed(qs.entries, key = { idx, t -> "${t.videoId}_${idx}_${t.title.hashCode()}" }) { idx, t ->
                        val isActive = idx == currentIdx
                        val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f }, confirmValueChange = { value ->
                            if (!lockQueue && (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart)) vm.removeAt(idx)
                            false
                        })
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                            if (!lockQueue) {
                                SwipeToDismissBox(state = dismissState, backgroundContent = {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.CenterStart) {
                                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                            Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }, content = {
                                    Row(
                                        Modifier.fillMaxWidth().background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                                            if (idx == currentIdx) player.toggle() else { vm.playAt(idx); player.playAt(idx) }
                                        }).padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(model = t.artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text((t.artists.joinToString(", ").ifEmpty { t.album ?: "" }) + (t.duration?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { menuTrack = t; menuIdx = idx }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp)) }
                                    }
                                })
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                                        if (idx == currentIdx) player.toggle() else { vm.playAt(idx); player.playAt(idx) }
                                    }).padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = t.artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.bodyMedium, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text((t.artists.joinToString(", ").ifEmpty { t.album ?: "" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { menuTrack = t; menuIdx = idx }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, null, Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                    if (qs.isFetchingRadio) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
                }
            }
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { scope.launch { try { listState.animateScrollToItem((currentIdx - 1).coerceAtLeast(0)) } catch (_: Throwable) {} }; player.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, null, tint = if (playerState.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                    FilledTonalIconButton(onClick = onDismiss, shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Icon(Icons.Filled.ExpandMore, null) }
                    IconButton(onClick = { scope.launch { vm.setLock(!lockQueue) } }) { Icon(if (lockQueue) Icons.Filled.Lock else Icons.Filled.LockOpen, null, tint = if (lockQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        menuTrack?.let { track ->
            val idx = menuIdx
            com.teamshryne.mediyo.core.design.TrackMenuSheet(track = track, show = true, onDismiss = { menuTrack = null; menuIdx = null }, onLike = { player.toggleLike(track) }, onAddToPlaylist = { showAddSheet = track }, onPlayNext = { player.addNext(track) }, onAddToQueue = { player.addToQueue(track) }, onRemove = idx?.let { { vm.removeAt(it) } })
        }
        showAddSheet?.let { t -> com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddSheet = null }) }
    }
}
