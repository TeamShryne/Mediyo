package com.teamshryne.mediyo.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Detail-screen sticky header: the title row slides in only once the hero
 * (big title) has fully scrolled out, while the search field is always
 * pinned — it can never slide under anything because it *is* the pinned
 * element. Solid fill, back exits search mode before navigating.
 *
 * Render as a stickyHeader right after the hero item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSearchHeader(
    listState: LazyListState,
    title: String,
    search: ListSearchUiState,
    searching: Boolean,
    resultCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    heroKey: Any = "hero",
    placeholder: String = "Search in this list",
    showSearch: Boolean = true
) {
    // Key-based: exact regardless of hero height or scroll physics.
    val heroGone by remember(listState, heroKey) {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            items.isNotEmpty() && items.none { it.key == heroKey }
        }
    }
    val query = search.query
    BackHandler(enabled = showSearch && query.isNotBlank()) { search.updateQuery("") }
    AnimatedVisibility(
        visible = heroGone || query.isNotBlank(),
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 3 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 3 },
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                AnimatedVisibility(visible = heroGone) {
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
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
                if (showSearch) {
                    ListSearchField(
                        state = search,
                        placeholder = placeholder,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                if (showSearch && query.isNotBlank()) {
                    Text(
                        if (searching) "Searching…"
                        else "$resultCount result${if (resultCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
