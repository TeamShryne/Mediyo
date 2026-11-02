package com.teamshryne.mediyo.music.ui.screens.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.teamshryne.mediyo.R
import com.teamshryne.mediyo.data.mediyo.FfiPodcastOption
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.data.mediyo.toMediaMetadata
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.music.LocalPlayerAwareWindowInsets
import com.teamshryne.mediyo.music.LocalPlayerConnection
import com.teamshryne.mediyo.music.constants.AppBarHeight
import com.teamshryne.mediyo.music.extensions.toMediaItem
import com.teamshryne.mediyo.music.extensions.togglePlayPause
import com.teamshryne.mediyo.music.playback.queues.ListQueue
import com.teamshryne.mediyo.music.ui.component.NavigationTitle
import com.teamshryne.mediyo.music.ui.utils.backToMain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val bridge: MediyoBridge,
) : ViewModel() {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false)
    var continuation by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var title by mutableStateOf("")
    var author by mutableStateOf<String?>(null)
    var thumb by mutableStateOf<String?>(null)
    var description by mutableStateOf<String?>(null)
    var sorts by mutableStateOf<List<FfiPodcastOption>>(emptyList())
    var filters by mutableStateOf<List<FfiPodcastOption>>(emptyList())

    fun load(id: String) {
        loading = true
        error = null
        continuation = null
        viewModelScope.launch {
            try {
                val page = bridge.podcast(id)
                items = page.items
                continuation = page.continuation
                title = page.title.ifBlank { "Podcast" }
                author = page.author
                thumb = page.artworkUrl
                description = page.description
                sorts = page.sorts
                filters = page.filters
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun applyOption(token: String) {
        if (loading || loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val page = bridge.podcastNext(token)
                items = page.items
                continuation = page.continuation
                if (page.sorts.isNotEmpty()) sorts = page.sorts
                if (page.filters.isNotEmpty()) filters = page.filters
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loadingMore = false
            }
        }
    }

    fun loadMore() {
        val token = continuation?.takeIf { it.isNotBlank() } ?: return
        if (loading || loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val page = bridge.podcastNext(token)
                items = if (page.reloaded) page.items else (items + page.items).distinctBy {
                    it.videoId ?: it.detailId.ifBlank { it.title }
                }
                continuation = page.continuation
                if (page.sorts.isNotEmpty()) sorts = page.sorts
                if (page.filters.isNotEmpty()) filters = page.filters
            } catch (_: Throwable) {
                continuation = null
            } finally {
                loadingMore = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    navController: NavController,
    browseId: String,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(browseId) { viewModel.load(browseId) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= viewModel.items.size - 3 && viewModel.continuation != null) {
                    viewModel.loadMore()
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            viewModel.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            viewModel.error != null && viewModel.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(32.dp), Alignment.Center
            ) {
                Text(viewModel.error ?: "Failed to load")
            }
            else -> LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .add(WindowInsets(top = -WindowInsets.systemBars.asPaddingValues().calculateTopPadding() - AppBarHeight))
                    .asPaddingValues()
            ) {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = viewModel.thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    viewModel.title.ifBlank { "Podcast" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                viewModel.author?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "${viewModel.items.size} episodes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        viewModel.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            var expanded by remember(browseId) { mutableStateOf(false) }
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp).clickable { expanded = !expanded }
                            )
                        }
                        if (viewModel.sorts.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(viewModel.sorts, key = { it.label }) { option ->
                                    FilterChip(
                                        selected = option.selected,
                                        onClick = { if (!option.selected) viewModel.applyOption(option.token) },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = viewModel.items,
                    key = { it.videoId ?: it.detailId.ifBlank { it.title } }
                ) { episode ->
                    EpisodeRow(
                        episode = episode,
                        isActive = episode.videoId != null && episode.videoId == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onClick = {
                            if (episode.videoId != null && episode.videoId == mediaMetadata?.id) {
                                playerConnection?.player?.togglePlayPause()
                            } else {
                                playEpisodes(playerConnection, viewModel.items, episode)
                            }
                        },
                        onOpen = {
                            val id = episode.detailId.ifBlank { episode.videoId.orEmpty() }
                            if (id.isNotBlank()) navController.navigate("episode/$id")
                        }
                    )
                }

                if (viewModel.loadingMore) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = { Text(viewModel.title.ifBlank { "Podcast" }) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: FfiSearchResult,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    episode.info?.takeIf { it.isNotBlank() },
                    episode.duration?.takeIf { it.isNotBlank() }
                ).joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(if (isActive && isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun playEpisodes(
    playerConnection: com.teamshryne.mediyo.music.playback.PlayerConnection?,
    items: List<FfiSearchResult>,
    selected: FfiSearchResult,
) {
    val playable = items.filter { !it.videoId.isNullOrBlank() }
    if (playable.isEmpty()) return
    val index = playable.indexOfFirst { it.videoId == selected.videoId }.coerceAtLeast(0)
    val mediaItems = playable.map { it.toMediaMetadata().toMediaItem() }
    playerConnection?.playQueue(ListQueue(title = selected.album ?: "Podcast", items = mediaItems, startIndex = index))
}
