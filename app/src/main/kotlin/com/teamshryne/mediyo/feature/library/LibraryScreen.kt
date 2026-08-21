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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.auth.AuthRepository

@HiltViewModel
class LibraryVm @Inject constructor(val auth: AuthRepository) : ViewModel()

@Composable
fun LibraryScreen(vm: LibraryVm = hiltViewModel()) {
    val auth by vm.auth.flow.collectAsState(initial = com.teamshryne.mediyo.data.auth.AuthState())
    var showLogin by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Library", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (!auth.isLoggedIn) {
            item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sign in", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Button(onClick = { showLogin = true }, Modifier.fillMaxWidth()) { Text("Sign in with YouTube") }
                        if (showLogin) Card(shape = RoundedCornerShape(12.dp)) { Text("WebView login", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf("Liked" to Icons.Filled.Favorite, "Playlists" to Icons.Filled.PlaylistPlay, "History" to Icons.Filled.History)) { (t, ic) ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(140.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(ic, null, tint = MaterialTheme.colorScheme.primary)
                            Text(t, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
