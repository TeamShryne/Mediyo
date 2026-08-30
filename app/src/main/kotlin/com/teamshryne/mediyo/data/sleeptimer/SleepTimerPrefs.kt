package com.teamshryne.mediyo.data.sleeptimer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sleepPrefs by preferencesDataStore("sleep_timer_prefs")

@Singleton
class SleepTimerPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_MODE = stringPreferencesKey("mode")
    private val K_END_ELAPSED = longPreferencesKey("endElapsed")
    private val K_TOTAL = longPreferencesKey("totalMs")
    private val K_REMAINING = longPreferencesKey("remainingMs")
    private val K_FADE = booleanPreferencesKey("fade")

    suspend fun save(state: SleepTimerState) {
        ctx.sleepPrefs.edit {
            it[K_MODE] = state.mode.name
            it[K_END_ELAPSED] = state.endElapsedRealtime
            it[K_TOTAL] = state.totalMs
            it[K_REMAINING] = state.remainingMs
            it[K_FADE] = state.fadeOut
        }
    }

    suspend fun load(): SleepTimerState? {
        val p = ctx.sleepPrefs.data.first()
        val modeStr = p[K_MODE] ?: return null
        val mode = try { SleepMode.valueOf(modeStr) } catch (_: Throwable) { return null }
        if (mode == SleepMode.OFF) return null
        return SleepTimerState(
            mode = mode,
            endElapsedRealtime = p[K_END_ELAPSED] ?: 0L,
            totalMs = p[K_TOTAL] ?: 0L,
            remainingMs = p[K_REMAINING] ?: 0L,
            fadeOut = p[K_FADE] ?: true
        )
    }

    suspend fun clear() {
        ctx.sleepPrefs.edit { it.clear() }
    }
}
