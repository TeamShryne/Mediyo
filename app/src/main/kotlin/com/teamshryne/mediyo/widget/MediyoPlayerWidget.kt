package com.teamshryne.mediyo.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.ContentScale
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

// Fixed dark card palette (no material3 dependency — always looks right
// behind album art, on light and dark home screens alike).
private val WidgetSurface = ColorProvider(Color(0xFF231E2B))
private val WidgetOnSurface = ColorProvider(Color(0xFFF2EDF4))
private val WidgetVariant = ColorProvider(Color(0xFFCFC3D6))
private val WidgetPrimary = ColorProvider(Color(0xFFE91E63))
private val WidgetOnPrimary = ColorProvider(Color.White)
private val WidgetContainer = ColorProvider(Color(0xFF3A3340))
private val WidgetOnContainer = ColorProvider(Color.White)

private val MiniMaxWidth = 220.dp
private val LargeMinHeight = 220.dp

/**
 * One responsive widget that covers your picks:
 * - Narrow cell  -> Mini Controls (artwork + play/next)
 * - Medium+ cell -> Now Playing Hero (artwork + meta + progress + prev/play/next/like)
 *
 * Reads the cached [WidgetNowPlaying] so it renders instantly even when the
 * process is dead; taps cold-start [PlaybackService] via [WidgetPlaybackController].
 */
class MediyoPlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SizeMode.SmallRectangle,
            SizeMode.MediumRectangle,
            SizeMode.LargeRectangle,
            SizeMode.LargeSquare
        )
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
            val size = LocalSize.current
            when {
                size.width <= MiniMaxWidth -> MiniContent(state, art)
                size.height >= LargeMinHeight -> HeroContent(state, art, openApp, large = true)
                else -> HeroContent(state, art, openApp, large = false)
            }
        }
    }
}

@Composable
private fun MiniContent(state: WidgetNowPlaying, art: Bitmap?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetSurface)
            .cornerRadius(20.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetArt(state, art, 52.dp, null)
        Spacer(GlanceModifier.width(10.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniText(state.title.ifBlank { "Mediyo" }, true)
            if (state.artist.isNotBlank()) MiniText(state.artist, false)
        }
        CircleBtn(if (state.isPlaying) "❚❚" else "▶", actionRunCallback<TogglePlayCallback>())
        Spacer(GlanceModifier.width(4.dp))
        CircleBtn("⏭", actionRunCallback<NextCallback>())
    }
}

@Composable
private fun HeroContent(
    state: WidgetNowPlaying,
    art: Bitmap?,
    openApp: Intent?,
    large: Boolean
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetSurface)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArt(state, art, if (large) 84.dp else 64.dp, openApp)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (!state.hasTrack) {
                    MiniText("Nothing playing", true)
                    MiniText("Tap ♥ to shuffle liked", false)
                } else {
                    MiniText(state.title, true)
                    if (state.artist.isNotBlank()) MiniText(state.artist, false)
                    if (large) {
                        Spacer(GlanceModifier.height(2.dp))
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
            Spacer(GlanceModifier.height(10.dp))
            LinearProgressIndicator(
                progress = state.progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = WidgetPrimary
            )
        } else {
            Spacer(GlanceModifier.height(10.dp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleBtn("⏮", actionRunCallback<PrevCallback>())
            Spacer(GlanceModifier.width(10.dp))
            HeroPlayBtn(state)
            Spacer(GlanceModifier.width(10.dp))
            CircleBtn("⏭", actionRunCallback<NextCallback>())
            if (large) {
                Spacer(GlanceModifier.width(10.dp))
                CircleBtn("♥▶", actionRunCallback<PlayLikedShuffleCallback>())
            }
        }
    }
}

@Composable
private fun WidgetArt(state: WidgetNowPlaying, art: Bitmap?, size: Dp, openApp: Intent?) {
    val mod = GlanceModifier.size(size).cornerRadius(14.dp)
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = state.title.ifBlank { "Mediyo" },
            modifier = if (openApp != null) mod.clickable(actionStartActivity(openApp)) else mod,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = if (openApp != null) {
                mod.background(WidgetPrimary).clickable(actionStartActivity(openApp))
            } else {
                mod.background(WidgetPrimary)
            },
            contentAlignment = Alignment.Center
        ) {
            Text("♪", style = TextStyle(fontSize = 22.sp, color = WidgetOnPrimary))
        }
    }
}

@Composable
private fun MiniText(text: String, bold: Boolean) {
    Text(
        text = text.ifBlank { "—" },
        style = TextStyle(
            fontSize = if (bold) 14.sp else 12.sp,
            color = if (bold) WidgetOnSurface else WidgetVariant
        ),
        maxLines = 1
    )
}

@Composable
private fun CircleBtn(label: String, action: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(WidgetContainer)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontSize = 16.sp, color = WidgetOnContainer))
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
        modifier = GlanceModifier
            .size(56.dp)
            .cornerRadius(28.dp)
            .background(WidgetPrimary)
            .clickable(actionRunCallback<TogglePlayCallback>()),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontSize = 20.sp, color = WidgetOnPrimary))
    }
}

/** System receiver — declared in the manifest. */
class MediyoPlayerReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediyoPlayerWidget()
}
