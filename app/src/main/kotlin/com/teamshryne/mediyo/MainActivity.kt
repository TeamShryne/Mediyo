package com.teamshryne.mediyo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.core.design.LocalOverlayBottom
import com.teamshryne.mediyo.core.design.GlobalSheets
import com.teamshryne.mediyo.core.design.SheetHostVm
import com.teamshryne.mediyo.core.design.EaseIn
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.MediyoTheme
import com.teamshryne.mediyo.core.design.Motion
import com.teamshryne.mediyo.core.design.popEnter
import com.teamshryne.mediyo.data.appearance.PlayerOpenStyle
import com.teamshryne.mediyo.data.appearance.TabStyle
import com.teamshryne.mediyo.core.design.popExit
import com.teamshryne.mediyo.core.design.pushEnter
import com.teamshryne.mediyo.core.design.pushExit
import com.teamshryne.mediyo.core.design.sheetEnter
import com.teamshryne.mediyo.core.design.sheetExit
import com.teamshryne.mediyo.core.design.tabEnter
import com.teamshryne.mediyo.core.design.tabExit
import com.teamshryne.mediyo.feature.album.AlbumScreen
import com.teamshryne.mediyo.feature.artist.ArtistScreen
import com.teamshryne.mediyo.feature.channel.ChannelScreen
import com.teamshryne.mediyo.feature.debug.NotificationReportScreen
import com.teamshryne.mediyo.feature.episode.EpisodeScreen
import com.teamshryne.mediyo.feature.history.HistoryScreen
import com.teamshryne.mediyo.feature.home.HomeScreen
import com.teamshryne.mediyo.feature.library.LibraryScreen
import com.teamshryne.mediyo.feature.library.LikedScreen
import com.teamshryne.mediyo.feature.library.LocalPlaylistDetailScreen
import com.teamshryne.mediyo.feature.section.SectionScreen
import com.teamshryne.mediyo.feature.player.FullPlayer
import com.teamshryne.mediyo.feature.player.MiniPlayer
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.feature.playlist.PlaylistScreen
import com.teamshryne.mediyo.feature.podcast.PodcastScreen
import com.teamshryne.mediyo.feature.profile.ProfileScreen
import com.teamshryne.mediyo.feature.queue.QueueScreen
import com.teamshryne.mediyo.feature.search.SearchScreen
import com.teamshryne.mediyo.feature.search.SearchVm
import com.teamshryne.mediyo.feature.settings.AppearanceScreen
import com.teamshryne.mediyo.feature.settings.AppearanceVm
import com.teamshryne.mediyo.feature.settings.PlayerBackgroundScreen
import com.teamshryne.mediyo.feature.settings.TabBarStyleScreen
import com.teamshryne.mediyo.feature.settings.LyricsAppearanceScreen
import com.teamshryne.mediyo.feature.settings.LyricsSettingsScreen
import com.teamshryne.mediyo.feature.settings.MotionScreen
import com.teamshryne.mediyo.feature.settings.PlayerMotionScreen
import com.teamshryne.mediyo.feature.settings.SettingsScreen
import com.teamshryne.mediyo.feature.update.UpdateDialog
import com.teamshryne.mediyo.feature.update.UpdateViewModel
import com.teamshryne.mediyo.feature.equalizer.EqualizerScreen
import com.teamshryne.mediyo.feature.downloads.DownloadsScreen
import com.teamshryne.mediyo.feature.downloads.StorageScreen
import com.teamshryne.mediyo.core.design.navigateArtist
import com.teamshryne.mediyo.core.design.navigateChannel
import com.teamshryne.mediyo.core.design.navigatePodcast
import com.teamshryne.mediyo.feature.update.UpdatesSettingsScreen
import com.teamshryne.mediyo.core.design.isAlbum
import com.teamshryne.mediyo.core.design.isArtist
import com.teamshryne.mediyo.core.design.isEpisode
import com.teamshryne.mediyo.core.design.isPlaylist
import com.teamshryne.mediyo.core.design.isPodcast
import com.teamshryne.mediyo.debug.AdbBridgeEntryPoint
import com.teamshryne.mediyo.debug.AdbCommand
import com.teamshryne.mediyo.debug.AdbControl
import com.teamshryne.mediyo.domain.model.ART_HERO_PX
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.thumbSized
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint

sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Home)
    data object Search : Tab("search", "Search", Icons.Filled.Search)
    data object Library : Tab("library", "Library", Icons.Filled.LibraryMusic)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

/** Sibling tabs cross-dissolve; every other route is a push/pop. */
private fun isTabRoute(route: String?): Boolean =
    route == Tab.Home.route || route == Tab.Search.route ||
        route == Tab.Library.route || route == Tab.Settings.route

/** Tab bar + mini-player live in Scaffold's bottomBar as a Column, so the
 *  player is *laid out* above the tab bar — never overlapped, on any device.
 *  (A hardcoded dp lift can't work everywhere: NavigationBar height shifts
 *  with font scale, and the system gesture/3-button inset varies per device.) */


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediyoTheme {
                AppShell()
            }
        }
    }
}

@Composable
private fun AppShell() {
    val nav = rememberNavController()
    val tabs = listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings)
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Tab bar is only visible on the 4 top-level destinations.
    // All detail/sub pages (artist, playlist, album, view-all/list, liked,
    // history, settings sub-screens, profile, etc.) hide it.
    val showTabBar = currentRoute in tabs.map { it.route }

    // Media notifications need the notification permission on Android 13+
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val playerVm: PlayerViewModel = hiltViewModel()
    // Single writer of the system bar colours for every sheet host in the app.
    com.teamshryne.mediyo.core.design.SheetSystemBars()
    val playerState by playerVm.state.collectAsState()
    // All bottom sheets render at root (GlobalSheets) so their dim covers
    // the full window including system-bar regions.
    val sheetVm: SheetHostVm = hiltViewModel()
    // Silent update check once per launch — dialog appears only if Available.
    // Disabled entirely in debug builds.
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()
    LaunchedEffect(Unit) { if (!BuildConfig.DEBUG) updateVm.silentCheck() }
    val sleepState by playerVm.sleepState.collectAsState()
    var showQueueOverlay by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    // One 0..1 value for the mini player ↔ full player pair. The drag, the
    // predictive back gesture and the chosen open style all write it, so the
    // preview under the finger and the animation that follows cannot drift
    // apart. Reads live inside graphics layers (never in composition), so a
    // drag redraws instead of recomposing the app.
    val playerProgress = remember { mutableFloatStateOf(0f) }
    var playerMounted by remember { mutableStateOf(false) }
    var playerJob by remember { mutableStateOf<Job?>(null) }
    // Root-coordinate rectangle of the pill's cover: where the flight starts.
    var miniArtRect by remember { mutableStateOf<Rect?>(null) }
    var rootHeightPx by remember { mutableIntStateOf(0) }

    val contextLabel = if (playerState.originLabel.isNotBlank() && playerState.title.isNotEmpty()) {
        playerState.originLabel
    } else when {
        currentRoute == null -> "Mediyo"
        currentRoute.startsWith("album/") -> "Album"
        currentRoute.startsWith("artist/") -> "Artist"
        currentRoute.startsWith("playlist/") -> "Playlist"
        currentRoute.startsWith("localPlaylist/") -> "Playlist"
        currentRoute.startsWith("liked") -> "Liked"
        currentRoute.startsWith("history") -> "History"
        currentRoute.startsWith("section/") -> "Playlist"
        currentRoute.startsWith("channel/") -> "Channel"
        else -> tabs.firstOrNull { it.route == currentRoute }?.label ?: "Mediyo"
    }

    // Hoisted out of SearchScreen so the search state survives tab switches and
    // can be driven from adb (see the AdbControl collector below).
    val searchVm: SearchVm = hiltViewModel()
    val appearanceVm: AppearanceVm = hiltViewModel()
    val tabStyle by appearanceVm.style.collectAsState()
    val livelyMotion by appearanceVm.motion.collectAsState()
    val screenStyle by appearanceVm.screenStyle.collectAsState()
    val playerOpenStyle by appearanceVm.playerOpenStyle.collectAsState()
    val density = LocalDensity.current
    var tabBarH by remember { mutableIntStateOf(0) }
    var pillH by remember { mutableIntStateOf(0) }

    // Shared by the tap, the drag release and the predictive-back cancel.
    // Cancelling the previous job first matters: a close that is mid-flight
    // must not finish and unmount the player that a new open just mounted.
    val playerScope = rememberCoroutineScope()
    fun settlePlayer(open: Boolean) {
        if (open && playerState.title.isEmpty()) return
        playerJob?.cancel()
        if (open) {
            playerMounted = true
        }
        playerJob = playerScope.launch {
            animate(
                playerProgress.floatValue,
                if (open) 1f else 0f,
                animationSpec = tween(
                    if (open) Motion.SheetEnter else Motion.SheetExit,
                    easing = if (open) EaseOutExpo else EaseIn
                )
            ) { value, _ -> playerProgress.floatValue = value }
            if (!open) {
                playerMounted = false
            }
        }
    }
    // A full-screen drag is too long: opening over ~55% of the height keeps
    // the gesture inside one comfortable thumb sweep.
    val dragRangePx = with(density) {
        (rootHeightPx * 0.55f).coerceAtLeast(320.dp.toPx())
    }
    // Queue cleared while the player is open: do not leave it mounted with
    // nothing behind it, and do not let a later back press try to close it.
    LaunchedEffect(playerState.title.isEmpty()) {
        if (playerState.title.isEmpty()) {
            playerJob?.cancel()
            playerProgress.floatValue = 0f
            playerMounted = false
        }
    }
    // Warm the big cover the moment a track becomes the now-playing one. The
    // player asks for the same URL under the same memory-cache key, so by the
    // time it opens the bitmap is decoded: the cover cannot blink in, and the
    // artwork flight has real pixels on its first frame.
    val imageLoader = context.imageLoader
    LaunchedEffect(playerState.artwork) {
        val url = playerState.artwork.thumbSized(ART_HERO_PX) ?: return@LaunchedEffect
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("hero-art:$url")
                .build()
        )
    }
    // Space the floating pill needs: measured live, 82dp estimate on the
    // very first frame so content never jumps once measured.
    val pillReserve = (if (pillH > 0) with(density) { pillH.toDp() } else 82.dp) + 8.dp
    // Dismissing the keyboard here (not just navigating) matters: with the
    // keyboard up, the first tap outside the field is eaten to close it, so
    // without this the tab press would silently do nothing.
    val focusManager = LocalFocusManager.current
    fun selectTab(t: Tab) {
        focusManager.clearFocus()
        nav.navigate(t.route) {
            launchSingleTop = true
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }

    // Leaving for another screen: no collapse animation, the destination
    // slides in over the player immediately.
    fun dismissPlayerNow() {
        playerJob?.cancel()
        playerProgress.floatValue = 0f
        playerMounted = false
    }

    // Back while the player is open collapses it, never pops the nav stack.
    // PredictiveBackHandler rather than BackHandler so the gesture pulls the
    // player down with the finger; it is composed after the nav-level handler,
    // and the dispatcher gives the most recent one the gesture, so this keeps
    // its priority over the screen transition above.
    val playerBackEnabled = playerMounted && !showQueueOverlay

    // adb control — debug builds only. Nothing in a release APK can emit on
    // AdbControl (its receiver is declared in src/debug only), and the
    // constant-false BuildConfig.DEBUG here lets R8 drop all of it.
    if (BuildConfig.DEBUG) {
        val adbScope = rememberCoroutineScope()
        val appContext = context.applicationContext
        LaunchedEffect(Unit) {
            AdbControl.commands.collect { cmd ->
                if (!AdbControl.isFresh(cmd)) {
                    AdbControl.log("dropped stale command: $cmd")
                    return@collect
                }
                when (cmd) {
                    is AdbCommand.Go -> {
                        // navigate throws on an unknown route rather than
                        // failing silently, so an agent finds out either way.
                        val err = runCatching { nav.navigate(cmd.route) }.exceptionOrNull()
                        AdbControl.log(
                            if (err == null) "navigated to ${cmd.route}"
                            else "no route '${cmd.route}': ${err.message}"
                        )
                    }

                    is AdbCommand.Search -> {
                        selectTab(Tab.Search)
                        searchVm.query = cmd.query
                        searchVm.submit()
                        AdbControl.log("search '${cmd.query}' submitted")
                    }

                    is AdbCommand.Play ->
                        adbScope.launch { playFromAdb(appContext, playerVm, cmd) }

                    is AdbCommand.Sheet -> {
                        // Sheets are the easiest thing to regress without noticing,
                        // so give adb a way to open each one for a screenshot.
                        val track = playerVm.currentTrack()
                        when (cmd.target) {
                            "menu" -> if (track == null) {
                                AdbControl.log("sheet menu needs something in the queue")
                            } else {
                                sheetVm.showTrackMenu(
                                    com.teamshryne.mediyo.core.design.TrackMenuRequest(
                                        track = track,
                                        onComments = track.videoId?.let { { sheetVm.showComments(it) } },
                                        onAddToPlaylist = { sheetVm.openAddToPlaylist(track) }
                                    )
                                )
                                AdbControl.log("sheet menu opened for '${track.title}'")
                            }

                            "comments" -> {
                                val vid = track?.videoId
                                if (vid == null) AdbControl.log("sheet comments needs a videoId")
                                else {
                                    sheetVm.showComments(vid)
                                    AdbControl.log("sheet comments opened for $vid")
                                }
                            }

                            "media" -> {
                                // The search-result overflow menu. There is no
                                // search result to hand it, so synthesise one
                                // from whatever is playing.
                                val t = track
                                if (t == null) {
                                    AdbControl.log("sheet media needs something in the queue")
                                } else {
                                    sheetVm.showMediaMenu(
                                        com.teamshryne.mediyo.core.design.MediaMenuRequest(
                                            item = com.teamshryne.mediyo.data.mediyo.FfiSearchResult(
                                                title = t.title,
                                                videoId = t.videoId,
                                                browseId = null,
                                                browseParams = null,
                                                playlistId = null,
                                                category = "Song",
                                                year = null,
                                                duration = t.duration,
                                                explicit = false,
                                                thumbnails = emptyList(),
                                                artists = t.artists,
                                                artistIds = t.artistIds,
                                                album = t.album,
                                                albumId = null,
                                                info = null,
                                                isTopResult = false,
                                                channelName = null,
                                                channelId = null
                                            ),
                                            nav = nav,
                                            onPlayTracks = { tracks, idx, origin -> playerVm.playTracks(tracks, idx, origin) },
                                            onEnqueueTracks = { playerVm.addToQueueList(it) },
                                            onAddToPlaylist = { playerVm.addNext(it) },
                                            onPlayNext = { playerVm.addNext(it) },
                                            onAddToQueue = { playerVm.addToQueue(it) },
                                            onComments = { playerVm.currentTrack()?.videoId?.let { sheetVm.showComments(it) } }
                                        )
                                    )
                                    AdbControl.log("sheet media opened for '${t.title}'")
                                }
                            }

                            "playlist" -> if (track == null) {
                                AdbControl.log("sheet playlist needs something in the queue")
                            } else {
                                sheetVm.openAddToPlaylist(track)
                                AdbControl.log("sheet add-to-playlist opened")
                            }

                            "sleep" -> {
                                showSleepSheet = true
                                AdbControl.log("sheet sleep timer opened")
                            }

                            "lyrics" -> if (track == null) {
                                AdbControl.log("sheet lyrics needs something in the queue")
                            } else {
                                sheetVm.showLyricsSearch(
                                    track,
                                    playerState.durationMs.takeIf { it > 0 }
                                )
                                AdbControl.log("sheet lyrics search opened for '${track.title}'")
                            }
                        }
                    }

                    is AdbCommand.Overlay -> when (cmd.target) {
                        "player" -> {
                            settlePlayer(true)
                            AdbControl.log("overlay player opened")
                        }
                        "queue" -> {
                            showQueueOverlay = true
                            AdbControl.log("overlay queue opened")
                        }
                    }

                    is AdbCommand.Resolve ->
                        adbScope.launch { resolveIds(appContext, cmd.query) }

                    is AdbCommand.Dump -> AdbControl.log(
                        "dump title=${playerState.title} artist=${playerState.artist} " +
                            "playing=${playerState.isPlaying} queue=${playerState.queueSize} " +
                            "index=${playerState.queueIndex} shuffle=${playerState.shuffle} " +
                            "liked=${playerState.liked} origin=${playerState.originLabel}"
                    )
                }
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background
    // The tab bar floats with no scrim: any gradient behind it reads as a
    // tinted halo around the pill, so the overlay stays fully transparent
    // and page content shows through untouched.
    val pillScrim = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.65f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.3f)
        )
    }
    // Full-bleed content: overlays float above it (positioned from the
    // measured bar/pill heights — never hardcoded). Lists consume
    // overlayBottom below so last items stay reachable.
    val barReserve = if (showTabBar && tabBarH > 0) with(density) { tabBarH.toDp() } else 0.dp
    // Scrollable clearance for lists: overlays float over full-bleed
    // content, so screens add this to their list bottom padding — the last
    // item stays reachable while content lives behind the bars (visible
    // through their translucency). BarReserve/pillReserve still position
    // the overlays themselves (see below).
    val sysBottom = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
    val pillPart = if (playerState.title.isNotEmpty()) pillReserve else 0.dp
    val overlayBottom = sysBottom + barReserve + pillPart

    Box(Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height }) {
        Scaffold(
            containerColor = bg
        ) { pad ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                CompositionLocalProvider(LocalOverlayBottom provides overlayBottom) {
                // Predictive back is the NavHost's own now: it drives the pop
                // transitions below with the gesture progress, so the screen
                // you are leaving moves away and the one you are returning to
                // comes up under it, in whatever style Motion is set to.
                NavHost(
                    navController = nav,
                    startDestination = Tab.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    // Push/pop get depth (slide + scale + fade); sibling tabs
                    // get a cross-dissolve instead of pretending they are
                    // pages in a horizontal stack. See Motion.kt.
                    enterTransition = {
                        when {
                            !livelyMotion -> EnterTransition.None
                            isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) -> tabEnter()
                            else -> pushEnter(screenStyle)
                        }
                    },
                    exitTransition = {
                        when {
                            !livelyMotion -> ExitTransition.None
                            isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) -> tabExit()
                            else -> pushExit(screenStyle)
                        }
                    },
                    popEnterTransition = {
                        when {
                            !livelyMotion -> EnterTransition.None
                            isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) -> tabEnter()
                            else -> popEnter(screenStyle)
                        }
                    },
                    popExitTransition = {
                        when {
                            !livelyMotion -> ExitTransition.None
                            isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) -> tabExit()
                            else -> popExit(screenStyle)
                        }
                    }
                ) {
            composable(Tab.Home.route) { HomeScreen(nav, playerVm) }
            composable(Tab.Search.route) { SearchScreen(nav, playerVm, searchVm) }
            composable(Tab.Library.route) { LibraryScreen(nav, playerVm) }
            composable(Tab.Settings.route) { SettingsScreen(nav) }
            composable("settings/content/lyrics") { LyricsSettingsScreen(nav) }
            // The updater's view model is the one the app shell holds, so
            // the screen and the startup check share a single state: a
            // check made here and the one made at launch cannot disagree
            // about what the newest build is.
            composable("settings/updates") { UpdatesSettingsScreen(nav, updateVm) }
            composable("playlist/{id}") { PlaylistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("album/{id}") { AlbumScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("artist/{id}") { ArtistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("podcast/{id}") { PodcastScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("episode/{id}") { EpisodeScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("channel/{id}") { ChannelScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable(
                route = "section/{kind}/{id}?params={params}&title={title}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                    navArgument("params") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                SectionScreen(
                    kind = it.arguments?.getString("kind") ?: "artist",
                    browseId = it.arguments?.getString("id") ?: "",
                    params = it.arguments?.getString("params"),
                    title = it.arguments?.getString("title"),
                    nav = nav,
                    player = playerVm
                )
            }
            composable("localPlaylist/{id}") { LocalPlaylistDetailScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
            composable("liked") { LikedScreen(nav, playerVm) }
            composable("history") { HistoryScreen(nav, playerVm) }
            composable("profile") { ProfileScreen(nav) }
            composable("settings/appearance") { AppearanceScreen(nav) }
            composable("settings/equalizer") { EqualizerScreen(nav) }
            composable("settings/storage") { StorageScreen(nav) }
            composable("downloads") { DownloadsScreen(nav, playerVm) }
            composable("settings/appearance/tabs") { TabBarStyleScreen(nav) }
            composable("settings/appearance/player") { PlayerBackgroundScreen(nav) }
            composable("settings/appearance/player-motion") { PlayerMotionScreen(nav) }
            composable("settings/appearance/lyrics") { LyricsAppearanceScreen(nav) }
            composable("settings/appearance/motion") { MotionScreen(nav) }
            // Debug-only route: a release build cannot reach a screen
            // that does not exist in its graph.
            if (BuildConfig.DEBUG) {
                composable("settings/debug/notification") { NotificationReportScreen(nav) }
            }
            }
                }
            }
        }

        // Tab bar overlay — fully transparent surroundings, the pill floats
        // alone with no scrim halo (page content stays visible around it).
        if (showTabBar) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
            ) {
                // 52dp fade zone above the bar: content shows through.
                Column(Modifier.padding(top = 52.dp)) {
                    Box(Modifier.fillMaxWidth().onSizeChanged { tabBarH = it.height }) {
                        MediyoTabBar(
                            style = tabStyle,
                            tabs = tabs,
                            currentRoute = currentRoute,
                            onSelect = ::selectTab
                        )
                    }
                }
            }
        }

        // Floating mini player — overlays page content with transparent
        // surroundings (no solid strip around the pill). Lifts above the
        // measured tab-bar height + an 8dp gap, or floats on the system
        // inset alone when the bar is hidden.
        val sleepBadge = when {
            !sleepState.isActive -> null
            sleepState.mode.name == "TIMER" -> {
                val s = sleepState.remainingMs / 1000
                val txt = if (s >= 3600) "%d:%02d:%02d".format(s/3600, (s%3600)/60, s%60) else "%02d:%02d".format(s/60, s%60)
                "Sleep • $txt"
            }
            sleepState.mode.name == "END_OF_TRACK" -> "Sleep after track"
            sleepState.mode.name == "END_OF_QUEUE" -> "Sleep after queue"
            else -> null
        }
        AnimatedVisibility(
            visible = playerState.title.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Offset lifts the scrim above the tab bar; the scrim itself
            // covers only the pill + a fade zone, with a subtle dark tint.
            Box(
                modifier = Modifier.padding(
                    bottom = (if (showTabBar && tabBarH > 0) {
                        with(density) { tabBarH.toDp() }
                    } else 0.dp) + 8.dp
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(pillScrim)
                        // Same as the tab bar above: navigationBars keeps the
                        // pill pinned behind the keyboard instead of riding it.
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                        .padding(top = 40.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            .onSizeChanged { pillH = it.height }
                            // Drag the pill up and the player opens under the
                            // finger — every frame is the same progress the
                            // release animation starts from.
                            .pointerInput(dragRangePx) {
                                detectVerticalDragGestures(
                                    onDragStart = { playerJob?.cancel() },
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        val next = (playerProgress.floatValue - dy / dragRangePx)
                                            .coerceIn(0f, 1f)
                                        if (next > 0f) playerMounted = true
                                        playerProgress.floatValue = next
                                    },
                                    onDragEnd = { settlePlayer(playerProgress.floatValue > 0.32f) },
                                    onDragCancel = { settlePlayer(playerProgress.floatValue > 0.32f) }
                                )
                            }
                            // The pill leaves the stage as the player takes it
                            // over; on the way back it fades in under the
                            // returning cover.
                            .graphicsLayer { alpha = 1f - playerProgress.floatValue }
                    ) {
                        MiniPlayer(
                            state = playerState,
                            onToggle = playerVm::toggle,
                            onNext = playerVm::next,
                            onExpand = { settlePlayer(true) },
                            sleepBadge = sleepBadge,
                            onArtworkBounds = { miniArtRect = it }
                        )
                    }
                }
            }
        }

        // Immersive full-screen player overlay — consumes clicks & back.
        // Mounted while opening, open or closing: the progress value owns the
        // motion, so the drag, the tap and the back gesture share one path.
        if (playerMounted && playerState.title.isNotEmpty()) {
            PredictiveBackHandler(enabled = playerBackEnabled) { backEvents ->
                try {
                    backEvents.collect { event ->
                        // The finger pulls the player down in real time.
                        playerJob?.cancel()
                        playerProgress.floatValue = (1f - event.progress).coerceIn(0f, 1f)
                    }
                    // Committed: finish the last little bit of the collapse
                    // from wherever the finger left it. A non-predictive back
                    // (Android 12 and older) reports no progress, so this is
                    // also the normal close animation there.
                    settlePlayer(false)
                } catch (_: CancellationException) {
                    // Gesture cancelled: spring back open from where it was.
                    settlePlayer(true)
                }
            }
            FullPlayer(
                state = playerState,
                contextLabel = contextLabel,
                onToggle = playerVm::toggle,
                onNext = playerVm::next,
                onPrevious = playerVm::previous,
                onSeek = playerVm::seekTo,
                onToggleShuffle = playerVm::toggleShuffle,
                onToggleRepeat = playerVm::toggleRepeat,
                onCollapse = { settlePlayer(false) },
                onShowQueue = { showQueueOverlay = true },
                onShowComments = { playerState.videoId?.let { sheetVm.showComments(it) } },
                onShowSleepTimer = { showSleepSheet = true },
                onGoToArtist = { dismissPlayerNow(); nav.navigateArtist(it) },
                onOpenChannel = { dismissPlayerNow(); nav.navigateChannel(it) },
                onOpenPodcast = { dismissPlayerNow(); nav.navigatePodcast(it) },
                playerVm = playerVm,
                progress = { playerProgress.floatValue },
                openStyle = playerOpenStyle,
                miniArt = miniArtRect,
                // Dragging the open player down folds it back into the pill on
                // the same progress value the animation uses, so the finger
                // and the release can never disagree.
                onDragTo = { playerJob?.cancel(); playerProgress.floatValue = it },
                onDragSettle = { open -> settlePlayer(open) }
            )
        }

        // Queue overlay — sits ON TOP of the full player, same way the player
        // sits on top of the app. Closing it reveals the player underneath.
        // Its BackHandler is composed after the player's, so back closes the
        // queue first and returns to the player.
        AnimatedVisibility(
            visible = showQueueOverlay,
            enter = sheetEnter(),
            exit = sheetExit(),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = true) { showQueueOverlay = false }
            QueueScreen(
                player = playerVm,
                onClose = { showQueueOverlay = false },
                onShowComments = { vid -> sheetVm.showComments(vid) },
                onShowSleepTimer = { showSleepSheet = true },
                onGoToArtist = { showQueueOverlay = false; nav.navigateArtist(it) }
            )
        }

        GlobalSheets(sheetVm)

        if (!BuildConfig.DEBUG && updateState is UpdateViewModel.State.Available) {
            UpdateDialog(updateVm)
        }

        if (showSleepSheet) {
            com.teamshryne.mediyo.core.design.SheetStatusBar(open = true, key = "sleep")
            com.teamshryne.mediyo.feature.sleeptimer.SleepTimerSheet(
                state = sleepState,
                onSetTimer = { ms -> playerVm.sleepState.value // trigger via manager directly through vm
                    // use vm helpers via exposed manager: we call sleep manager via playerVm
                    // add helpers in PlayerViewModel for convenience
                    playerVm.setSleepTimer(ms)
                    showSleepSheet = false
                },
                onSetEndOfTrack = { playerVm.setSleepEndOfTrack(); showSleepSheet = false },
                onSetEndOfQueue = { playerVm.setSleepEndOfQueue(); showSleepSheet = false },
                onCancel = { playerVm.cancelSleepTimer(); showSleepSheet = false },
                onAddFive = { playerVm.extendSleepTimer() },
                onDismiss = { showSleepSheet = false }
            )
        }
    }
}

