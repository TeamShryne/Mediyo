package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LikeRepository {
    fun flowLiked(): Flow<List<LikedTrackEntity>>
    suspend fun getLiked(): List<LikedTrackEntity>
    fun isLikedFlow(videoId: String): Flow<Boolean>
    suspend fun isLiked(videoId: String): Boolean
    fun flowById(videoId: String): Flow<LikedTrackEntity?>
    suspend fun toggle(track: Track): Boolean // returns new isLiked
    suspend fun like(track: Track)
    suspend fun unlike(videoId: String)
    suspend fun clearAll()
    fun countFlow(): Flow<Int>
}
