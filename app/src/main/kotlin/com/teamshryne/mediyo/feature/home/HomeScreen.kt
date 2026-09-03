package com.teamshryne.mediyo.feature.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.*
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uniffi.mediyo_ffi.FfiSearchResult
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val charts: List<FfiSearchResult> = emptyList(),
    val newAlbums: List<FfiSearchResult> = emptyList(),
    val newVideos: List<FfiSearchResult> = emptyList(),
)

@HiltViewModel
class HomeVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var state by mutableStateOf(HomeState())
        private set

    fun load(isRefresh: Boolean = false) {
        if (state.loading || state.refreshing) {
            // allow initial load when empty (loading=true but no data yet)
            if (state.charts.isNotEmpty() || state.newAlbums.isNotEmpty()) return
        }
        if (isRefresh) state = state.copy(refreshing = true, error = null)
        else state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            // Each source isolated so partial success still renders
            val exploreResult = runCatching { bridge.explore() }
            val chartsResult = runCatching { bridge.listPage("FEmusic_charts", null) }

            val explore = exploreResult.getOrNull()
            val chartsPage = chartsResult.getOrNull()

            if (explore == null && chartsPage == null) {
                val msg = exploreResult.exceptionOrNull()?.message
                    ?: chartsResult.exceptionOrNull()?.message
                    ?: "Failed to load"
                state = state.copy(loading = false, refreshing = false, error = msg)
                return@launch
            }

            val newAlbums = explore?.carousels?.find { it.title == "New albums & singles" }?.items.orEmpty()
            val newVideos = explore?.carousels?.find { it.title == "New music videos" }?.items.orEmpty()
            val trending = explore?.carousels?.find { it.title == "Trending" }?.items.orEmpty()
            val chartsItems = chartsPage?.items?.takeIf { it.isNotEmpty() } ?: trending

            state = HomeState(
                loading = false,
                refreshing = false,
                charts = chartsItems,
                newAlbums = newAlbums,
                newVideos = newVideos,
            )
        }
    }

    fun refresh() = load(isRefresh = true)
}

@Composable
fun HomeScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: HomeVm = hiltViewModel()
) {
    LaunchedEffect(Unit) { vm.load() }
    val greeting = rememberGreeting()
    val s = vm.state

    fun open(r: FfiSearchResult, title: String) {
        // Prefer browse with params when present
        if (r.browseId != null && r.browseParams != null) {
            val enc = Uri.encode(r.browseParams)
            nav.navigate("list/${r.browseId}?params=$enc")
            return
        }
        when {
            r.videoId != null -> player.playTrack(r.toDomainTrack(), PlayOrigin.HomeShelf(title))
            r.browseId != null && r.category.contains("Album", true) -> nav.navigate("album/${r.browseId}")
            r.browseId != null && r.category.contains("Artist", true) -> nav.navigate("artist/${r.browseId}")
            r.browseId != null && r.category.contains("Playlist", true) -> nav.navigate("playlist/${r.browseId}")
            r.browseId != null -> nav.navigate("list/${r.browseId}")
            r.playlistId != null -> nav.navigate("playlist/${r.playlistId}")
            else -> nav.navigate("search?q=${Uri.encode(r.title)}")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("What do you want to listen to?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { nav.navigate("profile") }, contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        when {
            s.loading -> {
                items(3) { ShimmerShelf(round = it == 1) }
            }
            s.error != null && s.charts.isEmpty() && s.newAlbums.isEmpty() -> {
                item { ErrorState(s.error ?: "Unknown error") { vm.load() } }
            }
            s.charts.isEmpty() && s.newAlbums.isEmpty() && s.newVideos.isEmpty() -> {
                item { EmptyState("Nothing here yet", "Check your connection") { vm.load() } }
            }
            else -> {
                if (s.error != null) {
                    item { Text(s.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
                if (s.charts.isNotEmpty()) {
                    item(key = "charts") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Charts")
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(s.charts) { r ->
                                    MediaCard(title = r.title, subtitle = r.artists.joinToString(), artworkUrl = r.thumbnails.bestThumbUrl(), round = false) { open(r, "Charts") }
                                }
                            }
                        }
                    }
                }
                if (s.newAlbums.isNotEmpty()) {
                    item(key = "new_albums") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("New albums & singles")
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(s.newAlbums) { r ->
                                    MediaCard(title = r.title, subtitle = r.artists.joinToString(), artworkUrl = r.thumbnails.bestThumbUrl(), round = false) { open(r, "New albums") }
                                }
                            }
                        }
                    }
                }
                if (s.newVideos.isNotEmpty()) {
                    item(key = "new_videos") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("New music videos")
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(s.newVideos) { r ->
                                    MediaCard(title = r.title, subtitle = r.artists.joinToString(), artworkUrl = r.thumbnails.bestThumbUrl(), round = false) { open(r, "New videos") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerShelf(round: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).width(150.dp).height(22.dp).clip(RoundedCornerShape(8.dp)).shimmer())
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(4) { MediaCardShimmer(round = round) }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
    }
}
