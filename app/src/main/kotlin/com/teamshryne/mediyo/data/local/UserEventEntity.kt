package com.teamshryne.mediyo.data.local

import androidx.room.*

/**
 * Generic interaction log: every taste-relevant user action that is NOT a play.
 * Plays live in history_play_events; everything else (search, views, taps,
 * queue ops, likes, follows, saves, playlist curation, seeks, shuffle/repeat,
 * sleep timer, lyrics/comments opens, widget actions) lands here as a typed
 * event with the target ids + tiny meta string. Raw material for the future
 * home algo + stats. All on-device.
 */
@Entity(
    tableName = "user_events",
    indices = [Index("type"), Index("createdAt"), Index("videoId"), Index("type", "createdAt")]
)
data class UserEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Event type, see UserEventTypes. */
    val type: String,
    val videoId: String? = null,
    val browseId: String? = null,
    /** Free text slot: search query, playlist id, shelf title, etc. */
    val label: String? = null,
    /** Tiny k=v;k=v payload (rank, size, mode, ...). No JSON lib needed. */
    val meta: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Device-local hour 0..23 / day 1..7 (Calendar.SUNDAY=1). */
    val hourOfDay: Int = 0,
    val dayOfWeek: Int = 1,
    /** Process id — groups events into sessions (app open → kill). */
    val sessionId: String = "",
    val originType: String? = null,
    val originLabel: String? = null
)
