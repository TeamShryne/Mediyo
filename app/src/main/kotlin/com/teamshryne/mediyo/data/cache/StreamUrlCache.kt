package com.teamshryne.mediyo.data.cache

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stream-URL cache: YouTube URLs are signed and die after a few
 * hours, so they must never be persisted — only remembered briefly while
 * fresh. Byte caching on disk is keyed by videoId and outlives these URLs.
 *
 * Generation-guarded like MetroList's: a resolve that started before an
 * invalidation (e.g. a 403 expiry) loses instead of resurrecting a dead URL.
 */
@Singleton
class StreamUrlCache @Inject constructor(
    private val maxEntries: Int = 128,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val url: String, val expiresAtMillis: Long)

    private val entries = object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }
    private val generations = HashMap<String, Long>()

    @Synchronized
    fun get(mediaId: String): String? {
        val entry = entries[mediaId] ?: return null
        if (entry.expiresAtMillis <= currentTimeMillis()) {
            entries.remove(mediaId)
            advanceGeneration(mediaId)
            return null
        }
        return entry.url
    }

    @Synchronized
    fun generation(mediaId: String): Long = generations[mediaId] ?: 0L

    @Synchronized
    fun put(mediaId: String, url: String, expectedGeneration: Long = generation(mediaId)): Boolean {
        if ((generations[mediaId] ?: 0L) != expectedGeneration) return false
        val expiresAt = runCatching {
            Math.addExact(currentTimeMillis(), URL_TTL_MS)
        }.getOrDefault(Long.MAX_VALUE)
        entries[mediaId] = Entry(url, expiresAt)
        return true
    }

    @Synchronized
    fun invalidate(mediaId: String) {
        entries.remove(mediaId)
        advanceGeneration(mediaId)
    }

    private fun advanceGeneration(mediaId: String) {
        generations[mediaId] = (generations[mediaId] ?: 0L) + 1L
    }

    companion object {
        /** googlevideo URLs live for hours; 3h keeps us well inside with margin. */
        const val URL_TTL_MS = 3 * 60 * 60 * 1000L
    }
}

/** Swap the placeholder/lazy URI for a resolved http(s) stream URL. */
internal fun DataSpec.withStreamUrl(url: String): DataSpec =
    withUri(url.toUri())

/** Best-effort videoId recovery when a DataSpec arrives without a cache key. */
internal fun deriveVideoId(uri: Uri): String? {
    val u = uri.toString()
    return when {
        u.startsWith("mediyo://") -> u.removePrefix("mediyo://").takeIf { it.isNotBlank() }
        u.contains("watch?v=") -> u.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
            .takeIf { it.isNotBlank() }
        u.startsWith("http://") || u.startsWith("https://") -> null // already resolved; key unknown
        else -> u.substringAfterLast("/").substringBefore("?").substringBefore("&").takeIf { it.isNotBlank() }
    }
}
