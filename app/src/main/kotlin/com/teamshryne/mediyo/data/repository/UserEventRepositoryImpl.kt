package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.UserEventDao
import com.teamshryne.mediyo.data.local.UserEventEntity
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserEventRepositoryImpl @Inject constructor(
    private val dao: UserEventDao
) : UserEventRepository {
    /** Process id — every event in this run shares it (= one session). */
    private val sessionId: String = UUID.randomUUID().toString()

    /** Recomposition/rotation guard: drop identical (type,key,meta) within 30s. */
    @Volatile private var lastKey: String? = null
    @Volatile private var lastAt: Long = 0

    override suspend fun log(
        type: String,
        videoId: String?,
        browseId: String?,
        label: String?,
        meta: String?,
        originType: String?,
        originLabel: String?,
        at: Long
    ) {
        val key = "$type|${videoId.orEmpty()}|${browseId.orEmpty()}|${label.orEmpty()}|${meta.orEmpty()}"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 30_000) return
        lastKey = key
        lastAt = now
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        runCatching {
            dao.insert(
                UserEventEntity(
                    type = type,
                    videoId = videoId,
                    browseId = browseId,
                    label = label?.take(300),
                    meta = meta?.take(500),
                    createdAt = at,
                    hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                    dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                    sessionId = sessionId,
                    originType = originType,
                    originLabel = originLabel
                )
            )
        }
    }

    override fun flowRecent(limit: Int): Flow<List<UserEventEntity>> = dao.flowRecent(limit)
    override suspend fun byType(type: String, limit: Int): List<UserEventEntity> = dao.byType(type, limit)
    override suspend fun since(since: Long): List<UserEventEntity> = dao.since(since)
    override suspend fun countByTypeSince(type: String, since: Long): Int = dao.countByTypeSince(type, since)
    override suspend fun clearAll() = dao.clearAll()
}
