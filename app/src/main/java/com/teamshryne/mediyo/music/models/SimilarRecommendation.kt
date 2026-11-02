package com.teamshryne.mediyo.music.models

import com.teamshryne.mediyo.innertube.models.YTItem
import com.teamshryne.mediyo.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
