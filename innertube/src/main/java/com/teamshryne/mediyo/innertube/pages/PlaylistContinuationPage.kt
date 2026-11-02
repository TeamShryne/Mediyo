package com.teamshryne.mediyo.innertube.pages

import com.teamshryne.mediyo.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
