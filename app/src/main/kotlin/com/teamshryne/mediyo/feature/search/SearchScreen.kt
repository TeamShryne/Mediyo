package com.teamshryne.mediyo.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@HiltViewModel class SearchVm @Inject constructor(val bridge: MediyoBridge): ViewModel(){
    var query by mutableStateOf(""); var result by mutableStateOf(""); var filters by mutableStateOf(listOf("Songs","Videos","Albums"))
}

@Composable fun SearchScreen(vm: SearchVm = hiltViewModel()){
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{ Text("Search", style=MaterialTheme.typography.headlineSmall) }
        item{
            OutlinedTextField(value=vm.query, onValueChange={vm.query=it}, label={Text("Search YouTube Music")}, modifier=Modifier.fillMaxWidth())
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                vm.filters.forEach{ f -> AssistChip(onClick={}, label={Text(f)}) }
            }
        }
        item{
            Button(onClick={ scope.launch{ vm.result = vm.query.ifEmpty{"try"} .let{ vm.bridge.search(it).take(300) } } }){ Text("Search (mediyo-core + continuation)") }
        }
        item{ Card{ Text(vm.result.ifEmpty{ "Results with chips + 20/page continuation via musicShelfContinuation will appear here." }, Modifier.padding(12.dp)) } }
    }
}
