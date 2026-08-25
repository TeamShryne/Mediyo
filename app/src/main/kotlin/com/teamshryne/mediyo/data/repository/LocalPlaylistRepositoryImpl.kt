package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.newLocalId
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: LocalPlaylistDao,
    private val entryDao: LocalPlaylistEntryDao
) : PlaylistRepository {
    override fun flowPlaylists(): Flow<List<LocalPlaylistEntity>> = playlistDao.flowAll()
    override suspend fun getPlaylists(): List<LocalPlaylistEntity> = playlistDao.getAll()
    override suspend fun getPlaylist(id: String): LocalPlaylistEntity? = playlistDao.getById(id)
    override fun flowPlaylist(id: String): Flow<LocalPlaylistEntity?> = playlistDao.flowById(id)
    override fun flowEntries(playlistId: String): Flow<List<LocalPlaylistEntryEntity>> = entryDao.flowEntries(playlistId)
    override suspend fun getEntries(playlistId: String): List<LocalPlaylistEntryEntity> = entryDao.getEntries(playlistId)

    override suspend fun create(title: String, description: String?): String {
        val id = newLocalId()
        val t = title.trim().ifEmpty { "Untitled" }
        playlistDao.upsert(LocalPlaylistEntity(id = id, title = t, description = description))
        return id
    }

    override suspend fun rename(id: String, title: String, description: String?) {
        val t = title.trim().ifEmpty { "Untitled" }
        playlistDao.rename(id, t, description)
    }

    override suspend fun delete(id: String) {
        // cascade deletes entries via FK
        playlistDao.deleteById(id)
    }

    override suspend fun addTrack(playlistId: String, track: Track): Boolean {
        val vid = track.videoId ?: return false
        if (entryDao.findByVideoId(playlistId, vid) != null) return false
        val pos = (entryDao.maxPosition(playlistId) ?: -1) + 1
        val entry = LocalPlaylistEntryEntity(
            id = newLocalId(),
            playlistId = playlistId,
            trackVideoId = vid,
            title = track.title,
            artist = track.artists.joinToString(", "),
            artworkUrl = track.artworkUrl,
            album = track.album,
            duration = track.duration,
            category = track.category,
            position = pos
        )
        entryDao.insert(entry)
        val count = entryDao.count(playlistId)
        playlistDao.updateCount(playlistId, count)
        return true
    }

    override suspend fun removeTrack(playlistId: String, entryId: String) {
        entryDao.deleteById(entryId)
        val count = entryDao.count(playlistId)
        playlistDao.updateCount(playlistId, count)
        // recompact positions
        val entries = entryDao.getEntries(playlistId)
        entries.sortedBy { it.position }.forEachIndexed { idx, e ->
            if (e.position != idx) entryDao.updatePosition(e.id, idx)
        }
    }

    override suspend fun removeByVideoId(playlistId: String, videoId: String) {
        entryDao.deleteByVideoId(playlistId, videoId)
        val count = entryDao.count(playlistId)
        playlistDao.updateCount(playlistId, count)
        val entries = entryDao.getEntries(playlistId)
        entries.sortedBy { it.position }.forEachIndexed { idx, e ->
            if (e.position != idx) entryDao.updatePosition(e.id, idx)
        }
    }

    override suspend fun reorder(playlistId: String, from: Int, to: Int) {
        entryDao.reorder(playlistId, from, to)
    }

    override suspend fun clear(playlistId: String) {
        entryDao.clearPlaylist(playlistId)
        playlistDao.updateCount(playlistId, 0)
    }

    override suspend fun contains(playlistId: String, videoId: String): Boolean =
        entryDao.findByVideoId(playlistId, videoId) != null
}
