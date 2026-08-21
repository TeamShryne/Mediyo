package com.teamshryne.mediyo.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.auth.AuthRepository

data class LibrarySection(val title: String, val icon: ImageVector, val count: String)

@HiltViewModel
class LibraryVm @Inject constructor(val auth: AuthRepository) : ViewModel()

@Composable
fun LibraryScreen(vm: LibraryVm = hiltViewModel()) {
    val auth by vm.auth.flow.collectAsState(initial = com.teamshryne.mediyo.data.auth.AuthState())
    var showWebView by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (auth.isLoggedIn) "Signed in • Sync with YouTube Music"
                    else "Anonymous • Sign in to sync playlists and history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!auth.isLoggedIn) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connect Your Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Sign in to see liked songs, playlists, albums, subscriptions and history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Button(onClick = { showWebView = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign in with YouTube") }
                        if (showWebView) {
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("WebView", fontWeight = FontWeight.Medium)
                                    Text("music.youtube.com → extract Cookie / SAPISID / visitorData / pageId → Encrypted DataStore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Preview", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            val sections = listOf(
                LibrarySection("Liked songs", Icons.Filled.Favorite, "128"),
                LibrarySection("Playlists", Icons.Filled.PlaylistPlay, "14"),
                LibrarySection("History", Icons.Filled.History, "•"),
                LibrarySection("Podcasts", Icons.Filled.PlaylistPlay, "6"),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sections) { s ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(160.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(s.title, fontWeight = FontWeight.Medium)
                            Text(s.count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Downloads", fontWeight = FontWeight.Medium); Text("Offline tracks • 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = {}) { Text("Manage") }
                }
            }
        }
    }
}
