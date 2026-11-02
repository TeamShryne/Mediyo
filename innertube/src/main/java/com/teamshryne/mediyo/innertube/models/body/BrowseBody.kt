package com.teamshryne.mediyo.innertube.models.body

import com.teamshryne.mediyo.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
)
