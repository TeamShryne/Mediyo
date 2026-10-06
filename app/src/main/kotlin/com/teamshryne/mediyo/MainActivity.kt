package com.teamshryne.mediyo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.teamshryne.mediyo.core.design.MediyoTheme
import com.teamshryne.mediyo.core.design.popEnter
import com.teamshryne.mediyo.data.appearance.TabStyle
import com.teamshryne.mediyo.core.design.popExit
import com.teamshryne.mediyo.core.design.pushEnter
import com.teamshryne.mediyo.core.design.pushExit
import com.teamshryne.mediyo.core.design.sheetEnter
import com.teamshryne.mediyo.core.design.sheetExit
import com.teamshryne.mediyo.feature.album.AlbumScreen
import com.teamshryne.mediyo.feature.artist.ArtistScreen
import com.teamshryne.mediyo.feature.channel.ChannelScreen
import com.teamshryne.mediyo.feature.comments.CommentsBottomSheet
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
import com.teamshryne.mediyo.feature.settings.AppearanceScreen
import com.teamshryne.mediyo.feature.settings.AppearanceVm
import com.teamshryne.mediyo.feature.settings.PlayerBackgroundScreen
import com.teamshryne.mediyo.feature.settings.TabBarStyleScreen
import com.teamshryne.mediyo.feature.settings.LyricsSettingsScreen
import com.teamshryne.mediyo.feature.settings.SettingsScreen
import com.teamshryne.mediyo.feature.update.UpdateDialog
import com.teamshryne.mediyo.feature.update.UpdateViewModel
import com.teamshryne.mediyo.feature.update.UpdatesSettingsScreen
import dagger.hilt.android.AndroidEntryPoint

sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Home)
    data object Search : Tab("search", "Search", Icons.Filled.Search)
    data object Library : Tab("library", "Library", Icons.Filled.LibraryMusic)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

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
    val playerState by playerVm.state.collectAsState()
    // Silent update check once per launch — dialog appears only if Available.
    // Disabled entirely in debug builds.
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()
    LaunchedEffect(Unit) { if (!BuildConfig.DEBUG) updateVm.silentCheck() }
    val sleepState by playerVm.sleepState.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    var showQueueOverlay by remember { mutableStateOf(false) }
    var showCommentsId by remember { mutableStateOf<String?>(null) }
    var showSleepSheet by remember { mutableStateOf(false) }
    // Player collapse has priority over nav pop — both handlers, inner one wins
    BackHandler(enabled = showFullPlayer && !showQueueOverlay) { showFullPlayer = false }

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

    val appearanceVm: AppearanceVm = hiltViewModel()
    val tabStyle by appearanceVm.style.collectAsState()
    val density = LocalDensity.current
    var tabBarH by remember { mutableIntStateOf(0) }
    var pillH by remember { mutableIntStateOf(0) }
    // Space the floating pill needs: measured live, 82dp estimate on the
    // very first frame so content never jumps once measured.
    val pillReserve = (if (pillH > 0) with(density) { pillH.toDp() } else 82.dp) + 8.dp
    fun selectTab(t: Tab) {
        nav.navigate(t.route) {
            launchSingleTop = true
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val tabScrim = remember(bg) {
        Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.65f), bg))
    }
    val pillScrim = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))
    }
    // Overlays float above content: content reserves only the opaque
    // footprints, so scrolling content glides visibly behind the fades.
    val barReserve = if (showTabBar && tabBarH > 0) with(density) { tabBarH.toDp() } else 0.dp
    val pillSpace = if (playerState.title.isNotEmpty()) pillReserve else 0.dp

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = bg
        ) { pad ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(bottom = barReserve + pillSpace)
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Tab.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    // Fast dynamic slide + fade (expo/keyframe curve — see Motion.kt).
                    // Short ~32dp travel: reads as a snappy push, stays GPU-only.
                    enterTransition = { pushEnter() },
                    exitTransition = { pushExit() },
                    popEnterTransition = { popEnter() },
                    popExitTransition = { popExit() }
                ) {
                composable(Tab.Home.route) { HomeScreen(nav, playerVm) }
                composable(Tab.Search.route) { SearchScreen(nav, playerVm) }
                composable(Tab.Library.route) { LibraryScreen(nav, playerVm) }
                composable(Tab.Settings.route) { SettingsScreen(nav) }
                composable("settings/lyrics") { LyricsSettingsScreen(nav) }
                composable("settings/updates") { UpdatesSettingsScreen(nav) }
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
                composable("comments/{videoId}") { back ->
                    val vid = back.arguments?.getString("videoId") ?: ""
                    CommentsBottomSheet(videoId = vid, onDismiss = { nav.popBackStack() })
                }
                composable("settings/appearance") { AppearanceScreen(nav) }
                composable("settings/appearance/tabs") { TabBarStyleScreen(nav) }
                composable("settings/appearance/player") { PlayerBackgroundScreen(nav) }
                }
            }
        }

        // Tab bar overlay — gradient scrim fading transparent → opaque
        // top to bottom, so page content stays visible above the bar
        // (including the empty area around the dock style).
        if (showTabBar) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(tabScrim)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    )
            ) {
                // 28dp fade zone above the bar: content shows through.
                Column(Modifier.padding(top = 28.dp)) {
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
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        )
                        .padding(top = 24.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            .onSizeChanged { pillH = it.height }
                    ) {
                        MiniPlayer(
                            state = playerState,
                            onToggle = playerVm::toggle,
                            onNext = playerVm::next,
                            onExpand = { if (playerState.title.isNotEmpty()) showFullPlayer = true },
                            sleepBadge = sleepBadge
                        )
                    }
                }
            }
        }

        // Immersive full-screen player overlay — consumes clicks & back
        // Faster than before (320ms full-height felt laggy): shorter travel
        // reads as "snappy bottom sheet" and stays GPU-composited.
        AnimatedVisibility(
            visible = showFullPlayer && playerState.title.isNotEmpty(),
            enter = sheetEnter(),
            exit = sheetExit(),
            modifier = Modifier.fillMaxSize()
        ) {
            // Inner BackHandler ensures player collapses before nav pop
            BackHandler(enabled = true) { showFullPlayer = false }
            FullPlayer(
                state = playerState,
                contextLabel = contextLabel,
                onToggle = playerVm::toggle,
                onNext = playerVm::next,
                onPrevious = playerVm::previous,
                onSeek = playerVm::seekTo,
                onToggleShuffle = playerVm::toggleShuffle,
                onToggleRepeat = playerVm::toggleRepeat,
                onCollapse = { showFullPlayer = false },
                onShowQueue = { showQueueOverlay = true },
                onShowComments = { playerState.videoId?.let { showCommentsId = it } },
                onShowSleepTimer = { showSleepSheet = true },
                onGoToArtist = { showFullPlayer = false; nav.navigate("artist/$it") },
                onOpenChannel = { showFullPlayer = false; nav.navigate("channel/$it") },
                onOpenPodcast = { showFullPlayer = false; nav.navigate("podcast/$it") },
                playerVm = playerVm
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
                onShowComments = { vid -> showCommentsId = vid },
                onShowSleepTimer = { showSleepSheet = true },
                onGoToArtist = { showQueueOverlay = false; nav.navigate("artist/$it") }
            )
        }

        showCommentsId?.let { vid ->
            CommentsBottomSheet(videoId = vid, onDismiss = { showCommentsId = null })
        }

        if (!BuildConfig.DEBUG && updateState is UpdateViewModel.State.Available) {
            UpdateDialog(updateVm)
        }

        if (showSleepSheet) {
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
        TabStyle.Classic -> ClassicTabBar(tabs, currentRoute, onSelect)
        TabStyle.Docked -> DockedTabBar(tabs, currentRoute, onSelect)
        TabStyle.Minimal -> MinimalTabBar(tabs, currentRoute, onSelect)
        TabStyle.Capsule -> CapsuleTabBar(tabs, currentRoute, onSelect)
    }
}

@Composable
private fun ClassicTabBar(tabs: List<Tab>, currentRoute: String?, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = currentRoute == t.route,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
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
private fun MinimalTabBar(tabs: List<Tab>, currentRoute: String?, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { t ->
            val sel = currentRoute == t.route
            NavigationBarItem(
                selected = sel,
                onClick = { onSelect(t) },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(t.icon, contentDescription = t.label)
                        Box(
                            Modifier.padding(top = 5.dp).size(5.dp).clip(CircleShape)
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                        )
                    }
                },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent
                )
            )
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
