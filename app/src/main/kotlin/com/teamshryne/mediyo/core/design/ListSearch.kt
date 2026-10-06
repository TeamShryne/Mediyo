package com.teamshryne.mediyo.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import kotlinx.coroutines.flow.first

/** Case-insensitive match across whichever fields a screen filters on. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val q = query.trim()
    if (q.isBlank()) return true
    return fields.any { it?.contains(q, ignoreCase = true) == true }
}

/** Keeps calling [loadMore] until [hasMore] is false (guarded). */
suspend fun loadAllPaged(
    hasMore: () -> Boolean,
    isLoading: () -> Boolean,
    loadMore: () -> Unit,
    maxPages: Int = 100
) {
    var guard = 0
    while (hasMore() && guard++ < maxPages) {
        loadMore()
        snapshotFlow { isLoading() }.first { !it }
    }
}

/** Per-screen in-list search state: query + expanded + full-load progress. */
@Stable
class ListSearchUiState {
    var query by mutableStateOf("")
    /** Search field is expanded (takes over the header row). */
    var active by mutableStateOf(false)
    /** A full load-all sweep is running (drives the waiting animation). */
    var searchingAll by mutableStateOf(false)

    fun updateQuery(q: String) {
        if (query == q) return
        query = q
        searchingAll = false
    }

    fun open() { active = true }

    fun close() {
        active = false
        if (query.isNotEmpty()) query = ""
        searchingAll = false
    }

    /** Back first clears text, then collapses — never exits the screen. */
    fun onBack(): Boolean {
        if (!active && query.isBlank()) return false
        if (query.isNotBlank()) {
            query = ""
            searchingAll = false
        } else {
            active = false
        }
        return true
    }
}

@Composable
fun rememberListSearchUiState(): ListSearchUiState = remember { ListSearchUiState() }

/** Spotify-style filter row: lives under the hero, scrolls with content. */
@Composable
fun ListSearchField(
    state: ListSearchUiState,
    placeholder: String = "Search in this list",
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value = state.query,
        onValueChange = { state.updateQuery(it) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (state.query.isNotEmpty()) {
            {
                IconButton(onClick = { state.updateQuery("") }) {
                    Icon(Icons.Filled.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    )
}

/**
 * Inline field used when search takes over a header row. Auto-focuses on
 * expand so the keyboard follows the animation instead of feeling forced.
 */
@Composable
fun InlineSearchField(
    state: ListSearchUiState,
    placeholder: String = "Search in this list",
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = state.query,
        onValueChange = { state.updateQuery(it) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
        trailingIcon = if (state.query.isNotEmpty()) {
            {
                IconButton(onClick = { state.updateQuery("") }) {
                    Icon(Icons.Filled.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        modifier = modifier.focusRequester(focus)
    )
}

/**
 * Expanded row: X on the left collapses, field takes the rest of the row.
 * Shared by sticky headers and hero top bars so both feel identical.
 */
@Composable
fun ExpandedSearchRow(
    state: ListSearchUiState,
    placeholder: String = "Search in this list",
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { state.close() }) {
            Icon(
                Icons.Filled.Close, contentDescription = "Close search",
                tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else iconTint
            )
        }
        InlineSearchField(
            state = state,
            placeholder = placeholder,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
    }
}

/**
 * Hero top bar with the same expand/collapse behaviour as the sticky
 * header. Collapsed: back + search + [trailing]. Expanded: X + field.
 */
@Composable
fun HeroSearchTopRow(
    state: ListSearchUiState,
    placeholder: String = "Search in this list",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    BackHandler(enabled = state.active) { state.onBack() }
    val tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else iconTint
    AnimatedContent(
        targetState = state.active,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "hero_search",
        modifier = modifier.fillMaxWidth()
    ) { expanded ->
        if (!expanded) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { state.open() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search in this list", tint = tint)
                    }
                    trailing()
                }
            }
        } else {
            ExpandedSearchRow(
                state = state,
                placeholder = placeholder,
                iconTint = iconTint,
                modifier = Modifier.padding(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Shown below loaded matches when the server holds more: invites the user
 * to sweep the rest of the list.
 */
@Composable
fun SearchMoreRow(matchCount: Int, modifier: Modifier = Modifier, onSearchMore: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clickable(onClick = onSearchMore)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Search, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$matchCount match${if (matchCount == 1) "" else "es"} in loaded tracks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Search the rest of this list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Delightful waiting UI while the rest of a long list loads. */
@Composable
fun SearchingRestAnimation(checkedCount: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "digging")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "spin"
    )
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(76.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            // Spinning vinyl.
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .rotate(rotation),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest))
            }
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            "Digging through the rest…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "$checkedCount tracks checked so far",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Flat row for mixed-type matches (channels, section grids). */
@Composable
fun FlatMatchRow(
    item: FfiSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(item.artists.joinToString().ifBlank { item.info?.takeIf { it.isNotBlank() } ?: item.category })
                    append("  •  ")
                    append(item.category)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (!item.duration.isNullOrBlank()) {
            Text(
                item.duration.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
