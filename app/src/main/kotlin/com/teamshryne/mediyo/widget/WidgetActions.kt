package com.teamshryne.mediyo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Hilt bridge for Glance callbacks (system-instantiated, no constructor injection). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun controller(): WidgetPlaybackController
    fun sync(): WidgetSync
    fun widgetRepo(): WidgetStateRepository
    fun playlistRepo(): PlaylistRepository
    fun artworkCache(): WidgetArtworkCache
}

private fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

class TogglePlayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).controller().toggle() } catch (_: Throwable) {}
    }
}

class NextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).controller().next() } catch (_: Throwable) {}
    }
}

class PrevCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).controller().previous() } catch (_: Throwable) {}
    }
}

class ToggleLikeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).controller().toggleLike() } catch (_: Throwable) {}
    }
}

class PlayLikedShuffleCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).controller().playLikedShuffle() } catch (_: Throwable) {}
    }
}

class RefreshWidgetsCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try { entryPoint(context).sync().refreshAll() } catch (_: Throwable) {}
    }
}

class PlayPlaylistCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[PlaylistIdKey] ?: return
        try { entryPoint(context).controller().playPlaylist(id) } catch (_: Throwable) {}
    }
}

val PlaylistIdKey = ActionParameters.Key<String>("playlist_id")
