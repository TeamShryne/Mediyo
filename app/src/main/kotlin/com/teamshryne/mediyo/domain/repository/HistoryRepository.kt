package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.data.local.HistoryPlayEventEntity
import com.teamshryne.mediyo.data.local.HourHistogramRow
import com.teamshryne.mediyo.data.local.TopTrackRow
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun flowHistory(): Flow<List<HistoryEntryEntity>>
    suspend fun page(limit: Int, offset: Int): List<HistoryEntryEntity>

    /** Record a play start. Returns the play-event id for later finalization. */
    suspend fun record(track: Track, origin: PlayOrigin? = null, startedAt: Long = System.currentTimeMillis()): Long

    /** Back-compat overload (no origin). */
    suspend fun record(track: Track)

    /**
     * Finalize the latest open play of [videoId] with how far playback got.
     * Feeds totalPlayMs / completions / skips rollups + the event row.
     */
    suspend fun finalizePlay(videoId: String, positionMs: Long, durationMs: Long)

    suspend fun remove(videoId: String)
    suspend fun clearAll()
    fun countFlow(): Flow<Int>

    // ── Algo / stats reads ──
    fun flowRecentEvents(): Flow<List<HistoryPlayEventEntity>>
    suspend fun recentEvents(limit: Int = 200): List<HistoryPlayEventEntity>
    suspend fun eventsForVideo(videoId: String, limit: Int = 50): List<HistoryPlayEventEntity>
    suspend fun eventsSince(since: Long): List<HistoryPlayEventEntity>
    suspend fun hourHistogram(since: Long): List<HourHistogramRow>
    suspend fun topTracksSince(since: Long, limit: Int = 50): List<TopTrackRow>
}
