package com.teamshryne.mediyo.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LrcLibApi : LyricsProvider {

    companion object {
        private const val BASE = "https://lrclib.net/api/get"
        private const val TAG = "LrcLibApi"
    }

    override suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int?): LyricsResult =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext LyricsResult.Error("Song and artist required", 422)
            }
            val qs = buildString {
                append("?track_name=").append(enc(title))
                append("&artist_name=").append(enc(artist))
                if (!album.isNullOrBlank()) append("&album_name=").append(enc(album))
                if (durationSec != null && durationSec > 0) append("&duration=").append(durationSec)
            }
            val urlStr = "$BASE$qs"
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
                Log.d(TAG, "GET $qs -> $code")
                when (code) {
                    200 -> {
                        val json = JSONObject(body)
                        val instrumental = json.optBoolean("instrumental", false)
                        if (instrumental) {
                            // Instrumental has no lyrics — treat as NotFound for UI
                            return@withContext LyricsResult.NotFound
                        }
                        val lyricsfile = json.optString("lyricsfile", "")
                        if (lyricsfile.isNotBlank()) {
                            val track = LyricsfileParser.parse(lyricsfile)
                            if (track.isEmpty) {
                                // Fallback to syncedLyrics if lyricsfile empty (legacy rows)
                                val synced = json.optString("syncedLyrics", "")
                                if (synced.isNotBlank()) {
                                    val lrcTrack = LrcParser.parse(synced)
                                    if (!lrcTrack.isEmpty) return@withContext LyricsResult.Success(lrcTrack, synced)
                                }
                                LyricsResult.NotFound
                            } else {
                                LyricsResult.Success(track, lyricsfile)
                            }
                        } else {
                            // Fallback: syncedLyrics (LRC)
                            val synced = json.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) {
                                val lrcTrack = LrcParser.parse(synced)
                                if (!lrcTrack.isEmpty) return@withContext LyricsResult.Success(lrcTrack, synced)
                            }
                            val plain = json.optString("plainLyrics", "")
                            if (plain.isNotBlank()) {
                                val plainTrack = PlainLyricsParser.parse(plain)
                                if (!plainTrack.isEmpty) return@withContext LyricsResult.Success(plainTrack, plain)
                            }
                            LyricsResult.NotFound
                        }
                    }
                    404 -> LyricsResult.NotFound
                    429 -> LyricsResult.RateLimited
                    else -> {
                        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
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
