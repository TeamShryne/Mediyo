package com.teamshryne.mediyo.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.EmptyState
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.shimmer
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uniffi.mediyo_ffi.FfiSearchFilter
import uniffi.mediyo_ffi.FfiSearchResult
import javax.inject.Inject

@HiltViewModel
class SearchVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var query by mutableStateOf("")
    var selectedLabel by mutableStateOf("All")
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var results by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var filters by mutableStateOf<List<FfiSearchFilter>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var hasSearched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var lastQueryInternal = ""
    val lastQuery: String get() = lastQueryInternal

    /** Run a fresh search. [filter] == null means "All". */
    fun runSearch(filter: FfiSearchFilter?) {
        val q = query.trim()
        if (q.isEmpty()) return
        lastQueryInternal = q
        selectedLabel = filter?.label ?: "All"
        loading = true; loadingMore = false; hasSearched = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val res = if (filter != null && !filter.params.isNullOrBlank()) {
                    bridge.searchFiltered(q, filter.params!!)
                } else {
                    bridge.search(q)
                }
                results = res.results
                filters = res.filters
                continuation = res.continuation.takeIf { res.results.isNotEmpty() }
            } catch (e: Throwable) {
                error = e.message ?: "Search failed"
                results = emptyList()
            } finally { loading = false }
        }
    }

    /** Submit from keyboard — resets to the All filter. */
    fun submit() = runSearch(null)

    fun loadMore() {
        val token = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val res = bridge.searchNext(token)
                val before = results.size
                results = results.appendUnique(res.results)
                continuation = if (res.results.isEmpty() || results.size == before) null else res.continuation
            } catch (_: Throwable) {
                continuation = null
            } finally { loadingMore = false }
        }
    }
}

@Composable
fun SearchScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: SearchVm = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menuItem by remember { mutableStateOf<FfiSearchResult?>(null) }
    var showAddTrack by remember { mutableStateOf<Track?>(null) }

    fun open(r: FfiSearchResult) {
        when {
            r.videoId != null -> {
                val track = r.toDomainTrack()
                player.playTrack(track, PlayOrigin.Search(vm.lastQuery.ifEmpty { vm.query }, vm.selectedLabel))
            }
            r.browseId != null && r.category.contains("Album", true) -> nav.navigate("album/${r.browseId}")
            r.browseId != null && r.category.contains("Artist", true) -> nav.navigate("artist/${r.browseId}")
            r.browseId != null && r.category.contains("Playlist", true) -> nav.navigate("playlist/${r.browseId}")
            r.browseId != null -> nav.navigate("list/${r.browseId}")
            r.playlistId != null -> nav.navigate("playlist/${r.playlistId}")
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Outer Scaffold padding already clears the status bar — 4dp only.
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "search_header") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Search",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text("Songs, artists, albums…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = vm.selectedLabel == "All",
                            onClick = { if (vm.selectedLabel != "All") scope.launch { vm.runSearch(null) } },
                            label = { Text("All") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    items(vm.filters, key = { it.label }) { f ->
                        FilterChip(
                            selected = vm.selectedLabel == f.label,
                            onClick = { if (vm.selectedLabel != f.label) scope.launch { vm.runSearch(f) } },
                            label = { Text(f.label.replaceFirstChar { it.uppercase() }) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        when {
            !vm.hasSearched -> item(key = "search_idle") {
                EmptyState("Search Mediyo", "Find songs, artists, albums and playlists")
            }
            vm.loading -> items(7, key = { "skel_$it" }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                        Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
                    }
                }
            }
            vm.error != null -> item(key = "error") { ErrorState(vm.error ?: "Search failed") { vm.runSearch(null) } }
            vm.results.isEmpty() -> item(key = "empty") {
                EmptyState("No results for \"${vm.lastQuery}\"", "Try different keywords or another filter")
            }
            else -> {
                // Top-result shelf rows first, featured — the rest as plain rows.
                val top = vm.results.filter { it.isTopResult }
                val rest = vm.results.filterNot { it.isTopResult }
                if (top.isNotEmpty()) {
                    item(key = "top_header") {
                        Text(
                            "Top result",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    // One card holds every YT top result: the first is featured
                    // big, the rest follow as a compact list inside the same card.
                    item(key = "top_card") {
                        TopResultsCard(
                            items = top,
                            lastQuery = vm.lastQuery.ifEmpty { vm.query },
                            selectedLabel = vm.selectedLabel,
                            player = player,
                            onOpen = { open(it) },
                            onMenu = { menuItem = it }
                        )
                    }
                }
                items(rest.size, key = { i ->
                    rest[i].let { it.videoId ?: it.browseId ?: it.playlistId }?.let { "r_${it}_$i" } ?: "r_$i"
                }) { i ->
                    val r = rest[i]
                    ResultRow(item = r, onClick = { open(r) }, onMenu = { menuItem = r })
                }
            }
        }

        item(key = "search_footer") { LoadingFooter(vm.loadingMore && !vm.loading) }
    }

    InfiniteScrollHandler(
        listState = listState,
        itemCount = vm.results.size + 2,
        enabled = vm.continuation != null && !vm.loading && !vm.loadingMore && vm.error == null
    ) { vm.loadMore() }

    menuItem?.let { m ->
        com.teamshryne.mediyo.core.design.MediaMenuSheet(
            item = m, show = true, onDismiss = { menuItem = null }, nav = nav,
            onPlayTracks = { tracks, idx, origin -> player.playTracks(tracks, idx, origin) },
            onEnqueueTracks = { player.addToQueueList(it) },
            onAddToPlaylist = { showAddTrack = it },
            onPlayNext = { player.addNext(it) },
            onAddToQueue = { player.addToQueue(it) },
            onComments = { vid -> nav.navigate("comments/$vid") }
        )
    }
    showAddTrack?.let { t -> com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddTrack = null }) }
}

/**
 * All YT top results in a single card: the first result is featured big,
 * every further top result follows as a compact row inside the same card.
 * Order mirrors the server response ([items] must already be in YT order).
 */
@Composable
private fun TopResultsCard(
    items: List<FfiSearchResult>,
    lastQuery: String,
    selectedLabel: String,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    onOpen: (FfiSearchResult) -> Unit,
    onMenu: (FfiSearchResult) -> Unit
) {
    if (items.isEmpty()) return
    val first = items[0]
    val rest = items.drop(1)
    val firstIsArtist = first.category.contains("Artist", true)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 4.dp)
    ) {
        TopResultHero(
            item = first,
            circularArt = firstIsArtist,
            actionIcon = if (firstIsArtist) Icons.Filled.OpenInNew else Icons.Filled.PlayArrow,
            onClick = { onOpen(first) },
            onMenu = { onMenu(first) },
            onPlay = {
                if (firstIsArtist || first.videoId == null) onOpen(first)
                else {
                    player.playTrack(
                        first.toDomainTrack(),
                        PlayOrigin.Search(lastQuery, selectedLabel)
                    )
                }
            }
        )
        rest.forEach { r ->
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            TopResultMiniRow(item = r, onClick = { onOpen(r) }, onMenu = { onMenu(r) })
        }
    }
}

