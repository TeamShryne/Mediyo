package com.teamshryne.mediyo.feature.library

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
import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.launch

@HiltViewModel class LibraryVm @Inject constructor(val auth: AuthRepository): ViewModel()

@Composable fun LibraryScreen(vm: LibraryVm = hiltViewModel()){
    val auth by vm.auth.flow.collectAsState(initial= com.teamshryne.mediyo.data.auth.AuthState())
    var showWebView by remember{ mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{ Text("Library", style=MaterialTheme.typography.headlineSmall) }
        item{
            if(!auth.isLoggedIn){
                Card{ Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Anonymous mode — login to see playlists, liked songs, history, podcasts (FEmusic_*).")
                    Button(onClick={ showWebView=true }){ Text("Login via WebView") }
                }}
            } else {
                Text("Logged in — landing/playlists/songs/albums/artists/history/podcasts via mediyo-core")
            }
        }
        if(showWebView) item{ Card{ Text("WebView login: music.youtube.com → extract Cookie/SAPISID/visitorData/pageId → DataStore → MediyoBridge", Modifier.padding(12.dp)) } }
    }
}
