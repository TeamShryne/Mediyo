package com.teamshryne.mediyo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.LocalOverlayBottom
import com.teamshryne.mediyo.core.design.MediaCard
import com.teamshryne.mediyo.core.design.PullToRefreshList
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.openRoute
import com.teamshryne.mediyo.core.design.rememberGreeting
import com.teamshryne.mediyo.core.design.TrackMenuRequest
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.core.design.navigateAlbum
import com.teamshryne.mediyo.core.design.navigateArtist
import com.teamshryne.mediyo.core.design.navigateChannel
import com.teamshryne.mediyo.core.design.navigatePlaylist
import com.teamshryne.mediyo.core.design.navigatePodcast
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/**
 * How many confirmed plays at the start of a session may update home on
 * their own. After that the shelves wait for the app to restart or for the
 * user to pull the screen down.
 */
private const val INSTANT_UPDATE_PLAYS = 5
/**
 * Everything home shows, frozen at one moment.
 *
 * Home is a snapshot, not a live view. The shelves are re-read when the app
 * starts, when the user pulls the screen down to refresh, and — for the
 * first few plays of a session — as soon as a play is confirmed. After that
 * they stay put: a screen that reshuffles under your thumb while you listen
 * is a screen you cannot trust.
 */
data class HomeSnapshot(
    val quickPicks: List<Track> = emptyList(),
    val weekTop: List<WeekTop> = emptyList(),
    val onRepeat: List<Track> = emptyList(),
    val rediscover: List<Track> = emptyList(),
    val topArtists: List<ArtistAffinity> = emptyList(),
    val mixes: List<HomeMix> = emptyList(),
    val episodes: List<Track> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val latestSearch: String? = null,
    val weekStats: WeekStats = WeekStats(0, 0),
    val finishAlbums: List<AlbumProgress> = emptyList(),
    val daypart: DaypartShelves = DaypartShelves(emptyList(), emptyList()),
    val saved: List<SavedCollectionEntity> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val likedCount: Int = 0,
    val playlistCount: Int = 0
)

