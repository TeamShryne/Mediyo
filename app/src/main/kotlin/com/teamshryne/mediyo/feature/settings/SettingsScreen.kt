package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.core.design.SectionHeader

/**
 * Data-driven hub. Add a new page by adding an entry to [hubEntries] — hub needs no other edits.
 * Each entry's [SettingsEntry.route] must be registered in MainActivity NavHost.
 */
private val hubEntries = listOf(
    SettingsEntry(
        id = "lyrics",
        title = "Lyrics",
        subtitle = "Provider priority • Better Lyrics + LRCLIB",
        icon = Icons.Filled.MusicNote,
        route = "settings/lyrics"
    ),
    SettingsEntry(
        id = "updates",
        title = "App updates",
        subtitle = "Check for new versions from GitHub",
        icon = Icons.Filled.SystemUpdate,
        route = "settings/updates"
    ),
)

@Composable
fun SettingsScreen(
    nav: NavController? = null,
) {
    // Updater is release-only — hide its entry in debug builds.
    val visibleEntries = remember {
        hubEntries.filterNot { it.id == "updates" && BuildConfig.DEBUG }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        // Outer Scaffold padding already clears the status bar — 4dp only.
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            Text(
                "Tweak how Mediyo looks and behaves",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        item { SectionHeader("General") }

        items(visibleEntries, key = { it.id }) { entry ->
            SettingsHubRow(entry = entry, onClick = { nav?.navigate(entry.route) })
        }

        // Future pages: append to hubEntries and add composable(route) in MainActivity.
    }
}

@Composable
private fun SettingsHubRow(entry: SettingsEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
