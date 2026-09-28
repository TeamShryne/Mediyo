package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.data.local.HistoryPlayEventEntity
import com.teamshryne.mediyo.data.local.HourHistogramRow
import com.teamshryne.mediyo.data.local.TopTrackRow
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbArtistIds
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao
) : HistoryRepository {
    override fun flowHistory(): Flow<List<HistoryEntryEntity>> = dao.flowAll()
    override suspend fun page(limit: Int, offset: Int): List<HistoryEntryEntity> = dao.page(limit, offset)

    override suspend fun record(track: Track) {
        record(track, origin = null, startedAt = System.currentTimeMillis())
    }

    override suspend fun record(track: Track, origin: PlayOrigin?, startedAt: Long): Long {
        val vid = track.videoId ?: return -1L
        val (type, label, id) = origin?.toColumns() ?: Triple(null, null, null)
        val existing = dao.getById(vid)
        if (existing != null) {
            dao.upsert(
                existing.copy(
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artistIds = track.dbArtistIds() ?: existing.artistIds,
                    artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                    album = track.album ?: existing.album,
                    albumId = track.albumId?.takeIf { it.isNotBlank() } ?: existing.albumId,
                    channelName = track.channelName?.takeIf { it.isNotBlank() } ?: existing.channelName,
                    channelId = track.channelId?.takeIf { it.isNotBlank() } ?: existing.channelId,
                    duration = track.duration ?: existing.duration,
                    lastPlayedAt = startedAt,
                    playCount = existing.playCount + 1,
                    lastOriginType = type ?: existing.lastOriginType,
                    lastOriginLabel = label ?: existing.lastOriginLabel,
                    lastOriginId = id ?: existing.lastOriginId
                )
            )
        } else {
            dao.upsert(
                HistoryEntryEntity(
                    videoId = vid,
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artistIds = track.dbArtistIds(),
                    artworkUrl = track.artworkUrl,
                    album = track.album,
                    albumId = track.albumId?.takeIf { it.isNotBlank() },
                    channelName = track.channelName?.takeIf { it.isNotBlank() },
                    channelId = track.channelId?.takeIf { it.isNotBlank() },
                    duration = track.duration,
                    category = track.category,
                    lastPlayedAt = startedAt,
                    firstPlayedAt = startedAt,
                    playCount = 1,
                    lastOriginType = type,
                    lastOriginLabel = label,
                    lastOriginId = id
                )
            )
        }
        val cal = Calendar.getInstance().apply { timeInMillis = startedAt }
        return dao.insertEvent(
            HistoryPlayEventEntity(
                videoId = vid,
                playedAt = startedAt,
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                originType = type,
                originLabel = label,
                originId = id
            )
        )
    }

    override suspend fun finalizePlay(videoId: String, positionMs: Long, durationMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val dur = durationMs.coerceAtLeast(0L)
        val ratio = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        val completed = dur > 0 && (ratio >= 0.8f || (dur >= 300_000 && pos >= 240_000))
        val skipped = !completed && dur > 30_000 && pos < 30_000 && ratio < 0.3f
        // Ignore sub-5s bumps (rapid skip storms / accidental taps).
        if (pos < 5_000 && !completed) return

        // Finalize the latest still-open event for this track.
        val latest = dao.eventsForVideo(videoId, 1).firstOrNull()
        if (latest != null && latest.playDurationMs == 0L && !latest.completed) {
            dao.finalizeEvent(latest.id, pos, ratio, completed)
        }

        val entry = dao.getById(videoId) ?: return
        dao.upsert(
            entry.copy(
                totalPlayMs = entry.totalPlayMs + pos,
                lastPlayDurationMs = pos,
                completions = entry.completions + if (completed) 1 else 0,
                skips = entry.skips + if (skipped) 1 else 0
            )
        )
    }

    override suspend fun remove(videoId: String) { dao.removeWithEvents(videoId) }
    override suspend fun clearAll() { dao.clearAllWithEvents() }
    override fun countFlow(): Flow<Int> = dao.countFlow()

    override fun flowRecentEvents(): Flow<List<HistoryPlayEventEntity>> = dao.flowRecentEvents()
    override suspend fun recentEvents(limit: Int): List<HistoryPlayEventEntity> = dao.recentEvents(limit)
    override suspend fun eventsForVideo(videoId: String, limit: Int): List<HistoryPlayEventEntity> =
        dao.eventsForVideo(videoId, limit)
    override suspend fun eventsSince(since: Long): List<HistoryPlayEventEntity> = dao.eventsSince(since)
    override suspend fun hourHistogram(since: Long): List<HourHistogramRow> = dao.hourHistogram(since)
    override suspend fun topTracksSince(since: Long, limit: Int): List<TopTrackRow> =
        dao.topTracksSince(since, limit)

    private fun PlayOrigin.toColumns(): Triple<String?, String?, String?> = when (this) {
        is PlayOrigin.Playlist -> Triple("Playlist", title, id)
        is PlayOrigin.Album -> Triple("Album", title, id)
        is PlayOrigin.ArtistTop -> Triple("ArtistTop", name, id)
        is PlayOrigin.Search -> Triple("Search", query, query)
        is PlayOrigin.HomeShelf -> Triple("HomeShelf", title, title)
        is PlayOrigin.Podcast -> Triple("Podcast", "Podcast", id)
        is PlayOrigin.GenericList -> Triple("GenericList", "List", id)
        is PlayOrigin.Liked -> Triple("Liked", "Liked songs", null)
        is PlayOrigin.LocalPlaylist -> Triple("LocalPlaylist", title, id)
        is PlayOrigin.History -> Triple("History", label, null)
        is PlayOrigin.Single -> Triple("Single", "Single", videoId)
        is PlayOrigin.Radio -> Triple("Radio", "Radio", seedVideoId)
        PlayOrigin.Unknown -> Triple("Unknown", "Mediyo", null)
    }
}