@HiltViewModel
class HomeVm @Inject constructor(
    private val repo: HomeRepository,
    private val bridge: MediyoBridge,
    private val events: UserEventRepository,
    private val discovery: DiscoveryCoordinator
) : ViewModel() {
    private val _snapshot = MutableStateFlow(HomeSnapshot())
    val snapshot: StateFlow<HomeSnapshot> = _snapshot.asStateFlow()

    // ── Discovery graph: unfamiliar songs, grown on demand ────────────────
    // The quick-picks carousel shows the tracks most likely to be tapped next
    // plus a shuffled sample of what the queue graph found; the rest of the
    // catalogue becomes the "Because you heard X" shelves. Everything the
    // user has already played, liked or been shown is excluded, so the
    // shelves keep producing new material.
    private val _feed = MutableStateFlow(DiscoveryFeed(emptyList(), emptyList()))
    val feed: StateFlow<DiscoveryFeed> = _feed.asStateFlow()

    var discoveryLoading by mutableStateOf(true)
    /** True while a pull-to-refresh is running. */
    var refreshing by mutableStateOf(false)
    private val growLock = Mutex()

    // ── Lazy network shelves: load once, hide silently on failure ──────────
    // Home must open instantly offline; these fill in below as they arrive.
    var newReleases by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var similarArtists by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var discoveryShelfLoading by mutableStateOf(true)

    /**
     * The last confirmed play home has seen, and when this session started.
     *
     * The first song of a session is the strongest signal a new listener
     * gives, and the app should visibly react to it — so the first handful
     * of plays refresh the shelves. After that, home waits for the app to
     * start again or for the user to pull it down.
     */
    private var lastPlayAt = 0L
    private val sessionStart = System.currentTimeMillis()

    /**
     * Called when a shelf reaches its end: expand the graph one round and
     * re-read the catalogue.
     *
     * A round that is already running is not queued behind: the shelf only
     * ever needs the newest batch, and a pile of pending rounds would hammer
     * the network for material the user scrolled past.
     */
    fun expandDiscovery() {
        if (!growLock.tryLock()) return
        viewModelScope.launch {
            try {
                discovery.expand()
                _feed.value = discovery.feed()
            } catch (_: Throwable) {
            } finally {
                discoveryLoading = false
                growLock.unlock()
            }
        }
    }

    /**
     * Re-read every local shelf. Called when the app opens, when the user
     * pulls the screen down, and for the first few confirmed plays.
     */
    fun refresh() {
        viewModelScope.launch { _snapshot.value = readSnapshot() }
    }

    /** The user dragged the screen down: refresh everything, network included. */
    fun pullRefresh() {
        if (!growLock.tryLock()) return
        viewModelScope.launch {
            try {
                refreshing = true
                _snapshot.value = readSnapshot()
                discovery.expand()
                _feed.value = discovery.feed()
            } catch (_: Throwable) {
            } finally {
                refreshing = false
                discoveryLoading = false
                growLock.unlock()
            }
        }
    }

    /** Read every local shelf once, right now. */
    private suspend fun readSnapshot() = HomeSnapshot(
        quickPicks = repo.flowQuickPicks().first(),
        weekTop = repo.flowWeekTop().first(),
        onRepeat = repo.flowOnRepeat().first(),
        rediscover = repo.flowRediscover().first(),
        topArtists = repo.flowTopArtists().first(),
        mixes = repo.flowMixSeeds().first(),
        episodes = repo.flowEpisodes().first(),
        recentSearches = repo.flowRecentSearches().first(),
        latestSearch = repo.flowLatestSearch().first(),
        weekStats = repo.flowWeekStats().first(),
        finishAlbums = repo.flowFinishAlbums().first(),
        daypart = repo.flowDaypart().first(),
        saved = repo.flowSaved().first(),
        likedTracks = repo.flowLikedTracks().first(),
        likedCount = repo.flowLikedCount().first(),
        playlistCount = repo.flowPlaylistCount().first()
    )

    init {
        // The opening snapshot, and a first round of discovery so the shelf
        // is never empty.
        refresh()
        expandDiscovery()

        // A confirmed play is real taste, so the app may react to it — but
        // only for the first few plays of the session. After that the
        // shelves wait for the app to restart or for the user to pull the
        // screen down.
        viewModelScope.launch {
            events.flowRecent(60).collect { list ->
                // The list is newest-first, so the newest confirmed play is
                // the head. Its timestamp is what says "a new play happened";
                // the count is what says whether it may still update home.
                val plays = list.filter {
                    it.type == UserEventTypes.PLAY_CONFIRMED && it.createdAt >= sessionStart
                }
                val latest = plays.firstOrNull()?.createdAt ?: 0L
                if (latest <= lastPlayAt) return@collect
                lastPlayAt = latest
                if (plays.size > INSTANT_UPDATE_PLAYS) return@collect
                // Give the history write a moment to land before reading it.
                delay(400)
                refresh()
                _feed.value = discovery.feed()
            }
        }

        // One artist-page fetch per affinity artist feeds BOTH shelves:
        // release shelves (Albums / Singles) → new music, similar shelf → discovery.
        viewModelScope.launch {
            try {
                val tops = repo.flowTopArtists().first()
                val ids = (tops.filter { it.followed } + tops.filter { !it.followed })
                    .take(5).mapNotNull { it.artistId }.distinct()
                val releases = linkedMapOf<String, FfiSearchResult>()
                val similars = linkedMapOf<String, FfiSearchResult>()
                for (id in ids) {
                    val page = try {
                        bridge.artist(id)
                    } catch (_: Throwable) {
                        continue
                    }
                    for (c in page.carousels) {
                        if (c.title.contains("album", true) ||
                            c.title.contains("single", true) || c.title.contains("EP")
                        ) {
                            for (r in c.items.take(2)) {
                                releases.putIfAbsent(r.browseId ?: r.videoId ?: continue, r)
                            }
                        }
                        if (c.title.contains("like", true) ||
                            c.title.contains("similar", true) || c.title.contains("fans", true)
                        ) {
                            for (r in c.items) {
                                if (!r.category.contains("Artist", true)) continue
                                similars.putIfAbsent(r.browseId ?: continue, r)
                            }
                        }
                    }
                }
                newReleases = releases.values.take(10)
                similarArtists = similars.values.take(10)
            } catch (_: Throwable) {
            } finally {
                discoveryShelfLoading = false
            }
        }
    }

    /** A mix plays its seed first (familiar), then drifts into radio (novel). */
    fun playMix(mix: HomeMix, player: com.teamshryne.mediyo.feature.player.PlayerViewModel) {
        val vid = mix.seed.videoId ?: return
        viewModelScope.launch {
            try {
                val q = bridge.radioFor(vid)
                val rest = (q?.items ?: emptyList()).map { it.toDomainTrack() }
                    .filter { it.videoId != null && it.videoId != vid }.take(24)
                player.playTracks(listOf(mix.seed) + rest, 0, PlayOrigin.Radio(vid))
            } catch (_: Throwable) {
                player.playTrack(mix.seed, PlayOrigin.Radio(vid))
            }
        }
    }

    fun logTap(track: Track) {
        viewModelScope.launch {
            events.log(
                UserEventTypes.HOME_TAP, videoId = track.videoId,
                label = "Continue listening", meta = "title=${track.title.take(80)}"
            )
        }
    }

    fun logSavedTap(item: SavedCollectionEntity) {
        viewModelScope.launch {
            events.log(
                UserEventTypes.HOME_TAP, browseId = item.browseId,
                label = "Your library", meta = "kind=${item.kind};title=${item.title.take(80)}"
            )
        }
    }
}

