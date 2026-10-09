package com.teamshryne.mediyo.data.equalizer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.eqPrefs by preferencesDataStore("eq_prefs")

/** Persisted 5-band graphic EQ state. Gains and preamp are in dB. */
data class PersistedEq(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(EqualizerBands.COUNT) { 0f },
    val preampDb: Float = 0f,
    val presetId: String = EqPresets.FLAT_ID
)

@Singleton
class EqPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_ENABLED = booleanPreferencesKey("enabled")
    private val K_PREAMP = floatPreferencesKey("preamp_db")
    private val K_PRESET = stringPreferencesKey("preset_id")
    private val bandKeys = List(EqualizerBands.COUNT) { i -> floatPreferencesKey("band_${i}_db") }

    val stateFlow: Flow<PersistedEq> = ctx.eqPrefs.data.map { p ->
        PersistedEq(
            enabled = p[K_ENABLED] ?: false,
            gainsDb = bandKeys.map { k ->
                (p[k] ?: 0f).coerceIn(EqualizerBands.MIN_GAIN_DB, EqualizerBands.MAX_GAIN_DB)
            },
            preampDb = (p[K_PREAMP] ?: 0f).coerceIn(EqualizerBands.MIN_PREAMP_DB, EqualizerBands.MAX_PREAMP_DB),
            presetId = p[K_PRESET] ?: EqPresets.FLAT_ID
        )
    }

    suspend fun load(): PersistedEq = stateFlow.first()

    suspend fun save(state: PersistedEq) {
        ctx.eqPrefs.edit { e ->
            e[K_ENABLED] = state.enabled
            e[K_PREAMP] = state.preampDb.coerceIn(EqualizerBands.MIN_PREAMP_DB, EqualizerBands.MAX_PREAMP_DB)
            e[K_PRESET] = state.presetId
            bandKeys.forEachIndexed { i, k ->
                e[k] = state.gainsDb.getOrElse(i) { 0f }
                    .coerceIn(EqualizerBands.MIN_GAIN_DB, EqualizerBands.MAX_GAIN_DB)
            }
        }
    }
}
