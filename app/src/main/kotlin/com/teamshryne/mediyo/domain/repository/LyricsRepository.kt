package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    suspend fun getLyrics(track: Track, durationSec: Int? = null): LyricsResult
    suspend fun getLyricsCached(key: String): String?
    fun observeLyrics(key: String): Flow<String?>
    suspend fun clearCache()
}
