package com.teamshryne.mediyo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    fun flowAll(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    suspend fun getAll(): List<LocalPlaylistEntity>

    @Query("SELECT * FROM local_playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalPlaylistEntity?

    @Query("SELECT * FROM local_playlists WHERE id = :id LIMIT 1")
    fun flowById(id: String): Flow<LocalPlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: LocalPlaylistEntity)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_playlists SET trackCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun updateCount(id: String, count: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE local_playlists SET title = :title, description = :desc, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, desc: String?, now: Long = System.currentTimeMillis())
}

@Dao
interface LocalPlaylistEntryDao {
    @Query("SELECT * FROM local_playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    fun flowEntries(playlistId: String): Flow<List<LocalPlaylistEntryEntity>>

    @Query("SELECT * FROM local_playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getEntries(playlistId: String): List<LocalPlaylistEntryEntity>

    @Query("SELECT * FROM local_playlist_entries WHERE playlistId = :playlistId AND trackVideoId = :videoId LIMIT 1")
    suspend fun findByVideoId(playlistId: String, videoId: String): LocalPlaylistEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LocalPlaylistEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LocalPlaylistEntryEntity>)

    @Query("DELETE FROM local_playlist_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId AND trackVideoId = :videoId")
    suspend fun deleteByVideoId(playlistId: String, videoId: String)

    @Query("DELETE FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Query("SELECT MAX(position) FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: String): Int?

    @Transaction
    suspend fun reorder(playlistId: String, fromPos: Int, toPos: Int) {
        val list = getEntries(playlistId).sortedBy { it.position }
        if (fromPos !in list.indices || toPos !in list.indices) return
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromPos)
        mutable.add(toPos, item)
        mutable.forEachIndexed { idx, e ->
            updatePosition(e.id, idx)
        }
    }

    @Query("UPDATE local_playlist_entries SET position = :pos WHERE id = :id")
    suspend fun updatePosition(id: String, pos: Int)

    @Query("SELECT COUNT(*) FROM local_playlist_entries WHERE playlistId = :playlistId")
    suspend fun count(playlistId: String): Int
}

@Dao
interface LikedTrackDao {
    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    fun flowAll(): Flow<List<LikedTrackEntity>>

    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    suspend fun getAll(): List<LikedTrackEntity>

    @Query("SELECT * FROM liked_tracks WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): LikedTrackEntity?

    @Query("SELECT * FROM liked_tracks WHERE videoId = :videoId LIMIT 1")
    fun flowById(videoId: String): Flow<LikedTrackEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE videoId = :videoId)")
    fun isLikedFlow(videoId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE videoId = :videoId)")
    suspend fun isLiked(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE videoId = :videoId")
    suspend fun remove(videoId: String)

    @Query("DELETE FROM liked_tracks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM liked_tracks")
    fun countFlow(): Flow<Int>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY lastPlayedAt DESC")
    fun flowAll(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY lastPlayedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<HistoryEntryEntity>

    @Query("SELECT * FROM history_entries WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): HistoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE videoId = :videoId")
    suspend fun remove(videoId: String)

    @Query("DELETE FROM history_entries")
    suspend fun clearAll()

    @Query("DELETE FROM history_entries WHERE lastPlayedAt < :before")
    suspend fun evictOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM history_entries")
    fun countFlow(): Flow<Int>
}
