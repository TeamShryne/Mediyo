package com.teamshryne.mediyo.data.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsPrefs by preferencesDataStore("lyrics_prefs")

enum class LyricsSource(val id: String, val label: String, val subtitle: String) {
    BetterLyrics("betterLyrics", "Apple TTML", "Word-synced • higher accuracy"),
    LrcLib("lrcLib", "LRCLIB", "Line-synced • large catalog");

    companion object {
        fun fromId(id: String): LyricsSource? = entries.find { it.id == id }
        val defaultOrder: List<LyricsSource> = listOf(BetterLyrics, LrcLib)
    }
}

@Singleton
class LyricsPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_ORDER = stringPreferencesKey("lyrics_order")

    val orderFlow: Flow<List<LyricsSource>> = ctx.lyricsPrefs.data.map { prefs ->
        val raw = prefs[K_ORDER]
        if (raw.isNullOrBlank()) {
            LyricsSource.defaultOrder
        } else {
            val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val mapped = ids.mapNotNull { LyricsSource.fromId(it) }
            // Ensure all sources present (migration when adding new source)
            val missing = LyricsSource.entries.filter { it !in mapped }
            (mapped + missing).distinctBy { it.id }
        }
    }

    suspend fun setOrder(order: List<LyricsSource>) {
        val raw = order.joinToString(",") { it.id }
        ctx.lyricsPrefs.edit { it[K_ORDER] = raw }
    }

    suspend fun move(from: Int, to: Int) {
        // read current order synchronously via flow first? Caller should provide current list.
        // This helper is for completeness but UI will use setOrder directly.
    }
}
