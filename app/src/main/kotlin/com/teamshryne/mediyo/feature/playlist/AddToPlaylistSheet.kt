package com.teamshryne.mediyo.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddToPlaylistVm @Inject constructor(
    private val repo: PlaylistRepository
) : ViewModel() {
    val playlists = repo.flowPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var creating by mutableStateOf(false)
    var newTitle by mutableStateOf("")
    var snackbar by mutableStateOf<String?>(null)

    fun createAndAdd(track: Track, onDone: () -> Unit) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        creating = true
        viewModelScope.launch {
            try {
                val id = repo.create(t)
                repo.addTrack(id, track)
                snackbar = "Added to $t"
                newTitle = ""
                onDone()
            } catch (e: Throwable) {
                snackbar = e.message ?: "Failed"
            } finally { creating = false }
        }
    }

    fun addTo(playlistId: String, track: Track, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.addTrack(playlistId, track)
            snackbar = if (ok) "Added to playlist" else "Already in playlist"
            onDone(ok)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    onDismiss: () -> Unit,
    vm: AddToPlaylistVm = hiltViewModel()
) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add to playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            Text(track.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
            HorizontalDivider()
            if (showCreate) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.newTitle,
                        onValueChange = { vm.newTitle = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(onClick = {
                        vm.createAndAdd(track) { showCreate = false; onDismiss() }
                    }, enabled = vm.newTitle.isNotBlank() && !vm.creating) {
                        if (vm.creating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Check, contentDescription = "Create")
                    }
                    IconButton(onClick = { showCreate = false }) { Icon(Icons.Filled.Close, null) }
                }
            } else {
                FilledTonalButton(onClick = { showCreate = true }, shape = CircleShape) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("New playlist")
                }
            }
            if (playlists.isEmpty() && !showCreate) {
                Text("No playlists yet. Create one above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        PlaylistAddRow(pl) { vm.addTo(pl.id, track) { _ -> onDismiss() } }
                    }
                }
            }
            vm.snackbar?.let {
                Snackbar { Text(it) }
            }
        }
    }
}

@Composable
private fun PlaylistAddRow(pl: LocalPlaylistEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlaylistPlay, null) }
        }
        Column(Modifier.weight(1f)) {
            Text(pl.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text("${pl.trackCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
