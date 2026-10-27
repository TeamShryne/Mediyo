package com.teamshryne.mediyo.feature.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The cover, lifted out of the mini player and flown to its slot in the full
 * player.
 *
 * It draws the player's own [painter] rather than starting a load of its own.
 * That is the whole point: a second request would decode at a different size
 * and arrive late, which reads as a blink on the cover. One painter means the
 * first frame of the flight is already the finished bitmap.
 *
 * The layer is laid out at the hero slot and pulled back onto the pill's
 * rectangle, so the bitmap is sampled once at the size it ends up at, and
 * everything else (translation, scale, alpha) is GPU-only. Both rectangles are
 * in root coordinates; until they have both been measured nothing is drawn,
 * and the player's own artwork is what is on screen underneath.
 */
@Composable
fun FlyingArtwork(
    painter: Painter,
    miniRect: Rect?,
    heroRect: Rect?,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    if (miniRect == null || heroRect == null) return
    if (heroRect.width <= 0f || heroRect.height <= 0f) return
    val density = LocalDensity.current
    val width = with(density) { heroRect.width.toDp() }
    val height = with(density) { heroRect.height.toDp() }

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .offset { IntOffset(heroRect.left.roundToInt(), heroRect.top.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                // Smoothstep: the cover leaves the pill gently and lands
                // without an abrupt stop.
                val t = p * p * (3f - 2f * p)
                val left = miniRect.left + (heroRect.left - miniRect.left) * t
                val top = miniRect.top + (heroRect.top - miniRect.top) * t
                val w = miniRect.width + (heroRect.width - miniRect.width) * t
                val h = miniRect.height + (heroRect.height - miniRect.height) * t
                translationX = left - heroRect.left
                translationY = top - heroRect.top
                scaleX = (w / heroRect.width).coerceAtLeast(0.01f)
                scaleY = (h / heroRect.height).coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0f, 0f)
                shape = RoundedCornerShape((8f + 10f * t).dp)
                clip = true
                // The landed cover is the player's own artwork, at full
                // strength: dropping this layer at the finish is invisible.
                alpha = if (p >= 1f) 0f else 1f
            }
    )
}
