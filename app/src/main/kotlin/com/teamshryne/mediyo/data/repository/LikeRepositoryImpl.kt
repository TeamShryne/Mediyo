package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbArtistIds
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LikeRepositoryImpl @Inject constructor(
    private val dao: LikedTrackDao,
    private val events: UserEventRepository
) : LikeRepository {
    override fun flowLiked(): Flow<List<LikedTrackEntity>> = dao.flowAll()
    override suspend fun getLiked(): List<LikedTrackEntity> = dao.getAll()
    override fun isLikedFlow(videoId: String): Flow<Boolean> = dao.isLikedFlow(videoId)
    override suspend fun isLiked(videoId: String): Boolean = dao.isLiked(videoId)
    override fun flowById(videoId: String): Flow<LikedTrackEntity?> = dao.flowById(videoId)
    override suspend fun toggle(track: Track): Boolean {
        val vid = track.videoId ?: return false
        return if (dao.isLiked(vid)) {
            dao.remove(vid)
            events.log(UserEventTypes.UNLIKE, videoId = vid)
            false
        } else {
            dao.upsert(
                LikedTrackEntity(
                    videoId = vid,
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artistIds = track.dbArtistIds(),
                    artworkUrl = track.artworkUrl,
                    album = track.album,
                    albumId = track.albumId?.takeIf { it.isNotBlank() },
                    channelName = track.channelName?.takeIf { it.isNotBlank() },
                    channelId = track.channelId?.takeIf { it.isNotBlank() },
                    duration = track.duration,
                    category = track.category
                )
            )
            events.log(UserEventTypes.LIKE, videoId = vid, meta = "title=${track.title.take(80)}")
            true
        }
    }
    override suspend fun like(track: Track) {
        val vid = track.videoId ?: return
        if (dao.isLiked(vid)) return
        dao.upsert(
            LikedTrackEntity(
                videoId = vid, title = track.title, artist = track.artists.joinToString(", "),
                artistIds = track.dbArtistIds(),
                artworkUrl = track.artworkUrl, album = track.album,
                albumId = track.albumId?.takeIf { it.isNotBlank() },
                channelName = track.channelName?.takeIf { it.isNotBlank() },
                channelId = track.channelId?.takeIf { it.isNotBlank() },
                duration = track.duration, category = track.category
            )
        )
        events.log(UserEventTypes.LIKE, videoId = vid, meta = "title=${track.title.take(80)}")
    }
    override suspend fun unlike(videoId: String) {
        dao.remove(videoId)
        events.log(UserEventTypes.UNLIKE, videoId = videoId)
    }
    override suspend fun clearAll() { dao.clearAll() }
    override fun countFlow(): Flow<Int> = dao.countFlow()
}
