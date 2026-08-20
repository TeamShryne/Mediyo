package com.teamshryne.mediyo.feature.home

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
import com.teamshryne.mediyo.data.mediyo.MediyoBridge

@HiltViewModel class HomeVm @Inject constructor(private val bridge: MediyoBridge): ViewModel() {
    var state by mutableStateOf("Loading home…"); private set
    suspend fun load(){ state = try { bridge.home().take(200) } catch(e: Throwable){ e.message ?: "error" } }
}

@Composable fun HomeScreen(vm: HomeVm = hiltViewModel()){
    LaunchedEffect(Unit){ vm.load() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{ Text("Home", style=MaterialTheme.typography.headlineSmall) }
        item{ Card{ Text(vm.state, Modifier.padding(12.dp)) } }
        item{ Text("Carousels from mediyo-core (gridRenderer + musicCarouselShelf) will render here with Paging + Coil + shimmer. Pull-to-refresh + ViewAll.", style=MaterialTheme.typography.bodySmall) }
    }
}
