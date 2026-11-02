package com.teamshryne.mediyo.innertube.models.body

import com.teamshryne.mediyo.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
