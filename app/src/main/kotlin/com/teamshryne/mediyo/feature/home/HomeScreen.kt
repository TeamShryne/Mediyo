package com.teamshryne.mediyo.feature.home

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
import androidx.compose.material3.Icon
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
import javax.inject.Inject
import uniffi.mediyo_ffi.FfiSearchResult

@HiltViewModel
class HomeVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var carousels by mutableStateOf<List<uniffi.mediyo_ffi.FfiCarousel>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    fun load() {
        loading = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val page = bridge.home()
                carousels = page.carousels.filter { it.items.isNotEmpty() }
                continuation = page.continuation.takeIf { carousels.isNotEmpty() }
                if (carousels.isEmpty()) error = null
            } catch (e: Throwable) {
                error = e.message ?: "Failed to load"
                carousels = emptyList()
            } finally { loading = false }
        }
    }

    fun loadMore() {
        val token = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val page = bridge.homeContinue(token)
                val fresh = page.carousels.filter { it.items.isNotEmpty() }
                if (fresh.isNotEmpty()) carousels = carousels + fresh
                continuation = if (fresh.isEmpty()) null else page.continuation
            } catch (_: Throwable) {
                continuation = null
            } finally { loadingMore = false }
        }
    }
}

@Composable
fun HomeScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: HomeVm = hiltViewModel()
) {
    LaunchedEffect(Unit) { vm.load() }
    val greeting = rememberGreeting()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun open(r: FfiSearchResult, shelf: List<FfiSearchResult>, title: String) {
        when {
            r.videoId != null -> {
                // Generate fresh radio queue from single track (RDAMVM) — not shelf list, per always-radio spec
                val track = r.toDomainTrack()
                player.playTrack(track, PlayOrigin.HomeShelf(title))
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        // ── Header ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "What do you want to listen to?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { nav.navigate("profile") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when {
            vm.loading -> items(3) { ShimmerShelf(round = it == 1) }
            vm.error != null -> item { ErrorState(vm.error ?: "Unknown error") { vm.load() } }
            vm.carousels.isEmpty() -> item {
                EmptyState("Nothing here yet", "Pull down or reopen the app to refresh your feed")
            }
            else -> {
                // Quick picks from the first shelf
                val firstItems = vm.carousels.firstOrNull()?.items.orEmpty()
                if (firstItems.size >= 4) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader("Quick picks")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(firstItems.take(8)) { r ->
                                    MediaTile(
                                        title = r.title,
                                        artworkUrl = r.thumbnails.bestThumbUrl()
                                    ) { open(r, firstItems, "Quick picks") }
                                }
                            }
                        }
                    }
                }

                vm.carousels.forEachIndexed { ci, c ->
                    item(key = "shelf_$ci") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(c.title)
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
                                        onClick = { open(r, c.items, c.title) }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "home_footer") { LoadingFooter(vm.loadingMore) }
            }
        }
    }

    InfiniteScrollHandler(
        listState = listState,
        itemCount = vm.carousels.size + 1,
        enabled = vm.continuation != null && !vm.loading && !vm.loadingMore && vm.error == null
    ) { vm.loadMore() }
}

@Composable
private fun ShimmerShelf(round: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(150.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(4) { MediaCardShimmer(round = round) }
        }
    }
}
