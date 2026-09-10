package com.teamshryne.mediyo.core.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Shared motion tokens — one source of truth for every animation in the app.
 *
 * Feel = video-editor keyframes: an expo bezier gives an explosive attack
 * with a soft settle (fast start, gentle landing), exactly what eased
 * keyframes produce. A single tween with this curve is cheaper than a
 * multi-stop `keyframes` spec and runs fully on the GPU (translation +
 * alpha only — never layout, never size/padding).
 *
 * Budgets: enter ≤ 220ms, exit ≤ 160ms. Anything longer reads as "slow".
 */

// ── Easings (video-keyframe curves) ───────────────────────────────────────────

/** Fast attack, soft landing — use for everything entering. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Gentle acceleration — use for everything exiting. */
val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

// ── Durations (ms) ────────────────────────────────────────────────────────────

object Motion {
    const val SlideEnter = 220
    const val SlideExit = 160
    const val FadeEnter = 150
    const val FadeExit = 120
    const val TabEnter = 180
    const val TabExit = 140
    const val SheetEnter = 260
    const val SheetExit = 200
}

// ── Nav transitions (push / pop, dynamic slide + fade) ───────────────────────

/**
 * Push enter: new screen slides in from +8% width (~32dp — short travel =
 * fast feel) on the expo curve while fading in. 8% keeps it proportional
 * on every screen size without offscreen composition cost.
 */
fun pushEnter(): EnterTransition =
    fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
        slideInHorizontally(tween(Motion.SlideEnter, easing = EaseOutExpo)) { (it * 0.08f).toInt() }

/** Push exit: old screen drifts out to −8% and fades — quick ease-in. */
fun pushExit(): ExitTransition =
    fadeOut(tween(Motion.FadeExit, easing = EaseIn)) +
        slideOutHorizontally(tween(Motion.SlideExit, easing = EaseIn)) { -(it * 0.08f).toInt() }

/** Pop enter/exit mirror the push direction. */
fun popEnter(): EnterTransition =
    fadeIn(tween(Motion.FadeEnter, easing = EaseOutExpo)) +
        slideInHorizontally(tween(Motion.SlideEnter, easing = EaseOutExpo)) { -(it * 0.08f).toInt() }

fun popExit(): ExitTransition =
    fadeOut(tween(Motion.FadeExit, easing = EaseIn)) +
        slideOutHorizontally(tween(Motion.SlideExit, easing = EaseIn)) { (it * 0.08f).toInt() }

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
