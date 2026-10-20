package com.teamshryne.mediyo.core.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState

/** How far the list has to be dragged before it asks for a refresh. */
private val PullThreshold = 72.dp

/** How far the drag can pull the content before it stops giving. */
private val PullMax = 120.dp

/** The gap held open while the refresh runs. */
private val PullHold = 56.dp

/**
 * Drag-to-refresh for a scrolling list.
 *
 * Material 3's `PullToRefreshBox` is not in this app's Material version, so
 * the gesture is spelled out here: a nested-scroll connection swallows the
 * downward drag while the list is parked at the top, the content follows the
 * finger, and crossing the threshold asks for a refresh.
 *
 * The pull resets on its own when the drag comes back up, so a half-pulled
 * list never gets stuck.
 */
@Composable
fun PullToRefreshList(
    listState: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { PullThreshold.toPx() }
    val maxPull = with(density) { PullMax.toPx() }
    val hold = with(density) { PullHold.toPx() }

    var pull by remember { mutableFloatStateOf(0f) }
    // Read through updated state: the connection outlives a composition.
    val refreshingNow by rememberUpdatedState(refreshing)
    val onRefreshNow by rememberUpdatedState(onRefresh)

    val shown by animateFloatAsState(
        targetValue = if (refreshingNow) hold else pull,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pullOffset"
    )

    val connection = remember(listState, threshold, maxPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (refreshingNow) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f) {
                    // Only claim the drag when there is nothing above to
                    // scroll to; otherwise the list must scroll normally.
                    if (!atTop) return Offset.Zero
                    pull = (pull + available.y).coerceIn(0f, maxPull)
                    if (pull >= threshold) {
                        pull = 0f
                        onRefreshNow()
                    }
                    return Offset(0f, available.y)
                }
                // Dragging back up lets go of the pull.
                if (pull > 0f) {
                    val taken = available.y.coerceAtLeast(-pull)
                    pull = (pull + taken).coerceAtLeast(0f)
                    return Offset(0f, taken)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Offset, available: Offset): Velocity {
                if (pull > 0f) pull = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        if (shown > 0.5f || refreshingNow) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(Modifier.graphicsLayer { translationY = shown }) { content() }
    }
}
