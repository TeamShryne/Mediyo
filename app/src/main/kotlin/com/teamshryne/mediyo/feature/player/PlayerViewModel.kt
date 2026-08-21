package com.teamshryne.mediyo.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class PlayerState(val title: String = "", val artist: String = "", val artwork: String? = null, val isPlaying: Boolean = false, val progress: Float = 0f)

@HiltViewModel class PlayerViewModel @Inject constructor(
    private val resolver: NewPipeResolver,
    @ApplicationContext private val ctx: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private var player: ExoPlayer? = null

    private fun ensurePlayer(): ExoPlayer {
        if (player == null) player = ExoPlayer.Builder(ctx).build()
        return player!!
    }

    fun play(videoId: String, title: String, artist: String, artwork: String?) {
        _state.value = PlayerState(title, artist, artwork, false, 0f)
        viewModelScope.launch {
            val url = resolver.resolveStreamUrl(videoId)
            if (url != null) {
                val p = ensurePlayer()
                p.setMediaItem(MediaItem.fromUri(url))
                p.prepare(); p.play()
                _state.value = _state.value.copy(isPlaying = true)
            }
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); _state.value = _state.value.copy(isPlaying = false) }
        else { p.play(); _state.value = _state.value.copy(isPlaying = true) }
    }

    override fun onCleared() { player?.release(); super.onCleared() }
}