/**
 * Search and report the browse ids of the top hits, so a caller can navigate
 * to the screens that need one (artist, album, playlist) without guessing.
 *
 * One `resolve <kind> id=<id> title=<title>` line per hit, on the MediyoAdb
 * tag. Ids are grouped by kind and only the first few of each are reported —
 * enough to drive a screenshot tour, not a dump of every result.
 */
private suspend fun resolveIds(appContext: Context, query: String) {
    try {
        val bridge = EntryPointAccessors
            .fromApplication(appContext, AdbBridgeEntryPoint::class.java)
            .bridge()
        val results = bridge.search(query).results
        val kinds = listOf("artist", "album", "playlist", "podcast", "channel", "song")
        for (kind in kinds) {
            val hits = results.filter { r ->
                val b = r.browseId
                when (kind) {
                    "artist" -> b != null && (r.isArtist() || r.pageType.contains("ARTIST"))
                    "album" -> b != null && (r.isAlbum() || r.pageType.contains("ALBUM"))
                    "playlist" -> b != null && (r.isPlaylist() || r.pageType.contains("PLAYLIST"))
                    "podcast" -> b != null && (r.isPodcast() || r.pageType.contains("PODCAST"))
                    "channel" -> b != null && r.pageType.contains("USER_CHANNEL")
                    "song" -> r.videoId != null && !r.isEpisode()
                    else -> false
                }
            }.take(2)
            for (h in hits) {
                val id = if (kind == "song") h.videoId else h.browseId
                if (!id.isNullOrBlank()) {
                    AdbControl.log("resolve $kind id=$id title=${h.title}")
                }
            }
        }
        AdbControl.log("resolve done for '$query' (${results.size} results)")
    } catch (t: Throwable) {
        AdbControl.log("resolve failed: ${t.message}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// adb control
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Backs `am broadcast --es cmd play`. With a query it searches, queues every
 * song it finds (so the player has somewhere to go on `next`) and starts the
 * best match; with a videoId it plays that id directly.
 *
 * The title is unknown for the videoId path unless the caller supplies one —
 * there is no id -> metadata call in the core — so it falls back to the id.
 */
private suspend fun playFromAdb(
    appContext: Context,
    playerVm: PlayerViewModel,
    cmd: AdbCommand.Play
) {
    try {
        val videoId = cmd.videoId
        if (!videoId.isNullOrEmpty()) {
            val label = cmd.title ?: videoId
            playerVm.playTrack(Track(videoId = videoId, title = label), PlayOrigin.Single(videoId))
            AdbControl.log("playing videoId=$videoId as '$label'")
            return
        }

        val query = cmd.query
        if (query.isNullOrEmpty()) {
            AdbControl.log("play needs a query or a videoId")
            return
        }
        val bridge = EntryPointAccessors
            .fromApplication(appContext, AdbBridgeEntryPoint::class.java)
            .bridge()
        val songs = bridge.search(query).results.filter { it.videoId != null && !it.isEpisode() }
        if (songs.isEmpty()) {
            AdbControl.log("no playable results for '$query'")
            return
        }
        // Exact title match beats "first result" — search ranking for a song
        // name usually has it first anyway, but not for every query.
        val pick = songs.firstOrNull { it.title.trim().equals(query.trim(), ignoreCase = true) }
            ?: songs.first()
        playerVm.playFrom(songs, pick)
        AdbControl.log("playing '${pick.title}' (${songs.size} song(s) queued)")
    } catch (t: Throwable) {
        AdbControl.log("play failed: ${t.message}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab bar styles (Settings → Appearance)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MediyoTabBar(
    style: TabStyle,
    tabs: List<Tab>,
    currentRoute: String?,
    onSelect: (Tab) -> Unit
) {
    when (style) {
        TabStyle.Docked -> DockedTabBar(tabs, currentRoute, onSelect)
        TabStyle.Capsule -> CapsuleTabBar(tabs, currentRoute, onSelect)
    }
}

@Composable
private fun DockedTabBar(tabs: List<Tab>, currentRoute: String?, onSelect: (Tab) -> Unit) {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { t ->
                    val sel = currentRoute == t.route
                    if (sel) {
                        Surface(
                            onClick = { onSelect(t) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.animateContentSize()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(t.icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                Text(t.label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        androidx.compose.material3.IconButton(onClick = { onSelect(t) }, modifier = Modifier.size(44.dp)) {
                            Icon(t.icon, t.label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleTabBar(tabs: List<Tab>, currentRoute: String?, onSelect: (Tab) -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { t ->
                    val sel = currentRoute == t.route
                    Surface(
                        onClick = { onSelect(t) },
                        shape = CircleShape,
                        color = if (sel) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        modifier = Modifier.weight(1f).animateContentSize()
                    ) {
                        Row(
                            Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                t.icon, null,
                                tint = if (sel) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            if (sel) {
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    t.label,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
