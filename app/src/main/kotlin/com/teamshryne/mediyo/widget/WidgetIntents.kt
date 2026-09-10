package com.teamshryne.mediyo.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.teamshryne.mediyo.playback.PlaybackService

/**
 * Widget -> service intents, Metrolist-style: buttons address the playback
 * service directly (explicit intents, no Glance worker in between).
 * A tap on a widget is user interaction, so starting the foreground service
 * from it is allowed even on Android 14+.
 */
object WidgetIntents {
    const val ACTION_TOGGLE = "com.teamshryne.mediyo.widget.TOGGLE"
    const val ACTION_NEXT = "com.teamshryne.mediyo.widget.NEXT"
    const val ACTION_PREV = "com.teamshryne.mediyo.widget.PREV"
    const val ACTION_LIKE = "com.teamshryne.mediyo.widget.LIKE"
    const val ACTION_PLAY_PLAYLIST = "com.teamshryne.mediyo.widget.PLAY_PLAYLIST"
    const val ACTION_PLAY_LIKED = "com.teamshryne.mediyo.widget.PLAY_LIKED"

    const val EXTRA_PLAYLIST_ID = "playlist_id"

    fun serviceIntent(ctx: Context, action: String, requestCode: Int, playlistId: String? = null): PendingIntent {
        val intent = Intent(ctx, PlaybackService::class.java).apply {
            this.action = action
            if (playlistId != null) putExtra(EXTRA_PLAYLIST_ID, playlistId)
        }
        return PendingIntent.getService(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun toggle(ctx: Context) = serviceIntent(ctx, ACTION_TOGGLE, 101)
    fun next(ctx: Context) = serviceIntent(ctx, ACTION_NEXT, 102)
    fun prev(ctx: Context) = serviceIntent(ctx, ACTION_PREV, 103)
    fun like(ctx: Context) = serviceIntent(ctx, ACTION_LIKE, 104)
    fun playLiked(ctx: Context) = serviceIntent(ctx, ACTION_PLAY_LIKED, 105)
    fun playPlaylist(ctx: Context, playlistId: String, slot: Int) =
        serviceIntent(ctx, ACTION_PLAY_PLAYLIST, 200 + slot, playlistId)

    fun openApp(ctx: Context): PendingIntent {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(ctx.packageName)
        return PendingIntent.getActivity(
            ctx, 100, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Used by receivers when forwarding a broadcast tap (same exemption as direct taps). */
    fun startService(ctx: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java).apply {
                action = intent.action
                putExtras(intent)
            })
        } catch (_: Throwable) {}
    }
}
