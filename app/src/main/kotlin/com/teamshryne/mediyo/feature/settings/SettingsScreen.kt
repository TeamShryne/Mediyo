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
import com.teamshryne.mediyo.core.design.SectionHeader
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
    var stats by remember { mutableStateOf("—") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { stats = vm.stats().let { "${it.totalBytes / 1024} KB" } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item { SectionHeader("Storage & cache") }
        item {
            Card(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cache used", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stats, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { 0.4f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        "Limit ${prefs.maxBytes / 1024 / 1024} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { scope.launch { vm.clear(null); stats = vm.stats().let { "${it.totalBytes / 1024} KB" } } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear all") }
                        OutlinedButton(
                            onClick = { scope.launch { vm.clear("search") } },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Search") }
                        OutlinedButton(
                            onClick = { scope.launch { vm.clear("browse") } },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Browse") }
                    }
                }
            }
        }
        item { SectionHeader("Network") }
        item {
            Card(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.wifiOnly,
                        onClick = { scope.launch { vm.setPrefs(prefs.copy(wifiOnly = !prefs.wifiOnly)) } },
                        label = { Text("Wi-Fi only") }
                    )
                    FilterChip(
                        selected = prefs.offlineOnly,
                        onClick = { scope.launch { vm.setPrefs(prefs.copy(offlineOnly = !prefs.offlineOnly)) } },
                        label = { Text("Offline") }
                    )
                }
            }
        }
    }
}
