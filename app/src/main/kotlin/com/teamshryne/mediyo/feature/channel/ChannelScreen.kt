package com.teamshryne.mediyo.feature.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.MediaCard
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.data.mediyo.FfiCarousel
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class ChannelVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var title by mutableStateOf(""); var subs by mutableStateOf<String?>(null)
    var avatar by mutableStateOf<String?>(null); var banner by mutableStateOf<String?>(null)
    var emptyMessage by mutableStateOf<String?>(null)
    var sections by mutableStateOf<List<FfiCarousel>>(emptyList())
    fun load(id: String) {
        loading = true; error = null
        viewModelScope.launch { events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.VIEW_ARTIST, browseId = id) }
        viewModelScope.launch {
            try {
                val p = bridge.channel(id)
                title = p.title; subs = p.subscriberCount
                avatar = p.avatarUrl; banner = p.bannerUrl
                emptyMessage = p.emptyMessage?.ifBlank { null }
                sections = p.sections
            } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
}

private fun sectionRoute(browseId: String, params: String?, title: String): String {
    val sb = StringBuilder("section/channel/$browseId")
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
fun ChannelScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: ChannelVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }
    var menuItem by remember { mutableStateOf<FfiSearchResult?>(null) }
    var showAddTrack by remember { mutableStateOf<Track?>(null) }

    fun handle(r: FfiSearchResult, shelf: List<FfiSearchResult>) {
        when {
            r.videoId != null -> {
                if (player?.state?.value?.videoId == r.videoId) player?.toggle()
                else player?.playFromWithOrigin(shelf, r, PlayOrigin.GenericList(browseId))
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
            val headerVisible = com.teamshryne.mediyo.core.design.rememberHeaderVisible(listState)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp)) {
                stickyHeader(key = "topbar") {
                    com.teamshryne.mediyo.core.design.CollapsingTopBar(
                        title = vm.title.ifBlank { "Channel" },
                        visible = headerVisible,
                        onBack = { nav?.popBackStack() }
                    )
                }
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = vm.banner,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        IconButton(
                            onClick = { nav?.popBackStack() },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = vm.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.title.ifBlank { "Channel" },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            vm.subs?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (vm.emptyMessage != null && vm.sections.isEmpty()) {
                    item {
                        Text(
                            vm.emptyMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                vm.sections.forEachIndexed { ci, c ->
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
                            val songs = c.items.all { it.videoId != null }
                            if (songs) {
                                Column {
                                    c.items.forEachIndexed { i, r ->
                                        TrackRow(
                                            item = r,
                                            isPlaying = playingId != null && playingId == r.videoId,
                                            number = i + 1,
                                            showArtwork = true,
                                            trailing = { com.teamshryne.mediyo.core.design.TrackOverflowIcon(onClick = { menuItem = r }) }
                                        ) { handle(r, c.items) }
                                    }
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(c.items) { r ->
                                        MediaCard(
                                            title = r.title,
                                            subtitle = r.artists.joinToString().ifBlank { r.info ?: r.category },
                                            artworkUrl = r.thumbnails.bestThumbUrl(),
                                            round = false,
                                            onClick = { handle(r, c.items) }
                                        )
                                    }
                                }
                            }
                        }
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
                    onComments = { m.videoId?.let { nav?.navigate("comments/$it") } }
                )
            }
            showAddTrack?.let { t ->
                com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = t, onDismiss = { showAddTrack = null })
            }
        }
    }
}
