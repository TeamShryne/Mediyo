package com.teamshryne.mediyo.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch

@HiltViewModel class ProfileVm @Inject constructor(private val bridge: MediyoBridge): ViewModel(){
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var name by mutableStateOf(""); var handle by mutableStateOf<String?>(null); var photo by mutableStateOf<String?>(null)
    suspend fun load(){ loading=true; try{ val a=bridge.account(); name=a.name; handle=a.handle; photo=a.photoUrl } catch(e:Throwable){error=e.message} finally{loading=false} }
}

@Composable fun ProfileScreen(vm: ProfileVm = hiltViewModel()){
    LaunchedEffect(Unit){ vm.load() }
    LazyColumn(contentPadding=PaddingValues(bottom=80.dp), modifier=Modifier.fillMaxSize()){
        item{
            when{
                vm.loading -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center){ CircularProgressIndicator() }
                vm.error!=null -> Card(Modifier.padding(16.dp).fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){ Text(vm.error?:"", Modifier.padding(16.dp)) }
                else -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)){
                    AsyncImage(model=vm.photo, contentDescription=null, modifier=Modifier.size(96.dp).clip(CircleShape), contentScale=ContentScale.Crop)
                    Text(vm.name, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    vm.handle?.let{ Text(it, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Card(shape=RoundedCornerShape(16.dp)){ Column(Modifier.padding(16.dp)){ Text("Library • Playlists • History", style=MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}
