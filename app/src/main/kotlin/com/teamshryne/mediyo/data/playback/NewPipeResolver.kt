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

private class SimpleDownloader : Downloader {
    override fun execute(request: Request): Response {
        val url = request.url()
        val headers = request.headers()
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = request.httpMethod()
        headers.forEach { (k, v) -> v.forEach { conn.addRequestProperty(k, it) } }
        // handle POST body if present
        request.dataToSend()?.let { data ->
            conn.doOutput = true
            conn.outputStream.write(data)
        }
        conn.connect()
        val code = conn.responseCode
        val body = try { conn.inputStream.bufferedReader().readText() } catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        val latestUrl = conn.url.toString()
        val respHeaders = mutableMapOf<String, List<String>>()
        conn.headerFields?.forEach { (k, v) -> if (k != null) respHeaders[k] = v }
        return Response(code.toString(), body, respHeaders, latestUrl, null)
    }
}

@Singleton
class NewPipeResolver @Inject constructor() {
    private var inited = false
    private fun ensureInit() {
        if (inited) return
        try {
            NewPipe.init(SimpleDownloader(), null)
            inited = true
        } catch (e: Throwable) { Log.e("NewPipe", "init failed", e) }
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val svc = ServiceList.YouTube
            val linkHandler = svc.getStreamLHFactory().fromId(videoId)
            val extractor = svc.getStreamExtractor(linkHandler)
            extractor.fetchPage()
            val url = extractor.audioStreams.maxByOrNull { it.averageBitrate }?.content
                ?: extractor.videoStreams.firstOrNull()?.content
            Log.d("NewPipe", "resolved $videoId -> $url")
            url
        } catch (e: Throwable) {
            Log.e("NewPipe", "resolve failed $videoId", e)
            null
        }
    }
}
