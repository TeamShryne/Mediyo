package com.teamshryne.mediyo.feature.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.MediyoBridge

@HiltViewModel class AlbumVm @Inject constructor(private val bridge: MediyoBridge): ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var title by mutableStateOf(""); var artist by mutableStateOf(""); var year by mutableStateOf(""); var thumb by mutableStateOf<String?>(null)
    var tracks by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    suspend fun load(id: String){ loading=true; try{ val p=bridge.album(id); title=p.title; artist=p.artist?:""; year=p.year?:""; thumb=p.thumbnails.firstOrNull()?.url; tracks=p.tracks } catch(e:Throwable){error=e.message} finally{loading=false} }
}

@Composable fun AlbumScreen(browseId: String, vm: AlbumVm = hiltViewModel()){
    LaunchedEffect(browseId){ vm.load(browseId) }
    when{
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center){ CircularProgressIndicator() }
        vm.error!=null -> Card(Modifier.padding(16.dp).fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){ Text(vm.error?:"", Modifier.padding(16.dp)) }
        else -> LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){
            item{
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp), horizontalAlignment=Alignment.CenterHorizontally){
                    AsyncImage(model=vm.thumb, contentDescription=null, modifier=Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)), contentScale=ContentScale.Crop)
                    Text(vm.title, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold, modifier=Modifier.fillMaxWidth())
                    Text("${vm.artist} • ${vm.year}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.fillMaxWidth())
                    Button(onClick={}, modifier=Modifier.fillMaxWidth()){ Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play") }
                }
            }
            items(vm.tracks){ t ->
                ListItem(headlineContent={Text(t.title, maxLines=1)}, supportingContent={Text(t.artists.joinToString(), maxLines=1)}, leadingContent={AsyncImage(model=t.thumbnails.firstOrNull()?.url, contentDescription=null, modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))}, trailingContent={Text(t.duration?:"", style=MaterialTheme.typography.bodySmall)})
                Divider()
            }
        }
    }
}
