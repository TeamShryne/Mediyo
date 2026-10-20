package com.teamshryne.mediyo.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.data.update.AppUpdater
import com.teamshryne.mediyo.data.update.UpdateChannel
import com.teamshryne.mediyo.data.update.UpdateCheck
import com.teamshryne.mediyo.data.update.UpdateInfo
import com.teamshryne.mediyo.data.update.UpdatePrefs
import com.teamshryne.mediyo.data.update.ChangelogEntry
import com.teamshryne.mediyo.data.update.notesSince
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    val updater: AppUpdater,
    private val prefs: UpdatePrefs,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(
            val info: UpdateInfo,
            val progress: Float? = null,
            val downloading: Boolean = false,
            val downloaded: File? = null,
            val error: String? = null,
        ) : State
        data object UpToDate : State
        data class Error(val message: String) : State
    }

    /** What changed between the build the user is running and the newest one. */
    sealed interface ChangelogState {
        data object Idle : ChangelogState
        data object Loading : ChangelogState
        data class Ready(val entries: List<ChangelogEntry>) : ChangelogState
        data class Failed(val message: String) : ChangelogState
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _changelog = MutableStateFlow<ChangelogState>(ChangelogState.Idle)
    val changelog: StateFlow<ChangelogState> = _changelog.asStateFlow()

    /** Which track this install follows. */
    val channel: StateFlow<UpdateChannel> =
        prefs.channelFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateChannel.STABLE)

    private var autoChecked = false

    /** Silent startup check: surfaces nothing unless an update is Available. No-op in debug. */
    fun silentCheck() {
        if (BuildConfig.DEBUG) return
        if (autoChecked) return
        autoChecked = true
        viewModelScope.launch {
            _state.value = State.Checking
            _state.value = when (val r = updater.check(channel.value)) {
                is UpdateCheck.Available -> State.Available(r.info)
                else -> State.Idle
            }
        }
    }

    fun manualCheck() {
        if (BuildConfig.DEBUG) {
            _state.value = State.Error("App updates are disabled in debug builds")
            return
        }
        viewModelScope.launch {
            _state.value = State.Checking
            _state.value = when (val r = updater.check(channel.value)) {
                is UpdateCheck.Available -> State.Available(r.info)
                is UpdateCheck.UpToDate -> State.UpToDate
                is UpdateCheck.Failed -> State.Error(r.message)
            }
        }
    }

    /**
     * Switch track. The two tracks are separate feeds, so switching means
     * re-checking: the version you are on may not be the newest on the
     * other track.
     */
    fun setChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            prefs.setChannel(channel)
            _state.value = State.Idle
            // The notes are per channel: a nightly carries the nightlies on
            // top of the releases, and the stable feed carries only the
            // releases. The old ones are not right any more.
            _changelog.value = ChangelogState.Idle
            manualCheck()
            loadChangelog()
        }
    }

    fun download() {
        if (BuildConfig.DEBUG) return
        val cur = _state.value as? State.Available ?: return
        if (cur.downloading) return
        viewModelScope.launch {
            _state.value = cur.copy(downloading = true, error = null)
            try {
                val file = updater.download(cur.info) { p ->
                    (_state.value as? State.Available)?.let {
                        _state.value = it.copy(progress = p)
                    }
                }
                (_state.value as? State.Available)?.let {
                    _state.value = it.copy(downloading = false, progress = 1f, downloaded = file)
                }
            } catch (e: Exception) {
                _state.value = cur.copy(downloading = false, error = e.message ?: "Download failed")
            }
        }
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    /**
     * Load what changed since the build this one is.
     *
     * The history is fetched rather than guessed: it is published with each
     * release, and the last copy is kept on disk so the notes are readable
     * without a connection.
     */
    fun loadChangelog() {
        if (BuildConfig.DEBUG) return
        if (_changelog.value is ChangelogState.Loading) return
        viewModelScope.launch {
            _changelog.value = ChangelogState.Loading
            val track = channel.value
            val history = updater.fetchChangelog(track) ?: updater.cachedChangelog(track)
            _changelog.value = if (history == null) {
                ChangelogState.Failed("Could not load the release notes")
            } else {
                ChangelogState.Ready(history.notesSince(BuildConfig.VERSION_CODE, track))
            }
        }
    }
}
