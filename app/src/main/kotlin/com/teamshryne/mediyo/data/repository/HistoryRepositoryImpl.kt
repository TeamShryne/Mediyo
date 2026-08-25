package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao
) : HistoryRepository {
    override fun flowHistory(): Flow<List<HistoryEntryEntity>> = dao.flowAll()
    override suspend fun page(limit: Int, offset: Int): List<HistoryEntryEntity> = dao.page(limit, offset)
    override suspend fun record(track: Track) {
        val vid = track.videoId ?: return
        val existing = dao.getById(vid)
        if (existing != null) {
            dao.upsert(
                existing.copy(
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                    album = track.album ?: existing.album,
                    duration = track.duration ?: existing.duration,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = existing.playCount + 1
                )
            )
        } else {
            dao.upsert(
                HistoryEntryEntity(
                    videoId = vid,
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artworkUrl = track.artworkUrl,
                    album = track.album,
                    duration = track.duration,
                    category = track.category,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = 1
                )
            )
        }
    }
    override suspend fun remove(videoId: String) { dao.remove(videoId) }
    override suspend fun clearAll() { dao.clearAll() }
    override fun countFlow(): Flow<Int> = dao.countFlow()
}
