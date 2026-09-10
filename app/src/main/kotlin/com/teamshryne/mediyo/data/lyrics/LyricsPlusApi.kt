package com.teamshryne.mediyo.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LyricsPlus (KPoe / YouLyPlus backend) provider.
 *
 * Contract (verified live):
 * - `GET {mirror}/v1/ttml/get?title=&artist=[&album=][&duration=]`
 *   → 200 `{"ttml": "<tt …>…"}` (Apple-style TTML, word timings)
 * - `GET {mirror}/v2/lyrics/get?…` (same params)
 *   → 200 `{"type":"Word"|"Line", "lyrics":[{time,duration,text,syllabus?}], "metadata":{…}}`
 *   (times in ms; syllabus texts carry their own trailing spaces)
 * - 404 `{"error": {"message": "Lyrics not found …"}}` → definitive miss.
 * - Plain-text `error code: 5xx` → transient; worth trying next mirror.
 *
 * Strategy: TTML first (reuses battle-tested [TtmlParser], same rendering as
 * BetterLyrics), v2 JSON as backup (covers non-Apple sources / LINE sync).
 * Mirrors are tried in order; a definitive 404 short-circuits everything,
 * transient errors fall through to the next mirror.
 */
class LyricsPlusApi : LyricsProvider {

    companion object {
        private const val TAG = "LyricsPlusApi"

        /** Primary first — the only consistently live mirror as of testing. */
        val MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus-seven.vercel.app",
            "https://lyrics-plus-backend.vercel.app"
        )
    }

    override suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext LyricsResult.Error("Song and artist required", 422)
            }
            val qs = buildQuery(title, artist, album, durationSec)
            var sawNotFound = false
            var lastError: LyricsResult.Error? = null

            for (base in MIRRORS) {
                // 1) TTML — preferred, word-accurate
                when (val ttml in get("$base/v1/ttml/get$qs")) {
                    is MirrorOut.Success -> {
                        val track = TtmlParser.parse(ttml.body)
                        if (!track.isEmpty) return@withContext LyricsResult.Success(track, ttml.body)
                        // Empty TTML parse → try v2 JSON on same mirror before moving on
                    }
                    is MirrorOut.NotFound -> sawNotFound = true
                    is MirrorOut.Error -> lastError = LyricsResult.Error(ttml.message, ttml.code)
                }
                // 2) v2 JSON — backup (LINE sync, non-Apple sources)
                when (val v2 in get("$base/v2/lyrics/get$qs")) {
                    is MirrorOut.Success -> {
                        val track = LyricsPlusParser.parse(v2.body)
                        if (!track.isEmpty) return@withContext LyricsResult.Success(track, v2.body)
                        // Empty parse with 200 → treat as miss on this mirror
                        sawNotFound = true
                    }
                    is MirrorOut.NotFound -> sawNotFound = true
                    is MirrorOut.Error -> lastError = LyricsResult.Error(v2.message, v2.code)
                }
                // Definitive miss on a mirror that actually searched → stop;
                // other mirrors share the same backend DB.
                if (sawNotFound && lastError == null) return@withContext LyricsResult.NotFound
                if (sawNotFound) {
                    // Mixed signals (404 on one endpoint, error on other) → keep trying mirrors
                    sawNotFound = false
                }
            }
            lastError ?: LyricsResult.NotFound
        }

    private fun buildQuery(title: String, artist: String, album: String?, durationSec: Int?): String =
        buildString {
            append("?title=").append(enc(title))
            append("&artist=").append(enc(artist))
            if (!album.isNullOrBlank()) append("&album=").append(enc(album))
            // Server expects seconds (decimals allowed); Int seconds is fine.
            if (durationSec != null && durationSec > 0) append("&duration=").append(durationSec)
        }

    private sealed interface MirrorOut {
        data class Success(val body: String) : MirrorOut
        data object NotFound : MirrorOut
        data class Error(val message: String, val code: Int?) : MirrorOut
    }

    private fun get(urlStr: String): MirrorOut {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mediyo/0.1")
                connect()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Log.d(TAG, "GET ${urlStr.substringAfter(".id").take(40)}… -> $code (${body.length}B)")
            when (code) {
                200 -> {
                    if (body.isBlank() || body.startsWith("error code:")) {
                        MirrorOut.Error("Empty/transient response", code)
                    } else {
                        // v1/ttml wraps XML in {"ttml": "…"}; v2 returns the doc itself.
                        val payload = if (urlStr.contains("/v1/ttml/")) {
                            runCatching { JSONObject(body).optString("ttml") }.getOrNull().orEmpty()
                        } else body
                        if (payload.isBlank()) MirrorOut.NotFound
                        else MirrorOut.Success(payload)
                    }
                }
                404 -> MirrorOut.NotFound
                429 -> MirrorOut.Error("Rate limited", 429)
                else -> {
                    val msg = body.take(200).ifBlank { "HTTP $code" }
                    MirrorOut.Error(msg, code)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "mirror failed: $urlStr", e)
            MirrorOut.Error(e.message ?: "Network error", null)
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
