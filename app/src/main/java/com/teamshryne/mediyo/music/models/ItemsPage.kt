package com.teamshryne.mediyo.music.models

import com.teamshryne.mediyo.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
