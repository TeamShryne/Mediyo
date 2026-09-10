package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.mediyo_ffi.FfiSearchResult
import javax.inject.Inject
import javax.inject.Singleton

data class HomeData(
    val charts: List<FfiSearchResult> = emptyList(),
    val newAlbums: List<FfiSearchResult> = emptyList(),
    val newVideos: List<FfiSearchResult> = emptyList(),
)

/**
 * Singleton in-memory cache for the home feed.
 *
 * Why this exists: [HomeVm] is scoped to the Home NavBackStackEntry, so it is
 * destroyed whenever Home leaves the composition (detail screen push, tab
 * switch with popUpTo+saveState). Without a longer-lived cache, every return
 * to Home re-fires the network request and shows the shimmer again.
 * This repository outlives the ViewModel, so a recreated [HomeVm] can show
 * cached rows instantly instead of reloading.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val bridge: MediyoBridge,
) {
    private val lock = Mutex()
    private var cache: HomeData? = null
    private var cachedAtMs: Long = 0L

    companion object {
        /** Fresh cache is reused as-is; stale cache is shown instantly then refreshed. */
        const val STALE_MS: Long = 10 * 60 * 1000L
    }

    fun peek(): HomeData? = cache

    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean {
        val c = cache ?: return true
        if (c.charts.isEmpty() && c.newAlbums.isEmpty() && c.newVideos.isEmpty()) return true
        return nowMs - cachedAtMs > STALE_MS
    }

    suspend fun load(force: Boolean = false): HomeData {
        // Fast path: fresh cache, no lock, no network.
        if (!force) {
            val c = cache
            if (c != null && !isStale()) return c
        }
        return lock.withLock {
            // Re-check under lock — a concurrent caller may have refreshed already.
            if (!force) {
                val c = cache
                if (c != null && !isStale()) return c
            }
            val exploreResult = runCatching { bridge.explore() }
            val chartsResult = runCatching { bridge.listPage("FEmusic_charts", null) }

            val explore = exploreResult.getOrNull()
            val chartsPage = chartsResult.getOrNull()

            if (explore == null && chartsPage == null) {
                // Keep serving stale cache if we have it; otherwise throw so
                // the ViewModel can render the error state.
                cache?.let { return it }
                throw exploreResult.exceptionOrNull()
                    ?: chartsResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to load")
            }

            val newAlbums = explore?.carousels?.find { it.title == "New albums & singles" }?.items.orEmpty()
            val newVideos = explore?.carousels?.find { it.title == "New music videos" }?.items.orEmpty()
            val trending = explore?.carousels?.find { it.title == "Trending" }?.items.orEmpty()
            val chartsItems = chartsPage?.items?.takeIf { it.isNotEmpty() } ?: trending

            val fresh = HomeData(
                charts = chartsItems,
                newAlbums = newAlbums,
                newVideos = newVideos,
            )
            cache = fresh
            cachedAtMs = System.currentTimeMillis()
            fresh
        }
    }

    /** Background revalidation: refresh stale cache without blocking the UI. */
    suspend fun refreshIfStale(): HomeData? {
        if (!isStale()) return cache
        return try {
            load(force = false)
        } catch (_: Throwable) {
            cache
        }
    }
}
