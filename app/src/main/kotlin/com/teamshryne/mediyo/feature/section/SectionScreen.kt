package com.teamshryne.mediyo.feature.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Search
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
import com.teamshryne.mediyo.core.design.EaseIn
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.EmptyState
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.isArtist
import com.teamshryne.mediyo.core.design.shimmer
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.core.design.navigateAlbum
import com.teamshryne.mediyo.core.design.navigateArtist
import com.teamshryne.mediyo.core.design.navigatePlaylist
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full "view all" page for a section shelf — artist discographies / track
 * lists and channel shelves. Backed by the typed core endpoints instead of a
 * generic browse list. Route: `section/{kind}/{browseId}?params&title`,
 * where kind is `artist` or `channel`.
 */
@HiltViewModel class SectionVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false)
    var continuation by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<com.teamshryne.mediyo.data.mediyo.FfiSearchResult>>(emptyList())
    private var lastToken: String? = null
    fun load(kind: String, id: String, params: String?, title: String?) {
        loading = true; error = null; continuation = null; lastToken = null
        viewModelScope.launch { events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.VIEW_SECTION, browseId = id, meta = params?.take(200)) }
        viewModelScope.launch {
            try {
                val p = if (kind == "channel") bridge.channelSection(id, params, title)
                else bridge.artistSection(id, params, title)
                items = p.items; continuation = p.continuation.takeIf { p.items.isNotEmpty() }
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
    fun loadMore(kind: String) {
        val token = continuation?.takeIf { it.isNotEmpty() } ?: return
        if (loadingMore || loading) return
        // Loop guard: a repeated token means the server has nothing new.
        if (token == lastToken) {
            continuation = null
            return
        }
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = if (kind == "channel") bridge.channelSectionNext(token)
                else bridge.artistSectionNext(token)
                lastToken = token
                if (p.reloaded) {
                    // Sort/filter-style token: the page replaces the list.
                    items = p.items
                    continuation = p.continuation
                } else {
                    val before = items.size
                    items = items.appendUnique(p.items)
                    continuation = if (p.items.isEmpty() || items.size == before) null else p.continuation
                }
            } catch (_: Throwable) { continuation = null } finally { loadingMore = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SectionScreen(
    kind: String,
    browseId: String,
    params: String? = null,
    title: String? = null,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: SectionVm = hiltViewModel()
) {
    LaunchedEffect(kind, browseId, params, title) { vm.load(kind, browseId, params, title) }
    // Sheets render at the root overlay (GlobalSheets) for full-window dim.
    val sheets: com.teamshryne.mediyo.core.design.SheetHostVm = hiltViewModel()
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    fun openMenu(m: com.teamshryne.mediyo.data.mediyo.FfiSearchResult) {
        val track = m.toDomainTrack()
        sheets.showTrackMenu(
            com.teamshryne.mediyo.core.design.TrackMenuRequest(
                track = track,
                onLike = { player?.toggleLike(track) },
                onAddToPlaylist = { sheets.openAddToPlaylist(it) },
                onPlayNext = { player?.addNext(track) },
                onAddToQueue = { player?.addToQueue(track) },
                onGoToAlbum = m.album?.takeIf { it.isNotBlank() }?.let { { menuScope.launch { val id = m.albumId?.takeIf { it.isNotBlank() } ?: menuVm.resolveAlbumId(m); id?.let { nav.navigateAlbum(it) } } } },
                onShowArtist = { name, id ->
                    menuScope.launch { (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))?.let { nav.navigateArtist(it) } }
                },
                onComments = { m.videoId?.let { sheets.showComments(it) } }
            )
        )
    }

    val heading = title?.takeIf { it.isNotBlank() } ?: "Playlist"
    fun origin() = PlayOrigin.GenericList("$browseId|$heading")

    fun handle(r: com.teamshryne.mediyo.data.mediyo.FfiSearchResult) {
        when {
            r.videoId != null -> {
                if (player?.state?.value?.videoId == r.videoId) player?.toggle()
                else player?.playFromWithOrigin(vm.items, r, origin())
            }
            r.playlistId != null && r.browseId == null ->
                nav.navigatePlaylist(r.playlistId)
            else -> com.teamshryne.mediyo.core.design.openRoute(r)?.let { nav?.navigate(it) }
        }
    }

    fun playAll(shuffle: Boolean) {
        val first = vm.items.firstOrNull { it.videoId != null } ?: return
        if (shuffle) player?.toggleShuffle()
        player?.playFromWithOrigin(vm.items, first, origin())
    }

    when {
        vm.loading -> SectionShimmer()
        vm.error != null && vm.items.isEmpty() -> ErrorState(vm.error ?: "Failed to load") { vm.load(kind, browseId, params, title) }
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
            val listSearch = com.teamshryne.mediyo.core.design.rememberListSearchUiState()
            val searchScope = rememberCoroutineScope()
            val sq = listSearch.query.trim()
            val matches = remember(vm.items, sq) {
                if (sq.isBlank()) vm.items
                else vm.items.filter { r ->
                    com.teamshryne.mediyo.core.design.matchesQuery(sq, r.title, r.artists.joinToString(), r.category, r.info)
                }
            }
            val hasMore = vm.continuation != null
            fun searchRest() {
                if (listSearch.searchingAll) return
                listSearch.searchingAll = true
                searchScope.launch {
                    com.teamshryne.mediyo.core.design.loadAllPaged(
                        hasMore = { vm.continuation != null },
                        isLoading = { vm.loadingMore },
                        loadMore = { vm.loadMore(kind) }
                    )
                    listSearch.searchingAll = false
                }
            }
            androidx.compose.runtime.LaunchedEffect(sq, matches.isEmpty(), hasMore) {
                if (sq.isNotBlank() && matches.isEmpty() && hasMore) searchRest()
            }

            // Sentinel pagination: the shimmer "loading" row itself triggers
            // the next page when it scrolls into view; it composes away once
            // the continuation runs dry, so loading stops on its own.
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
                    .collect { if (it) vm.loadMore(kind) }
            }
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
                    .collect { if (it) vm.loadMore(kind) }
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
                        contentPadding = PaddingValues(top = 72.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)
                    ) {
                        if (playable) {
                            item(key = "section_play", contentType = "header") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
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
                        if (sq.isBlank()) {
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
                                    trailing = { TrackOverflowIcon(onClick = { openMenu(r) }) }
                                ) { handle(r) }
                            }
                        }
                        }
                        if (vm.continuation != null && sq.isBlank()) {
                            item(key = "loading") {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    repeat(3) { SectionRowPlaceholder() }
                                }
                            }
                        }
                        if (sq.isNotBlank()) {
                            items(matches.size, key = { i ->
                                val r = matches[i]
                                "sm_${r.videoId ?: r.browseId ?: r.playlistId ?: i}_$i"
                            }) { i ->
                                val r = matches[i]
                                Box {
                                    TrackRow(
                                        item = r,
                                        isPlaying = playingId != null && playingId == r.videoId,
                                        number = vm.items.indexOf(r) + 1,
                                        showArtwork = false,
                                        trailing = { TrackOverflowIcon(onClick = { openMenu(r) }) }
                                    ) { handle(r) }
                                }
                            }
                            if (listSearch.searchingAll) {
                                item(key = "searching_rest") {
                                    com.teamshryne.mediyo.core.design.SearchingRestAnimation(vm.items.size)
                                }
                            } else if (hasMore) {
                                item(key = "search_more") {
                                    com.teamshryne.mediyo.core.design.SearchMoreRow(matches.size) { searchRest() }
                                }
                            } else if (matches.isEmpty()) {
                                item(key = "no_match") {
                                    com.teamshryne.mediyo.core.design.EmptyState(
                                        "Nothing matches \"$sq\"",
                                        "Try different keywords"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 72.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val gridItems = if (sq.isBlank()) vm.items else matches
                        items(gridItems.size, key = { i ->
                            gridItems[i].let { it.videoId ?: it.browseId ?: it.playlistId }
                                ?.let { "g_${it}_$i" } ?: "g_$i"
                        }) { i ->
                            val r = gridItems[i]
                            SectionGridCard(
                                item = r,
                                isPlaying = playingId != null && playingId == r.videoId,
                                onClick = { handle(r) },
                                onMenu = { openMenu(r) }
                            )
                        }
                        if (vm.continuation != null && sq.isBlank()) {
                            item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    repeat(2) { SectionGridPlaceholder() }
                                }
                            }
                        }
                        if (sq.isNotBlank()) {
                            if (listSearch.searchingAll) {
                                item(key = "searching_rest", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    com.teamshryne.mediyo.core.design.SearchingRestAnimation(vm.items.size)
                                }
                            } else if (hasMore) {
                                item(key = "search_more", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    com.teamshryne.mediyo.core.design.SearchMoreRow(matches.size) { searchRest() }
                                }
                            } else if (matches.isEmpty()) {
                                item(key = "no_match", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    com.teamshryne.mediyo.core.design.EmptyState(
                                        "Nothing matches \"$sq\"",
                                        "Try different keywords"
                                    )
                                }
                            }
                        }
                    }
                }

                com.teamshryne.mediyo.core.design.SectionSearchBar(
                    title = heading,
                    search = listSearch,
                    searching = listSearch.searchingAll,
                    resultCount = matches.size,
                    onBack = { nav?.popBackStack() },
                    placeholder = "Search this list"
                )

                AnimatedVisibility(
                    visible = fabVisible && playable,
                    // Fast expo pop (150ms) instead of the default spring, which
                    // bounces and reads as slow. Matches the app Motion tokens.
                    enter = scaleIn(tween(150, easing = EaseOutExpo), initialScale = 0.8f) +
                        fadeIn(tween(120, easing = EaseOutExpo)),
                    exit = scaleOut(tween(120, easing = EaseIn), targetScale = 0.8f) +
                        fadeOut(tween(100, easing = EaseIn)),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    FloatingActionButton(onClick = { playAll(shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all")
                    }
                }
            }

        }
    }
}

// ── Grid card + shimmer placeholders ─────────────────────────────────────────

@Composable
private fun SectionGridCard(
    item: com.teamshryne.mediyo.data.mediyo.FfiSearchResult,
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
        contentPadding = PaddingValues(top = 72.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)
    ) {
        items(10) { SectionRowPlaceholder() }
    }
}
