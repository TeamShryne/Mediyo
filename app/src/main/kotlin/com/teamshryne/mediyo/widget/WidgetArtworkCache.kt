package com.teamshryne.mediyo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why widgets felt slow: every redraw re-fetched artwork over the network
 * (up to 4s) *before* painting anything — including pure button-state changes
 * where the art hadn't even changed.
 *
 * This cache makes widget renders instant:
 * - memory hit -> ~0ms, disk hit -> ~10ms, only a genuinely new track hits
 *   network, and even then the widget paints text/buttons immediately with
 *   the previous art while [prefetch] warms the new art in the background and
 *   notifies once it lands.
 */
@Singleton
class WidgetArtworkCache @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mem = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > 6
    }
    private val warming = mutableSetOf<String>()

    /** Fast path: memory, then disk. Never touches the network. */
    suspend fun get(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        synchronized(mem) { mem[url] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val f = fileFor(url)
                if (!f.exists()) return@withContext null
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@withContext null
                synchronized(mem) { mem[url] = bmp }
                bmp
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Fire-and-forget network fetch for [url]. No-op when cached or already
     * warming. Invokes [onDone] exactly once when genuinely new art lands so
     * the caller can repaint (art pops in after text/buttons).
     */
    fun prefetch(url: String?, onDone: () -> Unit = {}) {
        if (url.isNullOrBlank()) return
        synchronized(mem) {
            if (mem.containsKey(url)) return
            if (!warming.add(url)) return
        }
        scope.launch {
            try {
                synchronized(mem) { mem[url] }?.let { return@launch }
                if (withContext(Dispatchers.IO) { fileFor(url).exists() }) {
                    // Disk has it — get() will pick it up; still notify so art pops in.
                    withContext(Dispatchers.IO) { get(url) }
                    try { onDone() } catch (_: Throwable) {}
                    return@launch
                }
                val bmp = loadWidgetArtwork(ctx, url) ?: return@launch
                synchronized(mem) { mem[url] = bmp }
                withContext(Dispatchers.IO) { saveToDisk(url, bmp) }
                try { onDone() } catch (_: Throwable) {}
            } catch (_: Throwable) {
            } finally {
                synchronized(mem) { warming.remove(url) }
            }
        }
    }

    private fun dir(): File = File(ctx.cacheDir, "widget_art").apply { mkdirs() }

    private fun fileFor(url: String): File {
        val h = url.hashCode()
        val name = "a${Integer.toUnsignedString(h, 36)}_${url.length % 997}.png"
        return File(dir(), name)
    }

    private fun saveToDisk(url: String, bmp: Bitmap) {
        try {
            fileFor(url).outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            // Bound disk usage: keep newest 20, drop the rest.
            val files = dir().listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            files.drop(20).forEach { runCatching { it.delete() } }
        } catch (_: Throwable) {}
    }
}

/** Widget-safe network artwork fetch: small, no-hardware bitmap, fast timeout, Coil disk-cached. */
private suspend fun loadWidgetArtwork(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            try {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(320, 320)
                    .build()
                (loader.execute(req).drawable as? BitmapDrawable)?.bitmap
            } catch (_: Throwable) {
                null
            }
        }
    }
}
