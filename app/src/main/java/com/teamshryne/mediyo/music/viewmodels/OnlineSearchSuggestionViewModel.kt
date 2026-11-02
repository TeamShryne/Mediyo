package com.teamshryne.mediyo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.innertube.models.YTItem
import com.teamshryne.mediyo.innertube.models.filterExplicit
import com.teamshryne.mediyo.data.mediyo.toYTItem
import com.teamshryne.mediyo.music.constants.HideExplicitKey
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.db.entities.SearchHistory
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search suggestions, from the same Go core as the results.
 *
 * The core returns both the typed suggestions and the inline entity results
 * (the "did you mean this song" rows), so the search screen never silently
 * falls back to a different backend than the one that serves its rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val bridge: MediyoBridge,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            query.flatMapLatest { query ->
                if (query.isEmpty()) {
                    database.searchHistory().map { history ->
                        SearchSuggestionViewState(
                            history = history
                        )
                    }
                } else {
                    val result = bridge.suggest(query)
                    database.searchHistory(query)
                        .map { it.take(3) }
                        .map { history ->
                            SearchSuggestionViewState(
                                history = history,
                                suggestions = result.texts
                                    .map { it.text }
                                    .filter { text -> history.none { it.query == text } },
                                items = result.entities
                                    .mapNotNull { it.toYTItem() }
                                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                            )
                        }
                }
            }.collect {
                _viewState.value = it
            }
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
