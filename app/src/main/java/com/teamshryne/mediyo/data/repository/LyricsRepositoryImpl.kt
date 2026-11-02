package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.cache.LyricsCache
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.lyrics.CachedLyricsParser
import com.teamshryne.mediyo.data.lyrics.KugouApi
import com.teamshryne.mediyo.data.lyrics.LrcLibApi
import com.teamshryne.mediyo.data.lyrics.LyricsPlusApi
import com.teamshryne.mediyo.data.lyrics.LyricsPrefs
import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.data.lyrics.PaxsenixApi
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
    private val lyricsPlus: LyricsPlusApi,
    private val paxsenix: PaxsenixApi,
    private val kugou: KugouApi,
    private val lrcLib: LrcLibApi,
    private val lyricsPrefs: LyricsPrefs,
    private val cache: LyricsCache
) : LyricsRepository {

    private fun keyFor(track: Track, durationSec: Int?): String {
        // Prefer stable videoId for cache isolation per track playback
        val vid = track.videoId?.takeIf { it.isNotBlank() }
        // The old app's `lyrics` table is keyed by media id, and MusicService
        // already writes there. Reusing the same key means the prefetch and
        // the UI share one cache entry.
        if (vid != null) return vid
        val title = track.title.lowercase().trim()
        val artist = track.artists.firstOrNull()?.lowercase()?.trim() ?: "unknown"
        val dur = durationSec?.let { ":${it}s" } ?: ""
        return "lyrics:tta:$title:$artist$dur"
    }

    private fun cacheKey(track: Track, durationSec: Int?): String = keyFor(track, durationSec)

    private fun providerKey(key: String): String = "$key:provider"

    private suspend fun providerFor(source: LyricsSource) = when (source) {
        LyricsSource.BetterLyrics -> betterLyrics
        LyricsSource.LyricsPlus -> lyricsPlus
        LyricsSource.Paxsenix -> paxsenix
        LyricsSource.Kugou -> kugou
        LyricsSource.LrcLib -> lrcLib
    }

    override suspend fun getLyrics(track: Track, durationSec: Int?, forceRefresh: Boolean): LyricsResult {
        val key = cacheKey(track, durationSec)

        // 1) Try cache — payload may be TTML, Lyricsfile YAML, LRC or
        //    LyricsPlus JSON depending on which provider won previously.
        //    The winning provider id is stored under "$key:provider".
        if (!forceRefresh) {
            val cached = cache.get(key)
            if (!cached.isNullOrBlank()) {
                // The old app also wrote its own payloads (and a
                // LYRICS_NOT_FOUND sentinel) into this table; a payload this
                // parser cannot read must fall through to the providers
                // instead of failing the load.
                val parsed = runCatching { CachedLyricsParser.parse(cached) }.getOrNull()
                if (parsed != null && !parsed.isEmpty) {
                    val provider = cache.get(providerKey(key))?.let { LyricsSource.fromId(it.trim()) }
                    return LyricsResult.Success(parsed, cached, provider)
                }
            }
        }

        // 2) Fetch in priority order (data-driven), skipping disabled providers
        val order = try { lyricsPrefs.orderFlow.first() } catch (_: Exception) { LyricsSource.defaultOrder }
        val enabled = try { lyricsPrefs.enabledFlow.first() } catch (_: Exception) { LyricsSource.defaultEnabled }
        val active = order.filter { it in enabled }
        if (active.isEmpty()) return LyricsResult.NotFound
        var lastNotFound: LyricsResult = LyricsResult.NotFound
        var lastError: LyricsResult? = null

        for (source in active) {
            val provider = providerFor(source)
            val result = provider.fetch(
                title = track.title,
                artist = track.artists.firstOrNull() ?: track.album ?: "",
                album = track.album,
                durationSec = durationSec
            )
            when (result) {
                is LyricsResult.Success -> {
                    runCatching {
                        cache.put(key, result.rawTtml)
                        result.provider?.let { cache.put(providerKey(key), it.id) }
                    }
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
        runCatching { cache.remove(providerKey(key)) }
        return getLyrics(track, durationSec, forceRefresh = true)
    }

    override suspend fun searchProvider(
        source: LyricsSource,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int?
    ): LyricsResult = providerFor(source).fetch(
        title = title,
        artist = artist,
        album = album,
        durationSec = durationSec
    )

    override suspend fun saveLyrics(track: Track, durationSec: Int?, result: LyricsResult.Success) {
        val key = cacheKey(track, durationSec)
        runCatching {
            cache.put(key, result.rawTtml)
            result.provider?.let { cache.put(providerKey(key), it.id) }
        }
    }

    override suspend fun getLyricsCached(key: String): String? = cache.get(key)

    override fun observeLyrics(key: String): Flow<String?> = flow { emit(cache.get(key)) }

    override suspend fun clearCache() = cache.clear()
}
