package com.teamshryne.mediyo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors

private val LauncherSurface = ColorProvider(Color(0xFF231E2B))
private val LauncherOnSurface = ColorProvider(Color(0xFFF2EDF4))
private val LauncherVariant = ColorProvider(Color(0xFFCFC3D6))
private val LauncherPrimary = ColorProvider(Color(0xFFE91E63))
private val LauncherOnPrimary = ColorProvider(Color.White)
private val LauncherRowBg = ColorProvider(Color(0xFF332C3A))

/**
 * Playlist Launcher — one-tap play without opening the app.
 * Reads Room directly (suspend, no network) so it works cold.
 * Row tap -> [PlayPlaylistCallback] -> [WidgetPlaybackController.playPlaylist].
 */
class PlaylistLauncherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = try {
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        } catch (_: Throwable) {
            null
        }
        val playlists = try {
            ep?.playlistRepo()?.getPlaylists()?.take(5).orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val openApp = try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        } catch (_: Throwable) {
            null
        }
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(LauncherSurface)
                    .cornerRadius(24.dp)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mediyo",
                        style = TextStyle(fontSize = 14.sp, color = LauncherOnSurface)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    if (openApp != null) {
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(LauncherRowBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable(actionStartActivity(openApp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Open",
                                style = TextStyle(fontSize = 12.sp, color = LauncherOnSurface)
                            )
                        }
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                // Liked shuffle hero row
                LauncherRow(
                    icon = "♥",
                    title = "Liked shuffle",
                    subtitle = "Instant mix",
                    onClick = actionRunCallback<PlayLikedShuffleCallback>()
                )
                if (playlists.isEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "Create a playlist in Library — it appears here.",
                        style = TextStyle(fontSize = 12.sp, color = LauncherVariant)
                    )
                } else {
                    playlists.forEach { pl ->
                        Spacer(GlanceModifier.height(6.dp))
                        LauncherRow(
                            icon = "▷",
                            title = pl.title,
                            subtitle = "${pl.trackCount} songs",
                            onClick = actionRunCallback<PlayPlaylistCallback>(
                                actionParametersOf(PlaylistIdKey to pl.id)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: androidx.glance.action.Action
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(14.dp)
            .background(LauncherRowBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(34.dp)
                .cornerRadius(17.dp)
                .background(LauncherPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = TextStyle(fontSize = 15.sp, color = LauncherOnPrimary))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                title,
                style = TextStyle(fontSize = 13.sp, color = LauncherOnSurface),
                maxLines = 1
            )
            Text(
                subtitle,
                style = TextStyle(fontSize = 11.sp, color = LauncherVariant),
                maxLines = 1
            )
        }
    }
}

class PlaylistLauncherReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaylistLauncherWidget()
}
