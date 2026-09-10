package com.teamshryne.mediyo.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.update.AppUpdater
import com.teamshryne.mediyo.data.update.UpdateCheck
import com.teamshryne.mediyo.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    val updater: AppUpdater,
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

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var autoChecked = false

    /** Silent startup check: surfaces nothing unless an update is Available. */
    fun silentCheck() {
        if (autoChecked) return
        autoChecked = true
        viewModelScope.launch {
            _state.value = State.Checking
            _state.value = when (val r = updater.check()) {
                is UpdateCheck.Available -> State.Available(r.info)
                else -> State.Idle
            }
        }
    }

    fun manualCheck() {
        viewModelScope.launch {
            _state.value = State.Checking
            _state.value = when (val r = updater.check()) {
                is UpdateCheck.Available -> State.Available(r.info)
                is UpdateCheck.UpToDate -> State.UpToDate
                is UpdateCheck.Failed -> State.Error(r.message)
            }
        }
    }

    fun download() {
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
}
