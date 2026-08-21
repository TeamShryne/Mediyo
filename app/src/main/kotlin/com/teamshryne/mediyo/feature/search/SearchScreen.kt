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
    val filters = listOf("All", "Songs", "Videos", "Albums", "Playlists")
    suspend fun search() {
        if (query.isBlank()) return
        loading = true; hasSearched = true
        try {
            bridge.search(query)
            results = List(12) { SearchItem("s$it", "Result $it", if (selected == "All") "Song" else selected) }
        } finally { loading = false }
    }
}
data class SearchItem(val id: String, val title: String, val type: String)

@Composable
fun SearchScreen(vm: SearchVm = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.query, onValueChange = { vm.query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.filters) { f ->
                        FilterChip(selected = vm.selected == f, onClick = { vm.selected = f }, label = { Text(f) })
                    }
                }
                Button(onClick = { scope.launch { vm.search() } }, Modifier.fillMaxWidth(), enabled = vm.query.isNotBlank() && !vm.loading) {
                    Text(if (vm.loading) "Searching…" else "Search")
                }
            }
        }
        if (!vm.hasSearched) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Chill", "Workout", "Trending", "Lo-fi")) { s -> SuggestionChip(onClick = { vm.query = s; scope.launch { vm.search() } }, label = { Text(s) }) }
                }
            }
        } else if (vm.loading) {
            items(6) { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp)) {} }
        } else {
            items(vm.results.size) { i ->
                val r = vm.results[i]
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(r.title, style = MaterialTheme.typography.bodyMedium)
                        AssistChip(onClick = {}, label = { Text(r.type) })
                    }
                }
            }
        }
    }
}
