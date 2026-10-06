package com.teamshryne.mediyo.data.appearance

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

enum class PlayerBgStyle(val id: String, val title: String, val subtitle: String) {
    Gradient("gradient", "Gradient", "Artwork colors melting into black"),
    Blur("blur", "Blur", "Soft-focus artwork wash"),
    Solid("solid", "Solid", "Clean flat backdrop");

    companion object {
        fun fromId(id: String?): PlayerBgStyle? = entries.find { it.id == id }
    }
}

@Singleton
class AppearancePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_TAB = stringPreferencesKey("tab_style")
    private val K_BG = stringPreferencesKey("player_bg_style")
    private val K_BLUR = floatPreferencesKey("player_bg_blur")
    private val K_DIM = floatPreferencesKey("player_bg_dim")
    private val K_DEPTH = floatPreferencesKey("player_bg_depth")
    private val K_TINT = booleanPreferencesKey("player_bg_tint")

    val tabStyleFlow: Flow<TabStyle> = ctx.appearancePrefs.data.map { prefs ->
        TabStyle.fromId(prefs[K_TAB]) ?: TabStyle.Classic
    }

    suspend fun setTabStyle(style: TabStyle) {
        ctx.appearancePrefs.edit { it[K_TAB] = style.id }
    }

    val bgStyleFlow: Flow<PlayerBgStyle> = ctx.appearancePrefs.data.map { prefs ->
        PlayerBgStyle.fromId(prefs[K_BG]) ?: PlayerBgStyle.Gradient
    }
    /** Blur radius in dp (Blur style). */
    val bgBlurFlow: Flow<Float> = ctx.appearancePrefs.data.map { it[K_BLUR] ?: 22f }
    /** Dark shade over everything, 0..0.8. */
    val bgDimFlow: Flow<Float> = ctx.appearancePrefs.data.map { (it[K_DIM] ?: 0.35f).coerceIn(0f, 0.8f) }
    /** Gradient richness, 0..1 (Gradient style). */
    val bgDepthFlow: Flow<Float> = ctx.appearancePrefs.data.map { (it[K_DEPTH] ?: 0.75f).coerceIn(0f, 1f) }
    /** Tint the solid backdrop with artwork colors (Solid style). */
    val bgTintFlow: Flow<Boolean> = ctx.appearancePrefs.data.map { it[K_TINT] ?: true }

    suspend fun setBgStyle(style: PlayerBgStyle) {
        ctx.appearancePrefs.edit { it[K_BG] = style.id }
    }
    suspend fun setBgBlur(dp: Float) {
        ctx.appearancePrefs.edit { it[K_BLUR] = dp.coerceIn(4f, 36f) }
    }
    suspend fun setBgDim(dim: Float) {
        ctx.appearancePrefs.edit { it[K_DIM] = dim.coerceIn(0f, 0.8f) }
    }
    suspend fun setBgDepth(depth: Float) {
        ctx.appearancePrefs.edit { it[K_DEPTH] = depth.coerceIn(0f, 1f) }
    }
    suspend fun setBgTint(tint: Boolean) {
        ctx.appearancePrefs.edit { it[K_TINT] = tint }
    }
}
