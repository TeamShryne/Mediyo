package com.teamshryne.mediyo.data.cache

import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.db.entities.LyricsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw lyric payload cache backed by the old app's `lyrics` table.
 *
 * The table is a plain `(id, lyrics)` key/value store, so the ported provider
 * stack can keep TTML / Lyricsfile YAML / LRC / LyricsPlus JSON payloads there
 * without adding a second database.
 */
@Singleton
class LyricsCache @Inject constructor(
    private val db: MusicDatabase,
) {
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { db.lyrics(key).first()?.lyrics }.getOrNull()
    }

    suspend fun put(key: String, raw: String) = withContext(Dispatchers.IO) {
        runCatching { db.upsert(LyricsEntity(key, raw)) }
        Unit
    }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        runCatching { db.delete(LyricsEntity(key, "")) }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { db.clearLyrics() }
        Unit
    }
}
