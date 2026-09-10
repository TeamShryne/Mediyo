package com.teamshryne.mediyo.data.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsPrefs by preferencesDataStore("lyrics_prefs")

enum class LyricsSource(val id: String, val label: String, val subtitle: String) {
    BetterLyrics("betterLyrics", "Better Lyrics", "Word-by-word sync • best for karaoke glow"),
    LyricsPlus("lyricsPlus", "Lyrics Plus", "Apple word-sync cache • fast multi-source"),
    Paxsenix("paxsenix", "Paxsenix", "Apple syllable-sync • iTunes ID lookup"),
    Kugou("kugou", "Kugou", "Huge catalog • line-by-line LRC"),
    LrcLib("lrcLib", "LRCLIB", "Huge catalog • line-by-line, great fallback");

    companion object {
        fun fromId(id: String): LyricsSource? = entries.find { it.id == id }
        val defaultOrder: List<LyricsSource> = listOf(BetterLyrics, LyricsPlus, Paxsenix, Kugou, LrcLib)
        /** LyricsPlus is flaky/slow enough to stay opt-in; everything else is on. */
        val defaultEnabled: Set<LyricsSource> = entries.filter { it != LyricsPlus }.toSet()
    }
}

@Singleton
class LyricsPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_ORDER = stringPreferencesKey("lyrics_order")
    private val K_ENABLED = stringPreferencesKey("lyrics_enabled")

    val orderFlow: Flow<List<LyricsSource>> = ctx.lyricsPrefs.data.map { prefs ->
        val raw = prefs[K_ORDER]
        if (raw.isNullOrBlank()) {
            LyricsSource.defaultOrder
        } else {
            val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val mapped = ids.mapNotNull { LyricsSource.fromId(it) }
            // Ensure all sources present (migration when adding new source).
            // Newcomers slot in before the LRCLIB fallback so existing users
            // actually benefit from them.
            val missing = LyricsSource.entries.filter { it !in mapped }
            if (missing.isEmpty()) {
                mapped.distinctBy { it.id }
            } else {
                val mutable = mapped.toMutableList()
                val lrcIdx = mutable.indexOf(LyricsSource.LrcLib)
                val (wordLevel, rest) = missing.partition {
                    it == LyricsSource.LyricsPlus || it == LyricsSource.Paxsenix || it == LyricsSource.Kugou
                }
                if (lrcIdx >= 0) mutable.addAll(lrcIdx, wordLevel + rest)
                else mutable.addAll(wordLevel + rest)
                mutable.distinctBy { it.id }
            }
        }
    }

    suspend fun setOrder(order: List<LyricsSource>) {
        val raw = order.joinToString(",") { it.id }
        ctx.lyricsPrefs.edit { it[K_ORDER] = raw }
    }

    /**
     * Per-provider on/off. Null (never set) → [LyricsSource.defaultEnabled];
     * an explicitly stored empty string means "all off" and is honored.
     */
    val enabledFlow: Flow<Set<LyricsSource>> = ctx.lyricsPrefs.data.map { prefs ->
        val raw = prefs[K_ENABLED] ?: return@map LyricsSource.defaultEnabled
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { LyricsSource.fromId(it) }.toSet()
    }

    suspend fun setEnabled(source: LyricsSource, enabled: Boolean) {
        val current = try {
            enabledFlow.first()
        } catch (_: Exception) {
            LyricsSource.defaultEnabled
        }
        val next = if (enabled) current + source else current - source
        ctx.lyricsPrefs.edit { it[K_ENABLED] = next.joinToString(",") { s -> s.id } }
    }

    suspend fun resetEnabled() {
        ctx.lyricsPrefs.edit { it[K_ENABLED] = LyricsSource.defaultEnabled.joinToString(",") { s -> s.id } }
    }

    suspend fun move(from: Int, to: Int) {
        // read current order synchronously via flow first? Caller should provide current list.
        // This helper is for completeness but UI will use setOrder directly.
    }
}
