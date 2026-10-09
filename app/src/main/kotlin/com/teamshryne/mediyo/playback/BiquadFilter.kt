package com.teamshryne.mediyo.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Biquad topology used by one EQ band. */
enum class BiquadType { PEAK, LOW_SHELF, HIGH_SHELF }

/**
 * Second-order IIR filter (Direct Form I) with per-channel state.
 *
 * Coefficients follow Robert Bristow-Johnson's Audio EQ Cookbook — the
 * standard closed-form biquad designs used by virtually every software EQ.
 * Immutable once built: retuning a band means building a new filter, which
 * keeps the audio thread free of coefficient-update races.
 */
class BiquadFilter(
    sampleRateHz: Int,
    frequencyHz: Double,
    gainDb: Double,
    q: Double = 1.0,
    type: BiquadType = BiquadType.PEAK
) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Delay-line state, independent per channel (stereo shares coefficients).
    private var x1l = 0.0
    private var x2l = 0.0
    private var y1l = 0.0
    private var y2l = 0.0
    private var x1r = 0.0
    private var x2r = 0.0
    private var y1r = 0.0
    private var y2r = 0.0

    init {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        val sinW = sin(omega)
        val cosW = cos(omega)
        when (type) {
            BiquadType.PEAK -> {
                val a = 10.0.pow(gainDb / 40.0)
                val alpha = sinW / (2.0 * q)
                val a0 = 1.0 + alpha / a
                b0 = (1.0 + alpha * a) / a0
                b1 = (-2.0 * cosW) / a0
                b2 = (1.0 - alpha * a) / a0
                a1 = (-2.0 * cosW) / a0
                a2 = (1.0 - alpha / a) / a0
            }
            BiquadType.LOW_SHELF, BiquadType.HIGH_SHELF -> {
                // Shelf slope S = 1 (maximally flat transition).
                val a = sqrt(10.0.pow(gainDb / 20.0))
                val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 1.0 - 1.0) + 2.0)
                val sqrtA = sqrt(a)
                val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                val ap1 = a + 1.0
                val am1 = a - 1.0
                if (type == BiquadType.LOW_SHELF) {
                    val a0 = ap1 + am1 * cosW + twoSqrtAAlpha
                    b0 = a * (ap1 - am1 * cosW + twoSqrtAAlpha) / a0
                    b1 = 2.0 * a * (am1 - ap1 * cosW) / a0
                    b2 = a * (ap1 - am1 * cosW - twoSqrtAAlpha) / a0
                    a1 = -2.0 * (am1 + ap1 * cosW) / a0
                    a2 = (ap1 + am1 * cosW - twoSqrtAAlpha) / a0
                } else {
                    val a0 = ap1 - am1 * cosW + twoSqrtAAlpha
                    b0 = a * (ap1 + am1 * cosW + twoSqrtAAlpha) / a0
                    b1 = -2.0 * a * (am1 + ap1 * cosW) / a0
                    b2 = a * (ap1 + am1 * cosW - twoSqrtAAlpha) / a0
                    a1 = 2.0 * (am1 - ap1 * cosW) / a0
                    a2 = (ap1 - am1 * cosW - twoSqrtAAlpha) / a0
                }
            }
        }
    }

    fun processMono(x: Double): Double {
        val y = b0 * x + b1 * x1l + b2 * x2l - a1 * y1l - a2 * y2l
        x2l = x1l
        x1l = x
        y2l = y1l
        y1l = y
        return y
    }

    fun processStereo(l: Double, r: Double): Pair<Double, Double> {
        val yl = b0 * l + b1 * x1l + b2 * x2l - a1 * y1l - a2 * y2l
        x2l = x1l
        x1l = l
        y2l = y1l
        y1l = yl
        val yr = b0 * r + b1 * x1r + b2 * x2r - a1 * y1r - a2 * y2r
        x2r = x1r
        x1r = r
        y2r = y1r
        y1r = yr
        return yl to yr
    }

    /** Drop delay-line history (track change / flush) to avoid stale tails. */
    fun reset() {
        x1l = 0.0; x2l = 0.0; y1l = 0.0; y2l = 0.0
        x1r = 0.0; x2r = 0.0; y1r = 0.0; y2r = 0.0
    }
}
