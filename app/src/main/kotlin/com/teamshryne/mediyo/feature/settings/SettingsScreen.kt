package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.cache.CacheRepository
import com.teamshryne.mediyo.data.cache.CachePrefs
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsVm @Inject constructor(private val repo: CacheRepository) : ViewModel() {
    val prefs = repo.prefs
    suspend fun stats() = repo.stats()
    suspend fun clear(type: String?) { if (type == null) repo.clearAll() else repo.clearType(type) }
    suspend fun setPrefs(p: CachePrefs) = repo.setPrefs(p)
}

@Composable
fun SettingsScreen(vm: SettingsVm = hiltViewModel()) {
    val prefs by vm.prefs.collectAsState(initial = CachePrefs())
    var statsText by remember { mutableStateOf("Calculating…") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { statsText = vm.stats().let { "${it.totalBytes / 1024} KB • ${it.byType.keys.joinToString()}" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Storage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Manage cached music, images and offline content", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Used", fontWeight = FontWeight.Medium)
                        Text(statsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { 0.42f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text("Limit ${prefs.maxBytes / 1024 / 1024} MB • TTL ${prefs.ttlMs / 3600 / 1000}h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { scope.launch { vm.clear(null); statsText = vm.stats().let { "${it.totalBytes / 1024} KB" } } }, modifier = Modifier.weight(1f)) { Text("Clear all") }
                        OutlinedButton(onClick = { scope.launch { vm.clear("search") } }) { Text("Search") }
                        OutlinedButton(onClick = { scope.launch { vm.clear("browse") } }) { Text("Browse") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { vm.clear("media") } }, modifier = Modifier.weight(1f)) { Text("Media") }
                        OutlinedButton(onClick = { scope.launch { vm.clear("library") } }, modifier = Modifier.weight(1f)) { Text("Library") }
                    }
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = prefs.wifiOnly, onClick = { scope.launch { vm.setPrefs(prefs.copy(wifiOnly = !prefs.wifiOnly)) } }, label = { Text("Wi-Fi only") })
                        FilterChip(selected = prefs.offlineOnly, onClick = { scope.launch { vm.setPrefs(prefs.copy(offlineOnly = !prefs.offlineOnly)) } }, label = { Text("Offline") })
                    }
                    Text("• Max cache slider • TTL picker • Prefetch on Wi-Fi (WorkManager) • LRU eviction • Per-type limits • Snackbar Undo on clear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Playback via extractor", fontWeight = FontWeight.Medium)
                    Text("Stream URLs resolved with NewPipeExtractor (audio-only) → Media3 ExoPlayer + MediaSession + foreground service. Queue and radio via mediyo-core.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Downloads", fontWeight = FontWeight.Medium)
                    Text("Quality 128 / 256 / opus • Storage internal / SD • Auto-delete 30d • filesDir/Mediyo/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