@Composable
fun HomeScreen(
    nav: androidx.navigation.NavController,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel,
    vm: HomeVm = hiltViewModel()
) {
    val greeting = rememberGreeting()
    val scope = rememberCoroutineScope()
    val menuScope = rememberCoroutineScope()
    val context = LocalContext.current
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()
    // Sheets render at the root overlay (GlobalSheets) for full-window dim.
    val sheets: com.teamshryne.mediyo.core.design.SheetHostVm = hiltViewModel()
    var resolving by remember { mutableStateOf<String?>(null) }
    val snap by vm.snapshot.collectAsState()
    val feed by vm.feed.collectAsState()
    val playingId = player.state.collectAsState().value.videoId

    // Local aliases, so the shelves below read the way they always did.
    val quickPicks = snap.quickPicks
    val weekTop = snap.weekTop
    val onRepeat = snap.onRepeat
    val rediscover = snap.rediscover
    val topArtists = snap.topArtists
    val mixes = snap.mixes
    val episodes = snap.episodes
    val recentSearches = snap.recentSearches
    val latestSearch = snap.latestSearch
    val weekStats = snap.weekStats
    val finishAlbums = snap.finishAlbums
    val daypart = snap.daypart
    val saved = snap.saved
    val likedTracks = snap.likedTracks
    val likedCount = snap.likedCount
    val playlistCount = snap.playlistCount

    fun play(t: Track, origin: PlayOrigin) {
        vm.logTap(t)
        if (playingId != null && playingId == t.videoId) player.toggle()
        else player.playTrack(t, origin)
    }

    /** The overflow menu on a quick-picks row. */
    fun openMenu(t: Track) = sheets.showTrackMenu(
        TrackMenuRequest(
            track = t,
            onAddToPlaylist = { sheets.openAddToPlaylist(t) },
            onPlayNext = { player.addNext(t) },
            onAddToQueue = { player.addToQueue(t) },
            onGoToAlbum = t.albumId?.takeIf { it.isNotBlank() }?.let { { nav.navigateAlbum(it) } },
            onOpenChannel = t.channelId?.takeIf { it.isNotBlank() }?.let { { nav.navigateChannel(it) } },
            onShowArtist = { name, id ->
                menuScope.launch {
                    (id?.takeIf { it.isNotBlank() } ?: menuVm.resolveArtistIdByName(name))
                        ?.let { nav.navigateArtist(it) }
                }
            },
            onComments = { t.videoId?.let { sheets.showComments(it) } }
        )
    )

    fun openSaved(item: SavedCollectionEntity) {
        vm.logSavedTap(item)
        when (item.kind) {
            SavedCollectionEntity.ALBUM -> nav.navigateAlbum(item.browseId)
            SavedCollectionEntity.PODCAST -> nav.navigatePodcast(item.browseId)
            else -> nav.navigatePlaylist(item.browseId)
        }
    }

    // Context shelf follows the clock; outside morning/night the evergreen
    // night shelf still beats an empty slot.
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val daypartTitle: String?
    val daypartTracks: List<Track>
    if (hour in 5..10 && daypart.morning.isNotEmpty()) {
        daypartTitle = "Because it's morning"
        daypartTracks = daypart.morning
    } else if ((hour >= 21 || hour < 5) && daypart.night.isNotEmpty()) {
        daypartTitle = "Because it's late"
        daypartTracks = daypart.night
    } else if (daypart.night.isNotEmpty()) {
        daypartTitle = "Because it's late"
        daypartTracks = daypart.night
    } else {
        daypartTitle = null
        daypartTracks = emptyList()
    }

    // The quick-picks carousel: the tracks most likely to be tapped next,
    // then a shuffled sample of what the queue graph found.
    val quickRow = remember(snap.quickPicks, feed) {
        (snap.quickPicks + feed.quick).distinctBy { it.uniqueKey() }
    }

    // Every content section counts: otherwise a user whose history only
    // feeds some shelves sees "Nothing here yet" above real content.
    val hasAnything = quickRow.isNotEmpty() || snap.weekTop.isNotEmpty() ||
        snap.saved.isNotEmpty() || snap.recentSearches.isNotEmpty() ||
        snap.mixes.isNotEmpty() || snap.episodes.isNotEmpty() ||
        snap.onRepeat.isNotEmpty() || snap.rediscover.isNotEmpty() ||
        snap.topArtists.isNotEmpty() || snap.finishAlbums.isNotEmpty() ||
        daypartTracks.isNotEmpty() || feed.shelves.isNotEmpty() ||
        snap.likedTracks.isNotEmpty()

    val listState = rememberLazyListState()

    PullToRefreshList(
        listState = listState,
        refreshing = vm.refreshing,
        onRefresh = { vm.pullRefresh() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = LocalOverlayBottom.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(greeting, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("What do you want to listen to?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { nav.navigate("profile") }, contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    HomeSearchBar(onOpen = { nav.navigate("search") })
                }
            }

            if (!hasAnything && !vm.discoveryLoading && !vm.discoveryShelfLoading) {
                item { EmptyHome { nav.navigate("search") } }
            } else {
                if (quickRow.isNotEmpty()) {
                    item(key = "quick") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(
                                "Quick picks",
                                trailing = {
                                    OutlinedButton(
                                        onClick = {
                                            vm.logTap(quickRow.first())
                                            player.playTracks(quickRow, 0, PlayOrigin.HomeShelf("Quick picks"))
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Play all",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            )
                            TrackColumnCarousel(
                                tracks = quickRow,
                                playingId = playingId,
                                onPick = { play(it, PlayOrigin.History("Home")) },
                                onMenu = { openMenu(it) },
                                onNearEnd = { vm.expandDiscovery() }
                            )
                        }
                    }
                }
                if (weekTop.isNotEmpty()) {
                    val top = weekTop.first()
                    item(key = "rotation") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Heavy rotation")
                            RotationFeature(
                                track = top.track,
                                plays = top.plays,
                                isPlaying = playingId != null && playingId == top.track.videoId
                            ) { play(top.track, PlayOrigin.History("Home")) }
                        }
                    }
                }
                if (latestSearch != null) {
                    item(key = "spotlight") {
                        SearchSpotlightCard(query = latestSearch!!) { nav.navigate("search") }
                    }
                }
                if (recentSearches.size > 1) {
                    item(key = "searches") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Recent searches")
                            RecentSearchChips(recentSearches.drop(1)) { nav.navigate("search") }
                        }
                    }
                }
                if (mixes.isNotEmpty()) {
                    item(key = "mixes") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Your mixes")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(mixes, key = { i, m -> "mix_${m.artistName}_$i" }) { _, m ->
                                    MixCard(m) { vm.playMix(m, player) }
                                }
                            }
                        }
                    }
                }
                if (episodes.isNotEmpty()) {
                    item(key = "episodes") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionHeader("Jump back in")
                            episodes.forEach { t ->
                                EpisodeResumeRow(t) { play(t, PlayOrigin.Podcast(t.albumId ?: t.videoId.orEmpty())) }
                            }
                        }
                    }
                }
                if (onRepeat.isNotEmpty()) {
                    item(key = "repeat") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Because you loved it")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(onRepeat, key = { i, t -> "${t.uniqueKey()}_$i" }) { _, t ->
                                    MediaCard(
                                        title = t.title,
                                        subtitle = t.artists.joinToString(),
                                        artworkUrl = t.artworkUrl,
                                        round = false
                                    ) { play(t, PlayOrigin.History("Home")) }
                                }
                            }
                        }
                    }
                }
                // The discovery catalogue: one shelf per taste seed, each holding
                // the tracks the graph found by branching from it.
                feed.shelves.forEach { shelf ->
                    item(key = "because_${shelf.seedVideoId}") {
                        BecauseShelf(
                            shelf = shelf,
                            playingId = playingId,
                            onPick = { play(it, PlayOrigin.HomeShelf(shelf.title)) },
                            onMenu = { openMenu(it) },
                            onNearEnd = { vm.expandDiscovery() }
                        )
                    }
                }
                if (likedTracks.isNotEmpty()) {
                    item(key = "liked") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Because you liked it")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(likedTracks, key = { i, t -> "${t.uniqueKey()}_$i" }) { _, t ->
                                    MediaCard(
                                        title = t.title,
                                        subtitle = t.artists.joinToString(),
                                        artworkUrl = t.artworkUrl,
                                        round = false
                                    ) { play(t, PlayOrigin.Liked(likedTracks.size)) }
                                }
                            }
                        }
                    }
                }
                if (topArtists.isNotEmpty()) {
                    item(key = "artists") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(
                                "Your top artists",
                                trailing = if (resolving != null) {
                                    {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else null
                            )
                            ArtistCircleRow(topArtists) { a ->
                                // Rows saved before ids existed (and any byline we
                                // couldn't pair a UC id with) have nothing to
                                // navigate to, so resolve by name instead of
                                // silently doing nothing on tap.
                                val id = a.artistId
                                if (id != null) {
                                    nav.navigateArtist(id)
                                } else {
                                    scope.launch {
                                        resolving = a.name
                                        val resolved = try {
                                            menuVm.resolveArtistIdByName(a.name)
                                        } catch (_: Throwable) {
                                            null
                                        }
                                        resolving = null
                                        if (resolved != null) nav.navigateArtist(resolved)
                                        else android.widget.Toast.makeText(
                                            context,
                                            "Couldn't find ${a.name}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
                if (weekStats.plays > 0) {
                    item(key = "stats") { WeekStatsBanner(weekStats) }
                }
                if (rediscover.isNotEmpty()) {
                    item(key = "rediscover") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Because you were away")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(rediscover, key = { i, t -> "${t.uniqueKey()}_$i" }) { _, t ->
                                    MediaCard(
                                        title = t.title,
                                        subtitle = t.artists.joinToString(),
                                        artworkUrl = t.artworkUrl,
                                        round = false
                                    ) { play(t, PlayOrigin.History("Home")) }
                                }
                            }
                        }
                    }
                }
                if (daypartTitle != null) {
                    item(key = "daypart") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DaypartHeader(daypartTitle, night = daypartTitle == "Because it's late")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(daypartTracks, key = { i, t -> "${t.uniqueKey()}_$i" }) { _, t ->
                                    MediaCard(
                                        title = t.title,
                                        subtitle = t.artists.joinToString(),
                                        artworkUrl = t.artworkUrl,
                                        round = false
                                    ) { play(t, PlayOrigin.History("Home")) }
                                }
                            }
                        }
                    }
                }
                if (finishAlbums.isNotEmpty()) {
                    item(key = "finish") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionHeader("Finish the album")
                            finishAlbums.forEach { a ->
                                AlbumProgressRow(a) { nav.navigateAlbum(a.browseId) }
                            }
                        }
                    }
                }
                item(key = "library-band") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Your library")
                        LibraryBand(
                            likedCount = likedCount,
                            playlistCount = playlistCount,
                            savedCount = saved.size,
                            onLiked = { nav.navigate("liked") },
                            onPlaylists = { nav.navigate("library") },
                            onHistory = { nav.navigate("history") }
                        )
                    }
                }
                // The old "Because you played X" radio shelf is gone: the
                // discovery graph above is the same idea with several seeds,
                // branched and mixed, so it supersedes it instead of competing.
                if (vm.newReleases.isNotEmpty()) {
                    item(key = "releases") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("New from artists you follow")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(vm.newReleases, key = { "nr_${it.browseId ?: it.videoId}" }) { r ->
                                    MediaCard(
                                        title = r.title,
                                        subtitle = r.artists.joinToString().ifBlank { r.year ?: r.category },
                                        artworkUrl = r.thumbnails.firstOrNull()?.url,
                                        round = false
                                    ) { openRoute(r)?.let { nav.navigate(it) } }
                                }
                            }
                        }
                    }
                }
                if (vm.similarArtists.isNotEmpty()) {
                    item(key = "similar") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Fans also like")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(vm.similarArtists, key = { "sa_${it.browseId}" }) { r ->
                                    MediaCard(
                                        title = r.title,
                                        subtitle = "Artist",
                                        artworkUrl = r.thumbnails.firstOrNull()?.url,
                                        round = true,
                                        size = 124.dp
                                    ) {
                                        r.browseId?.let { nav.navigateArtist(it) }
                                    }
                                }
                            }
                        }
                    }
                } else if (vm.discoveryShelfLoading) {
                    item(key = "discovery-loading") { ShelfLoading() }
                }
                if (saved.isNotEmpty()) {
                    item(key = "saved") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("Saved collections")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(saved, key = { it.browseId }) { s ->
                                    MediaCard(
                                        title = s.title,
                                        subtitle = s.subtitle ?: s.trackCountText ?: "",
                                        artworkUrl = s.artworkUrl,
                                        round = false
                                    ) { openSaved(s) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Search for music and it will show up here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Button(onClick = onSearch) { Text("Search") }
    }
}
