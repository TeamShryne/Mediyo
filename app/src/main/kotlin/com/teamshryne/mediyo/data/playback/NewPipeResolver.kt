package com.teamshryne.mediyo.data.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeResolver @Inject constructor() {
    // Only used for stream URL — no metadata, mediyo-core owns all browsing.
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val svc = ServiceList.YouTube
            val linkHandler = svc.getStreamLHFactory().fromId(videoId)
            val extractor = svc.getStreamExtractor(linkHandler)
            extractor.fetchPage()
            // Prefer opus/m4a adaptive; fallback to video stream
            extractor.audioStreams.maxByOrNull { it.averageBitrate }?.content
                ?: extractor.videoStreams.firstOrNull()?.content
        } catch (_: Throwable) { null }
    }
}
