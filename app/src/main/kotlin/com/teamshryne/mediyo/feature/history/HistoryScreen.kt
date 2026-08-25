package com.teamshryne.mediyo.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.TrackMenuSheet
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class HistoryVm @Inject constructor(
    private val repo: HistoryRepository,
    private val likeRepo: LikeRepository
) : ViewModel() {
    val history = repo.flowHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun remove(videoId: String) { viewModelScope.launch { repo.remove(videoId) } }
    fun clearAll() { viewModelScope.launch { repo.clearAll() } }
    suspend fun isLiked(videoId: String) = likeRepo.isLiked(videoId)
    fun toggleLike(track: Track) { viewModelScope.launch { likeRepo.toggle(track) } }
}

@Composable
fun HistoryScreen(
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: HistoryVm = hiltViewModel()
) {
    val history by vm.history.collectAsState()
    val playingId = player?.state?.collectAsState()?.value?.videoId
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddSheet by remember { mutableStateOf<Track?>(null) }
    var showClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav?.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = { showClear = true }) { Text("Clear") }
        }
        Text("${history.size} plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No history yet", style = MaterialTheme.typography.titleMedium)
                    Text("Songs you play will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // group by date
            val grouped = remember(history) { groupByDate(history) }
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.forEach { (label, items) ->
                    item(key = "header_$label") {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    itemsIndexed(items, key = { _, e -> e.videoId + e.lastPlayedAt }) { idx, e ->
                        val isPlaying = playingId == e.videoId
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val tracks = items.map { it.toTrack() }
                                val pos = items.indexOf(e)
                                player?.playTracks(tracks, pos, PlayOrigin.History(label))
                            }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(model = e.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.bodyMedium, color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Text("${e.artist} • ${e.playCount} plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (isPlaying) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            TrackOverflowIcon(onClick = { menuTrack = e.toTrack() })
                        }
                    }
                }
            }
        }
    }

    menuTrack?.let { t ->
        var liked by remember { mutableStateOf(false) }
        LaunchedEffect(t.videoId) { liked = t.videoId?.let { vm.isLiked(it) } ?: false }
        TrackMenuSheet(
            track = t, show = true, onDismiss = { menuTrack = null },
            isLiked = liked,
            onLike = { vm.toggleLike(t) },
            onAddToPlaylist = { showAddSheet = t; menuTrack = null },
            onPlayNext = { player?.addNext(t) },
            onAddToQueue = { player?.addToQueue(t) },
            onComments = { nav?.navigate("comments/${t.videoId}") },
            onRemove = { vm.remove(t.videoId ?: ""); menuTrack = null }
        )
    }
    showAddSheet?.let { t -> AddToPlaylistSheet(track = t, onDismiss = { showAddSheet = null }) }
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("Clear history?") }, confirmButton = { Button(onClick = { vm.clearAll(); showClear = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } })
}

private fun HistoryEntryEntity.toTrack() = Track(videoId = videoId, title = title, artists = if (artist.isBlank()) emptyList() else artist.split(",").map { it.trim() }, artworkUrl = artworkUrl, album = album, duration = duration, category = category)

private fun groupByDate(list: List<HistoryEntryEntity>): List<Pair<String, List<HistoryEntryEntity>>> {
    val now = Calendar.getInstance()
    val fmt = SimpleDateFormat("MMM dd", Locale.getDefault())
    val groups = linkedMapOf<String, MutableList<HistoryEntryEntity>>()
    for (e in list) {
        val cal = Calendar.getInstance().apply { timeInMillis = e.lastPlayedAt }
        val label = when {
            isSameDay(cal, now) -> "Today"
            isYesterday(cal, now) -> "Yesterday"
            isSameWeek(cal, now) -> "This week"
            else -> fmt.format(Date(e.lastPlayedAt))
        }
        groups.getOrPut(label) { mutableListOf() }.add(e)
    }
    return groups.toList()
}
private fun isSameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR)==b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)
private fun isYesterday(a: Calendar, now: Calendar): Boolean {
    val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(a, y)
}
private fun isSameWeek(a: Calendar, b: Calendar) = a.get(Calendar.WEEK_OF_YEAR)==b.get(Calendar.WEEK_OF_YEAR) && a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
