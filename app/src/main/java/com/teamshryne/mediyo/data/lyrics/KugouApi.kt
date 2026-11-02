package com.teamshryne.mediyo.data.lyrics

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Direct Kugou lyrics provider (no key needed).
 *
 * Flow (verified live):
 * 1. `GET lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword={artist} - {title}[&duration=ms]`
 *    → 200 `{"candidates":[{id, accesskey, song, singer, duration, score,…}]}`
 *    NOTE: the keyword MUST be in `Artist - Title` form — a bare title returns
 *    zero candidates. `duration`/`timelength` do not filter server-side, so the
 *    best candidate is picked client-side (duration proximity + server score).
 * 2. `GET lyrics.kugou.com/download?ver=1&client=pc&id=&accesskey=&fmt=lrc&charset=utf8`
 *    → 200 `{"content": "<base64 utf-8 LRC>"}` → [LrcParser].
 */
class KugouApi : LyricsProvider {

    companion object {
        private const val SEARCH_BASE = "http://lyrics.kugou.com/search"
        private const val DOWNLOAD_BASE = "http://lyrics.kugou.com/download"
        private const val TAG = "KugouApi"
    }

    override suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext LyricsResult.Error("Song and artist required", 422)
            }
            val expectedMs = if (durationSec != null && durationSec > 0) durationSec * 1000L else null
            val candidates = search(artist, title, expectedMs)
            if (candidates.isEmpty()) return@withContext LyricsResult.NotFound
            val best = pickBest(candidates, expectedMs) ?: return@withContext LyricsResult.NotFound
            when (val dl = download(best.id, best.key)) {
                is DlOut.Success -> {
                    val track = LrcParser.parse(dl.lrc)
                    if (track.isEmpty) LyricsResult.NotFound
                    else LyricsResult.Success(track, dl.lrc, LyricsSource.Kugou)
                }
                is DlOut.Missing -> LyricsResult.NotFound
                is DlOut.Failure -> LyricsResult.Error(dl.message, dl.code)
            }
        }

    // ── Search ───────────────────────────────────────────────────────────────

    private data class Candidate(
        val id: String,
        val key: String,
        val durationMs: Long?,
        val score: Int
    )

    private fun search(artist: String, title: String, expectedMs: Long?): List<Candidate> {
        // "Artist - Title" is mandatory for hits (verified: bare title → []).
        val keyword = "$artist - $title"
        val qs = buildString {
            append("?ver=1&man=yes&client=pc")
            append("&keyword=").append(enc(keyword))
            if (expectedMs != null && expectedMs > 0) {
                append("&duration=").append(expectedMs)
                append("&timelength=").append(expectedMs)
            }
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(SEARCH_BASE + qs).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mediyo/0.1")
                connect()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Log.d(TAG, "search -> $code (${body.length}B)")
            if (code != 200) return emptyList()
            val arr = runCatching { JSONObject(body).optJSONArray("candidates") }.getOrNull()
                ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val key = o.optString("accesskey").trim()
                    if (id.isEmpty() || key.isEmpty()) continue
                    add(
                        Candidate(
                            id = id,
                            key = key,
                            durationMs = o.optLong("duration", -1L).takeIf { it > 0 },
                            score = o.optInt("score", 0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "search failed", e)
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /** Closest duration wins; server score breaks ties (higher = better). */
    private fun pickBest(cands: List<Candidate>, expectedMs: Long?): Candidate? {
        if (expectedMs == null || expectedMs <= 0) return cands.maxByOrNull { it.score }
        return cands.minWithOrNull(
            compareBy<Candidate> { abs((it.durationMs ?: expectedMs) - expectedMs) }
                .thenByDescending { it.score }
        )
    }

    // ── Download ─────────────────────────────────────────────────────────────

    private sealed interface DlOut {
        data class Success(val lrc: String) : DlOut
        data object Missing : DlOut
        data class Failure(val message: String, val code: Int?) : DlOut
    }

    private fun download(id: String, accessKey: String): DlOut {
        var conn: HttpURLConnection? = null
        return try {
            val qs = "?ver=1&client=pc&id=${enc(id)}&accesskey=${enc(accessKey)}&fmt=lrc&charset=utf8"
            conn = (URL(DOWNLOAD_BASE + qs).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mediyo/0.1")
                connect()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Log.d(TAG, "download -> $code (${body.length}B)")
            if (code != 200) return DlOut.Failure(body.take(200).ifBlank { "HTTP $code" }, code)
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return DlOut.Failure("Bad JSON", code)
            if (json.optInt("status", 200) !in 200..299) return DlOut.Missing
            val b64 = json.optString("content")
            if (b64.isBlank()) return DlOut.Missing
            val lrc = runCatching {
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()?.trim().orEmpty()
            if (lrc.isBlank()) DlOut.Missing else DlOut.Success(lrc)
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            DlOut.Failure(e.message ?: "Network error", null)
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
