package com.teamshryne.mediyo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One node of the discovery graph.
 *
 * A track found by queueing/radio, kept on device so home can keep offering
 * *new* material across launches instead of re-fetching the same
 * neighbourhoods every time the app opens.
 *
 * - [servedAt] — set once the track has been handed to the user. Served
 *   tracks are never offered again and are excluded from later expansions,
 *   so the shelf keeps producing fresh songs instead of looping.
 * - [expandedAt] — set once the track has been used as a branch parent, i.e.
 *   we queued *from* it to find its own neighbours. Without this we would
 *   re-expand the same branch forever.
 */
@Entity(
    tableName = "discovery_items",
    indices = [Index("servedAt"), Index("expandedAt"), Index("addedAt")]
)
data class DiscoveryItemEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    /** Comma-joined browse ids, parallel to the credited names. */
    val artistIds: String? = null,
    val artworkUrl: String? = null,
    val album: String? = null,
    val duration: String? = null,
    /** Graph distance from the taste seed: 1 = direct queue neighbour. */
    val depth: Int = 1,
    /** The taste seed that started this branch. */
    val rootSeedId: String,
    /** The track we queued from to find this one. */
    val parentVideoId: String,
    /** Position inside the source queue — the provider's own confidence order. */
    val rank: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val servedAt: Long? = null,
    val expandedAt: Long? = null
)
