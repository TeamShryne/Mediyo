package com.teamshryne.mediyo.data.playback

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.util.HashMap

private class SimpleDownloader : Downloader {
    override fun download(url: String, headers: Map<String, List<String>>?): org.schabi.newpipe.extractor.downloader.Downloader.Response {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        headers?.forEach { (k, v) -> v.forEach { conn.addRequestProperty(k, it) } }
        conn.connect()
        val code = conn.responseCode
        val body = try { conn.inputStream.bufferedReader().readText() } catch (e: IOException) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        val latestUrl = conn.url.toString()
        val respHeaders = HashMap<String, List<String>>()
        conn.headerFields?.forEach { (k, v) -> if (k != null) respHeaders[k] = v }
        return org.schabi.newpipe.extractor.downloader.Downloader.Response(code.toString(), body, respHeaders, latestUrl, null)
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
