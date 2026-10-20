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

    override suspend fun all(): List<DiscoveryItemEntity> = dao.all()

    override suspend fun knownIds(): Set<String> = dao.knownIds().toSet()

    override suspend fun frontier(limit: Int): List<DiscoveryItemEntity> = dao.frontier(limit)

    override suspend fun save(items: List<DiscoveryItemEntity>) {
        if (items.isNotEmpty()) dao.upsertAll(items)
    }

    override suspend fun markExpanded(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markExpanded(ids, System.currentTimeMillis())
    }

    override suspend fun evict(before: Long) = dao.evict(before)
}
