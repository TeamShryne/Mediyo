package com.teamshryne.mediyo.core.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.teamshryne.mediyo.data.appearance.ScreenMotionStyle

/**
 * Shared motion tokens — one source of truth for every animation in the app.
 *
 * Feel = video-editor keyframes: an expo bezier gives an explosive attack
 * with a soft settle (fast start, gentle landing), exactly what eased
 * keyframes produce. A single tween with this curve is cheaper than a
 * multi-stop `keyframes` spec and runs fully on the GPU (translation, scale
 * and alpha only — never layout, never size/padding).
 *
 * Budgets: chrome (tabs, sheets) enter ≤ 200ms, exit ≤ 150ms. A screen that
 * travels a quarter of the width gets 300ms: at that distance anything
 * shorter reads as a jump rather than a move.
 */

// ── Easings (video-keyframe curves) ───────────────────────────────────────────

/** Fast attack, soft landing — use for everything entering. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Gentle acceleration — use for everything exiting. */
val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

// ── Durations (ms) ────────────────────────────────────────────────────────────

object Motion {
    /** A screen arriving from a quarter width away. */
    const val ScreenEnter = 300

    /** The screen being left, getting out of the way. */
    const val ScreenExit = 260
    const val FadeEnter = 140
    const val FadeExit = 110
    const val TabEnter = 160
    const val TabExit = 130
    const val SheetEnter = 240
    const val SheetExit = 190

    /**
     * How far the screen a back gesture is leaving travels before it is
     * committed, as a fraction of width. It has to be big enough to read as
     * "this screen is going away" under the finger — the previous value (6%)
     * was so small the gesture looked broken.
     */
    const val PopPeek = 0.32f
}

// ── Nav transitions (push / pop) ─────────────────────────────────────────────

/**
 * Push enter: the new screen travels a quarter of the way in from the right
 * and nothing else moves. No scale, no fade — three motions at once is what
 * made the old slide read as soft: the screen wobbled into place instead of
 * arriving. A quarter of the width is far enough to feel like travel, and 300ms
 * is the time that distance needs before it starts to look like a jump.
 */
fun pushEnter(style: ScreenMotionStyle = ScreenMotionStyle.Push): EnterTransition =
    when (style) {
        ScreenMotionStyle.Push ->
            slideInHorizontally(tween(Motion.ScreenEnter, easing = EaseOutExpo)) {
                (it * 0.25f).toInt()
            }
        ScreenMotionStyle.Fade ->
            fadeIn(tween(Motion.ScreenEnter, easing = EaseOutExpo))
        ScreenMotionStyle.Zoom ->
            fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
                scaleIn(
                    animationSpec = tween(Motion.ScreenEnter, easing = EaseOutExpo),
                    initialScale = 0.90f
                )
        ScreenMotionStyle.Rise ->
            fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
                slideInVertically(tween(Motion.ScreenEnter, easing = EaseOutExpo)) {
                    (it * 0.12f).toInt()
                }
    }

/**
 * Push exit: the old screen gives way a little and is covered by the new one.
 * It keeps its opacity the whole way — it is the incoming screen that hides it,
 * so fading too would only make the push look muddy.
 */
fun pushExit(style: ScreenMotionStyle = ScreenMotionStyle.Push): ExitTransition =
    when (style) {
        ScreenMotionStyle.Push ->
            slideOutHorizontally(tween(Motion.ScreenExit, easing = EaseIn)) {
                -(it * 0.08f).toInt()
            }
        ScreenMotionStyle.Fade ->
            fadeOut(tween(Motion.ScreenExit, easing = EaseIn))
        ScreenMotionStyle.Zoom ->
            fadeOut(tween(Motion.ScreenExit, easing = EaseIn)) +
                scaleOut(
                    animationSpec = tween(Motion.ScreenExit, easing = EaseIn),
                    targetScale = 1.04f
                )
        ScreenMotionStyle.Rise ->
            fadeOut(tween(Motion.FadeExit, easing = EaseIn))
    }

