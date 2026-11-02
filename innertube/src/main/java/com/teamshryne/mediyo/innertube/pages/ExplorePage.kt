package com.teamshryne.mediyo.innertube.pages

import com.teamshryne.mediyo.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
