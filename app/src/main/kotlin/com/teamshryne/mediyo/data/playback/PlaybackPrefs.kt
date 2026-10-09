package com.teamshryne.mediyo.data.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackPrefs by preferencesDataStore("playback_params_prefs")

/** Persisted playback-rate state. Speed/pitch are ExoPlayer PlaybackParameters multipliers. */
data class PersistedPlaybackParams(
    val speed: Float = PlaybackPrefs.DEFAULT_SPEED,
    val pitch: Float = PlaybackPrefs.DEFAULT_PITCH,
    val preservePitch: Boolean = PlaybackPrefs.DEFAULT_PRESERVE_PITCH
)

@Singleton
class PlaybackPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    companion object {
        const val DEFAULT_SPEED = 1f
        const val DEFAULT_PITCH = 1f
        const val DEFAULT_PRESERVE_PITCH = true

        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f

        fun clampSpeed(v: Float) = v.coerceIn(MIN_SPEED, MAX_SPEED)
        fun clampPitch(v: Float) = v.coerceIn(MIN_PITCH, MAX_PITCH)
    }

    private val K_SPEED = floatPreferencesKey("speed")
    private val K_PITCH = floatPreferencesKey("pitch")
    private val K_PRESERVE = booleanPreferencesKey("preserve_pitch")

    val paramsFlow: Flow<PersistedPlaybackParams> = ctx.playbackPrefs.data.map { p ->
        PersistedPlaybackParams(
            speed = clampSpeed(p[K_SPEED] ?: DEFAULT_SPEED),
            pitch = clampPitch(p[K_PITCH] ?: DEFAULT_PITCH),
            preservePitch = p[K_PRESERVE] ?: DEFAULT_PRESERVE_PITCH
        )
    }

    suspend fun load(): PersistedPlaybackParams = paramsFlow.first()

    suspend fun setSpeed(speed: Float) {
        val v = clampSpeed(speed)
        ctx.playbackPrefs.edit { it[K_SPEED] = v }
    }

    suspend fun setPitch(pitch: Float) {
        val v = clampPitch(pitch)
        ctx.playbackPrefs.edit { it[K_PITCH] = v }
    }

    suspend fun setPreservePitch(preserve: Boolean) {
        ctx.playbackPrefs.edit { it[K_PRESERVE] = preserve }
    }

    suspend fun setAll(speed: Float, pitch: Float, preservePitch: Boolean) {
        ctx.playbackPrefs.edit {
            it[K_SPEED] = clampSpeed(speed)
            it[K_PITCH] = clampPitch(pitch)
            it[K_PRESERVE] = preservePitch
        }
    }

    suspend fun reset() {
        ctx.playbackPrefs.edit {
            it[K_SPEED] = DEFAULT_SPEED
            it[K_PITCH] = DEFAULT_PITCH
            it[K_PRESERVE] = DEFAULT_PRESERVE_PITCH
        }
    }
}
