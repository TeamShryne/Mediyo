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
        loading = true; error = null
        try {
            bridge.home()
            carousels = listOf(
                HomeCarousel("Quick picks", List(8) { HomeItem("q$it", "Midnight City $it", "M83") }),
                HomeCarousel("New releases", List(6) { HomeItem("n$it", "Album $it", "Artist") }),
                HomeCarousel("Moods", List(8) { HomeItem("m$it", "Chill", "") }),
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
        item { Text("Home", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (vm.loading) items(2) { ShimmerCarousel() }
        else if (vm.error != null) item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(vm.error ?: "", Modifier.padding(16.dp))
            }
        } else {
            vm.carousels.forEach { c ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {}) { Text("More") }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(c.items) { HomeCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(item: HomeItem) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.width(132.dp)) {
        Column {
            Box(
                Modifier.height(132.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFE91E63)))),
                Alignment.BottomEnd
            ) { FilledIconButton(onClick = {}, Modifier.padding(6.dp).size(32.dp)) { Icon(Icons.Filled.PlayArrow, null) } }
            Column(Modifier.padding(10.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                if (item.subtitle.isNotEmpty()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
