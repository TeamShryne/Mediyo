package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun flowHistory(): Flow<List<HistoryEntryEntity>>
    suspend fun page(limit: Int, offset: Int): List<HistoryEntryEntity>
    suspend fun record(track: Track)
    suspend fun remove(videoId: String)
    suspend fun clearAll()
    fun countFlow(): Flow<Int>
}
