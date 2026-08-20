package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.cache.CacheRepository
import com.teamshryne.mediyo.data.cache.CachePrefs
import kotlinx.coroutines.launch

@HiltViewModel class SettingsVm @Inject constructor(private val repo: CacheRepository): ViewModel(){
    val prefs = repo.prefs
    suspend fun stats() = repo.stats()
    suspend fun clear(type: String?){ if(type==null) repo.clearAll() else repo.clearType(type) }
    suspend fun setPrefs(p: CachePrefs) = repo.setPrefs(p)
}

@Composable fun SettingsScreen(vm: SettingsVm = hiltViewModel()){
    val prefs by vm.prefs.collectAsState(initial=CachePrefs())
    var statsText by remember{ mutableStateOf("loading stats…") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit){ statsText = vm.stats().let{ "Total ${it.totalBytes/1024}KB — ${it.byType}" } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{ Text("Storage & Cache — full control", style=MaterialTheme.typography.headlineSmall) }
        item{ Card{ Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(statsText, style=MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress={ (statsText.length % 100)/100f }, modifier=Modifier.fillMaxWidth())
            Text("Max ${(prefs.maxBytes/1024/1024)}MB • TTL ${prefs.ttlMs/1000/3600}h • WiFi-only ${prefs.wifiOnly} • Offline ${prefs.offlineOnly}", style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={ scope.launch{ vm.clear(null); statsText = vm.stats().let{ "Total ${it.totalBytes}B" } } }){ Text("Clear all") }
                OutlinedButton(onClick={ scope.launch{ vm.clear("search") } }){ Text("Clear search") }
                OutlinedButton(onClick={ scope.launch{ vm.clear("browse") } }){ Text("Clear browse") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={ scope.launch{ vm.clear("media") } }){ Text("Clear media") }
                OutlinedButton(onClick={ scope.launch{ vm.clear("library") } }){ Text("Clear library") }
            }
        } } }
        item{ Card{ Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Controls", style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=prefs.wifiOnly, onClick={ scope.launch{ vm.setPrefs(prefs.copy(wifiOnly=!prefs.wifiOnly)) } }, label={Text("Wi-Fi only")})
                FilterChip(selected=prefs.offlineOnly, onClick={ scope.launch{ vm.setPrefs(prefs.copy(offlineOnly=!prefs.offlineOnly)) } }, label={Text("Offline only")})
            }
            Text("Max cache slider, TTL picker, prefetch on Wi-Fi (WorkManager) — LRU eviction, per-type limits, auto-cleaner when limit hit, Snackbar Undo.")
        } } }
        item{ Card{ Column(Modifier.padding(12.dp)){ Text("Downloads (NewPipe)"); Text("Quality 128/256/opus • Wi-Fi only • Storage location internal/SD • Manage list + auto-delete 30d — filesDir/Mediyo/", style=MaterialTheme.typography.bodySmall) } } }
        item{ Text("Playback: NewPipeExtractor for stream URL only — ExoPlayer + MediaSession + foreground service + notification (queued via mediyo-core next/extendQueue).", style=MaterialTheme.typography.bodySmall) }
    }
}
