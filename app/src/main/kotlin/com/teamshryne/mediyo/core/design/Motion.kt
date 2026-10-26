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
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Shared motion tokens — one source of truth for every animation in the app.
 *
 * Feel = video-editor keyframes: an expo bezier gives an explosive attack
 * with a soft settle (fast start, gentle landing), exactly what eased
 * keyframes produce. A single tween with this curve is cheaper than a
 * multi-stop `keyframes` spec and runs fully on the GPU (translation, scale
 * and alpha only — never layout, never size/padding).
 *
 * Budgets: enter ≤ 200ms, exit ≤ 150ms. Anything longer reads as "slow".
 */

// ── Easings (video-keyframe curves) ───────────────────────────────────────────

/** Fast attack, soft landing — use for everything entering. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Gentle acceleration — use for everything exiting. */
val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

// ── Durations (ms) ────────────────────────────────────────────────────────────

object Motion {
    const val SlideEnter = 200
    const val SlideExit = 150
    const val FadeEnter = 140
    const val FadeExit = 110
    const val TabEnter = 160
    const val TabExit = 130
    const val SheetEnter = 240
    const val SheetExit = 190

    /**
     * How far the screen follows the back gesture, as a fraction of width.
     * Small on purpose: the gesture is a preview, not a second transition.
     */
    const val Predictive = 0.06f

    /** How much the screen recedes while the back gesture is in progress. */
    const val PredictiveScale = 0.03f
}

// ── Nav transitions (push / pop, dynamic slide + scale + fade) ───────────────

/**
 * Push enter: the new screen slides in from +10% width while growing from
 * 96% and fading in. The scale is what makes the push feel like depth rather
 * than a strip of pages sliding sideways. 10% stays proportional on every
 * screen size without composing anything offscreen.
 */
fun pushEnter(): EnterTransition =
    fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
        slideInHorizontally(tween(Motion.SlideEnter, easing = EaseOutExpo)) {
            (it * 0.10f).toInt()
        } +
        scaleIn(
            animationSpec = tween(Motion.SlideEnter, easing = EaseOutExpo),
            initialScale = 0.96f
        )

/** Push exit: the old screen drifts back a little and fades — quick ease-in. */
fun pushExit(): ExitTransition =
    fadeOut(tween(Motion.FadeExit, easing = EaseIn)) +
        slideOutHorizontally(tween(Motion.SlideExit, easing = EaseIn)) {
            -(it * 0.05f).toInt()
        } +
        scaleOut(
            animationSpec = tween(Motion.SlideExit, easing = EaseIn),
            targetScale = 0.98f
        )

/** Pop enter/exit mirror the push direction. */
fun popEnter(): EnterTransition =
    fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
        slideInHorizontally(tween(Motion.SlideEnter, easing = EaseOutExpo)) {
            -(it * 0.10f).toInt()
        } +
        scaleIn(
            animationSpec = tween(Motion.SlideEnter, easing = EaseOutExpo),
            initialScale = 0.96f,
            transformOrigin = TransformOrigin(1f, 0.5f)
        )

fun popExit(): ExitTransition =
    fadeOut(tween(Motion.FadeExit, easing = EaseIn)) +
        slideOutHorizontally(tween(Motion.SlideExit, easing = EaseIn)) {
            (it * 0.05f).toInt()
        } +
        scaleOut(
            animationSpec = tween(Motion.SlideExit, easing = EaseIn),
            targetScale = 0.98f,
            transformOrigin = TransformOrigin(1f, 0.5f)
        )

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
