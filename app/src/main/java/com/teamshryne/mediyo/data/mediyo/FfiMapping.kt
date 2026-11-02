package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.innertube.models.AlbumItem
import com.teamshryne.mediyo.innertube.models.Artist
import com.teamshryne.mediyo.innertube.models.ArtistItem
import com.teamshryne.mediyo.innertube.models.PlaylistItem
import com.teamshryne.mediyo.innertube.models.SongItem
import com.teamshryne.mediyo.innertube.models.WatchEndpoint
import com.teamshryne.mediyo.innertube.models.YTItem
import com.teamshryne.mediyo.music.models.MediaMetadata
import com.teamshryne.mediyo.domain.model.bestThumbUrl

/**
 * Bridges Go-core results to the old app's display/playback models.
 *
 * The old UI was written against InnerTube's [YTItem]; mapping here keeps
 * every screen, menu and player path unchanged while the data comes from the
 * Go core.
 */

/** Prefix used to route a podcast show through the playlist item type. */
const val PODCAST_ID_PREFIX = "podcast:"

fun parseDurationSeconds(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.trim().split(':')
    return try {
        when (parts.size) {
            1 -> parts[0].toIntOrNull()
            2 -> parts[0].toInt() * 60 + parts[1].toInt()
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun FfiSearchResult.toMediaMetadata(): MediaMetadata {
    val id = videoId ?: browseId ?: playlistId ?: title
    val seconds = parseDurationSeconds(duration) ?: -1
    return MediaMetadata(
        id = id,
        title = title,
        artists = artists.mapIndexed { i, name ->
            MediaMetadata.Artist(id = artistIds.getOrNull(i)?.takeIf { it.isNotBlank() }, name = name)
        },
        duration = seconds,
        thumbnailUrl = thumbnails.bestThumbUrl(),
        album = albumId?.let { MediaMetadata.Album(it, album.orEmpty()) },
        explicit = explicit
    )
}

/** Playable songs/videos/episodes only. */
fun FfiSearchResult.toPlayableMediaMetadata(): MediaMetadata? =
    if (videoId.isNullOrBlank()) null else toMediaMetadata()

fun FfiSearchResult.toYTItem(): YTItem? {
    val thumb = thumbnails.bestThumbUrl().orEmpty()
    val seconds = parseDurationSeconds(duration)
    val artistList = artists.mapIndexed { i, name ->
        Artist(name = name, id = artistIds.getOrNull(i)?.takeIf { it.isNotBlank() })
    }
    return when (category) {
        "Song", "Video" -> {
            val id = videoId ?: return null
            SongItem(
                id = id,
                title = title,
                artists = artistList,
                album = album?.let { Album(album, albumId.orEmpty()) },
                duration = seconds,
                thumbnail = thumb,
                explicit = explicit,
                endpoint = WatchEndpoint(videoId = id)
            )
        }
        "Episode" -> {
            val id = videoId ?: return null
            SongItem(
                id = id,
                title = title,
                artists = artistList,
                album = album?.let { Album(album, albumId.orEmpty()) },
                duration = seconds,
                thumbnail = thumb,
                explicit = explicit,
                // detailId rides along so a tap can open the episode page.
                endpoint = WatchEndpoint(videoId = id, params = detailId.takeIf { it.isNotBlank() })
            )
        }
        "Album" -> {
            val browse = browseId
            val list = playlistId
            if (browse != null) {
                AlbumItem(
                    browseId = browse,
                    playlistId = list.orEmpty(),
                    title = title,
                    artists = artistList.ifEmpty { null },
                    year = year?.toIntOrNull(),
                    thumbnail = thumb,
                    explicit = explicit
                )
            } else {
                PlaylistItem(
                    id = list ?: return null,
                    title = title,
                    author = artistList.firstOrNull(),
                    songCountText = info,
                    thumbnail = thumb,
                    playEndpoint = list?.let { WatchEndpoint(playlistId = it) },
                    shuffleEndpoint = null,
                    radioEndpoint = null
                )
            }
        }
        "Artist", "Profile" -> {
            val id = browseId ?: return null
            ArtistItem(
                id = id,
                title = title,
                thumbnail = thumb,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
        }
        "Podcast" -> {
            val id = browseId ?: return null
            PlaylistItem(
                id = PODCAST_ID_PREFIX + id,
                title = title,
                author = artistList.firstOrNull() ?: info?.let { Artist(it, null) },
                songCountText = info,
                thumbnail = thumb,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
        }
        else -> {
            // Playlist / unknown: prefer the playlist id for playback, the
            // browse id for navigation.
            val playId = playlistId ?: browseId ?: return null
            PlaylistItem(
                id = playId,
                title = title,
                author = artistList.firstOrNull(),
                songCountText = info,
                thumbnail = thumb,
                playEndpoint = playlistId?.let { WatchEndpoint(playlistId = it) },
                shuffleEndpoint = null,
                radioEndpoint = null
            )
        }
    }
}

fun List<FfiSearchResult>.toYTItems(): List<YTItem> = mapNotNull { it.toYTItem() }
