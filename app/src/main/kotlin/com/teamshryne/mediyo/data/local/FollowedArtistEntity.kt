package com.teamshryne.mediyo.data.local

import androidx.room.*

/**
 * Local-only artist subscription ("follow").
 * No server call — purely stored on device. browseId is the YouTube Music
 * channel/artist id (e.g. UC...), stable across sessions.
 */
@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val browseId: String,
    val name: String,
    val artworkUrl: String?,
    val subscriberCount: String?,
    val followedAt: Long = System.currentTimeMillis()
)
