package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.FollowedArtistEntity
import kotlinx.coroutines.flow.Flow

/** Local-only artist subscriptions. No network — Room is source of truth. */
interface ArtistRepository {
    fun flowFollowed(): Flow<List<FollowedArtistEntity>>
    suspend fun getFollowed(): List<FollowedArtistEntity>
    fun isFollowedFlow(browseId: String): Flow<Boolean>
    suspend fun isFollowed(browseId: String): Boolean
    /** Returns new follow state. */
    suspend fun toggle(browseId: String, name: String, artworkUrl: String?, subscriberCount: String?): Boolean
    suspend fun follow(browseId: String, name: String, artworkUrl: String?, subscriberCount: String?)
    suspend fun unfollow(browseId: String)
    suspend fun clearAll()
    fun countFlow(): Flow<Int>
}