@Composable
private fun TopResultHero(
    item: FfiSearchResult,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    circularArt: Boolean = false,
    actionIcon: ImageVector = Icons.Filled.PlayArrow
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .let { if (circularArt) it.clip(CircleShape) else it.clip(RoundedCornerShape(12.dp)) }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Top result • ${item.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(item.artists.joinToString().ifBlank { item.category })
                    if (!item.duration.isNullOrBlank()) append(" • ${item.duration}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                Icon(actionIcon, contentDescription = "Open", modifier = Modifier.size(24.dp))
            }
            TrackOverflowIcon(onClick = onMenu)
        }
    }
}

@Composable
private fun TopResultMiniRow(item: FfiSearchResult, onClick: () -> Unit, onMenu: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .let {
                    if (item.category.contains("Artist", true)) it.clip(CircleShape)
                    else it.clip(RoundedCornerShape(8.dp))
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(
                        item.artists.joinToString()
                            .ifBlank { item.info?.takeIf { it.isNotBlank() } ?: item.category }
                    )
                    append("  •  ")
                    append(item.category)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!item.duration.isNullOrBlank()) {
            Text(
                item.duration.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TrackOverflowIcon(onClick = onMenu)
    }
}

@Composable
private fun ResultRow(item: FfiSearchResult, onClick: () -> Unit, onMenu: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .let {
                    if (item.category.contains("Artist", true)) it.clip(CircleShape)
                    else it.clip(RoundedCornerShape(8.dp))
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(
                        item.artists.joinToString()
                            .ifBlank { item.info?.takeIf { it.isNotBlank() } ?: item.category }
                    )
                    append("  •  ")
                    append(item.category)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            item.duration.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrackOverflowIcon(onClick = onMenu)
    }
}
