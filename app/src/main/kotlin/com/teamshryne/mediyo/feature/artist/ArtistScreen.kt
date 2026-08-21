package com.teamshryne.mediyo.feature.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@HiltViewModel class ArtistVm @Inject constructor(private val bridge: MediyoBridge): ViewModel(){
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var name by mutableStateOf(""); var subs by mutableStateOf<String?>(null); var thumb by mutableStateOf<String?>(null)
    var topSongs by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    var carousels by mutableStateOf<List<uniffi.mediyo_ffi.FfiCarousel>>(emptyList())
    suspend fun load(id:String){ loading=true; try{ val p=bridge.artist(id); name=p.name; subs=p.subscriberCount; thumb=p.thumbnails.firstOrNull()?.url; topSongs=p.topSongs; carousels=p.carousels } catch(e:Throwable){error=e.message} finally{loading=false} }
}

@Composable fun ArtistScreen(browseId:String, vm: ArtistVm = hiltViewModel()){
    LaunchedEffect(browseId){ vm.load(browseId) }
    when{
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center){ CircularProgressIndicator() }
        vm.error!=null -> Card(Modifier.padding(16.dp).fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){ Text(vm.error?:"", Modifier.padding(16.dp)) }
        else -> LazyColumn(contentPadding=PaddingValues(bottom=80.dp), verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)){
                    AsyncImage(model=vm.thumb, contentDescription=null, modifier=Modifier.size(120.dp).clip(CircleShape), contentScale=ContentScale.Crop)
                    Text(vm.name, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    vm.subs?.let{ Text(it, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ Button(onClick={}){ Text("Play") }; OutlinedButton(onClick={}){ Text("Shuffle") } }
                }
            }
            if(vm.topSongs.isNotEmpty()){
                item{ Text("Top songs", Modifier.padding(horizontal=16.dp), style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold) }
                items(vm.topSongs){ t ->
                    ListItem(headlineContent={Text(t.title, maxLines=1)}, supportingContent={Text(t.artists.joinToString(), maxLines=1)}, leadingContent={AsyncImage(model=t.thumbnails.firstOrNull()?.url, contentDescription=null, modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))})
                }
            }
            vm.carousels.forEach{ c ->
                item{
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(c.title, Modifier.padding(horizontal=16.dp), style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.SemiBold)
                        LazyRow(contentPadding=PaddingValues(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            items(c.items){ r ->
                                Card(shape=RoundedCornerShape(12.dp), modifier=Modifier.width(132.dp)){
                                    Column{ AsyncImage(model=r.thumbnails.firstOrNull()?.url, contentDescription=null, modifier=Modifier.height(132.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale=ContentScale.Crop); Text(r.title, Modifier.padding(8.dp), maxLines=1, style=MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
