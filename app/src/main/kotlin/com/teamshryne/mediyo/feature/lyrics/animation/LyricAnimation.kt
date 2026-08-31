package com.teamshryne.mediyo.feature.lyrics.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Highly scalable animation contracts.
 *
 * Adding a new animation:
 * 1. Implement [LyricWordAnimator] or [LyricLineAnimator]
 * 2. Register it in [LyricAnimationRegistry] or pass via [LyricsAnimationConfig]
 * No changes needed in [SyncedLyricsView].
 *
 * The design separates line-level (scale, alpha, spacing) and word-level
 * (fill/gradient) concerns so each can evolve independently.
 */

// ── Word-level animator ──────────────────────────────────────────────────────

/**
 * Controls how a single word is rendered given its playback progress.
 * @param progress 0f (not yet sung) → 1f (fully sung) – within active line's current word interpolates.
 * @param isActiveLine true if parent line is the currently sung line
 * @param isBackground background vocal styling
 */
interface LyricWordAnimator {
    fun brushForWord(progress: Float, isActiveLine: Boolean, isBackground: Boolean): Brush
    fun colorForWord(progress: Float, isActiveLine: Boolean): Color = Color.Unspecified // fallback uses brush
}

/**
 * Glow sweeps left→right as word is sung — Apple Music / Spotify karaoke style.
 * Uses a 3-stop linear gradient with a moving highlight edge.
 */
class GlowWordAnimator(
    private val activeColor: Color = Color.White,
    private val dimColor: Color = Color.White.copy(alpha = 0.28f),
    private val glowAlpha: Float = 0.95f,
) : LyricWordAnimator {
    override fun brushForWord(progress: Float, isActiveLine: Boolean, isBackground: Boolean): Brush {
        if (!isActiveLine) return Brush.linearGradient(listOf(dimColor, dimColor))
        if (isBackground) {
            // background vocals: more muted, italic glow
            val bgDim = Color.White.copy(alpha = 0.22f)
            val bgActive = Color.White.copy(alpha = 0.78f)
            return when {
                progress <= 0f -> Brush.linearGradient(listOf(bgDim, bgDim))
                progress >= 1f -> Brush.linearGradient(listOf(bgActive, bgActive))
                else -> {
                    val edge = (progress).coerceIn(0f, 1f)
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to bgActive,
                            (edge * 0.92f).coerceIn(0f, 1f) to bgActive,
                            (edge + 0.06f).coerceAtMost(1f) to bgDim,
                            1f to bgDim
                        )
                    )
                }
            }
        }
        return when {
            progress <= 0f -> Brush.linearGradient(listOf(dimColor, dimColor))
            progress >= 1f -> Brush.linearGradient(listOf(activeColor, activeColor))
            else -> {
                // seamless left→right wipe with soft glow edge
                val p = progress.coerceIn(0f, 1f)
                // glow band ~8% width
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to activeColor,
                        (p * 0.90f).coerceIn(0f, 1f) to activeColor,
                        (p + 0.07f).coerceAtMost(1f) to activeColor.copy(alpha = glowAlpha),
                        (p + 0.10f).coerceAtMost(1f) to dimColor,
                        1f to dimColor
                    )
                )
            }
        }
    }
}

/**
 * Plain fade animator — simplest fallback, easy to test.
 */
class FadeWordAnimator(
    private val activeColor: Color = Color.White,
    private val dimColor: Color = Color.White.copy(alpha = 0.35f)
) : LyricWordAnimator {
    override fun brushForWord(progress: Float, isActiveLine: Boolean, isBackground: Boolean): Brush {
        val c = if (isActiveLine && progress > 0f) activeColor else dimColor
        return Brush.linearGradient(listOf(c, c))
    }
}

// ── Line-level animator ──────────────────────────────────────────────────────

data class LineVisuals(
    val alpha: Float,
    val scale: Float,
    val fontWeight: FontWeight,
    val fontSize: TextUnit,
    val letterSpacing: TextUnit,
)

interface LyricLineAnimator {
    fun visualsForLine(isActive: Boolean, distance: Int, isBackground: Boolean): LineVisuals
}

/**
 * Spotify/Apple-Music inspired line emphasis:
 * active line: large 22sp Bold, full alpha, scale 1.02
 * ±1 lines: 19sp SemiBold alpha 0.68
 * further: 17sp alpha 0.32
 * Smooth psychology-pleasing scale + alpha curve.
 */
class FluidLineAnimator : LyricLineAnimator {
    override fun visualsForLine(isActive: Boolean, distance: Int, isBackground: Boolean): LineVisuals {
        return when {
            isActive -> LineVisuals(
                alpha = 1f,
                scale = 1.02f,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                letterSpacing = (-0.15).sp
            )
            distance == 1 -> LineVisuals(
                alpha = 0.62f,
                scale = 0.98f,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.05).sp
            )
            distance == 2 -> LineVisuals(
                alpha = 0.42f,
                scale = 0.96f,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.sp
            )
            else -> LineVisuals(
                alpha = 0.24f,
                scale = 0.95f,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

// ── Config & registry — easy to add new animations without touching views ────

data class LyricsAnimationConfig(
    val wordAnimator: LyricWordAnimator = GlowWordAnimator(),
    val lineAnimator: LyricLineAnimator = FluidLineAnimator(),
    val lineSpacingDp: Float = 18f,
    val autoScrollDurationMs: Int = 620,
    val autoScrollEasing: Easing = FastOutSlowInEasing,
    val wordLerpEasing: Easing = FastOutSlowInEasing,
)

/**
 * Central registry for scalable animation discovery.
 * Add new animators here and expose via DI or feature flag.
 */
object LyricAnimationRegistry {
    val glow = GlowWordAnimator()
    val fade = FadeWordAnimator()
    val fluidLine = FluidLineAnimator()

    fun configFor(name: String): LyricsAnimationConfig = when (name) {
        "fade" -> LyricsAnimationConfig(wordAnimator = fade, lineAnimator = fluidLine)
        else -> LyricsAnimationConfig(wordAnimator = glow, lineAnimator = fluidLine)
    }
}
