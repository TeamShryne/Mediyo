package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.DiscoveryDao
import com.teamshryne.mediyo.data.local.DiscoveryItemEntity
import com.teamshryne.mediyo.domain.repository.DiscoveryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    private val dao: DiscoveryDao
) : DiscoveryRepository {

    override suspend fun unserved(limit: Int): List<DiscoveryItemEntity> = dao.unserved(limit)

    override suspend fun frontier(limit: Int): List<DiscoveryItemEntity> = dao.frontier(limit)

    override suspend fun save(items: List<DiscoveryItemEntity>) {
        if (items.isNotEmpty()) dao.upsertAll(items)
    }

    override suspend fun markServed(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markServed(ids, System.currentTimeMillis())
    }

    override suspend fun markExpanded(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markExpanded(ids, System.currentTimeMillis())
    }

    override suspend fun servedIds(): Set<String> = dao.servedIds().toSet()

    override suspend fun unservedCount(): Int = dao.unservedCount()

    override suspend fun evictServedBefore(before: Long) = dao.evictServed(before)
}
