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
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeVm @Inject constructor(
    repo: HomeRepository,
    private val events: UserEventRepository
) : ViewModel() {
    val recent = repo.flowRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val saved = repo.flowSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logTap(track: Track) {
        viewModelScope.launch {
            events.log(
                UserEventTypes.HOME_TAP, videoId = track.videoId,
                label = "Continue listening", meta = "title=${track.title.take(80)}"
            )
        }
    }

    fun logSavedTap(item: SavedCollectionEntity) {
        viewModelScope.launch {
            events.log(
                UserEventTypes.HOME_TAP, browseId = item.browseId,
                label = "Your library", meta = "kind=${item.kind};title=${item.title.take(80)}"
            )
        }
    }
}

@Composable
fun HomeScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: HomeVm = hiltViewModel()
) {
    val greeting = rememberGreeting()
    val recent by vm.recent.collectAsState()
    val saved by vm.saved.collectAsState()
    val playingId = player.state.collectAsState().value.videoId

    fun openSaved(item: SavedCollectionEntity) {
        vm.logSavedTap(item)
        when (item.kind) {
            SavedCollectionEntity.ALBUM -> nav.navigate("album/${item.browseId}")
            SavedCollectionEntity.PODCAST -> nav.navigate("podcast/${item.browseId}")
            else -> nav.navigate("playlist/${item.browseId}")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
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
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { nav.navigate("profile") }, contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        if (recent.isEmpty() && saved.isEmpty()) {
            item { EmptyHome { nav.navigate("search") } }
        } else {
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Continue listening")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(recent, key = { it.uniqueKey() }) { t ->
                                val playing = playingId != null && playingId == t.videoId
                                MediaCard(
                                    title = t.title,
                                    subtitle = t.artists.joinToString(),
                                    artworkUrl = t.artworkUrl,
                                    round = false
                                ) {
                                    vm.logTap(t)
                                    if (playing) player.toggle()
                                    else player.playTrack(t, PlayOrigin.History("Home"))
                                }
                            }
                        }
                    }
                }
            }
            if (saved.isNotEmpty()) {
                item(key = "saved") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Your library")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(saved, key = { it.browseId }) { s ->
                                MediaCard(
                                    title = s.title,
                                    subtitle = s.subtitle ?: s.trackCountText ?: "",
                                    artworkUrl = s.artworkUrl,
                                    round = false
                                ) { openSaved(s) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Search for music and it will show up here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Button(onClick = onSearch) { Text("Search") }
    }
}
