package com.teamshryne.mediyo.feature.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@HiltViewModel class ListVm @Inject constructor(private val bridge: MediyoBridge): ViewModel(){
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<uniffi.mediyo_ffi.FfiSearchResult>>(emptyList())
    suspend fun load(id:String, params:String?){ loading=true; try{ items=bridge.listPage(id, params).items } catch(e:Throwable){error=e.message} finally{loading=false} }
}

@Composable fun GenericListScreen(browseId:String, params:String? = null, vm: ListVm = hiltViewModel()){
    LaunchedEffect(browseId, params){ vm.load(browseId, params) }
    when{
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center){ CircularProgressIndicator() }
        vm.error!=null -> Card(Modifier.padding(16.dp).fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){ Text(vm.error?:"", Modifier.padding(16.dp)) }
        else -> LazyColumn(contentPadding=PaddingValues(bottom=80.dp)){
            items(vm.items){ r ->
                ListItem(headlineContent={Text(r.title, maxLines=1)}, supportingContent={Text(r.artists.joinToString(), maxLines=1)}, leadingContent={AsyncImage(model=r.thumbnails.firstOrNull()?.url, contentDescription=null, modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale=ContentScale.Crop)}, trailingContent={Text(r.duration?:"", style=MaterialTheme.typography.bodySmall)})
                Divider()
            }
        }
    }
}
