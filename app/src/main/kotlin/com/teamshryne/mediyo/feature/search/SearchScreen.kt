package com.teamshryne.mediyo.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.EmptyState
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.LocalOverlayBottom
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.shimmer
import com.teamshryne.mediyo.data.mediyo.FfiSearchFilter
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.FfiSuggestText
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.ORIGIN_SEARCH
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.model.toTrack
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val events: UserEventRepository,
    private val history: HistoryRepository
) : ViewModel() {
    var query by mutableStateOf("")
    var selectedLabel by mutableStateOf("All")
    /** Active scope chip, identified by its params. Null = All (unscoped). */
    var selectedFilter by mutableStateOf<FfiSearchFilter?>(null)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var results by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var filters by mutableStateOf<List<FfiSearchFilter>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var hasSearched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Live completions for the current query (core Suggest endpoint). */
    var suggestTexts by mutableStateOf<List<FfiSuggestText>>(emptyList())
    var suggestEntities by mutableStateOf<List<FfiSearchResult>>(emptyList())
    /** Recent submitted queries, from logged search events. */
    var recents by mutableStateOf<List<String>>(emptyList())
    /**
     * The songs we played from the search results, newest first. The
     * history rows carry the origin, so a song tapped in the results shows
     * up here — and stays here when it is played again from this list.
     */
    val recentPlays: StateFlow<List<RecentSearchPlay>> = history.flowHistory()
        .map { rows ->
            rows.asSequence()
                .filter { it.lastOriginType == ORIGIN_SEARCH }
                .take(RECENT_PLAYS_LIMIT)
                .map { RecentSearchPlay(it.toTrack(), it.lastOriginLabel.orEmpty()) }
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Suggestion panel is allowed to be open (not dismissed by a scroll). */
    var suggestOpen by mutableStateOf(false)
    /**
     * The suggestion panel under the search bar is open (the user is
     * typing). The field itself never moves — only the panel comes and goes.
     */
    var panelOpen by mutableStateOf(false)
    private var suggestJob: kotlinx.coroutines.Job? = null
    /**
     * Guards against out-of-order responses: only the newest request may
     * publish. Without it, a slow reply for "dar" can land after "dark" and
     * replace the fresher list — the classic flickering-suggestion bug.
     */
    private var suggestSeq = 0
    /** Query the user scrolled away from; its panel stays closed until it changes. */
    private var dismissedFor: String? = null

    private var lastQueryInternal = ""
    val lastQuery: String get() = lastQueryInternal

    /** True only when there is something to show and the user hasn't dismissed it. */
    val suggestVisible: Boolean
        get() = suggestOpen && query.isNotBlank() && dismissedFor != query &&
            (suggestTexts.isNotEmpty() || suggestEntities.isNotEmpty())

    init {
        loadRecents()
    }

    fun loadRecents() {
        viewModelScope.launch {
            try {
                recents = events.byType(UserEventTypes.SEARCH, 200)
                    .mapNotNull { it.label?.trim()?.takeIf { q -> q.isNotEmpty() } }
                    .distinct()
                    .take(8)
            } catch (_: Throwable) { }
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            try {
                events.clearType(UserEventTypes.SEARCH)
                recents = emptyList()
            } catch (_: Throwable) { }
        }
    }

    /** Drop a single entry from the recent-search history. */
    fun removeRecent(q: String) {
        viewModelScope.launch {
            try {
                events.deleteByLabel(UserEventTypes.SEARCH, q)
                loadRecents()
            } catch (_: Throwable) { }
        }
    }

    /** Open the suggestion panel under the search bar. */
    fun openPanel() {
        if (panelOpen) return
        panelOpen = true
        // A fresh panel always shows its completions again, even for a
        // query the user scrolled away from earlier.
        dismissedFor = null
        suggestOpen = true
        if (query.isBlank()) loadRecents() else onQueryChange(query)
    }

    /** Close the suggestion panel; the results underneath stay as they were. */
    fun closePanel() {
        if (!panelOpen) return
        panelOpen = false
        dismissSuggest()
    }

    /** Cancel the search: the screen falls back to its idle state. */
    fun resetSearch() {
        query = ""
        hasSearched = false
        results = emptyList()
        filters = emptyList()
        continuation = null
        error = null
        loading = false
        loadingMore = false
        panelOpen = false
        clearSuggest()
        loadRecents()
    }

    /** Put [q] in the field without running the search (the arrow action). */
    fun fillQuery(q: String) {
        onQueryChange(q)
    }

    /** Called on every keystroke: updates the query and refreshes suggestions. */
    fun onQueryChange(q: String) {
        query = q
        suggestJob?.cancel()
        val seq = ++suggestSeq
        // Editing the text re-opens the panel even if it was scrolled away.
        if (q != dismissedFor) suggestOpen = true
        if (q.isBlank()) {
            clearSuggest()
            loadRecents()
            return
        }
        suggestJob = viewModelScope.launch {
            kotlinx.coroutines.delay(220)
            try {
                val s = bridge.suggest(q)
                // Drop stale replies: a newer keystroke already moved on.
                if (seq != suggestSeq || query != q) return@launch
                suggestTexts = s.texts
                suggestEntities = s.entities
            } catch (_: Throwable) { }
        }
    }

    /** Hide the panel for the current query (scroll / back / tap-away). */
    fun dismissSuggest() {
        suggestOpen = false
        if (query.isNotBlank()) dismissedFor = query
    }

    private fun clearSuggest() {
        suggestJob?.cancel()
        suggestSeq++
        suggestTexts = emptyList()
        suggestEntities = emptyList()
        suggestOpen = false
        dismissedFor = null
    }

    /** Run a fresh search. [filter] == null means "All". */
    fun runSearch(filter: FfiSearchFilter?) {
        val q = query.trim()
        if (q.isEmpty()) return
        clearSuggest()
        // A submitted search ends the typing state: the panel closes and the
        // results take over the screen.
        panelOpen = false
        lastQueryInternal = q
        // A chip without params behaves exactly like All — normalize it so
        // the All chip stays selected instead of showing nothing selected.
        selectedFilter = filter?.takeIf { !it.params.isNullOrBlank() }
        selectedLabel = filter?.label ?: "All"
        loading = true; loadingMore = false; hasSearched = true; error = null; continuation = null
        viewModelScope.launch {
            try {
                val res = if (filter != null && !filter.params.isNullOrBlank()) {
                    bridge.searchFiltered(q, filter.params!!)
                } else {
                    bridge.search(q)
                }
                results = res.results
                // Filtered responses don't always echo the chip cloud — never
                // let an empty chip list wipe the chips the user just tapped.
                // They also echo a param-less "Clear filters" reset chip (blank
                // or not); real filter chips always carry params, and a
                // param-less chip acts exactly like All, so drop them — our
                // own All chip handles resetting.
                val clean = res.filters.filter { it.label.isNotBlank() && !it.params.isNullOrBlank() }
                if (clean.isNotEmpty()) filters = clean
                continuation = res.continuation.takeIf { res.results.isNotEmpty() }
                events.log(UserEventTypes.SEARCH, label = q, meta = "filter=$selectedLabel;count=${res.results.size}")
                loadRecents()
            } catch (e: Throwable) {
                error = e.message ?: "Search failed"
                results = emptyList()
            } finally { loading = false }
        }
    }

    /** Submit from keyboard — resets to the All filter. */
    fun submit() = runSearch(null)

    fun logTap(r: FfiSearchResult) {
        val rank = results.indexOf(r)
        viewModelScope.launch {
            events.log(
                UserEventTypes.SEARCH_TAP, videoId = r.videoId, browseId = r.browseId ?: r.playlistId,
                label = lastQueryInternal.ifEmpty { query },
                meta = "rank=$rank;size=${results.size};filter=$selectedLabel;cat=${r.category}"
            )
        }
    }

    fun loadMore() {
        val token = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val res = bridge.searchNext(token)
                val before = results.size
                results = results.appendUnique(res.results)
                continuation = if (res.results.isEmpty() || results.size == before) null else res.continuation
            } catch (_: Throwable) {
                continuation = null
            } finally { loadingMore = false }
        }
    }
}

