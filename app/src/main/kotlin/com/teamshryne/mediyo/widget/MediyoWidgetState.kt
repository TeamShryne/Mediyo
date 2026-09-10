package com.teamshryne.mediyo.widget

/**
 * Single source of truth for what the home widgets render.
 *
 * Persisted to DataStore (see [WidgetStateRepository]) so widgets render
 * instantly after reboot / process death without waiting for the
 * MediaSession to reconnect.
 */
data class WidgetNowPlaying(
    val videoId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val liked: Boolean = false,
    /** 0..1 fraction; updated instantly on track/play change, throttled while playing. */
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L
) {
    val hasTrack: Boolean get() = videoId != null && title.isNotEmpty()
}
