package com.teamshryne.mediyo.feature.browse

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable fun BrowseScreen(browseId: String, params: String? = null){
    Text("Browse $browseId ${params.orEmpty()} — artist/album/playlist via browse_with_params + moreContentButton pagination, gridRenderer/moreContentButton handling")
}
