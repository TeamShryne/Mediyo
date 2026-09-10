package com.teamshryne.mediyo.widget

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Push path: app -> DataStore -> classic RemoteViews render.
 *
 * - Always persists (cheap, ~1ms) so widgets survive process death.
 * - Redraws are instant on track / play-state / like change, throttled to
 *   [PROGRESS_THROTTLE_MS] for progress-only ticks while playing.
 * - Rendering is delegated to [MediyoWidgetManager] (classic RemoteViews,
 *   no recomposition framework in the path).
 */
@Singleton
class WidgetSync @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: WidgetStateRepository,
    private val renderer: MediyoWidgetManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastPushMs = 0L
    @Volatile private var lastKey = ""

    fun pushAsync(state: WidgetNowPlaying) {
        scope.launch {
            try { push(state) } catch (_: Throwable) {}
        }
    }

    suspend fun push(state: WidgetNowPlaying) {
        try { repo.save(state) } catch (_: Throwable) {}
        // Structural key: anything that must redraw instantly.
        val key = "${state.videoId}|${state.isPlaying}|${state.isBuffering}|${state.liked}|${state.title}"
        val now = System.currentTimeMillis()
        val structuralChange = key != lastKey
        val throttleElapsed = now - lastPushMs >= PROGRESS_THROTTLE_MS
        if (!structuralChange && !throttleElapsed) return
        lastKey = key
        lastPushMs = now
        refreshAll()
    }

    /** Redraw every installed Mediyo widget. Safe to call from anywhere. */
    suspend fun refreshAll() {
        try { renderer.renderAll() } catch (_: Throwable) {}
    }

    fun refreshAllAsync() {
        scope.launch { try { refreshAll() } catch (_: Throwable) {} }
    }

    companion object {
        /** Progress-only redraws at most every 6s while playing. */
        const val PROGRESS_THROTTLE_MS = 6_000L
    }
}
