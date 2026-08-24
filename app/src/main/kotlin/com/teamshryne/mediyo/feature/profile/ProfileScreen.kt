package com.teamshryne.mediyo.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ErrorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.MediyoBridge

@HiltViewModel class ProfileVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true); var error by mutableStateOf<String?>(null)
    var name by mutableStateOf(""); var handle by mutableStateOf<String?>(null); var photo by mutableStateOf<String?>(null)
    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try { val a = bridge.account(); name = a.name; handle = a.handle; photo = a.photoUrl } catch (e: Throwable) { error = e.message } finally { loading = false }
        }
    }
}

@Composable
fun ProfileScreen(nav: androidx.navigation.NavController? = null, vm: ProfileVm = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = { nav?.popBackStack() }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        when {
            vm.loading -> Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) { CircularProgressIndicator() }
            vm.error != null -> ErrorState(vm.error ?: "Failed to load") { vm.load() }
            else -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = vm.photo,
                    contentDescription = vm.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(104.dp).clip(CircleShape).padding(4.dp)
                )
                Text(vm.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                vm.handle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
