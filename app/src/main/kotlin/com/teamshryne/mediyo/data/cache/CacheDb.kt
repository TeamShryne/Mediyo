package com.teamshryne.mediyo.data.cache

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teamshryne.mediyo.data.local.FollowedArtistEntity
import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.data.local.HistoryPlayEventEntity
import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryEntity
import com.teamshryne.mediyo.data.local.SavedCollectionDao
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
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
    @Query("DELETE FROM kv_cache WHERE `key`=:key") suspend fun delete(key: String)
    @Query("DELETE FROM kv_cache WHERE type=:type") suspend fun clearType(type: String)
    @Query("DELETE FROM kv_cache") suspend fun clearAll()
    @Query("SELECT type, COUNT(*) as cnt, SUM(sizeBytes) as bytes FROM kv_cache GROUP BY type")
    suspend fun stats(): List<CacheStatRow>
    @Query("SELECT SUM(sizeBytes) FROM kv_cache") suspend fun totalBytes(): Long?
    @Query("DELETE FROM kv_cache WHERE updatedAt < :before") suspend fun evictOlderThan(before: Long)
}

data class CacheStatRow(val type: String, val cnt: Long, val bytes: Long?)

@Database(
    entities = [KvCache::class, LocalPlaylistEntity::class, LocalPlaylistEntryEntity::class, LikedTrackEntity::class, HistoryEntryEntity::class, HistoryPlayEventEntity::class, FollowedArtistEntity::class, SavedCollectionEntity::class],
    version = 6,
    exportSchema = false
)
abstract class MediyoDb : RoomDatabase() {
    abstract fun kv(): KvDao
    abstract fun localPlaylistDao(): LocalPlaylistDao
    abstract fun localPlaylistEntryDao(): LocalPlaylistEntryDao
    abstract fun likedDao(): LikedTrackDao
    abstract fun historyDao(): HistoryDao
    abstract fun savedCollectionDao(): SavedCollectionDao
    abstract fun followedArtistDao(): com.teamshryne.mediyo.data.local.FollowedArtistDao
}

data class CacheStats(val totalBytes: Long, val byType: Map<String, Pair<Long,Long>>)

/**
 * v4 → v5: artist/channel identity columns on the three local track tables.
 * All nullable, so existing rows read back as NULL (old fallback behavior).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("local_playlist_entries", "liked_tracks", "history_entries")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN artistIds TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN albumId TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN channelName TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN channelId TEXT")
        }
    }
}

/**
 * v5 → v6: history becomes algo/stats-ready.
 * - history_entries gains first-play time, origin of last play, and
 *   engagement rollups (last/total duration, completions, skips).
 * - new history_play_events table: one row per play start with exact
 *   timestamp + precomputed hour/day buckets + origin, finalized later
 *   with duration/completion.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN firstPlayedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN lastPlayDurationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN completions INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN skips INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN lastOriginType TEXT")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN lastOriginLabel TEXT")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN lastOriginId TEXT")
        // Backfill: first play = last play for existing rows.
        db.execSQL("UPDATE history_entries SET firstPlayedAt = lastPlayedAt WHERE firstPlayedAt = 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS history_play_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "videoId TEXT NOT NULL, " +
                "playedAt INTEGER NOT NULL, " +
                "hourOfDay INTEGER NOT NULL, " +
                "dayOfWeek INTEGER NOT NULL, " +
                "originType TEXT, " +
                "originLabel TEXT, " +
                "originId TEXT, " +
                "playDurationMs INTEGER NOT NULL, " +
                "completionRatio REAL NOT NULL, " +
                "completed INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_play_events_videoId ON history_play_events(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_play_events_playedAt ON history_play_events(playedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_play_events_videoId_playedAt ON history_play_events(videoId, playedAt)")
    }
}
