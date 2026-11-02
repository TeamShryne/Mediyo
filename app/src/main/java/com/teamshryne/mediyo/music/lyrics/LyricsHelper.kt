package com.teamshryne.mediyo.music.lyrics

import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.LyricsRepository
import com.teamshryne.mediyo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.teamshryne.mediyo.music.models.MediaMetadata
import com.teamshryne.mediyo.music.utils.reportException
import javax.inject.Inject

/**
 * Background lyrics prefetch for the playback service.
 *
 * The old provider list is gone: this asks the same repository the lyrics
 * screen uses, so the payload stored here is the exact one the UI renders.
 */
class LyricsHelper @Inject constructor(
    private val repository: LyricsRepository,
) {
    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        val track = Track(
            videoId = mediaMetadata.id,
            title = mediaMetadata.title,
            artists = mediaMetadata.artists.map { it.name },
            artistIds = mediaMetadata.artists.map { it.id.orEmpty() },
            album = mediaMetadata.album?.title,
            albumId = mediaMetadata.album?.id,
            artworkUrl = mediaMetadata.thumbnailUrl,
            duration = mediaMetadata.duration.takeIf { it > 0 }?.let {
                "%d:%02d".format(it / 60, it % 60)
            },
            explicit = mediaMetadata.explicit
        )
        return try {
            when (val result = repository.getLyrics(track, mediaMetadata.duration.takeIf { it > 0 })) {
                is LyricsResult.Success -> result.rawTtml
                else -> LYRICS_NOT_FOUND
            }
        } catch (e: Exception) {
            reportException(e)
            LYRICS_NOT_FOUND
        }
    }
}
