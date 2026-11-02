package com.teamshryne.mediyo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.R
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.data.mediyo.toYTItem
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_EPISODE
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_PROFILE
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.teamshryne.mediyo.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.teamshryne.mediyo.innertube.models.filterExplicit
import com.teamshryne.mediyo.innertube.pages.SearchSummary
import com.teamshryne.mediyo.innertube.pages.SearchSummaryPage
import com.teamshryne.mediyo.music.constants.HideExplicitKey
import com.teamshryne.mediyo.music.models.ItemsPage
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import com.teamshryne.mediyo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Search backed by the Go core.
 *
 * The old screen still groups results into the same "All" shelves and filter
 * chips, but every row now comes from [MediyoBridge]. The core returns a flat
 * result page, so categories are selected client-side; continuations append
 * to the same pool and are re-grouped.
 */
@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: MediyoBridge,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }

    val filter = MutableStateFlow<SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    var summaryError by mutableStateOf<Throwable?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    private var results: List<FfiSearchResult> = emptyList()
    private var continuation: String? = null
    private var loading = false
    private var loaded = false

    private fun hideExplicit() = context.dataStore.get(HideExplicitKey, false)

    private fun categoryOf(filter: SearchFilter?): Set<String>? = when (filter) {
        FILTER_SONG -> setOf("Song")
        FILTER_VIDEO -> setOf("Video")
        FILTER_ALBUM -> setOf("Album")
        FILTER_ARTIST -> setOf("Artist")
        FILTER_PODCAST -> setOf("Podcast")
        FILTER_EPISODE -> setOf("Episode")
        FILTER_PROFILE -> setOf("Profile")
        FILTER_FEATURED_PLAYLIST, FILTER_COMMUNITY_PLAYLIST -> setOf("Playlist")
        else -> null
    }

    private fun filtered(filter: SearchFilter?): List<FfiSearchResult> {
        val categories = categoryOf(filter) ?: return results
        return results.filter { it.category in categories }
    }

    private fun publish(filter: SearchFilter?) {
        val items = filtered(filter)
            .mapNotNull { it.toYTItem() }
            .filterExplicit(hideExplicit())
            .distinctBy { it.id }
        viewStateMap[filter?.value ?: ALL] = ItemsPage(items, continuation)
    }

    private fun publishSummary() {
        val hide = hideExplicit()
        fun section(titleRes: Int, categories: Set<String>): SearchSummary? {
            val items = results
                .filter { it.category in categories }
                .mapNotNull { it.toYTItem() }
                .filterExplicit(hide)
                .distinctBy { it.id }
                .take(5)
            return items.takeIf { it.isNotEmpty() }
                ?.let { SearchSummary(context.getString(titleRes), it) }
        }

        val summaries = listOfNotNull(
            section(R.string.filter_songs, setOf("Song")),
            section(R.string.filter_videos, setOf("Video")),
            section(R.string.filter_albums, setOf("Album")),
            section(R.string.filter_artists, setOf("Artist")),
            section(R.string.filter_featured_playlists, setOf("Playlist")),
            section(R.string.filter_podcasts, setOf("Podcast")),
            section(R.string.filter_episodes, setOf("Episode")),
            section(R.string.filter_profiles, setOf("Profile"))
        )
        summaryPage = SearchSummaryPage(summaries)
        summaryError = if (summaries.isEmpty()) summaryError else null
    }

    private suspend fun loadFirstPage() {
        if (loaded || loading) return
        loading = true
        summaryError = null
        try {
            val response = bridge.search(query)
            results = response.results
            continuation = response.continuation
            loaded = true
            publishSummary()
            publish(filter.value)
            summaryError = null
        } catch (e: Throwable) {
            reportException(e)
            summaryError = e
            summaryPage = null
        } finally {
            loading = false
        }
    }

    init {
        viewModelScope.launch {
            loadFirstPage()
            filter.collect { current ->
                publish(current)
            }
        }
    }

    fun reloadSummaryPage() {
        loaded = false
        results = emptyList()
        continuation = null
        summaryPage = null
        summaryError = null
        viewStateMap.clear()
        viewModelScope.launch { loadFirstPage() }
    }

    fun loadMore() {
        val token = continuation?.takeIf { it.isNotBlank() } ?: return
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val next = bridge.searchNext(token)
                results = (results + next.results).distinctBy { it.videoId ?: it.browseId ?: it.title }
                continuation = next.continuation
                publishSummary()
                publish(filter.value)
            } catch (e: Throwable) {
                reportException(e)
            } finally {
                loading = false
            }
        }
    }

    private companion object {
        const val ALL = "__all__"
    }
}
