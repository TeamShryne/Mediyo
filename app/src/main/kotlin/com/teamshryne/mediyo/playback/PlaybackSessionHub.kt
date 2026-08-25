package com.teamshryne.mediyo.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide bridge between the playback logic layer ([com.teamshryne.mediyo.feature.player.PlayerViewModel])
 * and [PlaybackService]'s MediaSession. Both are singletons in the same process sharing one
 * ExoPlayer instance, so the player itself carries play/pause/progress/metadata — this hub only
 * carries what the session cannot know from the player: app-managed queue availability and the
 * like state of the current track.
 */
@Singleton
class PlaybackSessionHub @Inject constructor() {

    data class Snapshot(
        val hasTrack: Boolean = false,
        val isPlaying: Boolean = false,
        val liked: Boolean = false,
        val canSkipNext: Boolean = false,
        val canSkipPrevious: Boolean = false
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Handlers wired by the playback layer (queue navigation / like toggling). */
    @Volatile var onSkipNext: (() -> Unit)? = null
    @Volatile var onSkipPrevious: (() -> Unit)? = null
    @Volatile var onToggleLike: (() -> Unit)? = null

    fun publish(update: (Snapshot) -> Snapshot) {
        _snapshot.value = update(_snapshot.value)
    }
}
