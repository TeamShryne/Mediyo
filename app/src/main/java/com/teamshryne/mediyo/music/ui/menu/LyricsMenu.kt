package com.teamshryne.mediyo.music.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.R
import com.teamshryne.mediyo.music.ui.component.GridMenu
import com.teamshryne.mediyo.music.ui.component.GridMenuItem

/**
 * The lyrics overflow menu. Every other menu in the app is shown as a bottom
 * sheet through [com.teamshryne.mediyo.music.ui.component.LocalMenuState]; this
 * one used to be a DropdownMenu anchored to the three-dot button, which drew
 * it floating over the track title instead of above the bottom edge.
 */
@Composable
fun LyricsMenu(
    onRefetch: () -> Unit,
    onSearch: () -> Unit,
    onAppearance: () -> Unit,
    onDismiss: () -> Unit,
) {
    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = R.drawable.sync,
            title = R.string.refetch_lyrics,
        ) {
            onDismiss()
            onRefetch()
        }
        GridMenuItem(
            icon = R.drawable.search,
            title = R.string.search_on_another_source,
        ) {
            onDismiss()
            onSearch()
        }
        GridMenuItem(
            icon = R.drawable.palette,
            title = R.string.lyrics_appearance,
        ) {
            onDismiss()
            onAppearance()
        }
    }
}
