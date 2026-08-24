package com.teamshryne.mediyo.feature.playlist

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.TrackRow
import com.teamshryne.mediyo.core.design.immersiveBrush
import com.teamshryne.mediyo.core.design.rememberDominantColors
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel class PlaylistVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var title by mutableStateOf(""); var subtitle by mutableStateOf("")
    var thumb by mutableStateOf<String?>(null)
    var tracks by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    suspend fun load(id: String) {
        loading = true; error = null
        try {
            val p = bridge.playlist(id)
            title = p.title; subtitle = p.trackCount ?: ""
            thumb = p.thumbnails.firstOrNull()?.url; tracks = p.tracks
        } catch (e: Throwable) { error = e.message } finally { loading = false }
    }
}

@Composable
fun PlaylistScreen(
    browseId: String,
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: PlaylistVm = hiltViewModel()
) {
    LaunchedEffect(browseId) { vm.load(browseId) }

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load(browseId) }
        else -> {
            val dominant = rememberDominantColors(vm.thumb)
            val playingId = player?.state?.collectAsState()?.value?.videoId
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(immersiveBrush(dominant))
                            .padding(top = 4.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { nav?.popBackStack() },
                            modifier = Modifier.align(Alignment.Start).padding(start = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
                        if (vm.subtitle.isNotBlank()) {
                            Text(
                                vm.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(18.dp))
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
                                FilledIconButton(
                                    onClick = { vm.tracks.firstOrNull()?.let { player?.playFrom(vm.tracks, it) } },
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
                                        vm.tracks.firstOrNull()?.let { player?.playFrom(vm.tracks, it) }
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
                    TrackRow(item = t, isPlaying = playingId != null && playingId == t.videoId, number = i + 1, showArtwork = true) {
                        t.videoId?.let { player?.playFrom(vm.tracks, t) }
                    }
                }
            }
        }
    }
}
