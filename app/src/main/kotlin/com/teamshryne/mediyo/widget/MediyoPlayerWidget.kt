package com.teamshryne.mediyo.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.material3.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.teamshryne.mediyo.R
import dagger.hilt.android.EntryPointAccessors

/**
 * One responsive widget that covers your picks:
 * - Small cell  -> Mini Controls (artwork + play/next)
 * - Medium+ cell -> Now Playing Hero (artwork + meta + progress + prev/play/next/like)
 *
 * Reads the cached [WidgetNowPlaying] so it renders instantly even when the
 * process is dead; taps cold-start [PlaybackService] via [WidgetPlaybackController].
 */
class MediyoPlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SmallSize, MediumSize, LargeSize)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo: WidgetStateRepository? = try {
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetRepo()
        } catch (_: Throwable) {
            null
        }
        if (repo == null) return
        val state = try { repo.read() } catch (_: Throwable) { WidgetNowPlaying() }
        val art = loadWidgetArtwork(context, state.artworkUrl)
        val openApp = try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        } catch (_: Throwable) {
            null
        }
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width <= SmallSize.width -> MiniContent(context, state, art, openApp)
                    size.height >= LargeSize.height && size.width >= LargeSize.width ->
                        HeroContent(context, state, art, openApp, large = true)
                    else -> HeroContent(context, state, art, openApp, large = false)
                }
            }
        }
    }

    companion object {
        val SmallSize = androidx.glance.unit.DpSize(180.dp, 110.dp)
        val MediumSize = androidx.glance.unit.DpSize(300.dp, 160.dp)
        val LargeSize = androidx.glance.unit.DpSize(300.dp, 250.dp)
    }
}

@Composable
private fun MiniContent(
    context: Context,
    state: WidgetNowPlaying,
    art: Bitmap?,
    openApp: android.content.Intent?
) {
    Row(
        modifier = androidx.glance.GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetArt(state, art, 52.dp, openApp)
        Spacer(androidx.glance.GlanceModifier.width(10.dp))
        Column(
            modifier = androidx.glance.GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniText(state.title.ifBlank { "Mediyo" }, true)
            if (state.artist.isNotBlank()) MiniText(state.artist, false)
        }
        CircleBtn(if (state.isPlaying) "❚❚" else "▶", actionRunCallback<TogglePlayCallback>())
        Spacer(androidx.glance.GlanceModifier.width(4.dp))
        CircleBtn("⏭", actionRunCallback<NextCallback>())
    }
}

@Composable
private fun HeroContent(
    context: Context,
    state: WidgetNowPlaying,
    art: Bitmap?,
    openApp: android.content.Intent?,
    large: Boolean
) {
    Column(
        modifier = androidx.glance.GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = androidx.glance.GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArt(state, art, if (large) 84.dp else 64.dp, openApp)
            Spacer(androidx.glance.GlanceModifier.width(12.dp))
            Column(modifier = androidx.glance.GlanceModifier.defaultWeight()) {
                if (!state.hasTrack) {
                    MiniText("Nothing playing", true)
                    MiniText("Tap ♥ to shuffle liked", false)
                } else {
                    MiniText(state.title, true)
                    if (state.artist.isNotBlank()) MiniText(state.artist, false)
                    if (large) {
                        Spacer(androidx.glance.GlanceModifier.height(2.dp))
                        MiniText(
                            if (state.isBuffering) "Buffering…"
                            else if (state.isPlaying) "Now playing"
                            else "Paused",
                            false
                        )
                    }
                }
            }
            if (state.hasTrack) {
                CircleBtn(if (state.liked) "♥" else "♡", actionRunCallback<ToggleLikeCallback>())
            }
        }
        if (state.hasTrack && state.durationMs > 0) {
            Spacer(androidx.glance.GlanceModifier.height(10.dp))
            androidx.glance.appwidget.LinearProgressIndicator(
                progress = state.progress.coerceIn(0f, 1f),
                modifier = androidx.glance.GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary
            )
        } else {
            Spacer(androidx.glance.GlanceModifier.height(10.dp))
        }
        Spacer(androidx.glance.GlanceModifier.height(8.dp))
        Row(
            modifier = androidx.glance.GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleBtn("⏮", actionRunCallback<PrevCallback>())
            Spacer(androidx.glance.GlanceModifier.width(10.dp))
            HeroPlayBtn(state)
            Spacer(androidx.glance.GlanceModifier.width(10.dp))
            CircleBtn("⏭", actionRunCallback<NextCallback>())
            if (large) {
                Spacer(androidx.glance.GlanceModifier.width(10.dp))
                CircleBtn("♥▶", actionRunCallback<PlayLikedShuffleCallback>())
            }
        }
    }
}

@Composable
private fun WidgetArt(
    state: WidgetNowPlaying,
    art: Bitmap?,
    size: androidx.compose.ui.unit.Dp,
    openApp: android.content.Intent?
) {
    val mod = androidx.glance.GlanceModifier.size(size).cornerRadius(14.dp)
    val clickableMod = if (openApp != null) mod.clickable(actionStartActivity(openApp)) else mod
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = state.title.ifBlank { "Mediyo" },
            modifier = clickableMod,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = clickableMod.background(GlanceTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "♪",
                style = TextStyle(fontSize = 22.sp, color = ColorProvider(GlanceTheme.colors.onPrimaryContainer))
            )
        }
    }
}

@Composable
private fun MiniText(text: String, bold: Boolean) {
    Text(
        text = text.ifBlank { "—" },
        style = TextStyle(
            fontSize = if (bold) 14.sp else 12.sp,
            color = ColorProvider(if (bold) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant)
        ),
        maxLines = 1
    )
}

@Composable
private fun CircleBtn(label: String, action: androidx.glance.action.Action) {
    Box(
        modifier = androidx.glance.GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontSize = 16.sp, color = ColorProvider(GlanceTheme.colors.onSecondaryContainer)))
    }
}

@Composable
private fun HeroPlayBtn(state: WidgetNowPlaying) {
    val label = when {
        state.isBuffering -> "…"
        state.isPlaying -> "❚❚"
        else -> "▶"
    }
    Box(
        modifier = androidx.glance.GlanceModifier
            .size(56.dp)
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(actionRunCallback<TogglePlayCallback>()),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontSize = 20.sp, color = ColorProvider(GlanceTheme.colors.onPrimary)))
    }
}

/** System receiver — declared in the manifest. */
class MediyoPlayerReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediyoPlayerWidget()
}

// Keep R referenced so release shrinking never strips widget drawables.
private fun keepR() = R.drawable.ic_notification_small
