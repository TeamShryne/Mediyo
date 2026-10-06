package com.teamshryne.mediyo.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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

@HiltViewModel class PlaylistVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val savedRepo: com.teamshryne.mediyo.domain.repository.SavedCollectionRepository,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false); var continuation by mutableStateOf<String?>(null)
    var title by mutableStateOf(""); var stats by mutableStateOf("")
    var owner by mutableStateOf<String?>(null); var ownerAvatar by mutableStateOf<String?>(null)
    var description by mutableStateOf<String?>(null)
    var radioPlaylistId by mutableStateOf<String?>(null)
    var thumb by mutableStateOf<String?>(null)
    var tracks by mutableStateOf<List<com.teamshryne.mediyo.data.mediyo.FfiSearchResult>>(emptyList())
    fun isSavedFlow(id: String) = savedRepo.isSavedFlow(id)
    fun toggleSave(id: String) {
        viewModelScope.launch {
            try {
                savedRepo.toggle(
                    browseId = id,
                    kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PLAYLIST,
                    title = title,
                    subtitle = owner,
                    artworkUrl = thumb,
                    trackCountText = stats.ifBlank { null }
                )
            } catch (_: Throwable) { }
        }
    }
    fun load(id: String) {
        android.util.Log.d("PlaylistVm","load id=$id")
        loading = true; error = null; continuation = null
        viewModelScope.launch { events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.VIEW_PLAYLIST, browseId = id) }
        viewModelScope.launch {
            try {
                val p = bridge.playlist(id)
                title = p.title
                stats = listOfNotNull(p.trackCount, p.totalDuration).joinToString("  •  ")
                owner = p.owner; ownerAvatar = p.ownerAvatar
                description = p.description
                radioPlaylistId = p.radioPlaylistId
                thumb = p.thumbnails.bestThumbUrl(); tracks = p.tracks
                continuation = p.continuation.takeIf { p.tracks.isNotEmpty() }
                // Background refresh of the library snapshot when this playlist is saved.
                try {
                    if (savedRepo.isSaved(id)) {
                        savedRepo.save(
                            browseId = id,
                            kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PLAYLIST,
                            title = p.title, subtitle = p.owner,
                            artworkUrl = p.thumbnails.bestThumbUrl(),
                            trackCountText = p.trackCount
                        )
                    }
                } catch (_: Throwable) { }
            } catch (e: Throwable) {
                error = e.message
            } finally { loading = false }
        }
    }
    fun loadMore() {
        val token = continuation?.takeIf { it.isNotEmpty() } ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = bridge.playlistNext(token)
                // Defensive: playlist continuations must be tracks (videoId != null).
                val filtered = p.items.filter { it.videoId != null }
                val before = tracks.size
                tracks = tracks.appendUnique(filtered)
                continuation = if (filtered.isEmpty() || tracks.size == before) null else p.continuation
            } catch (_: Throwable) {
                continuation = null
            } finally { loadingMore = false }
        }
    }

    /** Start the playlist's radio mix (first track as seed). */
    fun playRadio(player: com.teamshryne.mediyo.feature.player.PlayerViewModel?) {
        val pid = radioPlaylistId ?: return
        val seed = tracks.firstOrNull { it.videoId != null }?.videoId ?: return
        viewModelScope.launch {
            try {
                val q = bridge.getQueue(seed, pid)
                val radio = q.items.map { it.toDomainTrack() }.filter { it.videoId != null }
                if (radio.isNotEmpty()) {
                    player?.playTracks(radio, 0, com.teamshryne.mediyo.domain.model.PlayOrigin.Radio(seed))
                }
            } catch (_: Throwable) { }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: PlaylistVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }
    var menuItem by remember { mutableStateOf<com.teamshryne.mediyo.data.mediyo.FfiSearchResult?>(null) }
    var showAddTrack by remember { mutableStateOf<Track?>(null) }
    val menuScope = rememberCoroutineScope()
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val dominant = rememberDominantColors(vm.thumb)
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val headerVisible = com.teamshryne.mediyo.core.design.rememberHeaderVisible(listState, 170.dp)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)) {
                stickyHeader(key = "topbar") {
                    com.teamshryne.mediyo.core.design.CollapsingTopBar(
                        title = vm.title,
                        visible = headerVisible,
                        onBack = { nav?.popBackStack() }
                    )
                }
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(immersiveBrush(dominant))
                            .padding(top = 4.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { nav?.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            val saveFlow = remember(browseId) { vm.isSavedFlow(browseId) }
                            val isSaved by saveFlow.collectAsState(initial = false)
                            IconButton(onClick = { vm.toggleSave(browseId) }) {
                                Icon(
                                    if (isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                    contentDescription = if (isSaved) "Remove from library" else "Add to library",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        AsyncImage(
                            model = vm.thumb,
                            contentDescription = vm.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(horizontal = 48.dp, vertical = 16.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Text(
                            vm.title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        vm.owner?.takeIf { it.isNotBlank() }?.let { owner ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            ) {
                                AsyncImage(
                                    model = vm.ownerAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    owner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (vm.stats.isNotBlank()) {
                            Text(
                                vm.stats,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        vm.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            var expanded by remember(browseId) { mutableStateOf(false) }
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp)
                                    .clickable { expanded = !expanded }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${vm.tracks.size} songs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (vm.radioPlaylistId != null) {
                                    OutlinedIconButton(
                                        onClick = { vm.playRadio(player) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(52.dp)
                                    ) { Icon(Icons.Filled.Radio, contentDescription = "Start radio", modifier = Modifier.size(26.dp)) }
                                }
                                FilledIconButton(
                                    onClick = {
                                        val first = vm.tracks.firstOrNull() ?: return@FilledIconButton
                                        player?.playFromWithOrigin(vm.tracks, first, PlayOrigin.Playlist(browseId, vm.title))
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
                                        val first = vm.tracks.firstOrNull() ?: return@OutlinedIconButton
                                        player?.playFromWithOrigin(vm.tracks, first, PlayOrigin.Playlist(browseId, vm.title))
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp)
                                ) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle") }
                            }
                        }
                    }
                }
                items(vm.tracks.size) { i ->
                    val t = vm.tracks[i]
                    TrackRow(
                        item = t,
                        isPlaying = playingId != null && playingId == t.videoId,
                        number = i + 1,
                        showArtwork = true,
                        trailing = { TrackOverflowIcon(onClick = { menuItem = t }) }
                    ) {
                        t.videoId?.let { player?.playFromWithOrigin(vm.tracks, t, PlayOrigin.Playlist(browseId, vm.title)) }
                    }
                }
                item(key = "playlist_footer") { LoadingFooter(vm.loadingMore) }
            }

            InfiniteScrollHandler(
                listState = listState,
                itemCount = vm.tracks.size + 1,
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
