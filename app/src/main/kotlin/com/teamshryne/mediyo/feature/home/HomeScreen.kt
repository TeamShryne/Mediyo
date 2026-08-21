package com.teamshryne.mediyo.feature.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.MediyoBridge

@HiltViewModel
class HomeVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true)
    var carousels by mutableStateOf<List<uniffi.mediyo_ffi.FfiCarousel>>(emptyList())
    var error by mutableStateOf<String?>(null)

    suspend fun load() {
        loading = true; error = null
        try {
            val page = bridge.home()
            carousels = page.carousels
            if (carousels.isEmpty()) error = null
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load"
            carousels = emptyList()
        } finally { loading = false }
    }
}

@Composable
fun HomeScreen(vm: HomeVm = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Text("Home", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        when {
            vm.loading -> items(2) { ShimmerCarousel() }
            vm.error != null -> item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Failed to load", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(vm.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(onClick = { /* TODO retry */ }) { Text("Retry") }
                    }
                }
            }
            vm.carousels.isEmpty() -> item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nothing here yet", fontWeight = FontWeight.Medium)
                        Text("Pull to refresh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                vm.carousels.forEach { c ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(c.title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(c.items) { r ->
                                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.width(132.dp)) {
                                        Column {
                                            AsyncImage(model = r.thumbnails.firstOrNull()?.url, contentDescription = null, modifier = Modifier.height(132.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                            Column(Modifier.padding(10.dp)) {
                                                Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                                Text(r.artists.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerCarousel() {
    val a by rememberInfiniteTransition(label = "").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(a)))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(4) { Box(Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(a))) } }
    }
}
