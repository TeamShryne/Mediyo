package com.teamshryne.mediyo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.teamshryne.mediyo.R
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Hilt bridge for the system-instantiated widget receivers. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetManager(): MediyoWidgetManager
}

fun widgetManagerOf(context: Context): MediyoWidgetManager? = try {
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetManager()
} catch (_: Throwable) {
    null
}

/**
 * Renders both home widgets with classic RemoteViews, Metrolist-style.
 *
 * - Synchronous push: text + icon resources + cached bitmap, then
 *   `updateAppWidget`. No recomposition framework in the path.
 * - Artwork comes from [WidgetArtworkCache] fast path (memory/disk); a
 *   genuinely new track paints text/buttons instantly and the art pops in
 *   via one follow-up render when the background fetch lands.
 * - Size adaptation reads `getAppWidgetOptions` minWidth like Metrolist:
 *   narrow -> mini layout, otherwise hero.
 */
@Singleton
class MediyoWidgetManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: WidgetStateRepository,
    private val playlists: PlaylistRepository,
    private val artCache: WidgetArtworkCache
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Full repaint of every installed Mediyo widget. Safe from anywhere. */
    suspend fun renderAll() {
        renderPlayer()
        renderLauncher()
    }

    fun renderAllAsync() {
        scope.launch { runCatching { renderAll() } }
    }

    suspend fun renderPlayer() {
        val state = try { repo.read() } catch (_: Throwable) { WidgetNowPlaying() }
        val art = try { artCache.get(state.artworkUrl) } catch (_: Throwable) { null }
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MediyoPlayerReceiver::class.java))
            for (id in ids) {
                val opts = try { mgr.getAppWidgetOptions(id) } catch (_: Throwable) { null }
                val minWidth = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300) ?: 300
                val views = if (minWidth < 220) {
                    miniViews(state, art)
                } else {
                    heroViews(state, art)
                }
                try { mgr.updateAppWidget(id, views) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        // New track whose art isn't cached yet: paint now, refresh when it lands.
        artCache.prefetch(state.artworkUrl) { renderAllAsync() }
    }

    suspend fun renderLauncher() {
        val list = try { playlists.getPlaylists().take(MAX_PLAYLIST_SLOTS) } catch (_: Throwable) { emptyList() }
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PlaylistLauncherReceiver::class.java))
            for (id in ids) {
                try { mgr.updateAppWidget(id, launcherViews(list)) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    // ── Player ───────────────────────────────────────────────────────────

    private fun miniViews(state: WidgetNowPlaying, art: Bitmap?): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_player_mini)
        views.setTextViewText(R.id.widget_title, state.title.ifBlank { "Mediyo" })
        views.setTextViewText(R.id.widget_artist, state.artist)
        setArt(views, R.id.widget_art, art)
        views.setImageViewResource(
            R.id.widget_btn_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setOnClickPendingIntent(R.id.widget_art, WidgetIntents.openApp(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_play, WidgetIntents.toggle(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_next, WidgetIntents.next(ctx))
        return views
    }

    private fun heroViews(state: WidgetNowPlaying, art: Bitmap?): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_player_hero)
        views.setTextViewText(
            R.id.widget_title,
            state.title.ifBlank { "Nothing playing" }
        )
        views.setTextViewText(
            R.id.widget_artist,
            when {
                !state.hasTrack -> "Tap ♥ to shuffle liked"
                state.isBuffering -> "Buffering…"
                state.isPlaying -> state.artist.ifBlank { "Now playing" }
                else -> state.artist.ifBlank { "Paused" }
            }
        )
        setArt(views, R.id.widget_art, art)
        views.setImageViewResource(
            R.id.widget_btn_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_btn_like,
            if (state.liked) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline
        )
        val level = (state.progress.coerceIn(0f, 1f) * 1000).toInt()
        views.setProgressBar(R.id.widget_progress, 1000, level, false)
        views.setOnClickPendingIntent(R.id.widget_art, WidgetIntents.openApp(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, WidgetIntents.prev(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_play, WidgetIntents.toggle(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_next, WidgetIntents.next(ctx))
        views.setOnClickPendingIntent(R.id.widget_btn_like, WidgetIntents.like(ctx))
        return views
    }

    private fun setArt(views: RemoteViews, viewId: Int, art: Bitmap?) {
        if (art != null) {
            try { views.setImageViewBitmap(viewId, art) } catch (_: Throwable) {
                views.setImageViewResource(viewId, R.drawable.ic_widget_note)
            }
        } else {
            views.setImageViewResource(viewId, R.drawable.ic_widget_note)
        }
    }

    // ── Launcher ─────────────────────────────────────────────────────────

    private fun launcherViews(list: List<com.teamshryne.mediyo.data.local.LocalPlaylistEntity>): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_launcher)
        views.setOnClickPendingIntent(R.id.widget_open, WidgetIntents.openApp(ctx))
        views.setOnClickPendingIntent(R.id.widget_row_liked, WidgetIntents.playLiked(ctx))
        for (slot in 0 until MAX_PLAYLIST_SLOTS) {
            val rowId = ctx.resources.getIdentifier("widget_row_pl_$slot", "id", ctx.packageName)
            val titleId = ctx.resources.getIdentifier("widget_row_pl_${slot}_title", "id", ctx.packageName)
            val subId = ctx.resources.getIdentifier("widget_row_pl_${slot}_sub", "id", ctx.packageName)
            if (rowId == 0) continue
            val pl = list.getOrNull(slot)
            if (pl == null) {
                views.setViewVisibility(rowId, View.GONE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                if (titleId != 0) views.setTextViewText(titleId, pl.title)
                if (subId != 0) views.setTextViewText(subId, "${pl.trackCount} songs")
                views.setOnClickPendingIntent(rowId, WidgetIntents.playPlaylist(ctx, pl.id, slot))
            }
        }
        return views
    }

    companion object {
        const val MAX_PLAYLIST_SLOTS = 5
    }
}
