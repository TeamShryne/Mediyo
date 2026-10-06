package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbIdList
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.SavedCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only home feed: recently played tracks + saved collections.
 *
 * The old network shelves (charts / explore) were removed with the Rust core;
 * Home now reads entirely from Room, so it loads instantly and works offline.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val history: HistoryRepository,
    private val saved: SavedCollectionRepository,
) {
    fun flowRecent(limit: Int = 20): Flow<List<Track>> =
        history.flowHistory().map { rows ->
            rows.take(limit).map { e ->
                Track(
                    videoId = e.videoId,
                    browseId = null,
                    playlistId = null,
                    title = e.title,
                    artists = listOf(e.artist).filter { it.isNotBlank() },
                    artistIds = e.artistIds.dbIdList(),
                    album = e.album,
                    albumId = e.albumId,
                    channelName = e.channelName,
                    channelId = e.channelId,
                    artworkUrl = e.artworkUrl,
                    duration = e.duration,
                    category = "Song"
                )
            }
        }

    fun flowSaved(): Flow<List<SavedCollectionEntity>> = saved.flowAll()
}
