@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.teamshryne.mediyo.feature.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

// ── Hold & adjust (long-press drag to reorder) ─────────────────────────────

/** Distance from the viewport edge where an in-flight drag starts auto-scrolling. */
private val DragEdgeZone = 88.dp

/** Peak auto-scroll speed while a dragged row sits inside the edge zone. */
private val DragScrollSpeed = 900.dp

/** Where a row sits right now — the list refreshes it as items are reordered. */
private class ReorderAnchor(var index: Int)

/**
 * Hold and adjust: long-press any row, drag it, and it swaps places live.
 *
 * The dragged row rides the finger on a translation layer (GPU only, never
 * layout) while the reorder itself is committed through [onMove]. Near the
 * viewport edges the list auto-scrolls so long queues stay reachable.
 *
 * Geometry is read from [listState] every frame instead of assumed, so the drag
 * stays correct whatever the row height is.
 */
private class QueueReorder(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgeZonePx: Float,
    private val scrollSpeedPx: Float,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    /** Index of the row under the finger, or -1 when nothing is being dragged. */
    var index by mutableIntStateOf(-1)
        private set

    /** Finger offset layered on top of the dragged row's laid-out position. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var autoScroll: Job? = null

    /**
     * Drag gesture for one row. Keyed on [anchor], never on the row index: rows
     * swap indexes mid-drag, and restarting the detector would drop the gesture.
     */
    fun dragModifier(anchor: ReorderAnchor, enabled: Boolean): Modifier =
        if (!enabled) Modifier else Modifier.pointerInput(anchor) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    if (index == -1) {
                        index = anchor.index
                        offset = 0f
                        autoScroll = scope.launch { autoScrollLoop() }
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    offset += amount.y
                    commit()
                },
                onDragEnd = { reset() },
                onDragCancel = { reset() }
            )
        }

    /** Lock toggled mid-drag (or the list went away) — drop the gesture. */
    fun reset() {
        autoScroll?.cancel()
        autoScroll = null
        index = -1
        offset = 0f
    }

    private suspend fun autoScrollLoop() {
        var last = 0L
        while (currentCoroutineContext().isActive && index >= 0) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val velocity = edgeVelocity()
            if (velocity != 0f) {
                val scrolled = listState.scrollBy(velocity * dt)
                if (scrolled == 0f) break
                // Scrolling moves every row, dragged one included — carry the
                // same distance into the offset so it stays under the finger.
                offset += scrolled
                commit()
            }
        }
        autoScroll = null
    }

    private fun edgeVelocity(): Float {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
        val center = item.offset + item.size / 2f + offset
        val top = info.viewportStartOffset.toFloat()
        val bottom = info.viewportEndOffset.toFloat()
        return when {
            center < top + edgeZonePx ->
                -scrollSpeedPx * ((top + edgeZonePx - center) / edgeZonePx).coerceIn(0f, 1f)
            center > bottom - edgeZonePx ->
                scrollSpeedPx * ((center - (bottom - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** Reorder once the dragged row's centre crosses a neighbour's centre. */
    private fun commit() {
        val from = index
        if (from < 0) return
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        val center = dragged.offset + dragged.size / 2f + offset
        var target = from
        for (other in info.visibleItemsInfo) {
            if (other.index == from) continue
            val otherCenter = other.offset + other.size / 2f
            if (other.index < from && center < otherCenter) target = minOf(target, other.index)
            else if (other.index > from && center > otherCenter) target = maxOf(target, other.index)
        }
        if (target == from) return
        val targetOffset = info.visibleItemsInfo.firstOrNull { it.index == target }?.offset ?: return
        // The row's laid-out slot jumps by the distance between the two slots,
        // so carry that into the offset or it snaps under the finger.
        offset += (dragged.offset - targetOffset).toFloat()
        index = target
        Snapshot.withMutableSnapshot { onMove(from, target) }
    }
}

// ── Full Screen Queue ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    player: PlayerViewModel? = null,
    onClose: () -> Unit = {},
    onShowComments: (String) -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    vm: QueueVm = hiltViewModel()
) {
    val qs by vm.state.collectAsState()
    val currentIdx = qs.index
    val playerState = player?.state?.collectAsState()?.value
    val lockQueue by vm.lockFlow.collectAsState(initial = false)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuIdx by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    val reorder = remember(listState, scope, density) {
        QueueReorder(
            listState = listState,
            scope = scope,
            edgeZonePx = with(density) { DragEdgeZone.toPx() },
            scrollSpeedPx = with(density) { DragScrollSpeed.toPx() },
            onMove = { from, to -> vm.move(from, to) }
        )
    }
    // Locking mid-drag must not strand the row in the air.
    LaunchedEffect(lockQueue) { if (lockQueue) reorder.reset() }

    // Stable per-row keys (videoId + occurrence). Index-based keys would give
    // every shifted row a brand new identity mid-drag, which tears down its
    // drag gesture; occurrence numbering keeps duplicates unique.
    val keys = remember(qs.entries) {
        val seen = HashMap<String, Int>()
        qs.entries.map { t ->
            val id = t.videoId ?: t.title
            val n = seen.getOrDefault(id, 0)
            seen[id] = n + 1
            "$id#$n"
        }
    }

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
                    // sleep timer quick entry — player is always provided from MainActivity
                    val sleepQ = player?.sleepState?.collectAsState()?.value
                    val sleepActiveQ = sleepQ?.isActive == true
                    IconButton(onClick = onShowSleepTimer) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (sleepActiveQ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                itemsIndexed(qs.entries, key = { idx, _ -> keys[idx] }) { idx, t ->
                    val isActive = idx == currentIdx
                    val isPlaying = isActive && (playerState?.isPlaying == true)
                    val anchor = remember { ReorderAnchor(idx) }
                    anchor.index = idx
                    val onRowClick: () -> Unit = { if (idx == currentIdx) player?.toggle() else { vm.playAt(idx); player?.playAt(idx) } }
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
                                        reorder = reorder,
                                        index = idx,
                                        canReorder = true,
                                        modifier = reorder.dragModifier(anchor, enabled = true),
                                        onClick = onRowClick,
                                        onMore = { menuTrack = t; menuIdx = idx }
                                    )
                                }
                            )
                        } else {
                            // Same row, same handle, same size as the unlocked
                            // branch — toggling the lock must not shift anything.
                            QueueRowContent(
                                track = t,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                reorder = reorder,
                                index = idx,
                                canReorder = false,
                                onClick = onRowClick,
                                onMore = { menuTrack = t; menuIdx = idx }
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
            onShowArtist = { name, id ->
                scope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let(onGoToArtist) }
            },
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
    reorder: QueueReorder,
    index: Int,
    canReorder: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val dragging = reorder.index == index
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (dragging) {
                    translationY = reorder.offset
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
        QueueDragHandle(active = canReorder)
    }
}

/**
 * Drag affordance. Rendered identically whether the queue is locked or not —
 * only the alpha changes — so locking can never reflow a row.
 */
@Composable
private fun QueueDragHandle(active: Boolean) {
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = "Hold and drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 0.8f else 0.25f),
        modifier = Modifier.size(22.dp)
    )
}
