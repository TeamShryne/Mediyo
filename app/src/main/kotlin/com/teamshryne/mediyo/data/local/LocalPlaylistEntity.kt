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
    val artworkUrl: String?,
    val album: String?,
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
    val artworkUrl: String?,
    val album: String?,
    val duration: String?,
    val likedAt: Long = System.currentTimeMillis(),
    val category: String = "Song"
)

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val album: String?,
    val duration: String?,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    val totalPlayMs: Long = 0,
    val category: String = "Song"
)

data class LocalPlaylistWithEntries(
    @Embedded val playlist: LocalPlaylistEntity,
    @Relation(parentColumn = "id", entityColumn = "playlistId")
    val entries: List<LocalPlaylistEntryEntity>
)
