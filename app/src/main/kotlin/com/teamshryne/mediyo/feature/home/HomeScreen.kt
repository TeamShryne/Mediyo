package com.teamshryne.mediyo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.core.design.openRoute
import com.teamshryne.mediyo.core.design.rememberGreeting
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeVm @Inject constructor(
    private val repo: HomeRepository,
    private val bridge: MediyoBridge,
    private val events: UserEventRepository
) : ViewModel() {
    val quickPicks = repo.flowQuickPicks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val weekTop = repo.flowWeekTop()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val onRepeat = repo.flowOnRepeat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rediscover = repo.flowRediscover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val topArtists = repo.flowTopArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mixes = repo.flowMixSeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val episodes = repo.flowEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentSearches = repo.flowRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val latestSearch = repo.flowLatestSearch()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val weekStats = repo.flowWeekStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekStats(0, 0))
    val finishAlbums = repo.flowFinishAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val daypart = repo.flowDaypart()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DaypartShelves(emptyList(), emptyList()))
    val saved = repo.flowSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedCount = repo.flowLikedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val playlistCount = repo.flowPlaylistCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Lazy network shelves: load once, hide silently on failure ──────────
    // Home must open instantly offline; these fill in below as they arrive.
    var radioShelf by mutableStateOf<RadioShelf?>(null)
    var radioLoading by mutableStateOf(true)
    var newReleases by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var similarArtists by mutableStateOf<List<FfiSearchResult>>(emptyList())
    var discoveryLoading by mutableStateOf(true)

    init {
        // Because-you-played: two-hop radio off the top scored track,
        // known tracks filtered so every card is actually new.
        viewModelScope.launch {
            try {
                val seed = repo.flowScored().first().firstOrNull()?.track
                val vid = seed?.videoId
                if (vid != null) {
                    val known = repo.flowKnownIds().first()
                    val q = bridge.radioFor(vid)
                    val tracks = (q?.items ?: emptyList()).map { it.toDomainTrack() }
                        .filter { it.videoId != null && it.videoId !in known }
                        // The queue can repeat a video; a duplicate Lazy key
                        // crashes the row, so dedupe before it reaches the UI.
                        .distinctBy { it.videoId }
                        .take(12)
                    if (tracks.isNotEmpty()) radioShelf = RadioShelf(seed.title, vid, tracks)
                }
            } catch (_: Throwable) {
            } finally {
                radioLoading = false
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
                discoveryLoading = false
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
    val context = LocalContext.current
    val menuVm: com.teamshryne.mediyo.core.design.MediaMenuVm = hiltViewModel()
    var resolving by remember { mutableStateOf<String?>(null) }
    val quickPicks by vm.quickPicks.collectAsState()
    val weekTop by vm.weekTop.collectAsState()
    val onRepeat by vm.onRepeat.collectAsState()
    val rediscover by vm.rediscover.collectAsState()
    val topArtists by vm.topArtists.collectAsState()
    val mixes by vm.mixes.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val recentSearches by vm.recentSearches.collectAsState()
    val latestSearch by vm.latestSearch.collectAsState()
    val weekStats by vm.weekStats.collectAsState()
    val finishAlbums by vm.finishAlbums.collectAsState()
    val daypart by vm.daypart.collectAsState()
    val saved by vm.saved.collectAsState()
    val likedCount by vm.likedCount.collectAsState()
    val playlistCount by vm.playlistCount.collectAsState()
    val playingId = player.state.collectAsState().value.videoId

    fun play(t: Track, origin: PlayOrigin) {
        vm.logTap(t)
        if (playingId != null && playingId == t.videoId) player.toggle()
        else player.playTrack(t, origin)
    }

    fun openSaved(item: SavedCollectionEntity) {
        vm.logSavedTap(item)
        when (item.kind) {
            SavedCollectionEntity.ALBUM -> nav.navigate("album/${item.browseId}")
            SavedCollectionEntity.PODCAST -> nav.navigate("podcast/${item.browseId}")
            else -> nav.navigate("playlist/${item.browseId}")
        }
    }

    // Context shelf follows the clock; outside morning/night the evergreen
    // night shelf still beats an empty slot.
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val daypartTitle: String?
    val daypartTracks: List<Track>
    if (hour in 5..10 && daypart.morning.isNotEmpty()) {
        daypartTitle = "Morning light"
        daypartTracks = daypart.morning
    } else if ((hour >= 21 || hour < 5) && daypart.night.isNotEmpty()) {
        daypartTitle = "After hours"
        daypartTracks = daypart.night
    } else if (daypart.night.isNotEmpty()) {
        daypartTitle = "After hours"
        daypartTracks = daypart.night
    } else {
        daypartTitle = null
        daypartTracks = emptyList()
    }

    val hasAnything = quickPicks.isNotEmpty() || weekTop.isNotEmpty() || saved.isNotEmpty() ||
        recentSearches.isNotEmpty() || mixes.isNotEmpty() || episodes.isNotEmpty()

    LazyColumn(
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

        if (!hasAnything && !vm.radioLoading && !vm.discoveryLoading) {
            item { EmptyHome { nav.navigate("search") } }
        } else {
            if (quickPicks.isNotEmpty()) {
                item(key = "quick") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Quick picks")
                        QuickPickGrid(quickPicks) { play(it, PlayOrigin.History("Home")) }
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
                            items(mixes, key = { "mix_${it.artistName}_${mixes.indexOf(it)}" }) { m ->
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
                        SectionHeader("On repeat")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(onRepeat, key = { it.uniqueKey() }) { t ->
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
                                nav.navigate("artist/$id")
                            } else {
                                scope.launch {
                                    resolving = a.name
                                    val resolved = try {
                                        menuVm.resolveArtistIdByName(a.name)
                                    } catch (_: Throwable) {
                                        null
                                    }
                                    resolving = null
                                    if (resolved != null) nav.navigate("artist/$resolved")
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
                        SectionHeader("Rediscover")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(rediscover, key = { it.uniqueKey() }) { t ->
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
                        DaypartHeader(daypartTitle, night = daypartTitle == "After hours")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(daypartTracks, key = { it.uniqueKey() }) { t ->
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
                            AlbumProgressRow(a) { nav.navigate("album/${a.browseId}") }
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
            val shelf = vm.radioShelf
            if (shelf != null) {
                item(key = "radio") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Because you played ${shelf.seedTitle}")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(shelf.tracks, key = { it.uniqueKey() }) { t ->
                                MediaCard(
                                    title = t.title,
                                    subtitle = t.artists.joinToString(),
                                    artworkUrl = t.artworkUrl,
                                    round = false
                                ) {
                                    vm.logTap(t)
                                    player.playTracks(
                                        shelf.tracks,
                                        shelf.tracks.indexOf(t).coerceAtLeast(0),
                                        PlayOrigin.Radio(shelf.seedVideoId ?: t.videoId.orEmpty())
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (vm.radioLoading) {
                item(key = "radio-loading") { ShelfLoading() }
            }
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
                                    r.browseId?.let { nav.navigate("artist/$it") }
                                }
                            }
                        }
                    }
                }
            } else if (vm.discoveryLoading) {
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

@Composable
private fun EmptyHome(onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Search for music and it will show up here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Button(onClick = onSearch) { Text("Search") }
    }
}
