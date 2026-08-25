package com.teamshryne.mediyo.data.cache

import androidx.room.*
import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity

@Entity(tableName = "kv_cache")
data class KvCache(
    @PrimaryKey val key: String,
    val type: String, // search|browse|media|lyrics|comments|library
    val json: String,
    val sizeBytes: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface KvDao {
    @Query("SELECT * FROM kv_cache WHERE `key`=:key LIMIT 1") suspend fun get(key: String): KvCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: KvCache)
    @Query("DELETE FROM kv_cache WHERE type=:type") suspend fun clearType(type: String)
    @Query("DELETE FROM kv_cache") suspend fun clearAll()
    @Query("SELECT type, COUNT(*) as cnt, SUM(sizeBytes) as bytes FROM kv_cache GROUP BY type")
    suspend fun stats(): List<CacheStatRow>
    @Query("SELECT SUM(sizeBytes) FROM kv_cache") suspend fun totalBytes(): Long?
    @Query("DELETE FROM kv_cache WHERE updatedAt < :before") suspend fun evictOlderThan(before: Long)
}

data class CacheStatRow(val type: String, val cnt: Long, val bytes: Long?)

@Database(
    entities = [KvCache::class, LocalPlaylistEntity::class, LocalPlaylistEntryEntity::class, LikedTrackEntity::class, HistoryEntryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MediyoDb : RoomDatabase() {
    abstract fun kv(): KvDao
    abstract fun localPlaylistDao(): LocalPlaylistDao
    abstract fun localPlaylistEntryDao(): LocalPlaylistEntryDao
    abstract fun likedDao(): LikedTrackDao
    abstract fun historyDao(): HistoryDao
}

data class CacheStats(val totalBytes: Long, val byType: Map<String, Pair<Long,Long>>)
