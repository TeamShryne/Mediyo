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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teamshryne.mediyo.core.design.MediyoTheme
import com.teamshryne.mediyo.feature.album.AlbumScreen
import com.teamshryne.mediyo.feature.artist.ArtistScreen
import com.teamshryne.mediyo.feature.comments.CommentsBottomSheet
import com.teamshryne.mediyo.feature.episodes.EpisodesScreen
import com.teamshryne.mediyo.feature.history.HistoryScreen
import com.teamshryne.mediyo.feature.home.HomeScreen
import com.teamshryne.mediyo.feature.library.LibraryScreen
import com.teamshryne.mediyo.feature.library.LikedScreen
import com.teamshryne.mediyo.feature.library.LocalPlaylistDetailScreen
import com.teamshryne.mediyo.feature.list.GenericListScreen
import com.teamshryne.mediyo.feature.player.FullPlayer
import com.teamshryne.mediyo.feature.player.MiniPlayer
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.feature.playlist.PlaylistScreen
import com.teamshryne.mediyo.feature.podcast.PodcastScreen
import com.teamshryne.mediyo.feature.profile.ProfileScreen
import com.teamshryne.mediyo.feature.queue.QueueScreen
import com.teamshryne.mediyo.feature.search.SearchScreen
import com.teamshryne.mediyo.feature.settings.LyricsSettingsScreen
import com.teamshryne.mediyo.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Home)
    data object Search : Tab("search", "Search", Icons.Filled.Search)
    data object Library : Tab("library", "Library", Icons.Filled.LibraryMusic)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

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
        currentRoute.startsWith("list/") -> "Playlist"
        else -> tabs.firstOrNull { it.route == currentRoute }?.label ?: "Mediyo"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
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
                    MiniPlayer(
                        state = playerState,
                        onToggle = playerVm::toggle,
                        onNext = playerVm::next,
                        onExpand = { if (playerState.title.isNotEmpty()) showFullPlayer = true },
                        sleepBadge = sleepBadge
                    )
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = currentRoute == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        launchSingleTop = true
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        restoreState = true
                                    }
                                },
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
            }
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(pad)
            ) {
                composable(Tab.Home.route) { HomeScreen(nav, playerVm) }
                composable(Tab.Search.route) { SearchScreen(nav, playerVm) }
                composable(Tab.Library.route) { LibraryScreen(nav, playerVm) }
                composable(Tab.Settings.route) { SettingsScreen(nav) }
                composable("settings/lyrics") { LyricsSettingsScreen(nav) }
                composable("playlist/{id}") { PlaylistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("album/{id}") { AlbumScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("artist/{id}") { ArtistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("podcast/{id}") { PodcastScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("episodes/{id}") { EpisodesScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("list/{id}") { GenericListScreen(it.arguments?.getString("id") ?: "", null, nav, playerVm) }
                composable("localPlaylist/{id}") { LocalPlaylistDetailScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("liked") { LikedScreen(nav, playerVm) }
                composable("history") { HistoryScreen(nav, playerVm) }
                composable("profile") { ProfileScreen(nav) }
                composable("comments/{videoId}") { back ->
                    val vid = back.arguments?.getString("videoId") ?: ""
                    CommentsBottomSheet(videoId = vid, onDismiss = { nav.popBackStack() })
                }
            }
        }

        // Immersive full-screen player overlay — consumes clicks & back
        AnimatedVisibility(
            visible = showFullPlayer && playerState.title.isNotEmpty(),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(240)),
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
                playerVm = playerVm
            )
        }

        // Queue overlay — sits ON TOP of the full player, same way the player
        // sits on top of the app. Closing it reveals the player underneath.
        // Its BackHandler is composed after the player's, so back closes the
        // queue first and returns to the player.
        AnimatedVisibility(
            visible = showQueueOverlay,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(240)),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = true) { showQueueOverlay = false }
            QueueScreen(
                player = playerVm,
                onClose = { showQueueOverlay = false },
                onShowComments = { vid -> showCommentsId = vid },
                onShowSleepTimer = { showSleepSheet = true }
            )
        }

        showCommentsId?.let { vid ->
            CommentsBottomSheet(videoId = vid, onDismiss = { showCommentsId = null })
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
