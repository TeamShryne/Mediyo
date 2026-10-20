package com.teamshryne.mediyo.data.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsStylePrefs by preferencesDataStore("lyrics_style_prefs")

/**
 * Persisted look of the synced lyrics (size / weight / line spacing).
 *
 * Kept apart from [LyricsPrefs] — that one owns *where* lyrics come from,
 * this one owns how they are drawn.
 *
 * Holds two views of the same thing:
 * - [styleFlow] — what is on disk.
 * - [live] — what the user is looking at right now. It moves the instant a
 *   slider moves, while the write to disk catches up a moment later. Binding
 *   a slider straight to the DataStore flow makes it feel stiff, because the
 *   value only moves once the write and a re-read have come back.
 */
@Singleton
class LyricsStylePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_SIZE = floatPreferencesKey("lyrics_font_size")
    private val K_WEIGHT = stringPreferencesKey("lyrics_font_weight")
    private val K_SPACING = floatPreferencesKey("lyrics_line_spacing")

    /** The look as it is saved on disk. */
    val styleFlow: Flow<LyricsStyle> = ctx.lyricsStylePrefs.data.map { prefs ->
        LyricsStyle(
            fontSizeSp = (prefs[K_SIZE] ?: LyricsStyle.DEFAULT_FONT_SIZE_SP)
                .coerceIn(LyricsStyle.MIN_FONT_SIZE_SP, LyricsStyle.MAX_FONT_SIZE_SP),
            weight = LyricsFontWeight.fromId(prefs[K_WEIGHT]),
            lineSpacingDp = (prefs[K_SPACING] ?: LyricsStyle.DEFAULT_LINE_SPACING_DP)
                .coerceIn(LyricsStyle.MIN_LINE_SPACING_DP, LyricsStyle.MAX_LINE_SPACING_DP)
        )
    }

    /**
     * The look as the UI sees it — updated the moment the user touches
     * something, saved a moment later.
     */
    private val _live = MutableStateFlow(LyricsStyle())
    val live: StateFlow<LyricsStyle> = _live.asStateFlow()

    /**
     * App-scoped, not view-scoped: a change that is on its way to disk still
     * lands when the user leaves the screen straight after letting go.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Sliders fire on every drag frame — only the *write* is debounced, so
    // the value on screen is always live.
    private var sizeJob: Job? = null
    private var spacingJob: Job? = null

    /** Set once the user touches anything, so a late seed can't undo it. */
    private var edited = false

    init {
        // Start from what is already saved.
        scope.launch {
            val saved = styleFlow.first()
            if (!edited) _live.value = saved
        }
    }

    fun setFontSize(sp: Float) {
        val v = sp.coerceIn(LyricsStyle.MIN_FONT_SIZE_SP, LyricsStyle.MAX_FONT_SIZE_SP)
        edited = true
        _live.update { it.copy(fontSizeSp = v) }
        sizeJob?.cancel()
        sizeJob = scope.launch {
            delay(300)
            ctx.lyricsStylePrefs.edit { it[K_SIZE] = v }
        }
    }

    fun setWeight(weight: LyricsFontWeight) {
        edited = true
        _live.update { it.copy(weight = weight) }
        scope.launch { ctx.lyricsStylePrefs.edit { it[K_WEIGHT] = weight.id } }
    }

    fun setLineSpacing(dp: Float) {
        val v = dp.coerceIn(LyricsStyle.MIN_LINE_SPACING_DP, LyricsStyle.MAX_LINE_SPACING_DP)
        edited = true
        _live.update { it.copy(lineSpacingDp = v) }
        spacingJob?.cancel()
        spacingJob = scope.launch {
            delay(300)
            ctx.lyricsStylePrefs.edit { it[K_SPACING] = v }
        }
    }

    /** Back to the shipped defaults. */
    fun reset() {
        sizeJob?.cancel()
        spacingJob?.cancel()
        edited = true
        _live.value = LyricsStyle()
        scope.launch { ctx.lyricsStylePrefs.edit { it.clear() } }
    }
}
