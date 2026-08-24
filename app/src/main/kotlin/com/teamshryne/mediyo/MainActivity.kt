package com.teamshryne.mediyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teamshryne.mediyo.core.design.MediyoTheme
import com.teamshryne.mediyo.feature.album.AlbumScreen
import com.teamshryne.mediyo.feature.artist.ArtistScreen
import com.teamshryne.mediyo.feature.episodes.EpisodesScreen
import com.teamshryne.mediyo.feature.home.HomeScreen
import com.teamshryne.mediyo.feature.library.LibraryScreen
import com.teamshryne.mediyo.feature.list.GenericListScreen
import com.teamshryne.mediyo.feature.player.FullPlayer
import com.teamshryne.mediyo.feature.player.MiniPlayer
import com.teamshryne.mediyo.feature.player.PlayerViewModel
import com.teamshryne.mediyo.feature.playlist.PlaylistScreen
import com.teamshryne.mediyo.feature.podcast.PodcastScreen
import com.teamshryne.mediyo.feature.profile.ProfileScreen
import com.teamshryne.mediyo.feature.search.SearchScreen
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

    val playerVm: PlayerViewModel = hiltViewModel()
    val playerState by playerVm.state.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    val contextLabel = when {
        currentRoute == null -> "Mediyo"
        currentRoute.startsWith("album/") -> "Album"
        currentRoute.startsWith("artist/") -> "Artist"
        currentRoute.startsWith("playlist/") -> "Playlist"
        currentRoute.startsWith("list/") -> "Playlist"
        else -> tabs.firstOrNull { it.route == currentRoute }?.label ?: "Mediyo"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = playerState,
                        onToggle = playerVm::toggle,
                        onNext = playerVm::next,
                        onExpand = { if (playerState.title.isNotEmpty()) showFullPlayer = true }
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
                composable(Tab.Library.route) { LibraryScreen() }
                composable(Tab.Settings.route) { SettingsScreen() }
                composable("playlist/{id}") { PlaylistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("album/{id}") { AlbumScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("artist/{id}") { ArtistScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("podcast/{id}") { PodcastScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("episodes/{id}") { EpisodesScreen(it.arguments?.getString("id") ?: "", nav, playerVm) }
                composable("list/{id}") { GenericListScreen(it.arguments?.getString("id") ?: "", null, nav, playerVm) }
                composable("profile") { ProfileScreen() }
            }
        }

        // Immersive full-screen player overlay
        AnimatedVisibility(
            visible = showFullPlayer && playerState.title.isNotEmpty(),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(240)),
            modifier = Modifier.fillMaxSize()
        ) {
            FullPlayer(
                state = playerState,
                contextLabel = contextLabel,
                onToggle = playerVm::toggle,
                onNext = playerVm::next,
                onPrevious = playerVm::previous,
                onSeek = playerVm::seekTo,
                onToggleShuffle = playerVm::toggleShuffle,
                onToggleRepeat = playerVm::toggleRepeat,
                onCollapse = { showFullPlayer = false }
            )
        }
    }
}
