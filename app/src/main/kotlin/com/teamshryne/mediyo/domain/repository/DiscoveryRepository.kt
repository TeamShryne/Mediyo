package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.DiscoveryItemEntity

/**
 * Persistence for the discovery graph pool.
 *
 * The pool is the on-device memory of "songs we found but the user has not
 * seen yet". Keeping it local is what makes the shelf endless *and* novel:
 * served tracks are remembered, so the next launch expands further out
 * instead of repeating yesterday's discoveries.
 */
interface DiscoveryRepository {
    /** Material the user has not been shown yet. */
    suspend fun unserved(limit: Int): List<DiscoveryItemEntity>

    /** Found but never used as a branch parent — seeds for the next hop. */
    suspend fun frontier(limit: Int): List<DiscoveryItemEntity>

    suspend fun save(items: List<DiscoveryItemEntity>)

    suspend fun markServed(ids: List<String>)

    suspend fun markExpanded(ids: List<String>)

    /** Ids already shown: excluded from every later expansion. */
    suspend fun servedIds(): Set<String>

    suspend fun unservedCount(): Int

    /** Served rows only exist to be excluded; drop them once stale. */
    suspend fun evictServedBefore(before: Long)
}
