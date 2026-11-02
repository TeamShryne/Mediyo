package com.teamshryne.mediyo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.data.mediyo.toYTItem
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.innertube.models.ArtistItem
import com.teamshryne.mediyo.innertube.models.BrowseEndpoint
import com.teamshryne.mediyo.innertube.pages.ArtistPage
import com.teamshryne.mediyo.innertube.pages.ArtistSection
import com.teamshryne.mediyo.music.constants.HideExplicitKey
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import com.teamshryne.mediyo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    private val bridge: MediyoBridge,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            try {
                val page = bridge.artist(artistId)
                val thumbnail = page.thumbnails.bestThumbUrl().orEmpty()
                artistPage = ArtistPage(
                    artist = ArtistItem(
                        id = artistId,
                        title = page.name,
                        thumbnail = thumbnail,
                        shuffleEndpoint = null,
                        radioEndpoint = null
                    ),
                    sections = page.carousels.map { carousel ->
                        ArtistSection(
                            title = carousel.title,
                            items = carousel.items.mapNotNull { it.toYTItem() },
                            moreEndpoint = carousel.viewAll?.let {
                                BrowseEndpoint(browseId = it.browseId, params = it.params)
                            }
                        )
                    }.filter { it.items.isNotEmpty() },
                    description = page.description
                ).filterExplicit(context.dataStore.get(HideExplicitKey, false))
            } catch (e: Throwable) {
                reportException(e)
            }
        }
    }
}
