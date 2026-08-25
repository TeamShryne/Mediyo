package com.teamshryne.mediyo.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.MediaCard
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import uniffi.mediyo_ffi.FfiSearchResult

@HiltViewModel class ArtistVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false); var continuation by mutableStateOf<String?>(null)
    var name by mutableStateOf(""); var subs by mutableStateOf<String?>(null)
    var thumb by mutableStateOf<String?>(null)
    var topSongs by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var carousels by mutableStateOf<List<uniffi.mediyo_ffi.FfiCarousel>>(emptyList())
    fun load(id: String) {
        loading = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val p = bridge.artist(id)
                name = p.name; subs = p.subscriberCount
                thumb = p.thumbnails.bestThumbUrl()
                topSongs = p.topSongs; carousels = p.carousels
                continuation = p.continuation.takeIf { p.topSongs.isNotEmpty() }
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
                val before = topSongs.size
                topSongs = topSongs.appendUnique(p.items)
                continuation = if (p.items.isEmpty() || topSongs.size == before) null else p.continuation
            } catch (_: Throwable) { continuation = null } finally { loadingMore = false }
        }
    }
}

@Composable
fun ArtistScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: ArtistVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }

    fun handle(r: FfiSearchResult, shelf: List<FfiSearchResult>) {
        when {
            r.videoId != null -> player?.playFrom(shelf, r)
            r.browseId != null && r.category.contains("Album", true) -> nav?.navigate("album/${r.browseId}")
            r.browseId != null && r.category.contains("Playlist", true) -> nav?.navigate("playlist/${r.browseId}")
            r.browseId != null -> nav?.navigate("list/${r.browseId}")
        }
    }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                // ── Hero ──
                item {
                    Box(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = vm.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to MaterialTheme.colorScheme.background))
                        )
                        IconButton(
                            onClick = { nav?.popBackStack() },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                vm.name,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            vm.subs?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledIconButton(
                                    onClick = { vm.topSongs.firstOrNull()?.let { player?.playFrom(vm.topSongs, it) } },
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(52.dp)
                                ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(26.dp)) }
                                OutlinedIconButton(
                                    onClick = {
                                        player?.toggleShuffle()
                                        vm.topSongs.firstOrNull()?.let { player?.playFrom(vm.topSongs, it) }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp)
                                ) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle") }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (vm.topSongs.isNotEmpty()) {
                    item { SectionHeader("Popular", Modifier.padding(top = 22.dp)) }
                    items(vm.topSongs.size) { i ->
                        val t = vm.topSongs[i]
                        TrackRow(item = t, isPlaying = playingId != null && playingId == t.videoId, number = i + 1, showArtwork = true) {
                            handle(t, vm.topSongs)
                        }
                    }
                }

                vm.carousels.forEachIndexed { ci, c ->
                    item(key = "shelf_$ci") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(c.title, Modifier.padding(top = 18.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(c.items) { r ->
                                    MediaCard(
                                        title = r.title,
                                        subtitle = r.artists.joinToString(),
                                        artworkUrl = r.thumbnails.bestThumbUrl(),
                                        round = r.category.contains("Artist", true),
                                        onClick = { handle(r, c.items) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "artist_footer") { LoadingFooter(vm.loadingMore) }
            }

            InfiniteScrollHandler(
                listState = listState,
                itemCount = vm.topSongs.size + vm.carousels.size + 1,
                enabled = vm.continuation != null && !vm.loading
            ) { vm.loadMore() }
        }
    }
}
