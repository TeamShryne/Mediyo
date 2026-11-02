package com.teamshryne.mediyo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.data.mediyo.toYTItem
import com.teamshryne.mediyo.innertube.models.filterExplicit
import com.teamshryne.mediyo.music.constants.HideExplicitKey
import com.teamshryne.mediyo.music.models.ItemsPage
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import com.teamshryne.mediyo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val bridge: MediyoBridge,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)

    init {
        viewModelScope.launch {
            try {
                val page = bridge.artistSection(browseId, params, null)
                itemsPage.value = ItemsPage(
                    items = page.items.mapNotNull { it.toYTItem() }
                        .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                    continuation = page.continuation
                )
            } catch (e: Throwable) {
                reportException(e)
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val oldItemsPage = itemsPage.value ?: return@launch
            val continuation = oldItemsPage.continuation ?: return@launch
            try {
                val next = bridge.artistSectionNext(continuation)
                itemsPage.update {
                    ItemsPage(
                        items = (oldItemsPage.items + next.items.mapNotNull { item -> item.toYTItem() })
                            .distinctBy { item -> item.id }
                            .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                        continuation = next.continuation
                    )
                }
            } catch (e: Throwable) {
                reportException(e)
            }
        }
    }
}
