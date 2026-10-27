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
    Docked("docked", "Dock", "Floating pill, centered"),
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

/**
 * How the full player arrives from the mini player. Every style is driven by
 * the same 0..1 progress value, so dragging the mini player up previews the
 * arrival in real time and the choice only changes the shape of the motion.
 *
 * Slide is the default because it is the motion the app shipped with; the
 * other styles are opt-in from Settings → Appearance.
 */
enum class PlayerOpenStyle(val id: String, val title: String, val subtitle: String) {
    Slide("slide", "Slide", "Rises from the bottom edge"),
    Fade("fade", "Fade", "Appears in place, no travel"),
    Scale("scale", "Scale", "Grows out from the centre"),
    Artwork("artwork", "Artwork flight", "The cover leaves the mini player and lands up top");

    companion object {
        fun fromId(id: String?): PlayerOpenStyle? = entries.find { it.id == id }
    }
}

/**
 * How one screen replaces another. Every style is driven by the same
 * navigation transitions, so predictive back gives you exactly this motion
 * under the finger — the style only changes its shape.
 */
enum class ScreenMotionStyle(val id: String, val title: String, val subtitle: String) {
    Slide("slide", "Slide", "The new screen slides in from the edge"),
    Fade("fade", "Fade", "One screen dissolves into the next"),
    Zoom("zoom", "Zoom", "The new screen grows in over the old one"),
    Rise("rise", "Rise", "The new screen rises from the bottom");

    companion object {
        fun fromId(id: String?): ScreenMotionStyle? = entries.find { it.id == id }
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
    private val K_MOTION = booleanPreferencesKey("lively_motion")
    private val K_SCREEN_STYLE = stringPreferencesKey("screen_motion_style")
    private val K_OPEN = stringPreferencesKey("player_open_style")

    val tabStyleFlow: Flow<TabStyle> = ctx.appearancePrefs.data.map { prefs ->
        TabStyle.fromId(prefs[K_TAB]) ?: TabStyle.Docked
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

    /**
     * Screen changes: a short slide, a little scale and a fade. On by
     * default — the app should feel like it moves — but a user who prefers
     * their phone calm can turn every page transition off here.
     */
    val motionFlow: Flow<Boolean> = ctx.appearancePrefs.data.map { it[K_MOTION] ?: true }

    suspend fun setMotion(enabled: Boolean) {
        ctx.appearancePrefs.edit { it[K_MOTION] = enabled }
    }

    /**
     * Screen-to-screen motion, chosen in Settings → Motion. The transitions in
     * Motion.kt read this, and predictive back uses those same transitions, so
     * the gesture and the animation can never disagree.
     */
    val screenStyleFlow: Flow<ScreenMotionStyle> = ctx.appearancePrefs.data.map { prefs ->
        ScreenMotionStyle.fromId(prefs[K_SCREEN_STYLE]) ?: ScreenMotionStyle.Slide
    }

    suspend fun setScreenStyle(style: ScreenMotionStyle) {
        ctx.appearancePrefs.edit { it[K_SCREEN_STYLE] = style.id }
    }

    /**
     * Mini player → full player motion. Kept in Appearance, next to the player
     * backdrop, because both are about how the player looks rather than about
     * playback.
     */
    val playerOpenStyleFlow: Flow<PlayerOpenStyle> = ctx.appearancePrefs.data.map { prefs ->
        PlayerOpenStyle.fromId(prefs[K_OPEN]) ?: PlayerOpenStyle.Slide
    }

    suspend fun setPlayerOpenStyle(style: PlayerOpenStyle) {
        ctx.appearancePrefs.edit { it[K_OPEN] = style.id }
    }
}
