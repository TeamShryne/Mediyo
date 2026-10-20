package com.teamshryne.mediyo.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.EmptyState
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.core.design.TrackOverflowIcon
import com.teamshryne.mediyo.core.design.appendUnique
import com.teamshryne.mediyo.core.design.shimmer
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import com.teamshryne.mediyo.data.mediyo.FfiSearchFilter
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.FfiSuggestText
import javax.inject.Inject

@HiltViewModel
class SearchVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val events: UserEventRepository
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
                // own Clear pill handles resetting.
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

/**
 * Height of the search bar block: the pill plus its breathing room. The bar
 * sits at the top of the screen in every state, so the results list starts
 * below it and the suggestion panel docks exactly under it.
 */
private val SEARCH_BAR_HEIGHT = 72.dp

@Composable
fun SearchScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: SearchVm = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Measured height of the search bar: the panel docks exactly under it,
    // whatever the font scale does to the pill.
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        barHeightPx.toDp()
    }.takeIf { it > 0.dp } ?: SEARCH_BAR_HEIGHT
    // Menus render at the root overlay (GlobalSheets) for full-window dim.
    val sheets: com.teamshryne.mediyo.core.design.SheetHostVm = hiltViewModel()

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

    // Scrolling the results dismisses the suggestion panel — for the current
    // query only, so the next keystroke re-opens it naturally.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
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

    fun open(r: FfiSearchResult) {
        vm.logTap(r)
        vm.dismissSuggest()
        // A tap on a result ends the typing state, whatever opened it.
        vm.closePanel()
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

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The search bar is fixed at the top of the screen, so the list
        // starts below it. Outer Scaffold padding already clears the status
        // bar — 4dp only.
        contentPadding = PaddingValues(
            top = barHeight + 4.dp,
            bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "search_header") {
            Text(
                "Search",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Filter chips scroll with the results: a pinned chip row would change
        // the header height when a search runs, shifting the list under the
        // user's finger. They appear right after the title instead.
        if (vm.hasSearched && vm.filters.isNotEmpty()) {
            item(key = "search_chips") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "clear_filter") {
                            val active = vm.selectedFilter != null
                            AnimatedVisibility(
                                visible = active,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                            ) {
                                // Themed clear-filter pill (plain Surface: full
                                // control of the fill without chip-color APIs).
                                Surface(
                                    onClick = { scope.launch { vm.runSearch(null) } },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Clear",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                        items(vm.filters, key = { it.label + "|" + (it.params ?: "") }) { f ->
                            val isSel = vm.selectedFilter?.let { it.label == f.label && it.params == f.params } == true
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel) scope.launch { vm.runSearch(null) }
                                    else scope.launch { vm.runSearch(f) }
                                },
                                label = { Text(f.label.replaceFirstChar { it.uppercase() }) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }
        }

        when {
            !vm.hasSearched -> if (vm.query.isBlank()) {
                if (vm.recents.isNotEmpty()) {
                    item(key = "recent_header") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.clearRecents() }) { Text("Clear") }
                        }
                    }
                    items(vm.recents, key = { "recent_$it" }) { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.query = q
                                    vm.runSearch(null)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.History, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                q,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    item(key = "search_idle") {
                        EmptyState("Search Mediyo", "Find songs, artists, albums and playlists")
                    }
                }
            }
            vm.loading -> items(7, key = { "skel_$it" }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                        Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
                    }
                }
            }
            vm.error != null -> item(key = "error") { ErrorState(vm.error ?: "Search failed") { vm.runSearch(null) } }
            vm.results.isEmpty() -> item(key = "empty") {
                EmptyState("No results for \"${vm.lastQuery}\"", "Try different keywords or another filter")
            }
            else -> {
                // Top-result shelf rows first, featured — the rest as plain rows.
                val top = vm.results.filter { it.isTopResult }
                val rest = vm.results.filterNot { it.isTopResult }
                if (top.isNotEmpty()) {
                    item(key = "top_header") {
                        Text(
                            "Top result",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    // One card holds every YT top result: the first is featured
                    // big, the rest follow as a compact list inside the same card.
                    item(key = "top_card") {
                        TopResultsCard(
                            items = top,
                            lastQuery = vm.lastQuery.ifEmpty { vm.query },
                            selectedLabel = vm.selectedLabel,
                            player = player,
                            onOpen = { open(it) },
                            onMenu = { openMenu(it) }
                        )
                    }
                }
                items(rest.size, key = { i ->
                    rest[i].let { it.videoId ?: it.browseId ?: it.playlistId }?.let { "r_${it}_$i" } ?: "r_$i"
                }) { i ->
                    val r = rest[i]
                    ResultRow(item = r, onClick = { open(r) }, onMenu = { openMenu(r) })
                }
            }
        }

        item(key = "search_footer") { LoadingFooter(vm.loadingMore && !vm.loading) }
    }

    // ── The search bar ──────────────────────────────────────────────────
    // Fixed at the top of the screen in every state — idle, typing and
    // looking at results. It never moves, so the input is always where the
    // user left it, and the suggestion panel docks right under it.
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { barHeightPx = it.height }
    ) {
        SearchPill(
            query = vm.query,
            onQueryChange = { vm.onQueryChange(it) },
            onSubmit = { q ->
                vm.query = q
                vm.submit()
                focusManager.clearFocus()
            },
            onClear = { vm.onQueryChange("") },
            onBack = {
                // The arrow is "cancel": it leaves the search UI, it never
                // leaves the tab.
                if (vm.panelOpen) {
                    vm.closePanel()
                    focusManager.clearFocus()
                } else if (vm.query.isNotEmpty() || vm.hasSearched) {
                    vm.resetSearch()
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.onFocusChanged { if (it.isFocused) vm.openPanel() }
        )
        // Hairline under the bar, but only once the results slide under it.
        AnimatedVisibility(
            visible = scrolled && !vm.panelOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }

    // ── Suggestion panel ────────────────────────────────────────────────
    // Opaque and full height: while the user types, the results stay out of
    // sight instead of peeking through under the suggestions.
    AnimatedVisibility(
        visible = vm.panelOpen,
        enter = fadeIn(tween(140)) + expandVertically(tween(220)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        modifier = Modifier.align(Alignment.TopCenter)
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
            onRemoveRecent = { vm.removeRecent(it) },
            onOpenEntity = {
                open(it)
                focusManager.clearFocus()
            },
            modifier = Modifier.padding(top = barHeight)
        )
    }
    }

    InfiniteScrollHandler(
        listState = listState,
        itemCount = vm.results.size + 2,
        enabled = vm.continuation != null && !vm.loading && !vm.loadingMore && vm.error == null
    ) { vm.loadMore() }
}

/**
 * All YT top results in a single card: the first result is featured big,
 * every further top result follows as a compact row inside the same card.
 * Order mirrors the server response ([items] must already be in YT order).
 */
@Composable
private fun TopResultsCard(
    items: List<FfiSearchResult>,
    lastQuery: String,
    selectedLabel: String,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    onOpen: (FfiSearchResult) -> Unit,
    onMenu: (FfiSearchResult) -> Unit
) {
    if (items.isEmpty()) return
    val first = items[0]
    val rest = items.drop(1)
    val firstIsArtist = first.category.contains("Artist", true)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 4.dp)
    ) {
        TopResultHero(
            item = first,
            circularArt = firstIsArtist,
            actionIcon = if (firstIsArtist) Icons.Filled.OpenInNew else Icons.Filled.PlayArrow,
            onClick = { onOpen(first) },
            onMenu = { onMenu(first) },
            onPlay = {
                if (firstIsArtist || first.videoId == null) onOpen(first)
                else {
                    player.playTrack(
                        first.toDomainTrack(),
                        PlayOrigin.Search(lastQuery, selectedLabel)
                    )
                }
            }
        )
        rest.forEach { r ->
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            TopResultMiniRow(item = r, onClick = { onOpen(r) }, onMenu = { onMenu(r) })
        }
    }
}

@Composable
private fun TopResultHero(
    item: FfiSearchResult,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    circularArt: Boolean = false,
    actionIcon: ImageVector = Icons.Filled.PlayArrow
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .let { if (circularArt) it.clip(CircleShape) else it.clip(RoundedCornerShape(12.dp)) }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Top result • ${item.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(item.artists.joinToString().ifBlank { item.category })
                    if (!item.duration.isNullOrBlank()) append(" • ${item.duration}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                Icon(actionIcon, contentDescription = "Open", modifier = Modifier.size(24.dp))
            }
            TrackOverflowIcon(onClick = onMenu)
        }
    }
}

@Composable
private fun TopResultMiniRow(item: FfiSearchResult, onClick: () -> Unit, onMenu: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .let {
                    if (item.category.contains("Artist", true)) it.clip(CircleShape)
                    else it.clip(RoundedCornerShape(8.dp))
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.bestThumbUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .let {
                    if (item.category.contains("Artist", true)) it.clip(CircleShape)
                    else it.clip(RoundedCornerShape(8.dp))
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
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
        Text(
            item.duration.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrackOverflowIcon(onClick = onMenu)
    }
}
