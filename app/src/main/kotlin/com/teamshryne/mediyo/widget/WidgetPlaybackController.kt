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
    private val sync: WidgetSync,
    private val artCache: WidgetArtworkCache
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
    suspend fun toggle() {
        val cached = withContext(Dispatchers.IO) { runCatching { widgetRepo.read() }.getOrNull() }
        val playingNow = withContext(Dispatchers.Main) {
            val cur = queueManager.currentState().current
            if (cur == null) {
                if (cached?.videoId != null) {
                    val track = Track(
                        videoId = cached.videoId,
                        title = cached.title.ifBlank { "Mediyo" },
                        artists = if (cached.artist.isBlank()) emptyList() else listOf(cached.artist),
                        artworkUrl = cached.artworkUrl
                    )
                    queueManager.setQueue(PlayOrigin.Single(cached.videoId), listOf(track), 0)
                    loadTrack(track)
                    true
                } else {
                    // Nothing to resume — just make sure the service + app can open.
                    ensureService()
                    false
                }
            } else {
                if (player.isPlaying) {
                    try { player.pause() } catch (_: Throwable) {}
                    false
                } else {
                    ensureService()
                    try { player.play() } catch (_: Throwable) {}
                    true
                }
            }
        }
        // Optimistic push of the NEW state (never the stale cache): the widget
        // repaints on the tap, and PlayerViewModel confirms ~500ms later.
        val c = queueManager.currentState().current
        val sameVideo = c?.videoId != null && c.videoId == cached?.videoId
        val liked = if (sameVideo) {
            cached?.liked == true
        } else {
            withContext(Dispatchers.IO) {
                runCatching { c?.videoId?.let { likeRepo.isLiked(it) } }.getOrNull() == true
            }
        }
        artCache.prefetch(c?.artworkUrl)
        sync.push(
            WidgetNowPlaying(
                videoId = c?.videoId ?: cached?.videoId,
                title = c?.title ?: cached?.title.orEmpty(),
                artist = c?.artists?.joinToString(", ") ?: cached?.artist.orEmpty(),
                artworkUrl = c?.artworkUrl ?: cached?.artworkUrl,
                isPlaying = playingNow,
                isBuffering = false,
                liked = liked,
                progress = if (sameVideo) cached?.progress ?: 0f else 0f,
                positionMs = if (sameVideo) cached?.positionMs ?: 0L else 0L,
                durationMs = if (sameVideo) cached?.durationMs ?: 0L else 0L
            )
        )
    }

    suspend fun next() {
        val cur = withContext(Dispatchers.Main) {
            val moved = try { queueManager.next(shuffle = false, repeatOne = false) } catch (_: Throwable) { null }
            val c = queueManager.currentState().current
            if (moved != null && c != null) {
                loadTrack(c)
                c
            } else {
                if (c == null) toggle()
                c
            }
        } ?: return
        // Optimistic: the new track is loading/playing NOW, not "soon".
        pushTrackOptimistic(cur, isBuffering = true)
    }

    suspend fun previous() {
        val seeked = withContext(Dispatchers.Main) {
            val pos = try { player.currentPosition } catch (_: Throwable) { 0L }
            if (pos > 3000) {
                try { player.seekTo(0) } catch (_: Throwable) {}
                true
            } else {
                try { queueManager.previous(pos) } catch (_: Throwable) {}
                queueManager.currentState().current?.let { loadTrack(it) }
                false
            }
        }
        if (seeked) {
            // Restarted same track: confirm playing state instantly with cached meta.
            val cached = withContext(Dispatchers.IO) { runCatching { widgetRepo.read() }.getOrNull() }
            if (cached != null) sync.push(cached.copy(isPlaying = true, isBuffering = false))
            else sync.refreshAll()
        } else {
            queueManager.currentState().current?.let { pushTrackOptimistic(it, isBuffering = true) }
                ?: sync.refreshAll()
        }
    }

    suspend fun toggleLike() {
        val cur = queueManager.currentState().current ?: return
        val cached = withContext(Dispatchers.IO) { runCatching { widgetRepo.read() }.getOrNull() }
        val flipped = !(cached?.liked ?: withContext(Dispatchers.IO) {
            runCatching { likeRepo.isLiked(cur.videoId ?: "") }.getOrDefault(false)
        })
        // Optimistic heart flip — authoritative state follows via like flow.
        sync.push(
            WidgetNowPlaying(
                videoId = cur.videoId,
                title = cur.title,
                artist = cur.artists.joinToString(", "),
                artworkUrl = cur.artworkUrl,
                isPlaying = cached?.isPlaying == true,
                liked = flipped,
                progress = cached?.progress ?: 0f,
                positionMs = cached?.positionMs ?: 0L,
                durationMs = cached?.durationMs ?: 0L
            )
        )
        withContext(Dispatchers.IO) { runCatching { likeRepo.toggle(cur) } }
        sync.refreshAllAsync()
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
            val first = queueManager.currentState().current
            if (first != null) {
                withContext(Dispatchers.Main) { loadTrack(first) }
                pushTrackOptimistic(first, isBuffering = true)
            } else {
                sync.refreshAll()
            }
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
            val first = queueManager.currentState().current
            if (first != null) {
                withContext(Dispatchers.Main) { loadTrack(first) }
                pushTrackOptimistic(first, isBuffering = true)
            } else {
                sync.refreshAll()
            }
        } catch (_: Throwable) {}
    }

    private suspend fun cachedVideoId(): String? = try { widgetRepo.read().videoId } catch (_: Throwable) { null }
    private suspend fun cachedTitle(): String = try { widgetRepo.read().title } catch (_: Throwable) { "" }

    /** Instant repaint for a track that just started loading: new meta, zeroed progress. */
    private suspend fun pushTrackOptimistic(track: Track, isBuffering: Boolean) {
        val cached = withContext(Dispatchers.IO) { runCatching { widgetRepo.read() }.getOrNull() }
        val sameVideo = track.videoId != null && track.videoId == cached?.videoId
        val liked = if (sameVideo) {
            cached?.liked == true
        } else {
            withContext(Dispatchers.IO) {
                runCatching { track.videoId?.let { likeRepo.isLiked(it) } }.getOrNull() == true
            }
        }
        artCache.prefetch(track.artworkUrl)
        sync.push(
            WidgetNowPlaying(
                videoId = track.videoId,
                title = track.title,
                artist = track.artists.joinToString(", "),
                artworkUrl = track.artworkUrl,
                isPlaying = true,
                isBuffering = isBuffering,
                liked = liked,
                progress = 0f,
                positionMs = 0L,
                durationMs = cached?.durationMs ?: 0L
            )
        )
    }
}
