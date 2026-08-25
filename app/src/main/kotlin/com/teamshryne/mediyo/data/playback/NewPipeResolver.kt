package com.teamshryne.mediyo.data.playback

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

private class SimpleDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val url = request.url()
        val headers = request.headers()
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = request.httpMethod()
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36")
        headers.forEach { (k, v) -> v.forEach { conn.addRequestProperty(k, it) } }
        // handle POST body if present
        request.dataToSend()?.let { data ->
            conn.doOutput = true
            conn.outputStream.write(data)
        }
        conn.connect()
        val code = conn.responseCode
        val respHeaders = conn.headerFields?.filterKeys { it != null }?.mapKeys { it.key as String } ?: emptyMap()
        val latestUrl = conn.url.toString()
        val body = try { conn.inputStream.bufferedReader().readText() } catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        return Response(code, conn.responseMessage, respHeaders, body, latestUrl)
    }
}

@Singleton
class NewPipeResolver @Inject constructor() {
    private data class Cached(val url: String, val at: Long)

    private var inited = false
    private val cache = ConcurrentHashMap<String, Cached>()

    private fun ensureInit() {
        if (inited) return
        try {
            NewPipe.init(SimpleDownloader())
            inited = true
        } catch (e: Throwable) { Log.e("NewPipe", "init failed", e) }
    }

    /** Returns a freshly-cached stream URL for [videoId], or null if none/expired. */
    fun cachedStreamUrl(videoId: String): String? {
        val c = cache[videoId] ?: return null
        if (System.currentTimeMillis() - c.at > CACHE_TTL_MS) {
            cache.remove(videoId)
            return null
        }
        return c.url
    }

    suspend fun resolveStreamUrl(videoId: String): String? {
        cachedStreamUrl(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            ensureInit()
            try {
                val svc = ServiceList.YouTube
                val linkHandler = svc.getStreamLHFactory().fromId(videoId)
                val extractor = svc.getStreamExtractor(linkHandler)
                extractor.fetchPage()
                val url = extractor.audioStreams.maxByOrNull { it.averageBitrate }?.content
                    ?: extractor.videoStreams.firstOrNull()?.content
                if (url != null) putCached(videoId, url)
                Log.d("NewPipe", "resolved $videoId -> $url")
                url
            } catch (e: Throwable) {
                Log.e("NewPipe", "resolve failed $videoId", e)
                null
            }
        }
    }

    /**
     * Warms the cache for [videoId] ahead of time (e.g. the next queue track)
     * so an upcoming skip starts instantly. Failures are swallowed — the real
     * load will retry through [resolveStreamUrl].
     */
    suspend fun prefetchStreamUrl(videoId: String) {
        if (cachedStreamUrl(videoId) != null) return
        resolveStreamUrl(videoId)
    }

    private fun putCached(videoId: String, url: String) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.entries.minByOrNull { it.value.at }?.key?.let { cache.remove(it) }
        }
        cache[videoId] = Cached(url, System.currentTimeMillis())
    }

    private companion object {
        /** googlevideo URLs expire after hours, not minutes — a short TTL is safe. */
        const val CACHE_TTL_MS = 5 * 60_000L
        const val MAX_CACHE_ENTRIES = 12
    }
}