/** Which of the five shapes the content area is showing. */
private enum class SearchBody { Idle, Loading, Error, Empty, Results }

/**
 * Search is one fixed header over one content area. The header never moves,
 * so the field and the filters are always where the user left them; the
 * content underneath is a single list at a time (idle, loading, results),
 * which keeps the screen from becoming a stack of competing panels.
 */
@Composable
fun SearchScreen(
    nav: androidx.navigation.NavController,
    player: PlayerViewModel,
    vm: SearchVm = hiltViewModel()
) {
    // Only the results list survives a search. Idle and loading get their
    // own state inside the Crossfade, because two live LazyColumns must never
    // share one LazyListState.
    val resultsState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val recentPlays by vm.recentPlays.collectAsState()
    val playingId = player.state.collectAsState().value.videoId
    val sheets: com.teamshryne.mediyo.core.design.SheetHostVm = hiltViewModel()

    val body = when {
        !vm.hasSearched -> SearchBody.Idle
        vm.loading -> SearchBody.Loading
        vm.error != null -> SearchBody.Error
        vm.results.isEmpty() -> SearchBody.Empty
        else -> SearchBody.Results
    }

    // A new search starts at the top; the scroll position of the previous
    // results would otherwise leak into the next one.
    LaunchedEffect(body) {
        if (body == SearchBody.Results) resultsState.scrollToItem(0)
    }

    // Scrolling the results dismisses the suggestion panel — for the current
    // query only, so the next keystroke re-opens it naturally.
    LaunchedEffect(resultsState) {
        snapshotFlow { resultsState.firstVisibleItemIndex to resultsState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { if (!vm.panelOpen) vm.dismissSuggest() }
    }

    // Back closes the suggestion panel before it leaves the screen.
    BackHandler(enabled = vm.panelOpen) {
        vm.closePanel()
        focusManager.clearFocus()
    }
    BackHandler(enabled = !vm.panelOpen && vm.suggestVisible) { vm.dismissSuggest() }

    // The view model outlives this screen (it is hoisted for the whole app),
    // so make sure no panel is left behind when the user leaves the tab.
    DisposableEffect(Unit) {
        onDispose { vm.closePanel() }
    }

    fun openMenu(m: FfiSearchResult) = sheets.showMediaMenu(
        com.teamshryne.mediyo.core.design.MediaMenuRequest(
            item = m, nav = nav,
            onPlayTracks = { tracks, idx, origin -> player.playTracks(tracks, idx, origin) },
            onEnqueueTracks = { player.addToQueueList(it) },
            onAddToPlaylist = { sheets.openAddToPlaylist(it) },
            onPlayNext = { player.addNext(it) },
            onAddToQueue = { player.addToQueue(it) },
            onComments = { vid -> sheets.showComments(vid) }
        )
    )

    fun open(r: FfiSearchResult) {
        vm.logTap(r)
        vm.closePanel()
        focusManager.clearFocus()
        when {
            r.videoId != null && r.category.contains("Episode", true) ->
                nav.navigate("episode/${r.detailId.ifBlank { r.videoId }}")
            r.videoId != null -> {
                val track = r.toDomainTrack()
                player.playTrack(track, PlayOrigin.Search(vm.lastQuery.ifEmpty { vm.query }, vm.selectedLabel))
            }
            else -> com.teamshryne.mediyo.core.design.openRoute(r)?.let { nav.navigate(it) }
        }
    }

    val scrolled by remember {
        derivedStateOf {
            resultsState.firstVisibleItemIndex > 0 || resultsState.firstVisibleItemScrollOffset > 8
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchHeader(
            query = vm.query,
            onQueryChange = { vm.onQueryChange(it) },
            onSubmit = { q ->
                vm.query = q
                vm.submit()
                focusManager.clearFocus()
            },
            onClear = {
                vm.resetSearch()
                focusManager.clearFocus()
            },
            onDismiss = {
                vm.closePanel()
                focusManager.clearFocus()
            },
            onFocusChanged = { focused ->
                // Only opening is tied to focus. Closing on blur would hide
                // the panel under the finger that is tapping one of its rows,
                // before the click gets to run.
                if (focused) vm.openPanel()
            },
            filters = vm.filters,
            selectedFilter = vm.selectedFilter,
            showFilters = vm.hasSearched && vm.filters.isNotEmpty(),
            onFilterSelect = { filter ->
                vm.runSearch(filter)
                focusManager.clearFocus()
            },
            scrolled = scrolled
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Crossfade(
                targetState = body,
                animationSpec = tween(180, easing = EaseOutExpo),
                label = "search-body"
            ) { state ->
                when (state) {
                    SearchBody.Idle -> SearchIdleContent(
                        recents = vm.recents,
                        recentPlays = recentPlays,
                        playingId = playingId,
                        onSearch = { q ->
                            vm.query = q
                            vm.submit()
                            focusManager.clearFocus()
                        },
                        onRemoveRecent = vm::removeRecent,
                        onClearRecents = vm::clearRecents,
                        onPlay = { play ->
                            player.playTrack(
                                play.track,
                                PlayOrigin.Search(play.query.ifBlank { "Search" }, vm.selectedLabel)
                            )
                        }
                    )

                    SearchBody.Loading -> SearchLoadingContent()

                    SearchBody.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(vm.error ?: "Search failed") { vm.runSearch(null) }
                    }

                    SearchBody.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            "No results for \u201C${vm.lastQuery}\u201D",
                            "Try different keywords or another filter"
                        )
                    }

                    SearchBody.Results -> SearchResultsContent(
                        results = vm.results,
                        latestQuery = vm.lastQuery.ifEmpty { vm.query },
                        selectedLabel = vm.selectedLabel,
                        loadingMore = vm.loadingMore,
                        player = player,
                        listState = resultsState,
                        onOpen = { open(it) },
                        onMenu = { openMenu(it) }
                    )
                }
            }

            // The suggestion panel is opaque and fills the content area:
            // while the user types, the results stay out of sight instead of
            // peeking through under the suggestions.
            androidx.compose.animation.AnimatedVisibility(
                visible = vm.panelOpen,
                enter = fadeIn(tween(140)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                SearchSuggestionPanel(
                    query = vm.query,
                    recents = vm.recents,
                    suggestTexts = vm.suggestTexts,
                    suggestEntities = vm.suggestEntities,
                    showSuggestions = vm.suggestVisible,
                    onSubmit = { q ->
                        vm.query = q
                        vm.submit()
                        focusManager.clearFocus()
                    },
                    onFill = { vm.fillQuery(it) },
                    onRemoveRecent = vm::removeRecent,
                    onOpenEntity = {
                        open(it)
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }

    InfiniteScrollHandler(
        listState = resultsState,
        itemCount = vm.results.size + 2,
        enabled = vm.continuation != null && !vm.loading && !vm.loadingMore && vm.error == null
    ) { vm.loadMore() }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    filters: List<FfiSearchFilter>,
    selectedFilter: FfiSearchFilter?,
    showFilters: Boolean,
    onFilterSelect: (FfiSearchFilter?) -> Unit,
    scrolled: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchPill(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onClear = onClear,
            onDismiss = onDismiss,
            onFocusChanged = onFocusChanged
        )

        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn(tween(140)) + expandVertically(tween(170), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) + shrinkVertically(tween(140), shrinkTowards = Alignment.Top)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item(key = "filter_all") {
                    SearchFilterChip(
                        label = "All",
                        selected = selectedFilter == null,
                        onClick = { if (selectedFilter != null) onFilterSelect(null) }
                    )
                }
                items(filters, key = { it.label + "|" + (it.params ?: "") }) { filter ->
                    val isSelected = selectedFilter?.let {
                        it.label == filter.label && it.params == filter.params
                    } == true
                    SearchFilterChip(
                        label = filter.label.replaceFirstChar { it.uppercase() },
                        selected = isSelected,
                        onClick = { onFilterSelect(if (isSelected) null else filter) }
                    )
                }
            }
        }

        // A hairline only when content has moved under the header. It gives
        // the fixed bar an edge without drawing a permanent box around it.
        AnimatedVisibility(
            visible = scrolled,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

@Composable
private fun SearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(20.dp),
        // A filled, borderless chip: the fill already separates it from the
        // background, and a second outline just adds noise.
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun SearchIdleContent(
    recents: List<String>,
    recentPlays: List<RecentSearchPlay>,
    playingId: String?,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onPlay: (RecentSearchPlay) -> Unit
) {
    if (recents.isEmpty() && recentPlays.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    "Search Mediyo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Find songs, artists, albums and playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 6.dp,
            bottom = LocalOverlayBottom.current + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (recents.isNotEmpty()) {
            item(key = "header_recents") {
                SectionHeader(
                    "Recent searches",
                    trailing = {
                        TextButton(onClick = onClearRecents) { Text("Clear") }
                    }
                )
            }
            items(recents.take(6), key = { "recent_$it" }) { q ->
                RecentSearchRow(
                    query = q,
                    onClick = { onSearch(q) },
                    onRemove = { onRemoveRecent(q) }
                )
            }
        }

        if (recents.isNotEmpty() && recentPlays.isNotEmpty()) {
            item(key = "gap") { Spacer(Modifier.height(10.dp)) }
        }

        if (recentPlays.isNotEmpty()) {
            item(key = "header_plays") { SectionHeader("Recently played") }
            items(recentPlays.take(6), key = { "played_${it.track.videoId}_${it.query}" }) { play ->
                RecentPlayRow(
                    track = play.track,
                    isPlaying = playingId != null && playingId == play.track.videoId,
                    onClick = { onPlay(play) }
                )
            }
        }
    }
}

@Composable
private fun SearchLoadingContent() {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 6.dp,
            bottom = LocalOverlayBottom.current + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(7, key = { "skeleton_$it" }) { SearchSkeletonRow() }
    }
}

@Composable
private fun SearchSkeletonRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).shimmer())
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<FfiSearchResult>,
    latestQuery: String,
    selectedLabel: String,
    loadingMore: Boolean,
    player: PlayerViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (FfiSearchResult) -> Unit,
    onMenu: (FfiSearchResult) -> Unit
) {
    val topIndex = results.indexOfFirst { it.isTopResult }
    val top = results.getOrNull(topIndex)
    val rest = if (topIndex >= 0) {
        results.filterIndexed { index, _ -> index != topIndex }
    } else {
        results
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 6.dp,
            bottom = LocalOverlayBottom.current + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (top != null) {
            item(key = "top_result") {
                TopResultCard(
                    item = top,
                    latestQuery = latestQuery,
                    selectedLabel = selectedLabel,
                    player = player,
                    onOpen = { onOpen(top) }
                )
            }
            if (rest.isNotEmpty()) {
                item(key = "top_result_gap") { Spacer(Modifier.height(8.dp)) }
            }
        }

        itemsIndexed(
            items = rest,
            key = { index, item ->
                val id = item.videoId ?: item.browseId ?: item.playlistId ?: item.title
                "r_${id}_$index"
            }
        ) { _, item ->
            ResultRow(
                item = item,
                onClick = { onOpen(item) },
                onMenu = { onMenu(item) }
            )
        }

        item(key = "search_footer") { LoadingFooter(loadingMore) }
    }
}

