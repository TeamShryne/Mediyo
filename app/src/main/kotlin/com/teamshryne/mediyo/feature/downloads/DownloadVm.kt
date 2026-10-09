package com.teamshryne.mediyo.feature.downloads

import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.teamshryne.mediyo.data.cache.DownloadCache
import com.teamshryne.mediyo.data.cache.PlayerCache
import com.teamshryne.mediyo.data.cache.StoragePrefs
import com.teamshryne.mediyo.data.local.DownloadedTrackEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

sealed interface DownloadUiState {
    data object NotDownloaded : DownloadUiState
    data object Queued : DownloadUiState
    data class Downloading(val percent: Int) : DownloadUiState
    data object Completed : DownloadUiState
    data object Failed : DownloadUiState
}

data class CacheSizes(val songBytes: Long = 0L, val downloadBytes: Long = 0L)

/**
 * UI front for [DownloadUtil] + [StoragePrefs]: per-track state, library
 * list, storage sizes and all user actions. Size reads are refresh-driven
 * (cheap `cacheSpace` calls, never a flow) so the UI only polls while open.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class DownloadVm @Inject constructor(
    private val downloads: DownloadUtil,
    private val storage: StoragePrefs,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache
) : ViewModel() {
    val downloadMap: StateFlow<Map<String, Download>> = downloads.downloads
    val tracks: Flow<List<DownloadedTrackEntity>> = downloads.downloadedTracks
    val storageSettings = storage.stateFlow

    private val _sizes = MutableStateFlow(CacheSizes())
    val sizes: StateFlow<CacheSizes> = _sizes.asStateFlow()

    init { refreshSizes() }

    fun stateFor(videoId: String): Flow<DownloadUiState> = combine(
        downloads.downloadState(videoId),
        downloads.isDownloaded(videoId)
    ) { dl, inDb -> dl.toUiState(inDb) }

    fun download(track: Track) = downloads.download(track)
    fun remove(videoId: String) = downloads.remove(videoId)
    fun removeAll() {
        downloads.removeAll()
        refreshSizes()
    }

    fun toggle(track: Track) {
        val id = track.videoId ?: return
        when (downloads.downloads.value[id]?.toUiState(true)) {
            is DownloadUiState.Downloading, DownloadUiState.Queued -> downloads.remove(id)
            DownloadUiState.Completed -> downloads.remove(id)
            else -> downloads.download(track)
        }
    }

    fun clearSongCache() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                playerCache.keys.forEach { runCatching { playerCache.removeResource(it) } }
            }
            refreshSizes()
        }
    }

    fun refreshSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            val song = runCatching { playerCache.cacheSpace }.getOrDefault(0L)
            val dl = runCatching { downloadCache.cacheSpace }.getOrDefault(0L)
            _sizes.value = CacheSizes(song, dl)
        }
    }

    fun setCacheEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { storage.setEnabled(enabled) } }
    }

    fun setMaxMb(mb: Int) {
        viewModelScope.launch { runCatching { storage.setMaxMb(mb) } }
    }

    private fun Download?.toUiState(inDb: Boolean): DownloadUiState = when {
        this == null -> if (inDb) DownloadUiState.Completed else DownloadUiState.NotDownloaded
        state == Download.STATE_COMPLETED -> DownloadUiState.Completed
        state == Download.STATE_FAILED -> DownloadUiState.Failed
        state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING -> DownloadUiState.Queued
        state == Download.STATE_DOWNLOADING -> {
            val total = contentLength
            val done = getBytesDownloaded()
            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
            DownloadUiState.Downloading(pct)
        }
        else -> if (inDb) DownloadUiState.Completed else DownloadUiState.NotDownloaded
    }
}
