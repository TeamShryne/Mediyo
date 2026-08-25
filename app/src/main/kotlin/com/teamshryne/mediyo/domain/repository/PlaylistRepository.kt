package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun flowPlaylists(): Flow<List<LocalPlaylistEntity>>
    suspend fun getPlaylists(): List<LocalPlaylistEntity>
    suspend fun getPlaylist(id: String): LocalPlaylistEntity?
    fun flowPlaylist(id: String): Flow<LocalPlaylistEntity?>
    fun flowEntries(playlistId: String): Flow<List<LocalPlaylistEntryEntity>>
    suspend fun getEntries(playlistId: String): List<LocalPlaylistEntryEntity>
    suspend fun create(title: String, description: String? = null): String
    suspend fun rename(id: String, title: String, description: String? = null)
    suspend fun delete(id: String)
    suspend fun addTrack(playlistId: String, track: Track): Boolean // false if already exists
    suspend fun removeTrack(playlistId: String, entryId: String)
    suspend fun removeByVideoId(playlistId: String, videoId: String)
    suspend fun reorder(playlistId: String, from: Int, to: Int)
    suspend fun clear(playlistId: String)
    suspend fun contains(playlistId: String, videoId: String): Boolean
}
