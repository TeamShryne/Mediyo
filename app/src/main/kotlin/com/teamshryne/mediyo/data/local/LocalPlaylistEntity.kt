package com.teamshryne.mediyo.data.local

import androidx.room.*

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0
)

@Entity(
    tableName = "local_playlist_entries",
    foreignKeys = [ForeignKey(entity = LocalPlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId"), Index("playlistId", "position"), Index("playlistId", "trackVideoId", unique = false)]
)
data class LocalPlaylistEntryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val trackVideoId: String,
    val title: String,
    val artist: String,
    /** Comma-joined artist browseIds, parallel to [artist] names. Null for rows saved before v5. */
    val artistIds: String? = null,
    val artworkUrl: String?,
    val album: String?,
    val albumId: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val duration: String?,
    val category: String = "Song",
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int
)

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    /** Comma-joined artist browseIds, parallel to [artist] names. Null for rows saved before v5. */
    val artistIds: String? = null,
    val artworkUrl: String?,
    val album: String?,
    val albumId: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val duration: String?,
    val likedAt: Long = System.currentTimeMillis(),
    val category: String = "Song"
)

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    /** Comma-joined artist browseIds, parallel to [artist] names. Null for rows saved before v5. */
    val artistIds: String? = null,
    val artworkUrl: String?,
    val album: String?,
    val albumId: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val duration: String?,
    /** Exact moment of the last play (millis since epoch). HH:mm/day are derived from this. */
    val lastPlayedAt: Long = System.currentTimeMillis(),
    /** Exact moment of the first-ever play. Backfilled to lastPlayedAt on v5→v6 migration. */
    val firstPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    /** Accumulated listening estimate (sum of finalized play durations). Wired up in v6. */
    val totalPlayMs: Long = 0,
    /** Duration actually reached on the last finalized play (player position at switch/end). */
    val lastPlayDurationMs: Long = 0,
    /** Full listens (completion threshold reached). Powers repeat-affinity ranking. */
    val completions: Int = 0,
    /** Early exits (<30s and <30%). Powers skip-penalty ranking. */
    val skips: Int = 0,
    /** Where the last play came from, e.g. Search / Playlist / Album / HomeShelf / Radio. */
    val lastOriginType: String? = null,
    val lastOriginLabel: String? = null,
    val lastOriginId: String? = null,
    val category: String = "Song"
)

/**
 * One row per play start (never deduplicated). This is the raw material for the
 * future home algo + stats: time-of-day / day-of-week affinity, recency decay,
 * frequency, streaks, per-period tops. Aggregates live on [HistoryEntryEntity].
 */
@Entity(
    tableName = "history_play_events",
    indices = [Index("videoId"), Index("playedAt"), Index("videoId", "playedAt")]
)
data class HistoryPlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    /** Exact play moment (millis). HH:mm + calendar day are derived from this. */
    val playedAt: Long = System.currentTimeMillis(),
    /** Device-local hour 0..23 — for "user plays X at 8am" affinity without date math in SQL. */
    val hourOfDay: Int = 0,
    /** Device-local day 1..7 (Calendar.SUNDAY=1) — for weekday/weekend affinity. */
    val dayOfWeek: Int = 1,
    val originType: String? = null,
    val originLabel: String? = null,
    val originId: String? = null,
    /** Filled in when the play is finalized (track switch / end). 0 = still open. */
    val playDurationMs: Long = 0,
    /** 0f..1f position/duration at finalize time. 0 when duration unknown. */
    val completionRatio: Float = 0f,
    val completed: Boolean = false,
    /** Player shuffle state at play start — shuffle-vs-intentional is a taste signal. */
    val shuffled: Boolean = false,
    /** Queue position at play start (rank tapped / jump distance). -1 = unknown. */
    val queueIndex: Int = -1
)

data class LocalPlaylistWithEntries(
    @Embedded val playlist: LocalPlaylistEntity,
    @Relation(parentColumn = "id", entityColumn = "playlistId")
    val entries: List<LocalPlaylistEntryEntity>
)
