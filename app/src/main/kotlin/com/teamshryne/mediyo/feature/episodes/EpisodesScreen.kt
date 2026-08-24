package com.teamshryne.mediyo.feature.episodes

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
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class EpisodesVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    fun load(id: String) {
        loading = true; error = null
        viewModelScope.launch {
            try { items = bridge.listPage(id, null).items } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
}

@Composable
fun EpisodesScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: EpisodesVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val playingId = player?.state?.collectAsState()?.value?.videoId
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
                item { SectionHeader("Episodes", Modifier.padding(bottom = 8.dp)) }
                items(vm.items.size) { i ->
                    val r = vm.items[i]
                    TrackRow(item = r, isPlaying = playingId != null && playingId == r.videoId, showArtwork = true) {
                        player?.playFrom(vm.items, r)
                    }
                }
            }
        }
    }
}
