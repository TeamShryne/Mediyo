package com.teamshryne.mediyo.data.cache

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.storagePrefs by preferencesDataStore("storage_prefs")

/** User-facing storage controls. Size changes apply on next start (the LRU evictor is sized once). */
data class SongCacheSettings(
    val enableSongCache: Boolean = true,
    val maxSongCacheMb: Int = 1024
)

@Singleton
class StoragePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_ENABLED = booleanPreferencesKey("song_cache_enabled")
    private val K_MAX_MB = intPreferencesKey("song_cache_max_mb")

    val stateFlow: Flow<SongCacheSettings> = ctx.storagePrefs.data.map { p ->
        SongCacheSettings(
            enableSongCache = p[K_ENABLED] ?: true,
            maxSongCacheMb = (p[K_MAX_MB] ?: 1024).coerceIn(0, 8192)
        )
    }

    suspend fun load(): SongCacheSettings = stateFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        ctx.storagePrefs.edit { it[K_ENABLED] = enabled }
    }

    suspend fun setMaxMb(mb: Int) {
        ctx.storagePrefs.edit { it[K_MAX_MB] = mb.coerceIn(0, 8192) }
    }

    companion object {
        /** Size steps offered in the UI, MB. 0 = disabled. */
        val SIZE_STEPS_MB = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192)
    }
}
