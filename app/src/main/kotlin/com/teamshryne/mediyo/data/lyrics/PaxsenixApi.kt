package com.teamshryne.mediyo.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Paxsenix Lyrically API provider (`lyrics.paxsenix.org`).
 *
 * Status (verified live): every endpoint EXCEPT `/apple-music/lyrics` is
 * traffic-disabled (`{"error":"Forbidden",…}`). The Apple endpoint is
 * unrestricted and returns syllable-accurate lyrics — but it needs an Apple
 * Music track id, so this provider resolves one first via the free,
 * keyless iTunes Search API and scores candidates by title/artist/duration.
 *
 * Flow:
 * 1. `GET itunes.apple.com/search?term={artist title}&entity=song&limit=10`
 *    → `results: [{trackId, trackName, artistName, trackTimeMillis, …}]`
 * 2. `GET lyrics.paxsenix.org/apple-music/lyrics?id={trackId}&ttml=true&v=2`
 *    → 200 `{"type":"TTML","content":"<tt …>"}` → [TtmlParser].
 */
class PaxsenixApi : LyricsProvider {

    companion object {
        private const val ITUNES_BASE = "https://itunes.apple.com/search"
        private const val PAX_BASE = "https://lyrics.paxsenix.org/apple-music/lyrics"
        private const val TAG = "PaxsenixApi"
    }

    override suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext LyricsResult.Error("Song and artist required", 422)
            }
            val trackId = resolveAppleId(title, artist, durationSec)
                ?: return@withContext LyricsResult.NotFound
            fetchAppleLyrics(trackId)
        }

    // ── 1) iTunes id resolution ──────────────────────────────────────────────

    private fun resolveAppleId(title: String, artist: String, durationSec: Int?): Long? {
        var conn: HttpURLConnection? = null
        return try {
            val term = enc("$artist $title")
            conn = (URL("$ITUNES_BASE?term=$term&entity=song&limit=10").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mediyo/0.1")
                connect()
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream?.bufferedReader()?.readText().orEmpty()
            val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
                ?: return null
            var bestId: Long? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val id = r.optLong("trackId", -1L)
                if (id <= 0) continue
                val score = scoreResult(
                    r.optString("trackName"),
                    r.optString("artistName"),
                    r.optLong("trackTimeMillis", -1L),
                    title, artist, durationSec
                )
                if (score > bestScore) {
                    bestScore = score
                    bestId = id
                }
            }
            // Reject garbage matches (e.g. only a remix of a different song matched)
            if (bestScore < 0) null else bestId.also {
                Log.d(TAG, "resolved apple id=$it score=$bestScore")
            }
        } catch (e: Exception) {
            Log.w(TAG, "itunes resolve failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Heuristic 0..~130: title slug equality ≫ title containment ≫ artist token
     * overlap, minus duration penalty. Remixes/covers keep base points but lose
     * to clean matches.
     */
    private fun scoreResult(
        trackName: String, artistName: String, timeMillis: Long,
        title: String, artist: String, durationSec: Int?
    ): Int {
        val wantTitle = slug(title)
        val gotTitle = slug(trackName)
        if (wantTitle.isEmpty() || gotTitle.isEmpty()) return Int.MIN_VALUE
        var score = 0
        when {
            gotTitle == wantTitle -> score += 100
            gotTitle.startsWith(wantTitle) -> score += 60 // "Title (Remix)" etc.
            gotTitle.contains(wantTitle) -> score += 30
            wantTitle.contains(gotTitle) -> score += 10
            else -> return Int.MIN_VALUE
        }
        val wantArtist = slug(artist)
        if (wantArtist.isNotEmpty()) {
            val gotArtist = slug(artistName)
            val overlap = wantArtist.split(' ').any { it.length > 2 && gotArtist.contains(it) }
            if (overlap) score += 25 else score -= 20
        }
        if (durationSec != null && durationSec > 0 && timeMillis > 0) {
            val diffSec = abs(timeMillis / 1000 - durationSec)
            score -= when {
                diffSec <= 3 -> 0
                diffSec <= 8 -> 10
                diffSec <= 20 -> 25
                else -> 60
            }
        }
        return score
    }

    private fun slug(s: String): String =
        s.lowercase()
            .replace(Regex("\\[.*?]"), " ")
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // ── 2) Apple lyrics via Paxsenix ─────────────────────────────────────────

    private fun fetchAppleLyrics(trackId: Long): LyricsResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$PAX_BASE?id=$trackId&ttml=true&v=2").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mediyo/0.1")
                connect()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Log.d(TAG, "apple lyrics $trackId -> $code (${body.length}B)")
            when (code) {
                200 -> {
                    val content = runCatching { JSONObject(body).optString("content") }.getOrNull().orEmpty()
                    if (content.isBlank() || !content.trimStart().startsWith("<")) {
                        LyricsResult.NotFound
                    } else {
                        val track = TtmlParser.parse(content)
                        if (track.isEmpty) LyricsResult.NotFound
                        else LyricsResult.Success(track, content, LyricsSource.Paxsenix)
                    }
                }
                404 -> LyricsResult.NotFound // Apple simply has no lyrics for this id
                429 -> LyricsResult.RateLimited
                else -> LyricsResult.Error(body.take(200).ifBlank { "HTTP $code" }, code)
            }
        } catch (e: Exception) {
            Log.w(TAG, "apple lyrics failed", e)
            LyricsResult.Error(e.message ?: "Network error", null)
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
