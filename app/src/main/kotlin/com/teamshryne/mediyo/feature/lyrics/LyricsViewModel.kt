package com.teamshryne.mediyo.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.LyricsResult
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
    data class Ready(val track: LyricTrack) : LyricsUiState
    data object NotFound : LyricsUiState
    data object NeedsApiKey : LyricsUiState
    data class Error(val message: String) : LyricsUiState
    data object RateLimited : LyricsUiState
}

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repo: LyricsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state

    private var job: Job? = null
    private var lastKey: String? = null

    /**
     * Maintainable entry point — single method to load lyrics for any Track.
     * Handles videoId-based caching and duration hint for BetterLyrics hit rate.
     */
    fun load(track: Track, durationMs: Long? = null) {
        val key = "${track.videoId}:${track.title}:${track.artists.firstOrNull()}"
        if (key == lastKey && _state.value is LyricsUiState.Ready) return
        lastKey = key
        job?.cancel()
        _state.value = LyricsUiState.Loading
        job = viewModelScope.launch {
            val durSec = durationMs?.let { (it / 1000).toInt().takeIf { v -> v in 30..600 } }
                ?: track.duration?.let { parseDurationToSec(it) }
            when (val res = repo.getLyrics(track, durSec)) {
                is LyricsResult.Success -> _state.value = LyricsUiState.Ready(res.track)
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
        val key = "${track.videoId}:${track.title}:${track.artists.firstOrNull()}"
        lastKey = null
        _state.value = LyricsUiState.Loading
        job?.cancel()
        job = viewModelScope.launch {
            val durSec = durationMs?.let { (it / 1000).toInt().takeIf { v -> v in 30..600 } }
                ?: track.duration?.let { parseDurationToSec(it) }
            when (val res = repo.refreshLyrics(track, durSec)) {
                is LyricsResult.Success -> {
                    lastKey = key
                    _state.value = LyricsUiState.Ready(res.track)
                }
                LyricsResult.NotFound -> _state.value = LyricsUiState.NotFound
                LyricsResult.NeedsApiKey -> _state.value = LyricsUiState.NeedsApiKey
                LyricsResult.RateLimited -> _state.value = LyricsUiState.RateLimited
                is LyricsResult.Error -> _state.value = LyricsUiState.Error(res.message)
            }
        }
    }

    fun clear() {
        job?.cancel()
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
