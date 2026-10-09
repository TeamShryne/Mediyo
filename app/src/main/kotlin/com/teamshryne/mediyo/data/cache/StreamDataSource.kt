package com.teamshryne.mediyo.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single choke point for turning a lazy `mediyo://videoId` request into
 * bytes. Shared by playback (ExoPlayer) and the offline downloader, so both
 * resolve URLs and hit caches identically.
 *
 * Read order (MetroList-style): permanent download store → transient song
 * cache → network. Everything is keyed by **videoId**, never by URL, because
 * YouTube stream URLs are signed and expire within hours.
 *
 * The outer (download) layer is write-disabled here: streaming must never
 * pollute the permanent store, and the download manager writes to it
 * directly. Completed downloads are removed from the song cache so only one
 * copy ever lives on disk.
 */
@OptIn(UnstableApi::class)
@Singleton
class StreamDataSource @Inject constructor(
    @ApplicationContext private val ctx: Context,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache,
    private val resolver: NewPipeResolver,
    private val urlCache: StreamUrlCache,
    private val storagePrefs: StoragePrefs
) {
    /** Read at startup; in-session toggle changes apply on next start (noted in UI). */
    val songCacheEnabled: Boolean =
        runCatching { runBlocking { storagePrefs.load().enableSongCache } }.getOrDefault(true)

    @OptIn(UnstableApi::class)
    fun cacheChain(): CacheDataSource.Factory {
        val inner = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
        if (!songCacheEnabled) {
            // Cache off: skip the song tier entirely (downloads still work).
            return CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(inner)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun resolvingFactory(): ResolvingDataSource.Factory =
        ResolvingDataSource.Factory(cacheChain(), ::resolve)

    /**
     * Runs on ExoPlayer's loader thread (blocking is expected — MetroList
     * does the same). Throws [IOException] when unresolvable so the player
     * surfaces a proper error instead of hanging.
     */
    @Throws(IOException::class)
    fun resolve(dataSpec: DataSpec): DataSpec {
        val mediaId = dataSpec.key?.takeIf { it.isNotBlank() }
            ?: deriveVideoId(dataSpec.uri)
            ?: return dataSpec
        if (mediaId.startsWith("http://") || mediaId.startsWith("https://")) return dataSpec

        // Fast path: bytes already on disk need no URL at all. Unknown total
        // length → probe the head chunk; CacheDataSource then serves the
        // cached head and fetches the rest in one go.
        val probeLen = if (dataSpec.length >= 0) dataSpec.length else PROBE_BYTES
        try {
            if (downloadCache.isCached(mediaId, dataSpec.position, probeLen)) return dataSpec
            if (songCacheEnabled && playerCache.isCached(mediaId, dataSpec.position, probeLen)) return dataSpec
        } catch (_: Throwable) {
            // Corrupt cache index must never break playback — fall through to network.
        }

        urlCache.get(mediaId)?.let { return dataSpec.withStreamUrl(it) }

        val generation = urlCache.generation(mediaId)
        val url = runBlocking { resolver.resolveStreamUrl(mediaId) }
            ?: throw IOException("No stream for $mediaId")
        urlCache.put(mediaId, url, expectedGeneration = generation)
        return dataSpec.withStreamUrl(url)
    }

    companion object {
        private const val PROBE_BYTES = 512 * 1024L
    }

    /** Drop remembered URLs for [mediaId] from both URL tiers (expiry recovery). */
    fun invalidateUrl(mediaId: String) {
        urlCache.invalidate(mediaId)
        resolver.invalidate(mediaId)
    }
}
