package com.teamshryne.mediyo.feature.player

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamshryne.mediyo.domain.model.ART_HERO_PX
import com.teamshryne.mediyo.domain.model.thumbSized
import kotlin.math.roundToInt

/**
 * The cover, lifted out of the mini player and flown to its slot in the full
 * player.
 *
 * It is a separate layer on top of the player rather than a shared-element
 * transition: Compose 1.6 has no stable shared-element API, and this keeps the
 * whole flight on the GPU. The [progress] lambda is read inside the graphics
 * layer, so dragging the mini player re-renders the flight without
 * recomposing the player screen.
 *
 * Both rectangles are in root coordinates. Until they have both been
 * measured nothing is drawn — the player's own artwork is visible underneath,
 * so the first frame has no hole.
 */
@Composable
fun FlyingArtwork(
    artwork: String?,
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

    AsyncImage(
        model = artwork.thumbSized(ART_HERO_PX),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            // The layer is laid out at the hero slot and then pulled back to
            // the mini player's rectangle, so the bitmap is decoded once at
            // the size it will end up at (no inflated blur).
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
                alpha = if (p >= 1f) 0f else 1f
            }
    )
}
