package com.teamshryne.mediyo.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val player: ExoPlayer,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

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
                        queueSize = 0, queueIndex = -1, originLabel = qs.origin.label()
                    )
                }
            }
        }
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
            }
        }
    }

    private fun setupAutoNext() {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val endedVid = _state.value.videoId
                    viewModelScope.launch {
                        delay(200)
                        // user already skipped away from the track that just ended → don't double-advance
                        if (endedVid != null && _state.value.videoId != endedVid) return@launch
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
        resolveJob?.cancel()
        resolving = true
        // stop current playback right away so the old stream stops downloading/playing
        // while we resolve (also resets position, keeping previous() navigation correct)
        try { player.stop(); player.clearMediaItems() } catch (_: Throwable) {}
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
        resolveJob = viewModelScope.launch {
            try {
                val url = resolver.resolveStreamUrl(vid)
                // superseded by a newer skip while resolving → drop this stale result
                if (queueManager.currentState().current?.videoId != vid) return@launch
                if (url == null) {
                    _state.value = _state.value.copy(isBuffering = false)
                    return@launch
                }
                try {
                    player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(vid).build())
                    player.prepare()
                    player.play()
                    _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                    maybeRecordHistory(cur)
                    prefetchNextForPlayback()
                } catch (_: Throwable) {
                    _state.value = _state.value.copy(isBuffering = false)
                }
            } finally {
                resolving = false
            }
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
    fun removeFromQueue(at: Int) { queueManager.removeAt(at) }
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
            // repeat same
            player.seekTo(0); player.play(); return
        }
        queueManager.next(_state.value.shuffle, _state.value.repeatOne) ?: return
        requestLoad(immediate)
    }

    fun previous() {
        // standard restart rule when we're actually mid-track
        if (!resolving && player.currentPosition > 3000) { player.seekTo(0); return }
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
        if (player.isPlaying) { player.pause(); _state.value = _state.value.copy(isPlaying = false) }
        else { player.play(); _state.value = _state.value.copy(isPlaying = true) }
    }

    fun seekTo(fraction: Float) {
        val dur = player.duration
        if (dur > 0) player.seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
    }

    override fun onCleared() {
        resolveJob?.cancel(); tickerJob?.cancel()
        pendingLoadJob?.cancel(); prefetchJob?.cancel()
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
