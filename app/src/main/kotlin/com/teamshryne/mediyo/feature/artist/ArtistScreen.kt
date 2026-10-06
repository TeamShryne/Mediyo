package com.teamshryne.mediyo.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.teamshryne.mediyo.core.design.MediaCard
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult

@HiltViewModel class ArtistVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val artistRepo: com.teamshryne.mediyo.domain.repository.ArtistRepository,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var name by mutableStateOf(""); var subs by mutableStateOf<String?>(null)
    var thumb by mutableStateOf<String?>(null)
    var browseId by mutableStateOf<String?>(null)
    var topSongs by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var topSongsViewAll by mutableStateOf<com.teamshryne.mediyo.data.mediyo.FfiViewAll?>(null)
    var carousels by mutableStateOf<List<com.teamshryne.mediyo.data.mediyo.FfiCarousel>>(emptyList())
    /** Inline preview; the full Popular list lives behind the view-all page. */
    val previewSongs: List<FfiSearchResult> get() = topSongs.take(4)
    fun load(id: String) {
        loading = true; error = null
        browseId = id
        viewModelScope.launch { events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.VIEW_ARTIST, browseId = id) }
        viewModelScope.launch {
            try {
                val p = bridge.artist(id)
                name = p.name; subs = p.subscriberCount
                thumb = p.thumbnails.bestThumbUrl()
                topSongs = p.topSongs; topSongsViewAll = p.topSongsViewAll; carousels = p.carousels
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
    fun isFollowedFlow(id: String) = artistRepo.isFollowedFlow(id)

    fun toggleFollow() {
        val id = browseId ?: return
        viewModelScope.launch {
            artistRepo.toggle(id, name, thumb, subs)
        }
    }
}

/** Section-screen route for a shelf "show all" (params/title encoded, optional). */
private fun sectionRoute(browseId: String, params: String?, title: String): String {
    val sb = StringBuilder("section/artist/$browseId")
    var first = true
    if (!params.isNullOrBlank()) {
        sb.append("?params=${android.net.Uri.encode(params)}")
        first = false
    }
    sb.append(if (first) "?" else "&").append("title=${android.net.Uri.encode(title)}")
    return sb.toString()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
            r.videoId != null -> {
                val id = vm.browseId
                if (player != null && id != null) {
                    player.playFromWithOrigin(shelf, r, com.teamshryne.mediyo.domain.model.PlayOrigin.ArtistTop(id, vm.name))
                } else {
                    player?.playFrom(shelf, r)
                }
            }
            else -> com.teamshryne.mediyo.core.design.openRoute(r)?.let { nav?.navigate(it) }
        }
    }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val headerVisible = com.teamshryne.mediyo.core.design.rememberHeaderVisible(listState, 220.dp)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)) {
                stickyHeader(key = "topbar") {
                    com.teamshryne.mediyo.core.design.CollapsingTopBar(
                        title = vm.name,
                        visible = headerVisible,
                        onBack = { nav?.popBackStack() }
                    )
                }
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledIconButton(
                                    onClick = {
                                        vm.topSongs.firstOrNull()?.let {
                                            val id = vm.browseId
                                            if (player != null && id != null) {
                                                player.playFromWithOrigin(vm.topSongs, it, com.teamshryne.mediyo.domain.model.PlayOrigin.ArtistTop(id, vm.name))
                                            } else {
                                                player?.playFrom(vm.topSongs, it)
                                            }
                                        }
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
                                        vm.topSongs.firstOrNull()?.let {
                                            val id = vm.browseId
                                            if (player != null && id != null) {
                                                player.playFromWithOrigin(vm.topSongs, it, com.teamshryne.mediyo.domain.model.PlayOrigin.ArtistTop(id, vm.name))
                                            } else {
                                                player?.playFrom(vm.topSongs, it)
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp)
                                ) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle") }
                                // Local-only subscribe — Room is source of truth, no server call.
                                val followFlow = remember(browseId) { vm.isFollowedFlow(browseId) }
                                val isFollowed by followFlow.collectAsState(initial = false)
                                if (isFollowed) {
                                    FilledTonalButton(
                                        onClick = { vm.toggleFollow() },
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                    ) { Text("Following") }
                                } else {
                                    OutlinedButton(
                                        onClick = { vm.toggleFollow() },
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                    ) { Text("Follow") }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (vm.topSongs.isNotEmpty()) {
                    item {
                        val preview = vm.previewSongs
                        SectionHeader(
                            "Popular",
                            Modifier.padding(top = 22.dp),
                            trailing = vm.topSongsViewAll?.let { va ->
                                {
                                    IconButton(onClick = {
                                        nav?.navigate(sectionRoute(va.browseId, va.params, "Popular"))
                                    }) {
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = "Show all popular songs",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    }
                    items(vm.previewSongs.size) { i ->
                        val t = vm.previewSongs[i]
                        TrackRow(item = t, isPlaying = playingId != null && playingId == t.videoId, number = i + 1, showArtwork = true) {
                            handle(t, vm.topSongs)
                        }
                    }
                }

                vm.carousels.forEachIndexed { ci, c ->
                    item(key = "shelf_$ci") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(
                                c.title,
                                Modifier.padding(top = 18.dp),
                                trailing = c.viewAll?.let { va ->
                                    {
                                        IconButton(onClick = {
                                            nav?.navigate(sectionRoute(va.browseId, va.params, c.title))
                                        }) {
                                            Icon(
                                                Icons.Filled.ChevronRight,
                                                contentDescription = "Show all ${c.title}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
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

            }
        }
    }
}
