package com.teamshryne.mediyo.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
 * The media notification follows the platform contract (the way YouTube Music /
 * Spotify do it):
 * - Android 13+: the system renders the card — artwork, title, artist, live
 *   progress bar — from the session/player state, and places standard prev /
 *   play-pause / next chips automatically; custom buttons land in the
 *   expanded overflow row.
 * - Pre-13: [androidx.media3.session.DefaultMediaNotificationProvider] builds
 *   the notification from our custom layout, which is why prev / play-pause /
 *   next are part of it alongside Like.
 *
 * Queue navigation is app-managed (ExoPlayer plays a single resolved stream at
 * a time), so a [ForwardingPlayer] advertises next/previous availability and
 * routes those calls into the app's queue logic via [PlaybackSessionHub].
 */
@OptIn(UnstableApi::class) // custom layout / CommandButton / ForwardingPlayer session APIs
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.teamshryne.mediyo.session.TOGGLE_LIKE"
    }

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var hub: PlaybackSessionHub

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null

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

    override fun onCreate() {
        super.onCreate()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
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

            // App manages the queue outside ExoPlayer → intercept skip commands.
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

        // Keep the Like button icon in sync on every connected controller.
        scope.launch {
            hub.snapshot.map { it.liked }.distinctUntilChanged().collect { syncCustomLayout() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swipe-away while playing (or buffering a resolve) keeps music going with its
        // notification, like every proper music app; otherwise shut down cleanly.
        val active = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (!active) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