/**
 * The top result gets a little more room than the rows below it, but the same
 * 20dp margins and the same one-tap action. Extra controls live in the rows'
 * overflow, not here, so the card stays quiet.
 */
@Composable
private fun TopResultCard(
    item: FfiSearchResult,
    latestQuery: String,
    selectedLabel: String,
    player: PlayerViewModel,
    onOpen: () -> Unit
) {
    val isArtist = item.category.contains("Artist", true)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnails.bestThumbUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Top result",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    searchMeta(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = {
                    if (isArtist || item.videoId == null) onOpen()
                    else player.playTrack(
                        item.toDomainTrack(),
                        PlayOrigin.Search(latestQuery, selectedLabel)
                    )
                },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    if (isArtist || item.videoId == null) Icons.Filled.OpenInNew else Icons.Filled.PlayArrow,
                    contentDescription = if (isArtist) "Open" else "Play",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Secondary line under a search result title: "Artist • Song", collapsing to
 * just "Song" when the artist is unknown.
 *
 * The artist used to fall back to the category and then had the category
 * appended again, which rendered every anonymous song as "Song • Song".
 */
private fun searchMeta(item: FfiSearchResult): String {
    val primary = item.artists.joinToString().ifBlank { item.info?.trim().orEmpty() }
    val category = item.category.trim()
    return when {
        primary.isEmpty() -> category
        category.isEmpty() || primary.equals(category, ignoreCase = true) -> primary
        else -> "$primary  •  $category"
    }
}

@Composable
private fun ResultRow(item: FfiSearchResult, onClick: () -> Unit, onMenu: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(
                    if (item.category.contains("Artist", true)) CircleShape
                    else RoundedCornerShape(10.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                searchMeta(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!item.duration.isNullOrBlank()) {
            Text(
                item.duration.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TrackOverflowIcon(onClick = onMenu)
    }
}

/** A submitted search that can be run again, or dropped from the history. */
@Composable
private fun RecentSearchRow(query: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove ${query} from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * A song we played from the search results: artwork, title and artist, with
 * the length on the right. Tapping it plays it again.
 */
@Composable
private fun RecentPlayRow(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl.upscaledThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artists.joinToString().ifBlank { track.album.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else if (!track.duration.isNullOrBlank()) {
            Text(
                track.duration.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
