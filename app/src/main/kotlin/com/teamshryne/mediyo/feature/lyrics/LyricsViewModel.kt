package com.teamshryne.mediyo.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.LyricsPrefs
import com.teamshryne.mediyo.data.lyrics.LyricsResult
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.data.lyrics.LyricsStyle
import com.teamshryne.mediyo.data.lyrics.LyricsStylePrefs
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.repository.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val track: LyricTrack, val provider: LyricsSource? = null) : LyricsUiState
    data object NotFound : LyricsUiState
    data object NeedsApiKey : LyricsUiState
    data class Error(val message: String) : LyricsUiState
    data object RateLimited : LyricsUiState
}

/**
 * One manual search: which source the user picked, the words they are
 * searching with, and what came back. [applied] is the signal the sheet uses
 * to close itself — the chosen lyrics are already on screen by then.
 */
data class LyricsSearchState(
    val source: LyricsSource,
    val title: String,
    val artist: String,
    val running: Boolean = false,
    val message: String? = null,
    val applied: Boolean = false,
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repo: LyricsRepository,
    private val events: com.teamshryne.mediyo.domain.repository.UserEventRepository,
    private val stylePrefs: LyricsStylePrefs,
    private val lyricsPrefs: LyricsPrefs
) : ViewModel() {

    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state

    /**
     * A manual provider search, or null when the sheet is closed. Kept here
     * rather than in the sheet so it survives the sheet's recomposition and a
     * result can land in [state] directly.
     */
    private val _search = MutableStateFlow<LyricsSearchState?>(null)
    val search: StateFlow<LyricsSearchState?> = _search

    /** Source chips follow the priority order the user set in Settings. */
    val searchOrder = lyricsPrefs.orderFlow

    /** Size / weight / line spacing chosen by the user. */
    val style: StateFlow<LyricsStyle> = stylePrefs.live

    private var job: Job? = null
    private var searchJob: Job? = null
    private var lastKey: String? = null
    private var lastSearchSource: LyricsSource? = null

    private fun keyOf(track: Track) =
        "${track.videoId}:${track.title}:${track.artists.firstOrNull()}"

    /**
     * Maintainable entry point — single method to load lyrics for any Track.
     * Handles videoId-based caching and duration hint for BetterLyrics hit rate.
     */
    fun load(track: Track, durationMs: Long? = null) {
        val key = keyOf(track)
        if (key == lastKey && _state.value is LyricsUiState.Ready) return
        // A new song invalidates a manual search for the previous one.
        _search.value = null
        searchJob?.cancel()
        lastKey = key
        job?.cancel()
        _state.value = LyricsUiState.Loading
        job = viewModelScope.launch {
            events.log(com.teamshryne.mediyo.domain.repository.UserEventTypes.LYRICS_OPEN, videoId = track.videoId)
            val durSec = durationMs?.let { (it / 1000).toInt().takeIf { v -> v in 30..600 } }
                ?: track.duration?.let { parseDurationToSec(it) }
            when (val res = repo.getLyrics(track, durSec)) {
                is LyricsResult.Success -> _state.value = LyricsUiState.Ready(res.track, res.provider)
                LyricsResult.NotFound -> _state.value = LyricsUiState.NotFound
                LyricsResult.NeedsApiKey -> _state.value = LyricsUiState.NeedsApiKey
                LyricsResult.RateLimited -> _state.value = LyricsUiState.RateLimited
                is LyricsResult.Error -> _state.value = LyricsUiState.Error(res.message)
            }
        }
    }

    fun retry(track: Track, durationMs: Long? = null) {
        lastKey = null
        load(track, durationMs)
    }

    /**
     * Force-bypass cache and refetch from network (used by three-dot menu when lyrics mode is on).
     * Unlike [retry], this evicts the cached entry so the next fetch hits providers.
     */
    fun refetch(track: Track, durationMs: Long? = null) {
        val key = keyOf(track)
        lastKey = null
        _search.value = null
        searchJob?.cancel()
        _state.value = LyricsUiState.Loading
        job?.cancel()
        job = viewModelScope.launch {
            val durSec = durationMs?.let { (it / 1000).toInt().takeIf { v -> v in 30..600 } }
                ?: track.duration?.let { parseDurationToSec(it) }
            when (val res = repo.refreshLyrics(track, durSec)) {
                is LyricsResult.Success -> {
                    lastKey = key
                    _state.value = LyricsUiState.Ready(res.track, res.provider)
                }
                LyricsResult.NotFound -> _state.value = LyricsUiState.NotFound
                LyricsResult.NeedsApiKey -> _state.value = LyricsUiState.NeedsApiKey
                LyricsResult.RateLimited -> _state.value = LyricsUiState.RateLimited
                is LyricsResult.Error -> _state.value = LyricsUiState.Error(res.message)
            }
        }
    }

    // ── Manual, provider-specific search ────────────────────────────────────

    /**
     * Seeds the search from the song that is playing, so the common case is
     * one tap on Search: the words are already the ones that failed.
     */
    fun openSearch(track: Track, durationMs: Long?) {
        val ready = (_state.value as? LyricsUiState.Ready)?.provider
        _search.value = LyricsSearchState(
            source = ready ?: lastSearchSource ?: LyricsSource.defaultOrder.first(),
            title = track.title,
            artist = track.artists.firstOrNull() ?: track.album.orEmpty(),
            applied = false
        )
        searchTrack = track
        searchDurationMs = durationMs
    }

    fun closeSearch() {
        // Only a manual search is cancelled: an automatic load may still be
        // running behind the sheet, and it must land when the sheet closes.
        searchJob?.cancel()
        _search.value = null
    }

    fun setSearchSource(source: LyricsSource) {
        _search.value = _search.value?.copy(source = source, message = null)
    }

    fun setSearchTitle(value: String) {
        _search.value = _search.value?.copy(title = value, message = null)
    }

    fun setSearchArtist(value: String) {
        _search.value = _search.value?.copy(artist = value, message = null)
    }

    private var searchTrack: Track? = null
    private var searchDurationMs: Long? = null

    /**
     * Asks exactly the chosen provider. On a hit the payload is pinned to this
     * track's cache and becomes the visible lyrics — a manual choice must not
     * be silently replaced by the priority order on the next open.
     */
    fun runSearch() {
        val s = _search.value ?: return
        val track = searchTrack ?: return
        val title = s.title.trim()
        val artist = s.artist.trim()
        if (title.isEmpty() || artist.isEmpty()) {
            _search.value = s.copy(message = "Title and artist are both needed.")
            return
        }
        job?.cancel()
        // The cancelled load would otherwise leave the screen spinning behind
        // the sheet if this search comes back empty.
        if (_state.value is LyricsUiState.Loading) _state.value = LyricsUiState.NotFound
        _search.value = s.copy(running = true, message = null)
        searchJob = viewModelScope.launch {
            val durSec = searchDurationMs?.let { (it / 1000).toInt().takeIf { v -> v in 30..600 } }
                ?: track.duration?.let { parseDurationToSec(it) }
            when (val res = repo.searchProvider(s.source, title, artist, track.album, durSec)) {
                is LyricsResult.Success -> {
                    repo.saveLyrics(track, durSec, res)
                    lastKey = keyOf(track)
                    lastSearchSource = s.source
                    _state.value = LyricsUiState.Ready(res.track, res.provider)
                    _search.value = _search.value?.copy(
                        running = false,
                        message = null,
                        applied = true
                    )
                }
                LyricsResult.NotFound -> _search.value = _search.value?.copy(
                    running = false,
                    message = "No lyrics for those words on ${s.source.label}."
                )
                LyricsResult.RateLimited -> _search.value = _search.value?.copy(
                    running = false,
                    message = "${s.source.label} is rate limiting right now. Try again shortly."
                )
                LyricsResult.NeedsApiKey -> _search.value = _search.value?.copy(
                    running = false,
                    message = "${s.source.label} needs an API key for this search."
                )
                is LyricsResult.Error -> _search.value = _search.value?.copy(
                    running = false,
                    message = "${s.source.label}: ${res.message.take(180)}"
                )
            }
        }
    }

    fun clear() {
        job?.cancel()
        searchJob?.cancel()
        _search.value = null
        _state.value = LyricsUiState.Idle
        lastKey = null
    }

    private fun parseDurationToSec(raw: String): Int? {
        return try {
            val parts = raw.split(":")
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                1 -> parts[0].toInt()
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
