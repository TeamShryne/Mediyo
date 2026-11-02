@file:OptIn(ExperimentalCoroutinesApi::class)

package com.teamshryne.mediyo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.music.constants.AlbumSortType
import com.teamshryne.mediyo.music.constants.ArtistSongSortDescendingKey
import com.teamshryne.mediyo.music.constants.ArtistSongSortType
import com.teamshryne.mediyo.music.constants.ArtistSongSortTypeKey
import com.teamshryne.mediyo.music.constants.ArtistSortType
import com.teamshryne.mediyo.music.constants.PlaylistSortDescendingKey
import com.teamshryne.mediyo.music.constants.PlaylistSortType
import com.teamshryne.mediyo.music.constants.PlaylistSortTypeKey
import com.teamshryne.mediyo.music.constants.SongSortType
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.extensions.toEnum
import com.teamshryne.mediyo.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val allSongs = database.songs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}

@HiltViewModel
class LibraryArtistsViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val allArtists = database.artistsBookmarked(ArtistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}

@HiltViewModel
class LibraryAlbumsViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val allAlbums = database.albumsLiked(AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}

@HiltViewModel
class LibraryPlaylistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val allPlaylists = context.dataStore.data
        .map {
            it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[PlaylistSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.playlists(sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = context.dataStore.data
        .map {
            it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.artistSongs(artistId, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
