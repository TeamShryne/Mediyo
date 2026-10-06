package com.teamshryne.mediyo.data.appearance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearancePrefs by preferencesDataStore("appearance_prefs")

enum class TabStyle(val id: String, val title: String, val subtitle: String) {
    Classic("classic", "Classic", "Standard bar with a soft indicator"),
    Docked("docked", "Dock", "Floating pill, centered"),
    Minimal("minimal", "Minimal", "Icons with a dot indicator"),
    Capsule("capsule", "Capsule", "Full-width segmented control");

    companion object {
        fun fromId(id: String?): TabStyle? = entries.find { it.id == id }
    }
}

@Singleton
class AppearancePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_TAB = stringPreferencesKey("tab_style")

    val tabStyleFlow: Flow<TabStyle> = ctx.appearancePrefs.data.map { prefs ->
        TabStyle.fromId(prefs[K_TAB]) ?: TabStyle.Classic
    }

    suspend fun setTabStyle(style: TabStyle) {
        ctx.appearancePrefs.edit { it[K_TAB] = style.id }
    }
}
