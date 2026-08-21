package com.teamshryne.mediyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.*
import com.teamshryne.mediyo.core.design.MediyoTheme
import com.teamshryne.mediyo.feature.home.HomeScreen
import com.teamshryne.mediyo.feature.library.LibraryScreen
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
                val nav = rememberNavController()
                val tabs = listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings)
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Mediyo", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = current == t.route,
                                    onClick = { nav.navigate(t.route) { launchSingleTop = true; popUpTo(nav.graph.startDestinationId) } },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        NavHost(navController = nav, startDestination = Tab.Home.route) {
                            composable(Tab.Home.route) { HomeScreen() }
                            composable(Tab.Search.route) { SearchScreen() }
                            composable(Tab.Library.route) { LibraryScreen() }
                            composable(Tab.Settings.route) { SettingsScreen() }
                        }
                    }
                }
            }
        }
    }
}
