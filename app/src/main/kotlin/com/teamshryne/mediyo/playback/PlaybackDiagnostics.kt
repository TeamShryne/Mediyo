package com.teamshryne.mediyo.playback

import android.os.SystemClock
import com.teamshryne.mediyo.BuildConfig

/**
 * A short, in-memory timeline of what the playback service actually did.
 *
 * The notification is assembled by Media3, behind several layers we cannot
 * see from the UI: the service is started, a controller connects, a
 * notification provider is asked for a notification, the foreground
 * promotion either lands or is refused. When it does not appear on a device
 * we do not own, the only useful question is *which* of those steps never
 * happened — and that is exactly what this records.
 *
 * Debug builds only: [record] returns immediately in release, so nothing is
 * retained and nothing can leak into a shipped app. The report screen reads
 * it through [snapshot].
 */
object PlaybackDiagnostics {

    data class Event(
        /** [SystemClock.elapsedRealtime] — comparable with the logcat timestamps. */
        val uptimeMs: Long,
        val tag: String,
        val detail: String,
    )

    /**
     * Enough for a start, a few notification updates and a teardown. A ring,
     * because a session left running all day would otherwise grow without a
     * bound while nobody is looking at it.
     */
    private const val CAPACITY = 200

    private val events = ArrayDeque<Event>()
    private val lock = Any()

    fun record(tag: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (events.size >= CAPACITY) events.removeFirst()
            events.addLast(Event(SystemClock.elapsedRealtime(), tag, detail))
        }
    }

    fun snapshot(): List<Event> = synchronized(lock) { events.toList() }

    fun clear() {
        synchronized(lock) { events.clear() }
    }
}
