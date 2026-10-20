package com.teamshryne.mediyo.feature.lyrics

/**
 * Smooth, rate-aware clock for the word-by-word lyric highlight.
 *
 * ## Why this exists
 *
 * The player publishes its position every ~500 ms. The old implementation
 * filled the gaps by adding *wall-clock* time to the last known position,
 * i.e. it assumed 1 ms of wall time = 1 ms of media time. That assumption
 * only holds at 1× speed:
 *
 * * at 0.5× the highlight ran **half a tick too far ahead** (500 ms of wall
 *   time = 250 ms of music), and
 * * the next player tick snapped it back to the truth.
 *
 * The result is the "the lyrics go back a little and jitter" artefact.
 *
 * ## How it works
 *
 * Every player sample is treated as an *anchor*: the position plus the
 * wall-clock time it was taken at. Between samples the position is
 * extrapolated with the **playback rate** instead of with raw wall time, so
 * the highlight advances exactly as fast as the audio does — at 0.5×, 1.5× or
 * anything in between.
 *
 * A new sample always re-anchors the clock, so error can never accumulate.
 * When the rate changes (the user drags the speed slider) the clock re-anchors
 * on its *current* position, so the highlight never jumps backwards.
 *
 * Not thread-safe: it is meant to be driven from a single frame loop.
 */
internal class LyricsClock {

    /** Position of the current anchor. */
    private var anchorPos = 0L

    /** Wall-clock time (elapsedRealtime) the anchor was taken at. */
    private var anchorWallMs = 0L

    /** Rate the anchor is extrapolated with (media-ms per wall-ms). */
    private var appliedRate = 1f

    /** Rate measured from two consecutive player samples. */
    private var measuredRate = 1f

    /** Position of the last player sample. */
    private var lastSamplePos = 0L

    /** Wall-clock time of the last player sample. */
    private var lastSampleWallMs = 0L

    /** Whether we already saw a sample. */
    private var primed = false

    /** Forget everything — used when the track changes. */
    fun reset() {
        anchorPos = 0L
        anchorWallMs = 0L
        appliedRate = 1f
        measuredRate = 1f
        lastSamplePos = 0L
        lastSampleWallMs = 0L
        primed = false
    }

    /**
     * Position the highlight should show right now.
     *
     * @param samplePos latest position reported by the player
     * @param sampleElapsedMs wall-clock time the sample was taken at
     *        (`SystemClock.elapsedRealtime()`); 0 means "unknown" — the clock
     *        then follows the raw samples instead of interpolating
     * @param isPlaying whether audio is currently advancing
     * @param nowElapsedMs current wall-clock time
     * @param declaredRate playback rate (1.0 = normal). Values <= 0 mean
     *        "unknown" and fall back to the measured rate.
     */
    fun positionAt(
        samplePos: Long,
        sampleElapsedMs: Long,
        isPlaying: Boolean,
        nowElapsedMs: Long,
        declaredRate: Float
    ): Long {
        val rate = if (declaredRate > 0f) declaredRate else measuredRate

        // No timestamp for the sample: we cannot tell how old it is, so we
        // must not extrapolate from it. Follow the raw samples instead — the
        // highlight moves in steps, but it never runs away.
        if (sampleElapsedMs <= 0L) {
            primed = false
            anchorPos = samplePos
            anchorWall = nowElapsedMs
            appliedRate = rate
            lastSamplePos = samplePos
            lastSampleWallMs = 0L
            return samplePos
        }

        val sampleWall = sampleElapsedMs
        val newSample = !primed || samplePos != lastSamplePos || sampleElapsedMs != lastSampleWallMs

        if (newSample) {
            if (primed) {
                // Measure the rate between the two samples. The player
                // timestamp and the position are read at the same instant, so
                // this is exact — unlike a wall-clock delta, which would also
                // contain the ticker's own jitter.
                val wallDelta = sampleWall - lastSampleWallMs
                val mediaDelta = samplePos - lastSamplePos
                if (wallDelta > 0L && mediaDelta >= 0L) {
                    val measured = mediaDelta.toFloat() / wallDelta.toFloat()
                    if (measured in 0.05f..6f) measuredRate = measured
                }
            }
            lastSamplePos = samplePos
            lastSampleWallMs = sampleWall
            // Re-anchor on the fresh sample: the extrapolation restarts from
            // the truth, so drift can never build up over the song.
            anchorPos = samplePos
            anchorWallMs = sampleWall
            appliedRate = rate
            primed = true
        } else if (rate != appliedRate) {
            // The playback rate changed (speed slider, external control).
            // Re-anchor on the position we are showing *now* so the highlight
            // keeps moving forward instead of snapping back.
            anchorPos = extrapolate(nowElapsedMs)
            anchorWallMs = nowElapsedMs
            appliedRate = rate
        }

        // Paused: hold the last known position, no extrapolation.
        if (!isPlaying) return anchorPos
        return extrapolate(nowElapsedMs)
    }

    private fun extrapolate(nowElapsedMs: Long): Long {
        val elapsed = (nowElapsedMs - anchorWallMs).coerceAtLeast(0L)
        return anchorPos + (elapsed * appliedRate).toLong()
    }
}
