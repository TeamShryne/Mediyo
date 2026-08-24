package com.teamshryne.mediyo.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.mediyo_ffi.FfiSearchResult

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
    val queueIndex: Int = -1
)

data class QueueEntry(val videoId: String, val title: String, val artist: String, val artwork: String?)

@HiltViewModel class PlayerViewModel @Inject constructor(
    private val resolver: NewPipeResolver,
    @ApplicationContext private val ctx: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var player: ExoPlayer? = null
    private var queue: List<QueueEntry> = emptyList()
    private var queueIndex = -1
    private var resolveJob: Job? = null
    private var tickerJob: Job? = null

    init { startTicker() }

    private fun ensurePlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(ctx).build()
            startTicker()
        }
        return player!!
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val p = player ?: continue
                val dur = p.duration.coerceAtLeast(0L)
                val pos = p.currentPosition.coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                _state.value = _state.value.copy(
                    positionMs = pos,
                    durationMs = dur,
                    progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    isBuffering = p.playbackState == ExoPlayer.STATE_BUFFERING
                )
            }
        }
    }

    /** Play a single track (replaces the queue with one entry). */
    fun play(videoId: String, title: String, artist: String, artwork: String?) =
        playQueue(listOf(QueueEntry(videoId, title, artist, artwork)), 0)

    /** Play a list of tracks starting at [startIndex]. */
    fun playQueue(entries: List<QueueEntry>, startIndex: Int) {
        if (entries.isEmpty()) return
        queue = entries
        queueIndex = startIndex.coerceIn(0, entries.lastIndex)
        loadCurrent()
    }

    /** Convenience for lists of search results that may contain non-track entries. */
    fun playFrom(items: List<FfiSearchResult>, item: FfiSearchResult) {
        val tracks = items.filter { it.videoId != null }
        val idx = tracks.indexOfFirst { it.videoId == item.videoId }
        if (idx < 0) {
            val vid = item.videoId ?: return
            play(vid, item.title, item.artists.joinToString(), item.thumbnails.firstOrNull()?.url)
        } else playQueue(tracks.map { it.toEntry() }, idx)
    }

    fun next() {
        if (queue.isEmpty()) return
        val nextIdx = when {
            _state.value.repeatOne -> queueIndex
            _state.value.shuffle && queue.size > 1 -> {
                var r = kotlin.random.Random.nextInt(queue.size)
                if (r == queueIndex) r = (r + 1) % queue.size
                r
            }
            queueIndex + 1 <= queue.lastIndex -> queueIndex + 1
            else -> 0
        }
        queueIndex = nextIdx
        loadCurrent()
    }

    fun previous() {
        val p = player
        if (p != null && p.currentPosition > 3000) { p.seekTo(0); return }
        if (queue.isEmpty()) return
        queueIndex = if (queueIndex - 1 >= 0) queueIndex - 1 else queue.lastIndex
        loadCurrent()
    }

    fun toggleRepeat() { _state.value = _state.value.copy(repeatOne = !_state.value.repeatOne) }
    fun toggleShuffle() { _state.value = _state.value.copy(shuffle = !_state.value.shuffle) }

    private fun FfiSearchResult.toEntry() = QueueEntry(
        videoId ?: "", title, artists.joinToString(), thumbnails.firstOrNull()?.url
    )

    private fun loadCurrent() {
        val entry = queue.getOrNull(queueIndex) ?: return
        resolveJob?.cancel()
        _state.value = _state.value.copy(
            videoId = entry.videoId.takeIf { it.isNotBlank() },
            title = entry.title,
            artist = entry.artist,
            artwork = entry.artwork,
            isPlaying = false,
            isBuffering = true,
            progress = 0f,
            positionMs = 0L,
            durationMs = 0L,
            queueSize = queue.size,
            queueIndex = queueIndex
        )
        resolveJob = viewModelScope.launch {
            val url = resolver.resolveStreamUrl(entry.videoId)
            if (url == null) {
                _state.value = _state.value.copy(isBuffering = false)
                return@launch
            }
            val p = ensurePlayer()
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.play()
            _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); _state.value = _state.value.copy(isPlaying = false) }
        else { p.play(); _state.value = _state.value.copy(isPlaying = true) }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val dur = p.duration
        if (dur > 0) p.seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
    }

    override fun onCleared() {
        resolveJob?.cancel(); tickerJob?.cancel()
        player?.release()
        super.onCleared()
    }
}
