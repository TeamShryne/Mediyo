package com.teamshryne.mediyo.data.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsStylePrefs by preferencesDataStore("lyrics_style_prefs")

/**
 * Persisted look of the synced lyrics (size / weight / line spacing).
 *
 * Kept apart from [LyricsPrefs] — that one owns *where* lyrics come from,
 * this one owns how they are drawn.
 */
@Singleton
class LyricsStylePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_SIZE = floatPreferencesKey("lyrics_font_size")
    private val K_WEIGHT = stringPreferencesKey("lyrics_font_weight")
    private val K_SPACING = floatPreferencesKey("lyrics_line_spacing")

    val styleFlow: Flow<LyricsStyle> = ctx.lyricsStylePrefs.data.map { prefs ->
        LyricsStyle(
            fontSizeSp = (prefs[K_SIZE] ?: LyricsStyle.DEFAULT_FONT_SIZE_SP)
                .coerceIn(LyricsStyle.MIN_FONT_SIZE_SP, LyricsStyle.MAX_FONT_SIZE_SP),
            weight = LyricsFontWeight.fromId(prefs[K_WEIGHT]),
            lineSpacingDp = (prefs[K_SPACING] ?: LyricsStyle.DEFAULT_LINE_SPACING_DP)
                .coerceIn(LyricsStyle.MIN_LINE_SPACING_DP, LyricsStyle.MAX_LINE_SPACING_DP)
        )
    }

    suspend fun setFontSize(sp: Float) {
        val v = sp.coerceIn(LyricsStyle.MIN_FONT_SIZE_SP, LyricsStyle.MAX_FONT_SIZE_SP)
        ctx.lyricsStylePrefs.edit { it[K_SIZE] = v }
    }

    suspend fun setWeight(weight: LyricsFontWeight) {
        ctx.lyricsStylePrefs.edit { it[K_WEIGHT] = weight.id }
    }

    suspend fun setLineSpacing(dp: Float) {
        val v = dp.coerceIn(LyricsStyle.MIN_LINE_SPACING_DP, LyricsStyle.MAX_LINE_SPACING_DP)
        ctx.lyricsStylePrefs.edit { it[K_SPACING] = v }
    }

    /** Back to the shipped defaults. */
    suspend fun reset() {
        ctx.lyricsStylePrefs.edit { it.clear() }
    }
}
