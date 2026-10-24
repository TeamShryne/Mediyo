package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    suspend fun getLyrics(track: Track, durationSec: Int? = null, forceRefresh: Boolean = false): LyricsResult
    suspend fun refreshLyrics(track: Track, durationSec: Int? = null): LyricsResult

    /**
     * Search one explicitly chosen provider, ignoring the priority order and
     * the per-provider switches. A user who asks for a source is asking for
     * that source, including one they normally keep off.
     */
    suspend fun searchProvider(
        source: LyricsSource,
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null
    ): LyricsResult

    /** Cache a manually chosen result so the next load returns it too. */
    suspend fun saveLyrics(track: Track, durationSec: Int?, result: LyricsResult.Success)
    suspend fun getLyricsCached(key: String): String?
    fun observeLyrics(key: String): Flow<String?>
    suspend fun clearCache()
}
