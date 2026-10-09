package com.teamshryne.mediyo.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.core.net.toUri
import com.teamshryne.mediyo.data.cache.DownloadCache
import com.teamshryne.mediyo.data.cache.MediyoDb
import com.teamshryne.mediyo.data.cache.PlayerCache
import com.teamshryne.mediyo.data.cache.StreamDataSource
import com.teamshryne.mediyo.data.local.DownloadedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline engine (MetroList-style): a single ExoPlayer [DownloadManager]
 * writing into the permanent, never-evicted download cache, keyed by videoId
 * so bytes stay valid long after the signed stream URL dies.
 *
 * Reliability rules:
 * - Downloads fetch through the same resolving chain as playback (cache
 *   fast-path → remembered URL → fresh resolve), so a half-cached song
 *   downloads only its missing tail.
 * - A finished download is removed from the transient song cache — one copy
 *   on disk, always in the permanent tier.
 * - 403/410/416 (expired signed URL) invalidates the remembered URL so the
 *   next attempt re-resolves instead of failing forever.
 * - Completion is recorded in [DownloadedTrackEntity] only when the download
 *   actually finishes — never at enqueue time.
 */
@OptIn(UnstableApi::class)
@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: MediyoDb,
    private val databaseProvider: androidx.media3.database.DatabaseProvider,
    @DownloadCache private val downloadCache: Cache,
    @PlayerCache private val playerCache: Cache,
    private val streams: StreamDataSource
) {
    private val dao get() = db.downloadedTrackDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloadedTracks: Flow<List<DownloadedTrackEntity>> = dao.flowAll()
    val downloadCount: Flow<Int> = dao.countFlow()

    fun downloadState(videoId: String): Flow<Download?> = downloads.map { it[videoId] }
    fun isDownloaded(videoId: String): Flow<Boolean> = dao.isDownloadedFlow(videoId)

    val notificationHelper = DownloadNotificationHelper(ctx, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager = DownloadManager(
        ctx,
        databaseProvider,
        downloadCache,
        streams.resolvingFactory(),
        Executor(Runnable::run)
    ).apply {
        maxParallelDownloads = 3
        addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    if (download.state == Download.STATE_FAILED && finalException.isExpiredStreamError()) {
                        streams.invalidateUrl(download.request.id)
                    }
                    downloads.update { it + (download.request.id to download) }
                    scope.launch {
                        when (download.state) {
                            Download.STATE_COMPLETED -> {
                                // Single copy on disk: drop the transient copy.
                                runCatching { playerCache.removeResource(download.request.id) }
                                val title = runCatching {
                                    String(download.request.data).takeIf { it.isNotBlank() }
                                }.getOrNull() ?: "Unknown"
                                val existing = runCatching { dao.getById(download.request.id) }.getOrNull()
                                dao.upsert(
                                    (existing ?: DownloadedTrackEntity(
                                        videoId = download.request.id,
                                        title = title,
                                        artist = "",
                                        artworkUrl = null
                                    )).copy(downloadedAt = System.currentTimeMillis())
                                )
                            }
                            Download.STATE_FAILED,
                            Download.STATE_STOPPED -> {
                                // Keep the metadata stub so the UI can show retry.
                            }
                            else -> Unit
                        }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    val id = download.request.id
                    streams.invalidateUrl(id)
                    scope.launch { runCatching { dao.remove(id) } }
                    downloads.update { it - id }
                }
            }
        )
    }

    init {
        // Rebuild the in-memory map from the persisted download index.
        val restored = mutableMapOf<String, Download>()
        runCatching {
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) restored[cursor.download.request.id] = cursor.download
            }
        }
        downloads.value = restored
        // Downloads completed in a previous session may still have a stale
        // transient copy — dedupe to one copy on disk.
        scope.launch {
            restored.values.filter { it.state == Download.STATE_COMPLETED }
                .forEach { runCatching { playerCache.removeResource(it.request.id) } }
        }
    }

    /** Enqueue a song for offline. Metadata stub first so the UI shows it as queued instantly. */
    fun download(track: Track) {
        val id = track.videoId?.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            val state = downloads.value[id]?.state
            if (state == Download.STATE_COMPLETED || state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED) return@launch
            runCatching {
                dao.upsert(
                    (dao.getById(id) ?: DownloadedTrackEntity(
                        videoId = id,
                        title = track.title,
                        artist = track.artists.joinToString(", "),
                        artworkUrl = track.artworkUrl
                    )).copy(
                        title = track.title,
                        artist = track.artists.joinToString(", ").ifEmpty { dao.getById(id)?.artist ?: "" },
                        artworkUrl = track.artworkUrl ?: dao.getById(id)?.artworkUrl
                    )
                )
            }
            val request = DownloadRequest.Builder(id, "mediyo://$id".toUri())
                .setCustomCacheKey(id)
                .setData(track.title.toByteArray())
                .build()
            runCatching {
                DownloadService.sendAddDownload(ctx, ExoDownloadService::class.java, request, false)
            }
        }
    }

    fun remove(videoId: String) {
        scope.launch { runCatching { dao.remove(videoId) } }
        runCatching {
            DownloadService.sendRemoveDownload(ctx, ExoDownloadService::class.java, videoId, false)
        }
        downloads.update { it - videoId }
    }

    fun removeAll() {
        scope.launch { runCatching { dao.clearAll() } }
        runCatching {
            DownloadService.sendRemoveAllDownloads(ctx, ExoDownloadService::class.java, false)
        }
        downloads.value = emptyMap()
    }

    fun release() = scope.cancel()
}

/** 403/410/416 from googlevideo = the signed URL died, not the song. */
private fun Throwable?.isExpiredStreamError(): Boolean {
    var current = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException &&
            (current.responseCode == 403 || current.responseCode == 410 || current.responseCode == 416)
        ) return true
        current = current.cause
    }
    return false
}
