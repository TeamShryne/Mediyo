package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.DiscoveryItemEntity

/**
 * Persistence for the discovery catalogue.
 *
 * The catalogue is the on-device memory of "songs the queue graph found",
 * grouped by the taste seed each one branched from. Keeping it local is what
 * makes the "Because you heard X" shelves endless *and* novel: what was
 * already found is remembered, so the next launch expands further out instead
 * of rediscovering yesterday's discoveries.
 */
interface DiscoveryRepository {
    /** The whole catalogue, oldest first. */
    suspend fun all(): List<DiscoveryItemEntity>

    /** Every id already found: never rediscover these. */
    suspend fun knownIds(): Set<String>

    /** Found but never used as a branch parent — seeds for the next hop. */
    suspend fun frontier(limit: Int): List<DiscoveryItemEntity>

    suspend fun save(items: List<DiscoveryItemEntity>)

    suspend fun markExpanded(ids: List<String>)

    /** The catalogue only grows; old branches can go. */
    suspend fun evict(before: Long)
}
