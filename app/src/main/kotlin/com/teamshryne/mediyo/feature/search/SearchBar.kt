package com.teamshryne.mediyo.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Placeholder shown in the search pill while the query is empty. */
const val SEARCH_PLACEHOLDER = "Songs, artists, albums…"

/**
 * The search bar of the search screen: one pill for every state — idle,
 * typing and looking at results. It never moves and never changes shape, so
 * the input stays where the user left it.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = SEARCH_PLACEHOLDER
) {
    Row(
        modifier
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
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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
                        .padding(vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
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
                    IconButton(onClick = onClear) {
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
 * The panel under the search bar: recent searches and live completions.
 *
 * It is opaque and fills the screen: while the user types, the results
 * behind it are hidden instead of peeking through.
 */
@Composable
fun SearchSuggestionPanel(
    query: String,
    recents: List<String>,
    suggestTexts: List<FfiSuggestText>,
    suggestEntities: List<FfiSearchResult>,
    showSuggestions: Boolean,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onOpenEntity: (FfiSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    // With a query in the box the history narrows down to what still
    // matches; an empty query shows the full history.
    val shownRecents = remember(recents, query) {
        val q = query.trim()
        if (q.isEmpty()) recents else recents.filter { it.contains(q, ignoreCase = true) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow taps: the results live behind this panel and must not
            // react to a tap that landed on the empty space below the rows.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .verticalScroll(rememberScrollState())
    ) {
        shownRecents.forEach { r ->
            SuggestionRow(
                icon = Icons.Filled.History,
                text = r,
                onClick = { onSubmit(r) },
                onFill = { onFill(r) },
                onRemove = { onRemoveRecent(r) }
            )
        }
        if (showSuggestions) {
            suggestTexts.forEach { t ->
                SuggestionRow(
                    icon = Icons.Filled.Search,
                    text = t.text,
                    onClick = { onSubmit(t.query.ifBlank { t.text }) },
                    onFill = { onFill(t.query.ifBlank { t.text }) }
                )
            }
            suggestEntities.forEach { e ->
                SuggestionEntityRow(item = e, onClick = { onOpenEntity(e) })
            }
        }
    }
}

/**
 * One row of the panel: an icon, the text, and the trailing actions.
 * [onFill] mirrors the YouTube Music arrow (put the text in the field),
 * [onRemove] drops the entry from the history.
 */
@Composable
private fun SuggestionRow(
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
private fun SuggestionEntityRow(item: FfiSearchResult, onClick: () -> Unit) {
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
