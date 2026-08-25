package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LikedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.LikeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LikeRepositoryImpl @Inject constructor(
    private val dao: LikedTrackDao
) : LikeRepository {
    override fun flowLiked(): Flow<List<LikedTrackEntity>> = dao.flowAll()
    override suspend fun getLiked(): List<LikedTrackEntity> = dao.getAll()
    override fun isLikedFlow(videoId: String): Flow<Boolean> = dao.isLikedFlow(videoId)
    override suspend fun isLiked(videoId: String): Boolean = dao.isLiked(videoId)
    override fun flowById(videoId: String): Flow<LikedTrackEntity?> = dao.flowById(videoId)
    override suspend fun toggle(track: Track): Boolean {
        val vid = track.videoId ?: return false
        return if (dao.isLiked(vid)) {
            dao.remove(vid); false
        } else {
            dao.upsert(
                LikedTrackEntity(
                    videoId = vid,
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    artworkUrl = track.artworkUrl,
                    album = track.album,
                    duration = track.duration,
                    category = track.category
                )
            ); true
        }
    }
    override suspend fun like(track: Track) {
        val vid = track.videoId ?: return
        if (dao.isLiked(vid)) return
        dao.upsert(
            LikedTrackEntity(
                videoId = vid, title = track.title, artist = track.artists.joinToString(", "),
                artworkUrl = track.artworkUrl, album = track.album, duration = track.duration, category = track.category
            )
        )
    }
    override suspend fun unlike(videoId: String) { dao.remove(videoId) }
    override suspend fun clearAll() { dao.clearAll() }
    override fun countFlow(): Flow<Int> = dao.countFlow()
}
