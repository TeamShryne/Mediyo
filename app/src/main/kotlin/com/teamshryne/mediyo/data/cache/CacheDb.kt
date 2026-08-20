package com.teamshryne.mediyo.data.cache

import androidx.room.*

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

@Database(entities = [KvCache::class], version = 1, exportSchema = false)
abstract class MediyoDb : RoomDatabase() { abstract fun kv(): KvDao }

data class CacheStats(val totalBytes: Long, val byType: Map<String, Pair<Long,Long>>)
