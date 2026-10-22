package com.teamshryne.mediyo.feature.search

import com.teamshryne.mediyo.domain.model.Track

/** How many played-from-search songs the search screen keeps on show. */
const val RECENT_PLAYS_LIMIT = 12

/**
 * A song we played from the search results, kept together with the query it
 * came from: playing it again keeps the same origin, so the song does not
 * drop out of the list it came from.
 */
data class RecentSearchPlay(
    val track: Track,
    val query: String
)
