package com.teamshryne.mediyo.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True when the list has scrolled past its hero: either the first item is
 * gone or it has moved up by more than [pastHero]. Detail screens use this
 * to fade their top bar in only once the big title scrolls out.
 */
@Composable
fun rememberHeaderVisible(listState: LazyListState, pastHero: Dp = 160.dp): Boolean {
    val thresholdPx = with(LocalDensity.current) { pastHero.toPx() }
    val visible by remember(listState, thresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
    return visible
}

/**
 * Detail-screen top bar. Render it as the LazyColumn's first stickyHeader —
 * zero-size and absent until [visible], then pinned with a soft slide+fade.
 * Translucent fill so content glides beneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTopBar(
    title: String,
    visible: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) +
            slideInVertically(tween(260)) { -it / 3 },
        exit = fadeOut(tween(180)) +
            slideOutVertically(tween(220)) { -it / 3 },
        modifier = modifier.fillMaxWidth()
    ) {
        TopAppBar(
            title = {
                Text(
                    title.ifBlank { "Mediyo" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
            scrollBehavior = scrollBehavior
        )
    }
}
