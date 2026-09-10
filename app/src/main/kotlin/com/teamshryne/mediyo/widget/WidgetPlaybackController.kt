package com.teamshryne.mediyo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import com.teamshryne.mediyo.playback.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Headless playback entry point for widgets.
 *
 * Unlike [com.teamshryne.mediyo.feature.player.PlayerViewModel] (which owns the
 * UI session), this works when the app process was dead: it rebuilds the queue
 * from the cached widget state / Room, promotes [PlaybackService] to
 * foreground, then drives the shared singleton [ExoPlayer] directly.
 *
 * Widget tap -> ActionCallback -> this controller -> [WidgetSync.refreshAll].
 */
@Singleton
class WidgetPlaybackController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val queueManager: PlaybackQueueManager,
    private val player: ExoPlayer,
    private val likeRepo: LikeRepository,
    private val playlistRepo: PlaylistRepository,
    private val widgetRepo: WidgetStateRepository,
    private val sync: WidgetSync
) {
    private fun ensureService(): Boolean = try {
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
        true
    } catch (_: Throwable) {
        false
    }

    private fun loadTrack(track: Track) {
        ensureService()
        val vid = track.videoId ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists.joinToString(", "))
            .setArtworkUri(track.artworkUrl?.let(Uri::parse))
            .build()
        val item = MediaItem.Builder()
            .setUri(Uri.parse("mediyo://$vid"))
            .setMediaId(vid)
            .setMediaMetadata(metadata)
            .build()
        try {
            player.setMediaItem(item)
            player.prepare()
            player.play()
        } catch (_: Throwable) {}
    }

    /** Play/pause. Cold-start: rebuilds single-track queue from cache, then plays. */
    suspend fun toggle() = withContext(Dispatchers.Main) {
        val cur = queueManager.currentState().current
        if (cur == null) {
            val cached = try { widgetRepo.read() } catch (_: Throwable) { null }
            if (cached?.videoId != null) {
                val track = Track(
                    videoId = cached.videoId,
                    title = cached.title.ifBlank { "Mediyo" },
                    artists = if (cached.artist.isBlank()) emptyList() else listOf(cached.artist),
                    artworkUrl = cached.artworkUrl
                )
                queueManager.setQueue(PlayOrigin.Single(cached.videoId), listOf(track), 0)
                loadTrack(track)
            } else {
                // Nothing to resume — just make sure the service + app can open.
                ensureService()
            }
        } else {
            if (player.isPlaying) {
                try { player.pause() } catch (_: Throwable) {}
            } else {
                ensureService()
                try { player.play() } catch (_: Throwable) {}
            }
        }
        // Optimistic widget refresh; PlayerViewModel ticker corrects seconds later.
        try {
            val c = queueManager.currentState().current
            val playing = try { player.isPlaying } catch (_: Throwable) { false }
            sync.push(
                WidgetNowPlaying(
                    videoId = c?.videoId ?: cachedVideoId(),
                    title = c?.title ?: cachedTitle(),
                    artist = c?.artists?.joinToString(", ").orEmpty(),
                    artworkUrl = c?.artworkUrl,
                    isPlaying = playing,
                    liked = c?.videoId?.let { runCatching { likeRepo.isLiked(it) }.getOrDefault(false) } == true
                )
            )
        } catch (_: Throwable) {}
    }

    suspend fun next() = withContext(Dispatchers.Main) {
        try {
            val moved = queueManager.next(shuffle = false, repeatOne = false)
            val cur = queueManager.currentState().current
            if (moved != null && cur != null) loadTrack(cur)
            else if (cur == null) toggle()
            sync.refreshAll()
        } catch (_: Throwable) {}
    }

    suspend fun previous() = withContext(Dispatchers.Main) {
        try {
            if (try { player.currentPosition } catch (_: Throwable) { 0L } > 3000) {
                try { player.seekTo(0) } catch (_: Throwable) {}
            } else {
                queueManager.previous(try { player.currentPosition } catch (_: Throwable) { 0L })
                queueManager.currentState().current?.let { loadTrack(it) }
            }
            sync.refreshAll()
        } catch (_: Throwable) {}
    }

    suspend fun toggleLike() = withContext(Dispatchers.IO) {
        try {
            val cur = queueManager.currentState().current ?: return@withContext
            try { likeRepo.toggle(cur) } catch (_: Throwable) {}
            sync.refreshAll()
        } catch (_: Throwable) {}
    }

    /** One-tap play of a local playlist. Cold-start safe. */
    suspend fun playPlaylist(playlistId: String) = withContext(Dispatchers.Main) {
        try {
            val raw = withContext(Dispatchers.IO) {
                runCatching { playlistRepo.getEntries(playlistId) }.getOrDefault(emptyList())
            }
            if (raw.isEmpty()) return@withContext
            val tracks = raw.map {
                Track(
                    videoId = it.trackVideoId, title = it.title,
                    artists = if (it.artist.isBlank()) emptyList() else it.artist.split(",").map { a -> a.trim() },
                    artworkUrl = it.artworkUrl.upscaledThumbUrl(), album = it.album,
                    duration = it.duration, category = it.category
                )
            }.filter { it.videoId != null }
            if (tracks.isEmpty()) return@withContext
            queueManager.setQueue(
                PlayOrigin.LocalPlaylist(playlistId, ""),
                tracks,
                0
            )
            queueManager.currentState().current?.let { loadTrack(it) }
            sync.refreshAll()
        } catch (_: Throwable) {}
    }

    /** Shuffle all liked tracks. Cold-start safe. */
    suspend fun playLikedShuffle() = withContext(Dispatchers.Main) {
        try {
            val raw = withContext(Dispatchers.IO) {
                runCatching { likeRepo.getLiked() }.getOrDefault(emptyList())
            }
            if (raw.isEmpty()) return@withContext
            val tracks = raw.map {
                Track(
                    videoId = it.videoId, title = it.title,
                    artists = if (it.artist.isBlank()) emptyList() else it.artist.split(",").map { a -> a.trim() },
                    artworkUrl = it.artworkUrl.upscaledThumbUrl(), album = it.album,
                    duration = it.duration, category = it.category
                )
            }.filter { it.videoId != null }.shuffled()
            if (tracks.isEmpty()) return@withContext
            queueManager.setQueue(PlayOrigin.Liked(tracks.size), tracks, 0)
            queueManager.currentState().current?.let { loadTrack(it) }
            sync.refreshAll()
        } catch (_: Throwable) {}
    }

    private suspend fun cachedVideoId(): String? = try { widgetRepo.read().videoId } catch (_: Throwable) { null }
    private suspend fun cachedTitle(): String = try { widgetRepo.read().title } catch (_: Throwable) { "" }
}
