package com.teamshryne.mediyo.feature.podcast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class PodcastVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val savedRepo: com.teamshryne.mediyo.domain.repository.SavedCollectionRepository
) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var loadingMore by mutableStateOf(false); var continuation by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    var title by mutableStateOf("")
    var thumb by mutableStateOf<String?>(null)
    fun isSavedFlow(id: String) = savedRepo.isSavedFlow(id)
    fun toggleSave(id: String) {
        viewModelScope.launch {
            try {
                savedRepo.toggle(
                    browseId = id,
                    kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PODCAST,
                    title = title.ifBlank { "Podcast" },
                    subtitle = null,
                    artworkUrl = thumb,
                    trackCountText = items.size.takeIf { it > 0 }?.let { "$it episodes" }
                )
            } catch (_: Throwable) { }
        }
    }
    fun load(id: String) {
        loading = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val p = bridge.podcast(id)
                items = p.items; continuation = p.continuation.takeIf { p.items.isNotEmpty() }
                // Derive show title/artwork from the first episode (podcast page has no header).
                val first = p.items.firstOrNull()
                title = first?.album?.takeIf { it.isNotBlank() }
                    ?: first?.artists?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: "Podcast"
                thumb = first?.thumbnails?.bestThumbUrl()
                // Background refresh of the library snapshot when this podcast is saved.
                try {
                    if (savedRepo.isSaved(id)) {
                        savedRepo.save(
                            browseId = id,
                            kind = com.teamshryne.mediyo.data.local.SavedCollectionEntity.PODCAST,
                            title = title, artworkUrl = thumb,
                            trackCountText = p.items.size.takeIf { it > 0 }?.let { "$it episodes" }
                        )
                    }
                } catch (_: Throwable) { }
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
fun PodcastScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: PodcastVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
                item(key = "podcast_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { nav?.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        SectionHeader(
                            if (vm.title.isNotBlank()) vm.title else "Podcast",
                            Modifier.weight(1f).padding(bottom = 0.dp)
                        )
                        val saveFlow = remember(browseId) { vm.isSavedFlow(browseId) }
                        val isSaved by saveFlow.collectAsState(initial = false)
                        IconButton(onClick = { vm.toggleSave(browseId) }) {
                            Icon(
                                if (isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                contentDescription = if (isSaved) "Remove from library" else "Add to library"
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(vm.items.size) { i ->
                    val r = vm.items[i]
                    TrackRow(item = r, isPlaying = playingId != null && playingId == r.videoId, showArtwork = true) {
                        player?.playFrom(vm.items, r)
                    }
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