/**
 * Pop enter: the screen being returned to. This is the one the back gesture
 * reveals, so it sits slightly to the left and settles home as the finger
 * moves. It stays opaque: it is the ground the leaving screen moves across.
 */
fun popEnter(style: ScreenMotionStyle = ScreenMotionStyle.Push): EnterTransition =
    when (style) {
        ScreenMotionStyle.Push ->
            slideInHorizontally(tween(Motion.ScreenEnter, easing = EaseOutExpo)) {
                -(it * 0.08f).toInt()
            }
        ScreenMotionStyle.Fade ->
            fadeIn(tween(Motion.ScreenEnter, easing = EaseOutExpo))
        ScreenMotionStyle.Zoom ->
            fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
                scaleIn(
                    animationSpec = tween(Motion.ScreenEnter, easing = EaseOutExpo),
                    initialScale = 0.92f
                )
        ScreenMotionStyle.Rise ->
            fadeIn(tween(Motion.ScreenEnter, easing = EaseOutExpo))
    }

/**
 * Pop exit: the screen leaving. It slides a third of the way out and only then
 * dims, so the first part of the back gesture is solid movement rather than a
 * dissolve. The fade still has to land on zero by the end — the screen is
 * disposed when the pop completes, and anything left on screen would blink out.
 */
fun popExit(style: ScreenMotionStyle = ScreenMotionStyle.Push): ExitTransition =
    when (style) {
        ScreenMotionStyle.Push ->
            slideOutHorizontally(tween(Motion.ScreenExit, easing = EaseIn)) {
                (it * Motion.PopPeek).toInt()
            } +
                fadeOut(tween(Motion.ScreenExit - 120, delayMillis = 120, easing = EaseIn))
        ScreenMotionStyle.Fade ->
            fadeOut(tween(Motion.ScreenExit, easing = EaseIn))
        ScreenMotionStyle.Zoom ->
            fadeOut(tween(Motion.ScreenExit, easing = EaseIn)) +
                scaleOut(
                    animationSpec = tween(Motion.ScreenExit, easing = EaseIn),
                    targetScale = 1.06f
                )
        ScreenMotionStyle.Rise ->
            fadeOut(tween(Motion.FadeExit, easing = EaseIn)) +
                slideOutVertically(tween(Motion.ScreenExit, easing = EaseIn)) {
                    (it * 0.10f).toInt()
                }
    }

// ── Tabs (sideways movement would be a lie — they are siblings) ─────────────

/** Tab switch: a quick fade with a shallow scale, like a cross-dissolve. */
fun tabEnter(): EnterTransition =
    fadeIn(tween(Motion.TabEnter, easing = EaseOutExpo)) +
        scaleIn(
            animationSpec = tween(Motion.TabEnter, easing = EaseOutExpo),
            initialScale = 0.97f
        )

fun tabExit(): ExitTransition =
    fadeOut(tween(Motion.TabExit, easing = EaseIn)) +
        scaleOut(
            animationSpec = tween(Motion.TabExit, easing = EaseIn),
            targetScale = 1.01f
        )

// ── Chrome (tab bar / bottom-sheet overlays, GPU-only) ───────────────────────

/** Tab bar: short 1/3-height rise + fade. No layout effect (overlay). */
fun tabBarEnter(): EnterTransition =
    slideInVertically(tween(Motion.TabEnter, easing = EaseOutExpo)) { it / 3 } +
        fadeIn(tween(Motion.TabExit, easing = EaseOutExpo))

fun tabBarExit(): ExitTransition =
    slideOutVertically(tween(Motion.TabExit, easing = EaseIn)) { it / 3 } +
        fadeOut(tween(Motion.FadeExit, easing = EaseIn))

/** Full player / queue sheets: full-height slide reads as bottom-sheet. */
fun sheetEnter(): EnterTransition =
    slideInVertically(tween(Motion.SheetEnter, easing = EaseOutExpo)) { it } +
        fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo))

fun sheetExit(): ExitTransition =
    slideOutVertically(tween(Motion.SheetExit, easing = EaseIn)) { it } +
        fadeOut(tween(Motion.FadeExit, easing = EaseIn))
