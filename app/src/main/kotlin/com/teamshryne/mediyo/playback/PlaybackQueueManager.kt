package com.teamshryne.mediyo.playback

import android.util.Log
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.PlayQueueState
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.toDomainTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor(
    private val bridge: MediyoBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlayQueueState())
    val state: StateFlow<PlayQueueState> = _state

    private var shuffleOrder: List<Int>? = null
    private var originalEntries: List<Track> = emptyList()

    fun currentState(): PlayQueueState = _state.value

    fun setQueue(origin: PlayOrigin, entries: List<Track>, startIndex: Int, resetRadio: Boolean = true) {
        if (entries.isEmpty()) return
        val idx = startIndex.coerceIn(0, entries.lastIndex)
        originalEntries = entries.toList()
        shuffleOrder = null
        _state.value = PlayQueueState(
            origin = origin,
            entries = entries,
            index = idx,
            radioContinuation = if (resetRadio) null else _state.value.radioContinuation,
            isFetchingRadio = false
        )
        maybePrefetchRadio()
    }

    fun playSingle(track: Track, origin: PlayOrigin = PlayOrigin.Single(track.videoId ?: "")) {
        setQueue(origin, listOf(track), 0)
    }

    fun addNext(track: Track) {
        val s = _state.value
        if (s.entries.isEmpty()) { setQueue(PlayOrigin.Single(track.videoId ?: ""), listOf(track), 0); return }
        val insertAt = (s.index + 1).coerceIn(0, s.entries.size)
        val newEntries = s.entries.toMutableList().apply { add(insertAt, track) }
        _state.value = s.copy(entries = newEntries)
    }

    fun addNextList(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val s = _state.value
        if (s.entries.isEmpty()) { setQueue(PlayOrigin.Unknown, tracks, 0); return }
        val insertAt = (s.index + 1).coerceIn(0, s.entries.size)
        val newEntries = s.entries.toMutableList().apply { addAll(insertAt, tracks) }
        _state.value = s.copy(entries = newEntries)
    }

    fun addLast(track: Track) {
        val s = _state.value
        if (s.entries.isEmpty()) { setQueue(PlayOrigin.Single(track.videoId ?: ""), listOf(track), 0); return }
        _state.value = s.copy(entries = s.entries + track)
    }

    fun addLastList(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val s = _state.value
        if (s.entries.isEmpty()) { setQueue(PlayOrigin.Unknown, tracks, 0); return }
        _state.value = s.copy(entries = s.entries + tracks)
    }

    fun removeAt(pos: Int) {
        val s = _state.value
        if (pos !in s.entries.indices) return
        val newEntries = s.entries.toMutableList().apply { removeAt(pos) }
        var newIndex = s.index
        if (pos < s.index) newIndex -= 1
        else if (pos == s.index) {
            // if we removed current, clamp to valid
            newIndex = newIndex.coerceIn(0, newEntries.lastIndex.coerceAtLeast(0))
        }
        if (newEntries.isEmpty()) newIndex = -1
        _state.value = s.copy(entries = newEntries, index = newIndex)
    }

    fun move(from: Int, to: Int) {
        val s = _state.value
        if (from !in s.entries.indices || to !in s.entries.indices) return
        val mutable = s.entries.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        var newIndex = s.index
        if (s.index == from) newIndex = to
        else if (from < s.index && to >= s.index) newIndex -= 1
        else if (from > s.index && to <= s.index) newIndex += 1
        _state.value = s.copy(entries = mutable, index = newIndex)
    }

    fun next(shuffle: Boolean, repeatOne: Boolean): Int? {
        val s = _state.value
        if (s.entries.isEmpty()) return null
        if (repeatOne) return s.index
        val nextIdx = if (shuffle && s.entries.size > 1) {
            // use stable shuffleOrder if exists else random non-repeating
            val order = shuffleOrder
            if (order != null && order.size == s.entries.size) {
                val posInOrder = order.indexOf(s.index)
                if (posInOrder >= 0 && posInOrder + 1 < order.size) order[posInOrder + 1]
                else order.firstOrNull() ?: ((s.index + 1) % s.entries.size)
            } else {
                var r = kotlin.random.Random.nextInt(s.entries.size)
                if (r == s.index) r = (r + 1) % s.entries.size
                r
            }
        } else {
            if (s.index + 1 <= s.entries.lastIndex) s.index + 1 else 0 // circular, radio will extend before wrap
        }
        _state.value = s.copy(index = nextIdx)
        maybePrefetchRadio()
        return nextIdx
    }

    fun previous(currentPositionMs: Long): Int? {
        val s = _state.value
        if (s.entries.isEmpty()) return null
        if (currentPositionMs > 3000) return s.index // caller should seek instead
        val prevIdx = if (s.index - 1 >= 0) s.index - 1 else s.entries.lastIndex
        _state.value = s.copy(index = prevIdx)
        return prevIdx
    }

    fun setIndex(idx: Int) {
        val s = _state.value
        if (idx !in s.entries.indices) return
        _state.value = s.copy(index = idx)
        maybePrefetchRadio()
    }

    fun setShuffle(enabled: Boolean, currentIndex: Int) {
        if (enabled) {
            val s = _state.value
            if (s.entries.size < 2) return
            // stable shuffle anchored at current
            val indices = s.entries.indices.toMutableList()
            indices.remove(currentIndex)
            indices.shuffle()
            val order = listOf(currentIndex) + indices
            shuffleOrder = order
            originalEntries = s.entries.toList()
        } else {
            shuffleOrder = null
        }
    }

    fun clear() {
        _state.value = PlayQueueState()
        shuffleOrder = null
        originalEntries = emptyList()
    }

    fun updateRadioContinuation(token: String?) {
        _state.value = _state.value.copy(radioContinuation = token, isFetchingRadio = false)
    }

    fun setFetching(f: Boolean) {
        _state.value = _state.value.copy(isFetchingRadio = f)
    }

    // ── Always-Radio prefetch ───────────────────────────────────────────────
    private fun maybePrefetchRadio() {
        val s = _state.value
        if (!s.isRadioEnabled) return
        if (s.entries.isEmpty() || s.index < 0) return
        // trigger when within 3 of end OR when queue is small (initial generation for search/home)
        // also always prefetch immediately after origin set (radioContinuation == null) to ensure home/search get radio
        val nearEnd = s.index >= (s.entries.size - 3).coerceAtLeast(0)
        val shouldPrefetch = s.radioContinuation == null || nearEnd || s.entries.size < 12
        if (!shouldPrefetch) return
        if (s.isFetchingRadio) return
        // if we already have a continuation, extend; else fetch initial radio queue for current track
        scope.launch {
            setFetching(true)
            try {
                val current = s.entries.getOrNull(s.index) ?: run { setFetching(false); return@launch }
                val vid = current.videoId ?: run { setFetching(false); return@launch }
                val originPid = s.origin.playlistIdForRadio()
                val fetched = if (s.radioContinuation != null) {
                    Log.d("QueueManager", "extend radio cont=${s.radioContinuation.take(20)}")
                    bridge.extendQueue(s.radioContinuation)
                } else {
                    Log.d("QueueManager", "get radio for $vid pid=$originPid")
                    bridge.getQueue(vid, originPid)
                }
                val newTracks = fetched.items.map { qi ->
                    Track(
                        videoId = qi.videoId,
                        browseId = null,
                        playlistId = null,
                        title = qi.title,
                        artists = qi.artists,
                        album = qi.album,
                        artworkUrl = qi.thumbnails.firstOrNull()?.url,
                        duration = qi.duration,
                        category = "Song"
                    )
                }.filter { it.videoId != null }
                if (newTracks.isEmpty()) {
                    updateRadioContinuation(null)
                    return@launch
                }
                // append unique by videoId
                val cur = _state.value
                val seen = cur.entries.mapNotNull { it.videoId }.toHashSet()
                val unique = newTracks.filter { seen.add(it.videoId!!) }
                if (unique.isEmpty()) {
                    // still need to update continuation to keep flowing
                    updateRadioContinuation(fetched.continuation)
                    return@launch
                }
                val newEntries = cur.entries + unique
                _state.value = cur.copy(entries = newEntries, radioContinuation = fetched.continuation, isFetchingRadio = false)
                Log.d("QueueManager", "radio append ${unique.size} total=${newEntries.size} nextCont=${fetched.continuation?.take(20)}")
            } catch (e: Throwable) {
                Log.e("QueueManager", "radio prefetch failed", e)
                setFetching(false)
            }
        }
    }

    fun forcePrefetch() {
        maybePrefetchRadio()
    }
}
