package com.teamshryne.mediyo.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push path: app -> DataStore -> Glance update.
 *
 * - Always persists (cheap, ~1ms) so widgets survive process death.
 * - Glance redraws are throttled: instant on track / play-state / like
 *   change, at most every [PROGRESS_THROTTLE_MS] for progress-only ticks
 *   while playing. This is what keeps the widget "live" without draining battery.
 */
@Singleton
class WidgetSync @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: WidgetStateRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastGlancePushMs = 0L
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
        val throttleElapsed = now - lastGlancePushMs >= PROGRESS_THROTTLE_MS
        if (!structuralChange && !throttleElapsed) return
        lastKey = key
        lastGlancePushMs = now
        refreshAll()
    }

    /** Redraw every installed Mediyo widget. Safe to call from anywhere. */
    suspend fun refreshAll() {
        try {
            val manager = GlanceAppWidgetManager(ctx)
            for (id in manager.getGlanceIds(MediyoPlayerWidget::class.java)) {
                try { MediyoPlayerWidget().update(ctx, id) } catch (_: Throwable) {}
            }
            for (id in manager.getGlanceIds(PlaylistLauncherWidget::class.java)) {
                try { PlaylistLauncherWidget().update(ctx, id) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun refreshAllAsync() {
        scope.launch { try { refreshAll() } catch (_: Throwable) {} }
    }

    companion object {
        /** Progress-only redraws at most every 6s while playing. */
        const val PROGRESS_THROTTLE_MS = 6_000L
    }
}
