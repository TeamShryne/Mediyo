package com.teamshryne.mediyo.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import kotlinx.coroutines.launch

@HiltViewModel
class SearchVm @Inject constructor(val bridge: MediyoBridge) : ViewModel() {
    var query by mutableStateOf("")
    var selected by mutableStateOf("All")
    var loading by mutableStateOf(false)
    var results by mutableStateOf<List<SearchItem>>(emptyList())
    var hasSearched by mutableStateOf(false)
    val filters = listOf("All", "Songs", "Videos", "Albums", "Playlists", "Artists")
    suspend fun search() {
        if (query.isBlank()) return
        loading = true; hasSearched = true
        try {
            bridge.search(query) // FFI call, result parsed in real impl
            results = List(12) { SearchItem("s$it", "Result $it for \"$query\"", if (selected == "All") listOf("Song","Video","Album").random() else selected) }
        } finally { loading = false }
    }
}
data class SearchItem(val id: String, val title: String, val type: String)

@Composable
fun SearchScreen(vm: SearchVm = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    label = { Text("Search songs, albums, artists") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.filters) { f ->
                        FilterChip(
                            selected = vm.selected == f,
                            onClick = { vm.selected = f },
                            label = { Text(f) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                Button(
                    onClick = { scope.launch { vm.search() } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vm.query.isNotBlank() && !vm.loading
                ) { Text(if (vm.loading) "Searching…" else "Search") }
            }
        }
        if (!vm.hasSearched) {
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try searching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Find your favorite tracks, albums or artists. Use filters to narrow results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SuggestedQueries { vm.query = it; scope.launch { vm.search() } }
                }
            }
        } else if (vm.loading) {
            items(6) {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(64.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {}
            }
        } else if (vm.results.isEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No results", fontWeight = FontWeight.SemiBold)
                        Text("Try a different query or filter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(vm.results.size) { idx ->
                val r = vm.results[idx]
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(r.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(r.type) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedQueries(onPick: (String) -> Unit) {
    val suggestions = listOf("Arijit Singh", "Chill vibes", "Lo-fi", "Workout", "Trending")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions) { s -> SuggestionChip(onClick = { onPick(s) }, label = { Text(s) }) }
    }
}
