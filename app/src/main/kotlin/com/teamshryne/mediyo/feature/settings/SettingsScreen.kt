package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.core.design.SectionHeader

/**
 * Data-driven hub. Add a new page by adding an entry to [hubEntries] — hub needs no other edits.
 * Each entry's [SettingsEntry.route] must be registered in MainActivity NavHost.
 */
private val hubEntries = listOf(
    SettingsEntry(
        id = "appearance",
        title = "Appearance",
        subtitle = "Tab bar style",
        icon = Icons.Filled.Palette,
        route = "settings/appearance"
    ),
    SettingsEntry(
        id = "equalizer",
        title = "Equalizer",
        subtitle = "Bands & presets",
        icon = Icons.Filled.GraphicEq,
        route = "settings/equalizer"
    ),
    SettingsEntry(
        id = "storage",
        title = "Storage & downloads",
        subtitle = "Offline & cache",
        icon = Icons.Filled.Download,
        route = "settings/storage"
    ),
    SettingsEntry(
        id = "lyrics",
        title = "Lyrics",
        subtitle = "Sources & priority",
        icon = Icons.Filled.MusicNote,
        route = "settings/lyrics"
    ),
    SettingsEntry(
        id = "updates",
        title = "App updates",
        subtitle = "Version & checking",
        icon = Icons.Filled.SystemUpdate,
        route = "settings/updates"
    ),
)

@Composable
fun SettingsScreen(
    nav: NavController? = null,
) {
    // Updater is release-only — hide its entry in debug builds.
    val personalize = remember { hubEntries.filter { it.id == "appearance" || it.id == "lyrics" || it.id == "equalizer" || it.id == "storage" } }
    val about = remember {
        hubEntries.filterNot { it.id == "updates" && BuildConfig.DEBUG }
            .filter { it.id != "appearance" && it.id != "lyrics" && it.id != "equalizer" && it.id != "storage" }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        // Outer Scaffold padding already clears the status bar — 4dp only.
        contentPadding = PaddingValues(top = 4.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp),
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

        item { SectionHeader("Personalize") }
        item { SettingsGroup(entries = personalize, onOpen = { nav?.navigate(it) }) }

        if (about.isNotEmpty()) {
            item { SectionHeader("About") }
            item { SettingsGroup(entries = about, onOpen = { nav?.navigate(it) }) }
        }

        item {
            Text(
                "Mediyo v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

/** iOS-style grouped list: one card, divided rows. */
@Composable
private fun SettingsGroup(entries: List<SettingsEntry>, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            entries.forEachIndexed { i, entry ->
                SettingsGroupRow(entry = entry, onClick = { onOpen(entry.route) })
                if (i < entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupRow(entry: SettingsEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}
