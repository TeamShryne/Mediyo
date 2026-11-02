package com.teamshryne.mediyo.domain.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ported lyrics/podcast screens log a handful of interaction events.
 *
 * The old app has its own statistics tables, so this is intentionally a
 * no-op sink: the UI contract is kept without creating a second event store.
 */
@Singleton
class UserEventRepository @Inject constructor() {
    suspend fun log(
        type: String,
        videoId: String? = null,
        browseId: String? = null,
        label: String? = null,
        meta: String? = null
    ) {
        // No-op: the old app records plays through its own Event table.
    }
}

object UserEventTypes {
    const val LYRICS_OPEN = "lyrics_open"
    const val VIEW_PODCAST = "view_podcast"
}
