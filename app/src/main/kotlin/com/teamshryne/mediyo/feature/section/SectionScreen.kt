package com.teamshryne.mediyo.feature.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.EmptyState
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.isArtist
import com.teamshryne.mediyo.core.design.shimmer
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full "view all" page for any section shelf (artist Popular / Singles &
 * EPs / Videos, home shelves, generic browse lists) — Metrolist style:
 * overlaid TopAppBar, song pages as a list / collection pages as an
 * adaptive grid, sentinel shimmer row driving pagination, shuffle FAB
 * that hides on scroll down.
 */
@HiltViewModel class SectionVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false)
    var continuation by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    fun load(id: String, params: String?) {
        loading = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val p = bridge.listPage(id, params)
                items = p.items; continuation = p.continuation.takeIf { p.items.isNotEmpty() }
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
    fun loadMore() {
        val token = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = bridge.nextPage(token)
                val before = items.size
                items = items.appendUnique(p.items)
                continuation = if (p.items.isEmpty() || items.size == before) null else p.continuation
            } catch (_: Throwable) { continuation = null } finally { loadingMore = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    browseId: String,
    params: String? = null,
    title: String? = null,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: SectionVm = hiltViewModel()
) {
    LaunchedEffect(browseId, params) { vm.load(browseId, params) }
    var menuItem by remember { mutableStateOf<uniffi.mediyo_ffi.FfiSearchResult?>(null) }
    var showAddTrack by remember { mutableStateOf<Track?>(null) }
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    val heading = title?.takeIf { it.isNotBlank() } ?: "Playlist"
    fun origin() = PlayOrigin.GenericList("$browseId|$heading")

    fun handle(r: uniffi.mediyo_ffi.FfiSearchResult) {
        when {
            r.videoId != null -> {
                if (player?.state?.value?.videoId == r.videoId) player?.toggle()
                else player?.playFromWithOrigin(vm.items, r, origin())
            }
            r.browseId != null && r.category.contains("Album", true) -> nav?.navigate("album/${r.browseId}")
            r.browseId != null && r.category.contains("Artist", true) -> nav?.navigate("artist/${r.browseId}")
            r.browseId != null && r.category.contains("Playlist", true) -> nav?.navigate("playlist/${r.browseId}")
            r.browseId != null && r.category.contains("Podcast", true) -> nav?.navigate("podcast/${r.browseId}")
            r.browseId != null -> {
                val p = r.browseParams?.takeIf { it.isNotBlank() }
                    ?.let { "?params=${android.net.Uri.encode(it)}" } ?: ""
                nav?.navigate("list/${r.browseId}$p")
            }
            r.playlistId != null -> nav?.navigate("playlist/${r.playlistId}")
        }
    }

    fun playAll(shuffle: Boolean) {
        val first = vm.items.firstOrNull { it.videoId != null } ?: return
        if (shuffle) player?.toggleShuffle()
        player?.playFromWithOrigin(vm.items, first, origin())
    }

    when {
        vm.loading -> SectionShimmer()
        vm.error != null && vm.items.isEmpty() -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId, params) }
        vm.items.isEmpty() -> EmptyState("Nothing here", "This section came back empty")
        else -> {
            // Song pages (Popular, Videos) as a list; collection pages
            // (discographies, mixes) as an adaptive grid.
            val songPage = remember(vm.items) { vm.items.all { it.videoId != null } }
            val playable = remember(vm.items) { vm.items.any { it.videoId != null } }
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = rememberLazyListState()
            val gridState = rememberLazyGridState()
            var fabVisible by remember { mutableStateOf(true) }

            // Sentinel pagination: the shimmer "loading" row itself triggers
            // the next page when it scrolls into view; it composes away once
            // the continuation runs dry, so loading stops on its own.
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
                    .collect { if (it) vm.loadMore() }
            }
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
                    .collect { if (it) vm.loadMore() }
            }
            // Shuffle FAB hides on scroll down, returns on scroll up / top.
            if (songPage) {
                LaunchedEffect(listState) {
                    var prevIndex = 0
                    var prevOffset = 0
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (index, offset) ->
                            fabVisible = index < prevIndex ||
                                (index == prevIndex && offset <= prevOffset) || index == 0
                            prevIndex = index; prevOffset = offset
                        }
                }
            } else {
                LaunchedEffect(gridState) {
                    var prevIndex = 0
                    var prevOffset = 0
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .collect { (index, offset) ->
                            fabVisible = index < prevIndex ||
                                (index == prevIndex && offset <= prevOffset) || index == 0
                            prevIndex = index; prevOffset = offset
                        }
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (songPage) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp)
                    ) {
                        item(key = "section_count", contentType = "header") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${vm.items.size} songs",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (playable) {
                                    FilledTonalButton(
                                        onClick = { playAll(shuffle = false) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all")
                                    }
                                }
                            }
                        }
                        items(vm.items.size, key = { i ->
                            vm.items[i].let { it.videoId ?: it.browseId ?: it.playlistId }
                                ?.let { "s_${it}_$i" } ?: "s_$i"
                        }) { i ->
                            val r = vm.items[i]
                            Box {
                                TrackRow(
                                    item = r,
                                    isPlaying = playingId != null && playingId == r.videoId,
                                    number = i + 1,
                                    showArtwork = false,
                                    trailing = { TrackOverflowIcon(onClick = { menuItem = r }) }
                                ) { handle(r) }
                            }
                        }
                        if (vm.continuation != null) {
                            item(key = "loading") {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    repeat(3) { SectionRowPlaceholder() }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 76.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                "${vm.items.size} items",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        items(vm.items.size, key = { i ->
                            vm.items[i].let { it.videoId ?: it.browseId ?: it.playlistId }
                                ?.let { "g_${it}_$i" } ?: "g_$i"
                        }) { i ->
                            val r = vm.items[i]
                            SectionGridCard(
                                item = r,
                                isPlaying = playingId != null && playingId == r.videoId,
                                onClick = { handle(r) },
                                onMenu = { menuItem = r }
                            )
                        }
                        if (vm.continuation != null) {
                            item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    repeat(2) { SectionGridPlaceholder() }
                                }
                            }
                        }
                    }
                }

                TopAppBar(
                    title = {
                        Text(
                            heading,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav?.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                AnimatedVisibility(
                    visible = fabVisible && playable,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    FloatingActionButton(onClick = { playAll(shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all")
                    }
                }
            }

            menuItem?.let { m ->
                val track = m.toDomainTrack()
                com.teamshryne.mediyo.core.design.TrackMenuSheet(
                    track = track, show = true, onDismiss = { menuItem = null },
                    onLike = { player?.toggleLike(track) },
                    onAddToPlaylist = { showAddTrack = track },
                    onPlayNext = { player?.addNext(track) },
                    onAddToQueue = { player?.addToQueue(track) },
                    onGoToAlbum = m.album?.takeIf { it.isNotBlank() }?.let { { menuScope.launch { val id = m.albumId?.takeIf { it.isNotBlank() } ?: menuVm.resolveAlbumId(m); id?.let { nav?.navigate("album/$it") } } } },
                    onShowArtist = { name, id ->
                        menuScope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let { nav?.navigate("artist/$it") } }
                    },
                    onComments = { m.videoId?.let { nav?.navigate("comments/$it") } }
                )
            }
            showAddTrack?.let { t ->
                com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddTrack = null })
            }
        }
    }
}

// ── Grid card + shimmer placeholders ─────────────────────────────────────────

@Composable
private fun SectionGridCard(
    item: uniffi.mediyo_ffi.FfiSearchResult,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = item.thumbnails.bestThumbUrl(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .let {
                        if (item.isArtist()) it.clip(CircleShape)
                        else it.clip(RoundedCornerShape(12.dp))
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onClick)
            )
            TrackOverflowIcon(
                onClick = onMenu,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            item.artists.joinToString().ifBlank { item.category },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionRowPlaceholder() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
        }
    }
}

@Composable
private fun SectionGridPlaceholder() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)).shimmer()
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(13.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
    }
}

@Composable
private fun SectionShimmer() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp)
    ) {
        items(10) { SectionRowPlaceholder() }
    }
}
