package com.teamshryne.mediyo.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Scalable API client — single responsibility: HTTP + BetterLyrics contract.
 * Adding new providers: implement [LyricsProvider] and inject via factory.
 */
interface LyricsProvider {
    suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult
}

class BetterLyricsApi : LyricsProvider {

    companion object {
        private const val BASE = "https://lyrics-api.boidu.dev"
        private const val TAG = "BetterLyricsApi"
    }

    override suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext LyricsResult.Error("Song and artist required", 422)
            }
            // Build query — providing more params improves hit rate per docs
            val qs = buildString {
                append("?s=").append(enc(title))
                append("&a=").append(enc(artist))
                if (!album.isNullOrBlank()) append("&al=").append(enc(album))
                if (durationSec != null && durationSec > 0) append("&d=").append(durationSec)
            }
            val urlStr = "$BASE/getLyrics$qs"
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "Mediyo/0.1")
                conn.connect()
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.readText().orEmpty()
                val cacheStatus = conn.getHeaderField("X-Cache-Status")
                val rateType = conn.getHeaderField("X-RateLimit-Type")

                Log.d(TAG, "GET $qs -> $code cache=$cacheStatus rate=$rateType")

                when (code) {
                    200 -> {
                        val json = JSONObject(body)
                        val ttml = json.optString("ttml", "")
                        if (ttml.isNotBlank()) {
                            val score = if (json.has("score")) json.optInt("score") else null
                            val track = TtmlParser.parse(ttml, score)
                            if (track.isEmpty) {
                                LyricsResult.NotFound
                            } else {
                                LyricsResult.Success(track, ttml)
                            }
                        } else {
                            // Fallback: some providers return lyrics string (Kugou legacy shape)
                            // But default endpoint should always be ttml; treat empty as NotFound
                            LyricsResult.NotFound
                        }
                    }
                    404 -> LyricsResult.NotFound
                    401 -> {
                        // Distinguish cache miss vs invalid key – both 401 per docs
                        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                        if (body.contains("API key required", ignoreCase = true)) LyricsResult.NeedsApiKey
                        else LyricsResult.Error(msg ?: "Unauthorized", 401)
                    }
                    422 -> LyricsResult.Error("Missing song/artist", 422)
                    429 -> LyricsResult.RateLimited
                    else -> {
                        val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: body.take(300)
                        LyricsResult.Error(msg ?: "HTTP $code", code)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetch failed $title - $artist", e)
                LyricsResult.Error(e.message ?: "Network error", null)
            } finally {
                conn?.disconnect()
            }
        }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
