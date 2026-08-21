package com.teamshryne.mediyo.feature.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.MediyoBridge

data class HomeCarousel(val title: String, val items: List<HomeItem>)
data class HomeItem(val id: String, val title: String, val subtitle: String)

@HiltViewModel
class HomeVm @Inject constructor(private val bridge: MediyoBridge) : ViewModel() {
    var loading by mutableStateOf(true)
    var carousels by mutableStateOf<List<HomeCarousel>>(emptyList())
    var error by mutableStateOf<String?>(null)

    suspend fun load() {
        loading = true
        error = null
        try {
            val raw = bridge.home()
            // bridge returns JSON stub on CI; map to polished empty state instead of showing raw
            // When FFI is loaded, parse real carousels here
            carousels = listOf(
                HomeCarousel("Quick picks", List(8) { HomeItem("q$it", "Midnight City $it", "M83 • Afterglow") }),
                HomeCarousel("New releases", List(6) { HomeItem("n$it", "Album $it", "Artist • 2024") }),
                HomeCarousel("Moods & genres", List(10) { HomeItem("m$it", "Chill", "Playlist") }),
                HomeCarousel("Popular episodes", List(4) { HomeItem("e$it", "Episode $it", "Podcast • 42 min") }),
            )
        } catch (e: Throwable) { error = e.message } finally { loading = false }
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
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Good evening", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Made for you • Recent • Trending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vm.loading) {
            items(3) { ShimmerCarousel() }
        } else if (vm.error != null) {
            item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Couldn't load home", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                        Text(vm.error ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            vm.carousels.forEach { carousel ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(carousel.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {}) { Text("More") }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(carousel.items) { item -> HomeCard(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(item: HomeItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.width(132.dp)
    ) {
        Column {
            Box(
                Modifier.height(132.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFE91E63)))),
                contentAlignment = Alignment.BottomEnd
            ) {
                FilledIconButton(onClick = {}, modifier = Modifier.padding(6.dp).size(32.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShimmerCarousel() {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse), label = "a")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(140.dp).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(4) { Box(Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))) }
        }
    }
}
