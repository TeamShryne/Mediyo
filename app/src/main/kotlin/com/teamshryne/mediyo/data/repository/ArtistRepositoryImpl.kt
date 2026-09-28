package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.FollowedArtistDao
import com.teamshryne.mediyo.data.local.FollowedArtistEntity
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val dao: FollowedArtistDao,
    private val events: UserEventRepository
) : ArtistRepository {
    override fun flowFollowed(): Flow<List<FollowedArtistEntity>> = dao.flowAll()
    override suspend fun getFollowed(): List<FollowedArtistEntity> = dao.getAll()
    override fun isFollowedFlow(browseId: String): Flow<Boolean> = dao.isFollowedFlow(browseId)
    override suspend fun isFollowed(browseId: String): Boolean = dao.isFollowed(browseId)

    override suspend fun toggle(browseId: String, name: String, artworkUrl: String?, subscriberCount: String?): Boolean {
        if (browseId.isBlank()) return false
        return if (dao.isFollowed(browseId)) {
            dao.remove(browseId)
            events.log(UserEventTypes.UNFOLLOW, browseId = browseId)
            false
        } else {
            dao.upsert(
                FollowedArtistEntity(
                    browseId = browseId,
                    name = name.ifBlank { "Unknown artist" },
                    artworkUrl = artworkUrl,
                    subscriberCount = subscriberCount
                )
            )
            events.log(UserEventTypes.FOLLOW, browseId = browseId, label = name.take(120))
            true
        }
    }

    override suspend fun follow(browseId: String, name: String, artworkUrl: String?, subscriberCount: String?) {
        if (browseId.isBlank()) return
        if (dao.isFollowed(browseId)) return
        dao.upsert(
            FollowedArtistEntity(
                browseId = browseId,
                name = name.ifBlank { "Unknown artist" },
                artworkUrl = artworkUrl,
                subscriberCount = subscriberCount
            )
        )
        events.log(UserEventTypes.FOLLOW, browseId = browseId, label = name.take(120))
    }

    override suspend fun unfollow(browseId: String) {
        dao.remove(browseId)
        events.log(UserEventTypes.UNFOLLOW, browseId = browseId)
    }
    override suspend fun clearAll() { dao.clearAll() }
    override fun countFlow(): Flow<Int> = dao.countFlow()
}
