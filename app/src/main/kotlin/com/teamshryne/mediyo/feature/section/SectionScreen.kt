package com.teamshryne.mediyo.feature.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.immersiveBrush
import com.teamshryne.mediyo.core.design.rememberDominantColors
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full "view all" page for any section shelf: artist Popular / Singles &
 * EPs / Videos, home shelves, generic browse lists. Replaces the old bare
 * list screen with a proper header (art, title, count, play/shuffle),
 * per-row overflow menus and pagination.
 */
@HiltViewModel class SectionVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false); var continuation by mutableStateOf<String?>(null)
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

    fun originOf(heading: String) = PlayOrigin.GenericList("$browseId|$heading")

    fun handle(r: uniffi.mediyo_ffi.FfiSearchResult, heading: String) {
        when {
            r.videoId != null -> player?.playFromWithOrigin(vm.items, r, originOf(heading))
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

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId, params) }
        else -> {
            val heading = title?.takeIf { it.isNotBlank() } ?: "Playlist"
            // Song-like pages (Popular, Videos) get numbered rows; mixed or
            // collection pages (discographies) get artwork rows.
            val numbered = remember(vm.items) {
                vm.items.isNotEmpty() && vm.items.all { it.videoId != null }
            }
            val heroArt = remember(vm.items) { vm.items.firstOrNull()?.thumbnails?.bestThumbUrl() }
            val dominant = rememberDominantColors(heroArt)
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "section_header") {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(immersiveBrush(dominant))
                            .padding(top = 4.dp, bottom = 20.dp)
                    ) {
                        IconButton(
                            onClick = { nav?.popBackStack() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!numbered && heroArt != null) {
                                AsyncImage(
                                    model = heroArt,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    heading,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (numbered) "${vm.items.size} songs" else "${vm.items.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledIconButton(
                                    onClick = {
                                        val first = vm.items.firstOrNull { it.videoId != null }
                                            ?: return@FilledIconButton
                                        player?.playFromWithOrigin(vm.items, first, originOf(heading))
                                    },
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
                                        val first = vm.items.firstOrNull { it.videoId != null }
                                            ?: return@OutlinedIconButton
                                        player?.playFromWithOrigin(vm.items, first, originOf(heading))
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp)
                                ) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle") }
                            }
                        }
                    }
                }
                items(vm.items.size) { i ->
                    val r = vm.items[i]
                    TrackRow(
                        item = r,
                        isPlaying = playingId != null && playingId == r.videoId,
                        number = if (numbered) i + 1 else null,
                        showArtwork = !numbered,
                        trailing = { TrackOverflowIcon(onClick = { menuItem = r }) }
                    ) { handle(r, heading) }
                }
                item(key = "section_footer") { LoadingFooter(vm.loadingMore) }
            }

            InfiniteScrollHandler(
                listState = listState,
                itemCount = vm.items.size + 1,
                enabled = vm.continuation != null && !vm.loading && !vm.loadingMore
            ) { vm.loadMore() }

            menuItem?.let { m ->
                val track = m.toDomainTrack()
                com.teamshryne.mediyo.core.design.TrackMenuSheet(
                    track = track, show = true, onDismiss = { menuItem = null },
                    onLike = { player?.toggleLike(track) },
                    onAddToPlaylist = { showAddTrack = track },
                    onPlayNext = { player?.addNext(track) },
                    onAddToQueue = { player?.addToQueue(track) },
                    onGoToAlbum = m.album?.takeIf { it.isNotBlank() }?.let { { menuScope.launch { val id = m.albumId?.takeIf { it.isNotBlank() } ?: menuVm.resolveAlbumId(m); id?.let { nav?.navigate("album/$it") } } } },
                    onGoToArtist = m.artists.firstOrNull()?.takeIf { it.isNotBlank() }?.let { { menuScope.launch { val id = m.artistIds.firstOrNull { it.isNotBlank() } ?: menuVm.resolveArtistId(m); id?.let { nav?.navigate("artist/$it") } } } },
                    onComments = { m.videoId?.let { nav?.navigate("comments/$it") } }
                )
            }
            showAddTrack?.let { t ->
                com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddTrack = null })
            }
        }
    }
}
