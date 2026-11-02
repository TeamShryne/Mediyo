package com.teamshryne.mediyo.innertube.pages

import com.teamshryne.mediyo.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
