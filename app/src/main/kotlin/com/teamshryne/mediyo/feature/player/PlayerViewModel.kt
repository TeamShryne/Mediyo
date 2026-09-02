package com.teamshryne.mediyo.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import com.teamshryne.mediyo.data.sleeptimer.SleepTimerManager
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.playback.PlaybackService
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import com.teamshryne.mediyo.playback.PlaybackSessionHub
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.mediyo_ffi.FfiSearchResult
import javax.inject.Inject

data class PlayerState(
    val videoId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val liked: Boolean = false,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val originLabel: String = "Mediyo"
)

data class QueueEntry(val videoId: String, val title: String, val artist: String, val artwork: String?)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val resolver: NewPipeResolver,
    private val queueManager: PlaybackQueueManager,
    private val historyRepo: HistoryRepository,
    private val likeRepo: LikeRepository,
    private val hub: PlaybackSessionHub,
    private val player: ExoPlayer,
    private val sleepManager: SleepTimerManager,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    val sleepState = sleepManager.state

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var resolveJob: Job? = null
    private var pendingLoadJob: Job? = null
    private var prefetchJob: Job? = null
    @Volatile private var resolving = false
    private var tickerJob: Job? = null
    private var lastHistoryVideoId: String? = null
    private var lastHistoryAt: Long = 0

    init {
        startTicker()
        setupAutoNext()
        setupSessionBridge()
        viewModelScope.launch {
            queueManager.state.collect { qs ->
                val cur = qs.current
                if (cur != null) {
                    _state.value = _state.value.copy(
                        videoId = cur.videoId,
                        title = cur.title,
                        artist = cur.artists.joinToString(", "),
                        artwork = cur.artworkUrl,
                        queueSize = qs.entries.size,
                        queueIndex = qs.index,
                        originLabel = qs.origin.label()
                    )
                } else {
                    _state.value = _state.value.copy(
                        videoId = null, title = "", artist = "", artwork = null,
                        isPlaying = false, isBuffering = false,
                        queueSize = 0, queueIndex = -1, originLabel = qs.origin.label()
                    )
                    // nothing left to play → stop audio and remove notification so we don't leave ghost playback
                    try { player.stop(); player.clearMediaItems() } catch (_: Throwable) {}
                    runCatching { ctx.stopService(Intent(ctx, PlaybackService::class.java)) }
                }
                pushSessionSnapshot()
            }
        }
    }

    /**
     * Notification / headset / Bluetooth controls route through the MediaSession
     * in [PlaybackService]; this wires its actions back into the app's own
     * queue + like logic, and keeps the session's like state up to date.
     */
    private fun setupSessionBridge() {
        hub.onSkipNext = { next(immediate = true) }
        hub.onSkipPrevious = { previous() }
        hub.onToggleLike = { toggleLikeCurrent() }
        // mirror the like state of the current track for the notification button
        viewModelScope.launch {
            state.map { it.videoId }.distinctUntilChanged().collectLatest { vid ->
                if (vid == null) {
                    _state.value = _state.value.copy(liked = false)
                } else {
                    likeRepo.isLikedFlow(vid).collect { liked ->
                        _state.value = _state.value.copy(liked = liked)
                        pushSessionSnapshot()
                    }
                }
            }
        }
    }

    private fun pushSessionSnapshot() {
        val s = _state.value
        val hasTrack = s.videoId != null
        hub.publish {
            it.copy(
                hasTrack = hasTrack,
                isPlaying = s.isPlaying,
                liked = s.liked,
                canSkipNext = hasTrack,
                canSkipPrevious = hasTrack
            )
        }
    }

    /** The MediaSession lives in [PlaybackService] — make sure it's running before audio starts. */
    private fun ensurePlaybackService(): Boolean {
        return try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
            true
        } catch (e: Throwable) {
            // ForegroundServiceStartNotAllowedException on Android 12+ from background (auto-next) — audio would be ghost playback
            android.util.Log.w("PlayerVM", "ensurePlaybackService failed", e)
            false
        }
    }

    private fun ensureServiceForPlayback(): Boolean {
        // Best-effort: if FGS deny, we still allow player.play() for foreground case but log; caller can decide
        val ok = ensurePlaybackService()
        if (!ok && !_state.value.isPlaying && player.playWhenReady) {
            // still try to keep session snapshot consistent
            pushSessionSnapshot()
        }
        return ok
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val dur = if (player.duration > 0) player.duration else 0L
                val pos = player.currentPosition.coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                // keep the buffering indicator alive while a stream URL is being resolved —
                // ExoPlayer itself reports IDLE during that window, not BUFFERING.
                val buffering = resolving || player.playbackState == ExoPlayer.STATE_BUFFERING
                val playing = player.isPlaying
                _state.value = _state.value.copy(
                    positionMs = pos,
                    durationMs = dur,
                    progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    isBuffering = buffering,
                    isPlaying = playing
                )
                pushSessionSnapshot()
            }
        }
    }

    private fun setupAutoNext() {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    // Sleep timer END_OF_TRACK / END_OF_QUEUE: block auto-next and pause instead
                    if (sleepManager.shouldBlockAutoNext()) {
                        viewModelScope.launch { sleepManager.onTimeout() }
                        return
                    }
                    val endedVid = _state.value.videoId
                    viewModelScope.launch {
                        delay(200)
                        // user already skipped away from the track that just ended → don't double-advance
                        if (endedVid != null && _state.value.videoId != endedVid) return@launch
                        // re-check sleep after delay (timer may have fired)
                        if (sleepManager.shouldBlockAutoNext()) {
                            sleepManager.onTimeout()
                            return@launch
                        }
                        next(immediate = true)
                    }
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val erroredVid = _state.value.videoId
                viewModelScope.launch {
                    delay(300)
                    if (erroredVid != null && _state.value.videoId != erroredVid) return@launch
                    next(immediate = true)
                }
            }
        })
    }

    private fun loadCurrent() {
        val cur = queueManager.currentState().current ?: return
        val vid = cur.videoId ?: return
        // Ensure service is foreground before swapping track so the notification
        // can be updated without an intermediate empty state.
        // If background start is denied we still prepare but don't claim foreground — avoids ghost audio
        ensureServiceForPlayback()
        resolveJob?.cancel()
        pendingLoadJob?.cancel()
        pendingLoadJob = null

        val metadata = MediaMetadata.Builder()
            .setTitle(cur.title)
            .setArtist(cur.artists.joinToString(", "))
            .setArtworkUri(cur.artworkUrl?.let(Uri::parse))
            .build()
        val placeholderUri = Uri.parse("mediyo://$vid")
        val mediaItem = MediaItem.Builder().setUri(placeholderUri).setMediaId(vid).setMediaMetadata(metadata).build()

        _state.value = _state.value.copy(
            videoId = vid,
            title = cur.title,
            artist = cur.artists.joinToString(", "),
            artwork = cur.artworkUrl,
            isPlaying = false,
            isBuffering = true,
            progress = 0f,
            positionMs = 0L,
            durationMs = 0L,
            queueSize = queueManager.currentState().entries.size,
            queueIndex = queueManager.currentState().index,
            originLabel = queueManager.currentState().origin.label()
        )
        resolving = true
        try {
            if (queueManager.currentState().current?.videoId != vid) return
            // Atomic replacement — setMediaItem replaces the previous item without
            // clearing the timeline to empty, so the media notification stays
            // attached while the new source buffers. Stream URL is resolved lazily
            // by ResolvingDataSource on the loader thread.
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
            maybeRecordHistory(cur)
            prefetchNextForPlayback()
        } catch (_: Throwable) {
            _state.value = _state.value.copy(isBuffering = false)
        } finally {
            resolving = false
        }
    }

    /** Resolve + cache the URL of whatever track would play on skip-next. */
    private fun prefetchNextForPlayback() {
        if (_state.value.repeatOne) return
        prefetchJob?.cancel()
        val nxt = queueManager.peekNext(_state.value.shuffle) ?: return
        val vid = nxt.videoId ?: return
        if (vid == _state.value.videoId) return
        prefetchJob = viewModelScope.launch {
            try { resolver.prefetchStreamUrl(vid) } catch (_: Throwable) {}
        }
    }

    /**
     * All loads funnel through here. Rapid skips coalesce into one load:
     * every tap advances the queue instantly (UI updates immediately), but the
     * network resolve only fires once taps pause for [SKIP_DEBOUNCE_MS].
     */
    private fun requestLoad(immediate: Boolean) {
        pendingLoadJob?.cancel()
        pendingLoadJob = null
        if (immediate) {
            loadCurrent()
        } else {
            pendingLoadJob = viewModelScope.launch {
                delay(SKIP_DEBOUNCE_MS)
                loadCurrent()
            }
        }
    }

    private fun maybeRecordHistory(track: Track) {
        val now = System.currentTimeMillis()
        if (track.videoId == lastHistoryVideoId && now - lastHistoryAt < 10_000) return
        lastHistoryVideoId = track.videoId
        lastHistoryAt = now
        viewModelScope.launch {
            try { historyRepo.record(track) } catch (_: Throwable) {}
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun play(videoId: String, title: String, artist: String, artwork: String?) {
        val t = Track(videoId = videoId, title = title, artists = if (artist.isBlank()) emptyList() else listOf(artist), artworkUrl = artwork)
        playTrack(t, PlayOrigin.Single(videoId))
    }

    fun playTrack(track: Track, origin: PlayOrigin = PlayOrigin.Single(track.videoId ?: "")) {
        queueManager.setQueue(origin, listOf(track), 0)
        requestLoad(immediate = true)
    }

    fun playQueue(entries: List<QueueEntry>, startIndex: Int) {
        val tracks = entries.map { Track(videoId = it.videoId, title = it.title, artists = if (it.artist.isBlank()) emptyList() else listOf(it.artist), artworkUrl = it.artwork) }
        queueManager.setQueue(PlayOrigin.Unknown, tracks, startIndex)
        requestLoad(immediate = true)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int, origin: PlayOrigin) {
        if (tracks.isEmpty()) return
        queueManager.setQueue(origin, tracks, startIndex)
        requestLoad(immediate = true)
    }

    fun playFrom(items: List<FfiSearchResult>, item: FfiSearchResult) {
        playFromWithOrigin(items, item, PlayOrigin.Unknown)
    }

    fun playFromWithOrigin(items: List<FfiSearchResult>, item: FfiSearchResult, origin: PlayOrigin) {
        val tracks = items.map { it.toDomainTrack() }.filter { it.videoId != null }
        val idx = tracks.indexOfFirst { it.videoId == item.videoId }
        if (idx < 0) {
            val vid = item.videoId ?: return
            val t = item.toDomainTrack()
            queueManager.setQueue(origin, listOf(t), 0)
        } else {
            // derive origin label if unknown
            val effOrigin = if (origin is PlayOrigin.Unknown) {
                // keep as is, no auto-derive; manager will still radio
                origin
            } else origin
            queueManager.setQueue(effOrigin, tracks, idx)
        }
        requestLoad(immediate = true)
    }

    // convenience for Track lists
    fun playFromTracks(tracks: List<Track>, track: Track, origin: PlayOrigin) {
        val idx = tracks.indexOfFirst { it.videoId == track.videoId }
        if (idx < 0) playTrack(track, origin) else playTracks(tracks, idx, origin)
    }

    fun addNext(track: Track) { queueManager.addNext(track) }
    fun addToQueue(track: Track) { queueManager.addLast(track) }
    fun addNextList(tracks: List<Track>) { queueManager.addNextList(tracks) }
    fun removeFromQueue(at: Int) {
        val wasCurrent = at == queueManager.currentState().index
        val wasOnly = queueManager.currentState().entries.size == 1
        queueManager.removeAt(at)
        if (wasCurrent && !wasOnly) {
            // switched to next track (removeAt already moved index) — load it so player doesn't keep old audio
            requestLoad(immediate = true)
        }
        // empty case handled by queue collector (stops player + service)
    }
    fun moveQueue(from: Int, to: Int) { queueManager.move(from, to) }
    fun playAt(index: Int) { queueManager.setIndex(index); requestLoad(immediate = true) }

    fun currentTrack(): Track? = queueManager.currentState().current
    suspend fun isCurrentLiked(): Boolean = currentTrack()?.videoId?.let { likeRepo.isLiked(it) } ?: false
    fun toggleLike(track: Track) { viewModelScope.launch { try { likeRepo.toggle(track) } catch (_: Throwable) {} } }
    fun toggleLikeCurrent() { currentTrack()?.let { toggleLike(it) } }
    fun isLikedFlow(videoId: String) = likeRepo.isLikedFlow(videoId)

    fun queueEntries(): List<Track> = queueManager.currentState().entries
    fun queueOrigin(): PlayOrigin = queueManager.currentState().origin

    fun next() = next(immediate = false)

    fun next(immediate: Boolean) {
        val s = queueManager.currentState()
        if (s.entries.isEmpty()) return
        if (s.current != null && _state.value.repeatOne) {
            // repeat same — ensure FGS before resuming, otherwise ghost playback without notification
            ensureServiceForPlayback()
            player.seekTo(0); player.play()
            _state.value = _state.value.copy(isPlaying = true)
            pushSessionSnapshot()
            return
        }
        queueManager.next(_state.value.shuffle, _state.value.repeatOne) ?: return
        requestLoad(immediate)
    }

    fun previous() {
        // standard restart rule when we're actually mid-track
        if (!resolving && player.currentPosition > 3000) {
            // seek within same track — if we were playing ensure service so notification keeps updating
            if (player.isPlaying || player.playWhenReady) ensureServiceForPlayback()
            player.seekTo(0)
            return
        }
        queueManager.previous(player.currentPosition) ?: return
        requestLoad(immediate = false)
    }

    fun toggleRepeat() { _state.value = _state.value.copy(repeatOne = !_state.value.repeatOne) }
    fun toggleShuffle() {
        val newVal = !_state.value.shuffle
        _state.value = _state.value.copy(shuffle = newVal)
        queueManager.setShuffle(newVal, queueManager.currentState().index)
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause(); _state.value = _state.value.copy(isPlaying = false)
        } else {
            // resuming from pause — must be foreground or notification disappears
            ensureServiceForPlayback()
            player.play(); _state.value = _state.value.copy(isPlaying = true)
        }
        pushSessionSnapshot()
    }

    fun seekTo(fraction: Float) {
        val dur = player.duration
        if (dur > 0) {
            if (player.isPlaying) ensureServiceForPlayback()
            player.seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun seekToMs(positionMs: Long) {
        if (player.isPlaying || player.playWhenReady) ensureServiceForPlayback()
        val dur = player.duration
        if (dur > 0) player.seekTo(positionMs.coerceIn(0L, dur)) else player.seekTo(positionMs.coerceAtLeast(0L))
    }

    // ── Sleep timer delegation ────────────────────────────────────────
    fun setSleepTimer(durationMs: Long) = sleepManager.setTimer(durationMs)
    fun setSleepEndOfTrack() = sleepManager.setEndOfTrack()
    fun setSleepEndOfQueue() = sleepManager.setEndOfQueue()
    fun cancelSleepTimer() = sleepManager.cancel()
    fun extendSleepTimer() = sleepManager.addFiveMinutes()

    override fun onCleared() {
        resolveJob?.cancel(); tickerJob?.cancel()
        pendingLoadJob?.cancel(); prefetchJob?.cancel()
        // Only detach if nothing is playing — keep notification controls alive when music continues after UI gone
        val stillActive = player.isPlaying || player.playWhenReady || queueManager.currentState().current != null
        if (!stillActive) {
            hub.onSkipNext = null; hub.onSkipPrevious = null; hub.onToggleLike = null
        }
        // don't release singleton player here — AppModule singleton lives beyond VM
        super.onCleared()
    }

    companion object {
        private const val SKIP_DEBOUNCE_MS = 280L
    }

    private fun FfiSearchResult.toEntry() = QueueEntry(
        videoId ?: "", title, artists.joinToString(), thumbnails.bestThumbUrl()
    )
}
