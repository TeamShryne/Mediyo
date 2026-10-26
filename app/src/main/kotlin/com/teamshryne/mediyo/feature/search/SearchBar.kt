package com.teamshryne.mediyo.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * The one search field for every state. It stays put while results scroll,
 * but the leading icon tells the user where they are: a magnifier when the
 * field is idle, a back arrow while the suggestion panel is open, so leaving
 * the panel never requires reaching for the keyboard.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = SEARCH_PLACEHOLDER
) {
    var focused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else Color.Transparent,
        animationSpec = tween(140),
        label = "search-field-border"
    )

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.heightIn(min = 54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clickable(enabled = !focused) { fieldFocus.requestFocus() },
                    contentAlignment = Alignment.Center
                ) {
                    if (focused) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search suggestions",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                        .focusRequester(fieldFocus)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged(it.isFocused)
                        }
                        .padding(vertical = 16.dp),
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
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(90))
                ) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The suggestion panel is a short card hanging under the field, not a second
 * full screen: while the user types they can still see where the results
 * begin. Suggestions come first, then matching history, each behind a small
 * label so the list never becomes an undifferentiated pile.
 */
@Composable
fun SearchSuggestions(
    query: String,
    recents: List<String>,
    suggestTexts: List<FfiSuggestText>,
    suggestEntities: List<FfiSearchResult>,
    showSuggestions: Boolean,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onOpenEntity: (FfiSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmed = query.trim()
    val shownRecents = remember(recents, trimmed) {
        if (trimmed.isEmpty()) recents
        else recents.filter { it.contains(trimmed, ignoreCase = true) }
    }.take(6)
    val texts = if (showSuggestions) suggestTexts.take(5) else emptyList()
    val entities = if (showSuggestions) suggestEntities.take(3) else emptyList()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            // Always something to act on while typing, even when the
            // suggestion endpoint has nothing to say.
            if (trimmed.isNotEmpty()) {
                item(key = "search_for") {
                    SuggestionRow(
                        icon = Icons.Filled.Search,
                        text = "Search for \u201C$trimmed\u201D",
                        onClick = { onSearch(trimmed) }
                    )
                }
            }
            if (texts.isNotEmpty()) {
                item(key = "label_suggestions") { SuggestionLabel("Suggestions") }
                items(texts.size, key = { "s_$it" }) { i ->
                    val text = texts[i]
                    SuggestionRow(
                        icon = Icons.Filled.Search,
                        text = text.text,
                        onClick = { onSearch(text.query.ifBlank { text.text }) }
                    )
                }
            }
            if (entities.isNotEmpty()) {
                item(key = "label_entities") {
                    SuggestionLabel(if (texts.isEmpty()) "Best match" else "Top matches")
                }
                items(entities.size, key = { "e_$it" }) { i ->
                    SuggestionEntityRow(
                        item = entities[i],
                        onClick = { onOpenEntity(entities[i]) }
                    )
                }
            }
            if (shownRecents.isNotEmpty()) {
                item(key = "label_recent") { SuggestionLabel("Recent") }
                items(shownRecents.size, key = { "r_${shownRecents[it]}" }) { i ->
                    val recent = shownRecents[i]
                    SuggestionRow(
                        icon = Icons.Filled.History,
                        text = recent,
                        onClick = { onSearch(recent) },
                        onRemove = { onRemoveRecent(recent) }
                    )
                }
            }
        }
    }
}

/** A small, quiet section marker inside the suggestion card. */
@Composable
private fun SuggestionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** One completion or one recent query. Tapping it runs the search. */
@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/** An entity (song, artist, album) suggested for the current query. */
@Composable
private fun SuggestionEntityRow(item: FfiSearchResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(
                    if (item.category.contains("Artist", true)) RoundedCornerShape(19.dp)
                    else RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(14.dp))
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
