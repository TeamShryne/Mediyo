package com.teamshryne.mediyo.domain.model

import uniffi.mediyo_ffi.FfiSearchResult
import uniffi.mediyo_ffi.FfiQueueItem
import uniffi.mediyo_ffi.FfiThumbnail
import java.util.UUID

data class Track(
    val videoId: String? = null,
    val browseId: String? = null,
    val playlistId: String? = null,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val artworkUrl: String? = null,
    val duration: String? = null,
    val category: String = "Song",
    val year: String? = null,
    val explicit: Boolean = false
) {
    fun uniqueKey(): String = videoId ?: browseId ?: playlistId ?: "title:$title"
    fun isPlayable(): Boolean = !videoId.isNullOrBlank()
}

fun FfiSearchResult.toDomainTrack(): Track = Track(
    videoId = videoId,
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    artists = artists,
    album = album,
    artworkUrl = thumbnails.bestThumbUrl(),
    duration = duration,
    category = category,
    year = year,
    explicit = explicit
)

fun FfiQueueItem.toDomainTrack(): Track = Track(
    videoId = videoId,
    browseId = null,
    playlistId = null,
    title = title,
    artists = artists,
    album = album,
    duration = duration,
    artworkUrl = thumbnails.bestThumbUrl(),
    category = "Song"
)

fun Track.toQueueArtwork(): String? = artworkUrl

fun List<FfiSearchResult>.toDomainTracks(): List<Track> = map { it.toDomainTrack() }.filter { it.isPlayable() }

fun Track.toFfiSearchResult(): FfiSearchResult = FfiSearchResult(
    title = title,
    videoId = videoId,
    browseId = browseId,
    browseParams = null,
    playlistId = playlistId,
    category = category,
    year = year,
    duration = duration,
    explicit = explicit,
    thumbnails = artworkUrl?.let { listOf(FfiThumbnail(it, 0u, 0u)) } ?: emptyList(),
    artists = artists,
    album = album
)

fun newLocalId(): String = UUID.randomUUID().toString()
