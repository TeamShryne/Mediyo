package com.teamshryne.mediyo.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Tunables for [PlayerBackground], persisted in AppearancePrefs. */
data class PlayerBgConfig(
    val style: com.teamshryne.mediyo.data.appearance.PlayerBgStyle,
    /** Blur radius in dp (Blur style). */
    val blurDp: Float = 22f,
    /** Dark shade over everything, 0..0.8. */
    val dim: Float = 0.35f,
    /** Orb intensity, 0..1 (Glow style). */
    val glow: Float = 0.8f,
    /** Gradient richness, 0..1 (Gradient style). */
    val depth: Float = 0.75f,
    /** Tint the solid backdrop with artwork colors (Solid style). */
    val tintArtwork: Boolean = true
)

/** Fixed vivid palette so the settings preview looks alive without network. */
val SamplePlayerColors = DominantColors(
    container = Color(0xFFA83A6A),
    deep = Color(0xFF191225)
)

/**
 * Full-screen player backdrop. [artwork] fills the box (real AsyncImage in
 * the player, a faux gradient in the settings preview) — styles that need
 * the artwork consume the slot, others ignore it.
 */
@Composable
fun PlayerBackground(
    config: PlayerBgConfig,
    dominant: DominantColors,
    artwork: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        when (config.style) {
            com.teamshryne.mediyo.data.appearance.PlayerBgStyle.Gradient -> {
                Box(Modifier.fillMaxSize().background(immersiveBrush(dominant)))
                // Depth controls how much black swallows the gradient.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = (1f - config.depth) * 0.65f)
                            )
                        )
                    )
                )
            }
            com.teamshryne.mediyo.data.appearance.PlayerBgStyle.Blur -> {
                // Scaled up so blurred edges never show transparency.
                val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(config.blurDp.coerceIn(4f, 36f).dp)
                } else Modifier
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer(scaleX = 1.25f, scaleY = 1.25f)
                        .then(blurMod),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Box(Modifier.fillMaxSize()) { artwork() }
                }
                Box(
                    Modifier.fillMaxSize()
                        .background(dominant.deep.copy(alpha = 0.35f))
                )
            }
            com.teamshryne.mediyo.data.appearance.PlayerBgStyle.Glow -> {
                val density = LocalDensity.current
                Box(Modifier.fillMaxSize().background(Color(0xFF08080B)))
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wPx = with(density) { maxWidth.toPx() }
                    val hPx = with(density) { maxHeight.toPx() }
                    val orbA = dominant.container.copy(alpha = 0.6f * config.glow)
                    val orbB = dominant.container.copy(
                        red = (dominant.container.blue * 0.9f).coerceIn(0f, 1f),
                        alpha = 0.5f * config.glow
                    )
                    Box(
                        Modifier.fillMaxSize()
                            .offset(
                                with(density) { (-wPx * 0.2f).toDp() },
                                with(density) { (-hPx * 0.12f).toDp() }
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(orbA, Color.Transparent),
                                    center = Offset(wPx * 0.45f, hPx * 0.3f),
                                    radius = wPx * 0.85f
                                )
                            )
                    )
                    Box(
                        Modifier.fillMaxSize()
                            .offset(
                                with(density) { (wPx * 0.2f).toDp() },
                                with(density) { (hPx * 0.15f).toDp() }
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(orbB, Color.Transparent),
                                    center = Offset(wPx * 0.55f, hPx * 0.72f),
                                    radius = wPx * 0.9f
                                )
                            )
                    )
                }
            }
            com.teamshryne.mediyo.data.appearance.PlayerBgStyle.Solid -> {
                Box(
                    Modifier.fillMaxSize().background(
                        if (config.tintArtwork) dominant.deep else MediyoColors.Bg0
                    )
                )
            }
        }
        // Universal shade.
        if (config.dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.dim)))
        }
    }
}
