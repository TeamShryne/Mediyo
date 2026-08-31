package com.teamshryne.mediyo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryVm @Inject constructor(
    private val playlistRepo: PlaylistRepository,
    likeRepo: LikeRepository,
    historyRepo: HistoryRepository
) : ViewModel() {
    val playlists = playlistRepo.flowPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedCount = likeRepo.countFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val historyCount = historyRepo.countFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var showCreateDialog by mutableStateOf(false)
    var newTitle by mutableStateOf("")
    var newDesc by mutableStateOf("")

    fun createPlaylist(onCreated: (String) -> Unit) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepo.create(t, newDesc.ifBlank { null })
            newTitle = ""; newDesc = ""; showCreateDialog = false
            onCreated(id)
        }
    }
}

@Composable
fun LibraryScreen(
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: LibraryVm = hiltViewModel()
) {
    val playlists by vm.playlists.collectAsState()
    val likedCount by vm.likedCount.collectAsState()
    val historyCount by vm.historyCount.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your Library", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                FilledTonalButton(onClick = { vm.showCreateDialog = true }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("New")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quick access", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp))
                QuickCard("Liked songs", "$likedCount songs", Icons.Filled.Favorite, listOf(Color(0xFF4A2AD6), Color(0xFF8E7BFF)), { nav?.navigate("liked") })
                QuickCard("History", "$historyCount plays", Icons.Filled.History, listOf(Color(0xFF9A3B12), Color(0xFFFF8A5C)), { nav?.navigate("history") })
                QuickCard("Playlists", "${playlists.size} playlists", Icons.Filled.PlaylistPlay, listOf(Color(0xFF0F7A5A), Color(0xFF3DDC97)), {})
            }
        }

        if (playlists.isNotEmpty()) {
            item { Text("Your playlists", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp)) }
            items(playlists, key = { it.id }) { pl ->
                PlaylistRow(pl, onClick = { nav?.navigate("localPlaylist/${pl.id}") })
            }
        }
    }

    if (vm.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { vm.showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = vm.newTitle, onValueChange = { vm.newTitle = it }, label = { Text("Title") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = vm.newDesc, onValueChange = { vm.newDesc = it }, label = { Text("Description (optional)") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.createPlaylist {} }, enabled = vm.newTitle.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { vm.showCreateDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, gradient: List<Color>, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(gradient)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistRow(pl: LocalPlaylistEntity, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(pl.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("${pl.trackCount} songs" + (pl.description?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
