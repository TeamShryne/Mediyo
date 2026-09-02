package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.cache.CacheRepository
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.lyrics.LrcLibApi
import com.teamshryne.mediyo.data.lyrics.LyricsPrefs
import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.data.lyrics.TtmlParser
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.LyricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintainable repository: ordered providers → parser → cache layers separated.
 * Priority is data-driven via [LyricsPrefs.orderFlow] — reorderable in Settings > Lyrics.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val betterLyrics: BetterLyricsApi,
    private val lrcLib: LrcLibApi,
    private val lyricsPrefs: LyricsPrefs,
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

    private suspend fun providerFor(source: LyricsSource) = when (source) {
        LyricsSource.BetterLyrics -> betterLyrics
        LyricsSource.LrcLib -> lrcLib
    }

    override suspend fun getLyrics(track: Track, durationSec: Int?, forceRefresh: Boolean): LyricsResult {
        val key = cacheKey(track, durationSec)

        // 1) Try cache — stored value may be TTML or Lyricsfile; try TTML first then Lyricsfile
        if (!forceRefresh) {
            cache.get(key)?.let { cached ->
                if (cached.isNotBlank()) {
                    // Heuristic: TTML contains "<tt" or "<p ", Lyricsfile starts with "version:"
                    val parsed: LyricTrack = if (cached.trimStart().startsWith("version:") || cached.contains("start_ms:")) {
                        com.teamshryne.mediyo.data.lyrics.LyricsfileParser.parse(cached).let { lf ->
                            if (!lf.isEmpty) lf else TtmlParser.parse(cached)
                        }
                    } else {
                        TtmlParser.parse(cached).let { ttml ->
                            if (!ttml.isEmpty) ttml else com.teamshryne.mediyo.data.lyrics.LyricsfileParser.parse(cached)
                        }
                    }
                    if (!parsed.isEmpty) return LyricsResult.Success(parsed, cached)
                }
            }
        }

        // 2) Fetch in priority order (data-driven)
        val order = try { lyricsPrefs.orderFlow.first() } catch (_: Exception) { LyricsSource.defaultOrder }
        var lastNotFound: LyricsResult = LyricsResult.NotFound
        var lastError: LyricsResult? = null

        for (source in order) {
            val provider = providerFor(source)
            val result = provider.fetch(
                title = track.title,
                artist = track.artists.firstOrNull() ?: track.album ?: "",
                album = track.album,
                durationSec = durationSec
            )
            when (result) {
                is LyricsResult.Success -> {
                    runCatching { cache.put(key, "lyrics", result.rawTtml) }
                    return result
                }
                LyricsResult.NotFound -> lastNotFound = result
                LyricsResult.RateLimited -> {
                    // Don't fall through immediately on rate-limit? try next provider
                    lastError = result
                    continue
                }
                LyricsResult.NeedsApiKey -> {
                    lastError = result
                    continue
                }
                is LyricsResult.Error -> {
                    lastError = result
                    continue
                }
            }
        }

        // All providers exhausted
        return lastError ?: lastNotFound
    }

    override suspend fun refreshLyrics(track: Track, durationSec: Int?): LyricsResult {
        val key = cacheKey(track, durationSec)
        runCatching { cache.remove(key) }
        return getLyrics(track, durationSec, forceRefresh = true)
    }

    override suspend fun getLyricsCached(key: String): String? = cache.get(key)

    override fun observeLyrics(key: String): Flow<String?> = flow { emit(cache.get(key)) }

    override suspend fun clearCache() = cache.clearType("lyrics")
}
