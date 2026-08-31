package com.teamshryne.mediyo.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data-driven registry for Settings hub.
 * Add a new settings page by adding an entry here — no other hub code changes needed.
 */
data class SettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

val SettingsCatalog: List<SettingsEntry> get() = _catalog
private val _catalog = mutableListOf<SettingsEntry>()

fun registerSettings(entry: SettingsEntry) {
    if (_catalog.none { it.id == entry.id }) _catalog.add(entry)
}

fun settingsEntryForRoute(route: String): SettingsEntry? = SettingsCatalog.find { it.route == route }
