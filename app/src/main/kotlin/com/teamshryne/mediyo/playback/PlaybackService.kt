package com.teamshryne.mediyo.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.teamshryne.mediyo.MainActivity
import com.teamshryne.mediyo.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the app's MediaSession around the shared singleton [ExoPlayer].
 *
 * Follows the proven Media3 pattern (Innertune / Metrolist): go foreground
 * immediately in [onCreate] / [onStartCommand] with a placeholder notification
 * so the 5–10s ANR window after [android.content.Context.startForegroundService]
 * is never missed, even when the stream URL resolve takes a long time. The real
 * media notification (artwork, title, progress, like/shuffle/repeat) replaces
 * the placeholder once playback starts — exactly how YT Music / Spotify behave.
 *
 * - Android 13+: system media card + our custom Like/Shuffle/Repeat buttons
 * - Pre-13: [DefaultMediaNotificationProvider] builds from custom layout
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.teamshryne.mediyo.session.TOGGLE_LIKE"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mediyo_playback"
    }

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var hub: PlaybackSessionHub

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var latestMediaNotification: Notification? = null

    private fun likeButton(liked: Boolean): CommandButton = CommandButton.Builder()
        .setDisplayName(if (liked) "Remove from liked music" else "Save to liked music")
        .setIconResId(if (liked) R.drawable.ic_media_liked else R.drawable.ic_media_like)
        .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
        .build()

    /** Full button set so pre-13 notifications get prev/play-pause/next too. */
    private fun customLayout(liked: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder().setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS).build(),
        CommandButton.Builder().setPlayerCommand(Player.COMMAND_PLAY_PAUSE).build(),
        CommandButton.Builder().setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT).build(),
        likeButton(liked)
    )

    private fun syncCustomLayout() {
        val s = session ?: return
        val layout = customLayout(hub.snapshot.value.liked)
        for (controller in s.connectedControllers) {
            s.setCustomLayout(controller, layout)
        }
    }

    // ── Foreground plumbing (Metrolist-proven) ──────────────────────────────

    private fun ensureForegroundChannelExists() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.music_player), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun createPlaceholderNotification(): Notification {
        ensureForegroundChannelExists()
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.music_player))
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun ensureForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (_: Throwable) {
        try { stopSelf() } catch (_: Throwable) {}
        false
    }

    override fun onCreate() {
        super.onCreate()

        // Media3 — surface ForegroundServiceStartNotAllowedException instead of crashing
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                // Already handling via try/catch in ensureForeground; nothing extra needed.
            }
        })

        // Must be foreground before any heavy I/O (DataStore, etc.) or the
        // 10s timeout after startForegroundService() will ANR us on slow networks.
        ensureForegroundChannelExists()
        ensureForeground(createPlaceholderNotification())

        // Media notification provider — wrapped to capture the latest media
        // notification so onStartCommand can re-promote without losing artwork.
        val defaultProvider = DefaultMediaNotificationProvider(
            this, { NOTIFICATION_ID }, CHANNEL_ID, R.string.music_player
        ).apply { setSmallIcon(R.drawable.ic_notification_small) }

        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                mediaButtonPreferences: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val trackingCallback = MediaNotification.Provider.Callback { incoming ->
                    latestMediaNotification = incoming.notification
                    onNotificationChangedCallback.onNotificationChanged(incoming)
                }
                return defaultProvider.createNotification(
                    mediaSession, mediaButtonPreferences, actionFactory, trackingCallback
                ).also { latestMediaNotification = it.notification }
            }

            override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
                defaultProvider.handleCustomCommand(session, action, extras)
        })

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)

            override fun seekToNext() { hub.onSkipNext?.invoke() ?: super.seekToNext() }
            override fun seekToNextMediaItem() { hub.onSkipNext?.invoke() ?: super.seekToNextMediaItem() }
            override fun seekToPrevious() { hub.onSkipPrevious?.invoke() ?: super.seekToPrevious() }
            override fun seekToPreviousMediaItem() { hub.onSkipPrevious?.invoke() ?: super.seekToPreviousMediaItem() }
        }

        val builder = MediaSession.Builder(this, sessionPlayer)
            .setCustomLayout(customLayout(hub.snapshot.value.liked))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> =
                    if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                        hub.onToggleLike?.invoke()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    } else {
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
            })
        if (sessionActivity != null) builder.setSessionActivity(sessionActivity)
        session = builder.build()

        scope.launch {
            hub.snapshot.map { it.liked }.distinctUntilChanged().collect { syncCustomLayout() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-promote on every start — OEMs strictly enforce an early startForeground()
        // even when the service is already considered foreground.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureForeground(latestMediaNotification ?: createPlaceholderNotification())
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (_: Throwable) {
            // ForegroundServiceStartNotAllowedException on Android 12+ when
            // starting from background — keep playback alive if we already are.
            if (!player.isPlaying) stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swipe-away while playing/buffering keeps music going (standard music-app
        // behaviour); otherwise shut down cleanly.
        val active = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (!active) stopSelf() else super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
