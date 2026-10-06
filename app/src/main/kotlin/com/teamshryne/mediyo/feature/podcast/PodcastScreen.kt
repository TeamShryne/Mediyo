package com.teamshryne.mediyo.feature.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.data.mediyo.FfiPodcastOption
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class PodcastVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val savedRepo: com.teamshryne.mediyo.domain.repository.SavedCollectionRepository,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false); var continuation by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var title by mutableStateOf("")
    var author by mutableStateOf<String?>(null)
    var thumb by mutableStateOf<String?>(null)
    var description by mutableStateOf<String?>(null)
    var sorts by mutableStateOf<List<FfiPodcastOption>>(emptyList())
    var filters by mutableStateOf<List<FfiPodcastOption>>(emptyList())
    fun isSavedFlow(id: String) = savedRepo.isSavedFlow(id)
    fun toggleSave(id: String) {
        viewModelScope.launch {
            try {
                savedRepo.toggle(
                    browseId = id,
                    kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PODCAST,
                    title = title.ifBlank { "Podcast" },
                    subtitle = author,
                    artworkUrl = thumb,
                    trackCountText = items.size.takeIf { it > 0 }?.let { "$it episodes" }
                )
            } catch (_: Throwable) { }
        }
    }
    fun load(id: String) {
        loading = true; error = null; continuation = null
        viewModelScope.launch { events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.VIEW_PODCAST, browseId = id) }
        viewModelScope.launch {
            try {
                val p = bridge.podcast(id)
                items = p.items; continuation = p.continuation.takeIf { p.items.isNotEmpty() }
                title = p.title.ifBlank { "Podcast" }
                author = p.author
                thumb = p.artworkUrl
                description = p.description
                sorts = p.sorts
                filters = p.filters
                try {
                    if (savedRepo.isSaved(id)) {
                        savedRepo.save(
                            browseId = id,
                            kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PODCAST,
                            title = title, subtitle = author, artworkUrl = thumb,
                            trackCountText = p.items.size.takeIf { it > 0 }?.let { "$it episodes" }
                        )
                    }
                } catch (_: Throwable) { }
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }

    /** Sort / played-filter switch: replaces the episode list. */
    fun applyOption(token: String) {
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = bridge.podcastNext(token)
                items = p.items
                continuation = p.continuation
                if (p.sorts.isNotEmpty()) sorts = p.sorts
                if (p.filters.isNotEmpty()) filters = p.filters
            } catch (e: Throwable) { error = e.message } finally { loadingMore = false }
        }
    }

    fun loadMore() {
        val token = continuation?.takeIf { it.isNotEmpty() } ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = bridge.podcastNext(token)
                if (p.reloaded) {
                    items = p.items
                } else {
                    val before = items.size
                    items = items.appendUnique(p.items)
                    if (p.items.isEmpty() || items.size == before) {
                        continuation = null
                        return@launch
                    }
                }
                continuation = p.continuation
                if (p.sorts.isNotEmpty()) sorts = p.sorts
                if (p.filters.isNotEmpty()) filters = p.filters
            } catch (_: Throwable) { continuation = null } finally { loadingMore = false }
        }
    }
}

fun episodeRouteId(r: FfiSearchResult): String =
    r.detailId.ifBlank { r.videoId.orEmpty() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: PodcastVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null && vm.items.isEmpty() -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val headerVisible = com.teamshryne.mediyo.core.design.rememberHeaderVisible(listState, 170.dp)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)) {
                stickyHeader(key = "topbar") {
                    com.teamshryne.mediyo.core.design.CollapsingTopBar(
                        title = if (vm.title.isNotBlank()) vm.title else "Podcast",
                        visible = headerVisible,
                        onBack = { nav?.popBackStack() }
                    )
                }
                item(key = "podcast_header") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { nav?.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(Modifier.weight(1f))
                            val saveFlow = remember(browseId) { vm.isSavedFlow(browseId) }
                            val isSaved by saveFlow.collectAsState(initial = false)
                            IconButton(onClick = { vm.toggleSave(browseId) }) {
                                Icon(
                                    if (isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                    contentDescription = if (isSaved) "Remove from library" else "Add to library"
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = vm.thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (vm.title.isNotBlank()) vm.title else "Podcast",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis
                                )
                                vm.author?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "${vm.items.size} episodes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        vm.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            var expanded by remember(browseId) { mutableStateOf(false) }
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)
                                    .clickable { expanded = !expanded }
                            )
                        }
                        if (vm.sorts.isNotEmpty() || vm.filters.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(vm.sorts, key = { "s:${it.label}" }) { o ->
                                    FilterChip(
                                        selected = o.selected,
                                        onClick = { if (!o.selected) vm.applyOption(o.token) },
                                        label = { Text(o.label) },
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                                items(vm.filters, key = { "f:${it.label}" }) { o ->
                                    FilterChip(
                                        selected = o.selected,
                                        onClick = { if (!o.selected) vm.applyOption(o.token) },
                                        label = { Text(o.label) },
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                items(vm.items.size, key = { i ->
                    val r = vm.items[i]
                    "ep_${r.videoId ?: r.detailId.ifBlank { null } ?: i}_$i"
                }) { i ->
                    val r = vm.items[i]
                    EpisodeRow(
                        item = r,
                        isPlaying = playingId != null && playingId == r.videoId,
                        onClick = {
                            if (playingId != null && playingId == r.videoId) player?.toggle()
                            else player?.playFromWithOrigin(vm.items, r, PlayOrigin.Podcast(browseId))
                        },
                        onOpen = {
                            val id = episodeRouteId(r)
                            if (id.isNotBlank()) nav?.navigate("episode/$id")
                        }
                    )
                }
                item(key = "podcast_footer") { LoadingFooter(vm.loadingMore) }
            }

            InfiniteScrollHandler(
                listState = listState,
                itemCount = vm.items.size + 2,
                enabled = vm.continuation != null && !vm.loading && !vm.loadingMore
            ) { vm.loadMore() }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: FfiSearchResult,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(item.info?.takeIf { it.isNotBlank() }, item.duration?.takeIf { it.isNotBlank() })
                    .joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
