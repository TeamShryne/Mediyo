package com.teamshryne.mediyo.domain.model

import com.teamshryne.mediyo.data.mediyo.FfiQueueItem
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.FfiThumbnail

/**
 * Playable/displayable track used by the ported lyrics + podcast stack.
 *
 * The old app's UI keeps its own [com.teamshryne.mediyo.music.models.MediaMetadata]
 * model; this is only the narrow view the ported features need.
 */
data class Track(
    val videoId: String? = null,
    val browseId: String? = null,
    val playlistId: String? = null,
    val title: String,
    val artists: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val album: String? = null,
    val albumId: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val artworkUrl: String? = null,
    val duration: String? = null,
    val category: String = "Song",
    val year: String? = null,
    val explicit: Boolean = false
) {
    fun uniqueKey(): String = videoId ?: browseId ?: playlistId ?: "title:$title"
    fun isPlayable(): Boolean = !videoId.isNullOrBlank()
}

/** Best artwork for a thumbnail list: widest first, then first non-empty. */
fun List<FfiThumbnail>.bestThumbUrl(): String? =
    maxByOrNull { it.width }?.url?.takeIf { it.isNotBlank() }
        ?: firstOrNull { it.url.isNotBlank() }?.url

fun FfiSearchResult.toDomainTrack(): Track = Track(
    videoId = videoId,
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    artists = artists,
    artistIds = artistIds,
    album = album,
    albumId = albumId,
    channelName = channelName,
    channelId = channelId,
    artworkUrl = thumbnails.bestThumbUrl(),
    duration = duration,
    category = category,
    year = year,
    explicit = explicit
)

fun FfiQueueItem.toDomainTrack(): Track = Track(
    videoId = videoId,
    title = title,
    artists = artists,
    artistIds = artistIds,
    album = album,
    duration = duration,
    artworkUrl = thumbnails.bestThumbUrl(),
    category = "Song"
)
