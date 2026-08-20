package com.teamshryne.mediyo.feature.detail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable fun DetailScreen(videoId: String){
    Text("Detail $videoId — watch next + lyrics (MUSIC_PAGE_TYPE_TRACK_LYRICS) + comments (Top/Newest + replies) + queue/extendQueue via mediyo-core")
}
