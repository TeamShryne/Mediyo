package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.cache.CacheRepository
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.TtmlParser
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.LyricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintainable repository: provider → parser → cache layers separated.
 * Scalable: swap [BetterLyricsApi] for another LyricsProvider via DI.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val api: BetterLyricsApi,
    private val cache: CacheRepository
) : LyricsRepository {

    private fun keyFor(track: Track, durationSec: Int?): String {
        // Prefer stable videoId for cache isolation per track playback
        val vid = track.videoId?.takeIf { it.isNotBlank() }
        if (vid != null) return "lyrics:vid:$vid"
        val title = track.title.lowercase().trim()
        val artist = track.artists.firstOrNull()?.lowercase()?.trim() ?: "unknown"
        val dur = durationSec?.let { ":${it}s" } ?: ""
        return "lyrics:tta:$title:$artist$dur"
    }

    private fun cacheKey(track: Track, durationSec: Int?): String = keyFor(track, durationSec)

    override suspend fun getLyrics(track: Track, durationSec: Int?): LyricsResult {
        val key = cacheKey(track, durationSec)

        // 1) Try cache (Room kv_cache type=lyrics) — instant, offline-friendly
        cache.get(key)?.let { cachedTtml ->
            if (cachedTtml.isNotBlank()) {
                val parsed = TtmlParser.parse(cachedTtml)
                if (!parsed.isEmpty) return LyricsResult.Success(parsed, cachedTtml)
            }
        }

        // 2) Fetch from BetterLyrics
        val result = api.fetch(
            title = track.title,
            artist = track.artists.firstOrNull() ?: track.album ?: "",
            album = track.album,
            durationSec = durationSec
        )

        // 3) On success cache indefinitely (positive cache per docs)
        if (result is LyricsResult.Success) {
            runCatching { cache.put(key, "lyrics", result.rawTtml) }
        }
        // Negative cache (NotFound) – we could cache empty sentinel for 7d but Room TTL evicts via CacheRepository.prefs
        // For now leave negative ephemeral to allow retry after 429 backoff.

        return result
    }

    override suspend fun getLyricsCached(key: String): String? = cache.get(key)

    override fun observeLyrics(key: String): Flow<String?> = flow { emit(cache.get(key)) }

    override suspend fun clearCache() = cache.clearType("lyrics")
}
