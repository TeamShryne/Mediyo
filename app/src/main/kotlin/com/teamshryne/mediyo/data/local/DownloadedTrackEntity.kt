package com.teamshryne.mediyo.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Offline library: one row per downloaded song. The bytes live in the
 * permanent download cache (keyed by [videoId]); this table is the metadata
 * that lets the Downloads screen render offline with title/artwork.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun flowAll(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): DownloadedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE videoId = :videoId)")
    fun isDownloadedFlow(videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE videoId = :videoId")
    suspend fun remove(videoId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM downloaded_tracks")
    fun countFlow(): Flow<Int>
}
