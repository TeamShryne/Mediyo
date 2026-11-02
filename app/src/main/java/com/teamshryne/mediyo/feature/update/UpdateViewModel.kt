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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

    /**
     * The track this install follows, or null while the stored choice is
     * still being read.
     *
     * Null rather than a guessed default: a guess would flash the wrong
     * channel on screen for anyone who chose the other one.
     */
    private val _channel = MutableStateFlow<UpdateChannel?>(null)
    val channel: StateFlow<UpdateChannel?> = _channel.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.channelFlow.collect { _channel.value = it }
        }
    }

    private var autoChecked = false

    /**
     * Silent startup check: surfaces nothing unless an update is Available.
     * No-op in debug.
     *
     * The track is read from disk rather than taken from [channel]: that
     * flow starts empty and only carries the stored choice once the read
     * lands, which is after this check has started. A nightly user would
     * have been checked against the stable feed and told there was nothing
     * new -- the one thing the nightly channel is for.
     */
    fun silentCheck() {
        if (BuildConfig.DEBUG) return
        if (autoChecked) return
        autoChecked = true
        viewModelScope.launch {
            check(prefs.currentChannel(), silent = true)
        }
    }

    fun manualCheck() {
        if (BuildConfig.DEBUG) {
            _state.value = State.Error("App updates are disabled in debug builds")
            return
        }
        viewModelScope.launch {
            check(prefs.currentChannel(), silent = false)
        }
    }

    /**
     * Checks one track and moves the state to match.
     *
     * A silent check says nothing when there is nothing to say: it is run at
     * startup, where a message about a network that was briefly down would
     * be noise, not news.
     */
    private suspend fun check(track: UpdateChannel, silent: Boolean) {
        _state.value = State.Checking
        _state.value = when (val r = updater.check(track)) {
            is UpdateCheck.Available -> State.Available(r.info)
            is UpdateCheck.UpToDate -> if (silent) State.Idle else State.UpToDate
            is UpdateCheck.Failed -> if (silent) State.Idle else State.Error(r.message)
        }
    }

    /**
     * Switch track. The two tracks are separate feeds, so switching means
     * re-checking: the version you are on may not be the newest on the
     * other track.
     */
    fun setChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            // The stored value, not the one in memory: the flow only carries
            // a change once it lands, and a tap that arrives in between has
            // to be compared with what is actually stored.
            if (prefs.currentChannel() == channel) return@launch
            prefs.setChannel(channel)
            _state.value = State.Idle
            // The notes are per channel: a nightly carries the nightlies on
            // top of the releases, and the stable feed carries only the
            // releases. The old ones are not right any more.
            _changelog.value = ChangelogState.Idle
            // The track that was just chosen, not the one that was in
            // memory: the check has to run against the feed the user asked
            // for, and the stored value only reaches the flow later.
            check(channel, silent = false)
            loadChangelog(channel)
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
            loadChangelog(prefs.currentChannel())
        }
    }

    private suspend fun loadChangelog(track: UpdateChannel) {
        _changelog.value = ChangelogState.Loading
        val history = updater.fetchChangelog(track) ?: updater.cachedChangelog(track)
        _changelog.value = if (history == null) {
            ChangelogState.Failed("Could not load the release notes")
        } else {
            ChangelogState.Ready(history.notesSince(BuildConfig.VERSION_CODE, track))
        }
    }
}
