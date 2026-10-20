package com.teamshryne.mediyo.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.FfiSuggestText
import com.teamshryne.mediyo.domain.model.bestThumbUrl

/** Placeholder shown in the overlay field while the query is empty. */
private const val OVERLAY_PLACEHOLDER = "Songs, artists, albums…"

/** Cap for the history/suggestion list so the results stay visible below. */
private val OVERLAY_LIST_MAX_HEIGHT = 420.dp

/**
 * YouTube-Music-style search overlay: the search field plus the recent
 * searches and the live completions, floating on top of the results.
 *
 * The overlay owns the focused text field — the field in the list header is
 * read-only and only serves as the entry point that opens this overlay.
 */
@Composable
fun SearchOverlay(
    query: String,
    recents: List<String>,
    suggestTexts: List<FfiSuggestText>,
    suggestEntities: List<FfiSearchResult>,
    showSuggestions: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onOpenEntity: (FfiSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    // The overlay is the editing surface: grab the caret as soon as it opens.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // With a query in the box the history narrows down to what still
    // matches; an empty query shows the full history.
    val shownRecents = remember(recents, query) {
        val q = query.trim()
        if (q.isEmpty()) recents else recents.filter { it.contains(q, ignoreCase = true) }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchOverlayField(
            query = query,
            focus = focus,
            onQueryChange = onQueryChange,
            onClose = onClose,
            onSubmit = onSubmit
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = OVERLAY_LIST_MAX_HEIGHT)
        ) {
            itemsIndexed(shownRecents, key = { i, r -> "recent_${i}_$r" }) { _, r ->
                OverlayRow(
                    icon = Icons.Filled.History,
                    text = r,
                    onClick = { onSubmit(r) },
                    onFill = { onFill(r) },
                    onRemove = { onRemoveRecent(r) }
                )
            }
            if (showSuggestions) {
                itemsIndexed(suggestTexts, key = { i, t -> "sug_${i}_${t.text}" }) { _, t ->
                    OverlayRow(
                        icon = Icons.Filled.Search,
                        text = t.text,
                        onClick = { onSubmit(t.query.ifBlank { t.text }) },
                        onFill = { onFill(t.query.ifBlank { t.text }) }
                    )
                }
                itemsIndexed(suggestEntities, key = { i, e ->
                    "ent_${i}_${e.videoId ?: e.browseId ?: e.title}"
                }) { _, e ->
                    OverlayEntityRow(item = e, onClick = { onOpenEntity(e) })
                }
            }
        }

        // Separator between the overlay and the results peeking through below.
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

/** The pill at the top of the overlay: back arrow, query, clear. */
@Composable
private fun SearchOverlayField(
    query: String,
    focus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    OVERLAY_PLACEHOLDER,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the overlay list: an icon, the text, and the trailing actions.
 * [onFill] mirrors the YouTube Music arrow (put the text in the field),
 * [onRemove] drops the entry from the history.
 */
@Composable
private fun OverlayRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.NorthWest,
                contentDescription = "Fill search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** An entity (song/artist/album) suggested for the current query. */
@Composable
private fun OverlayEntityRow(item: FfiSearchResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.artists.joinToString().ifBlank { item.category },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
