package com.teamshryne.mediyo.feature.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class ListVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    fun load(id: String, params: String?) {
        loading = true; error = null
        viewModelScope.launch {
            try { items = bridge.listPage(id, params).items } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
}

@Composable
fun GenericListScreen(
    browseId: String,
    params: String? = null,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: ListVm = hiltViewModel()
) {
    LaunchedEffect(browseId, params) { vm.load(browseId, params) }

    fun handle(r: uniffi.mediyo_ffi.FfiSearchResult) {
        when {
            r.videoId != null -> player?.playFrom(vm.items, r)
            r.browseId != null && r.category.contains("Album", true) -> nav?.navigate("album/${r.browseId}")
            r.browseId != null && r.category.contains("Artist", true) -> nav?.navigate("artist/${r.browseId}")
            r.browseId != null -> nav?.navigate("list/${r.browseId}")
            r.playlistId != null -> nav?.navigate("playlist/${r.playlistId}")
        }
    }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId, params) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(vm.items.size) { i ->
                    val r = vm.items[i]
                    TrackRow(item = r, isPlaying = playingId != null && playingId == r.videoId, showArtwork = true) {
                        handle(r)
                    }
                }
            }
        }
    }
}
