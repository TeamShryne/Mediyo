package com.teamshryne.mediyo.data.equalizer

import com.teamshryne.mediyo.playback.EqAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Graphic-EQ geometry: the classic 5 Android bands. Extreme bands are
 * shelves (they shape everything below/above), middle three are peaking.
 */
object EqualizerBands {
    const val COUNT = 5
    const val MIN_GAIN_DB = -15f
    const val MAX_GAIN_DB = 15f
    const val MIN_PREAMP_DB = -12f
    const val MAX_PREAMP_DB = 12f

    /** Center frequencies in Hz, ascending. */
    val FREQUENCIES = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
    val LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    fun clampGain(db: Float) = db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    fun clampPreamp(db: Float) = db.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB)
}

data class EqPreset(val id: String, val title: String, val gainsDb: List<Float>)

object EqPresets {
    const val FLAT_ID = "flat"
    const val CUSTOM_ID = "custom"

    val ALL = listOf(
        EqPreset(FLAT_ID, "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
        EqPreset("bass", "Bass boost", listOf(6f, 4f, 1f, 0f, 0f)),
        EqPreset("treble", "Treble boost", listOf(0f, 0f, 1f, 4f, 6f)),
        EqPreset("vocal", "Vocal", listOf(-3f, -1f, 3f, 4f, 2f)),
        EqPreset("dance", "Dance", listOf(5f, 3f, 0f, 2f, 4f)),
        EqPreset("rock", "Rock", listOf(4f, 3f, -2f, 3f, 5f)),
        EqPreset("jazz", "Jazz", listOf(4f, 2f, 0f, 2f, 4f)),
        EqPreset("classical", "Classical", listOf(3f, 2f, -1f, 2f, 3f)),
    )

    fun byId(id: String): EqPreset? = ALL.find { it.id == id }
}

/** Live EQ state: the single source of truth for the DSP, UI and disk. */
data class EqState(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(EqualizerBands.COUNT) { 0f },
    val preampDb: Float = 0f,
    val presetId: String = EqPresets.FLAT_ID
) {
    val isFlat: Boolean get() =
        preampDb == 0f && gainsDb.all { it == 0f }
}

/**
 * Owns EQ state end to end: applies it to the in-pipeline DSP, exposes it
 * for the UI, and persists it (debounced, so slider drags never thrash
 * DataStore). Restores eagerly on start; the processor holds the values as
 * pending until its first audio format arrives, so nothing is lost to
 * startup ordering.
 */
@Singleton
class EqualizerManager @Inject constructor(
    private val prefs: EqPrefs,
    private val processor: EqAudioProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()
    private var persistJob: Job? = null

    init {
        scope.launch {
            try {
                val saved = prefs.load()
                _state.value = EqState(
                    enabled = saved.enabled,
                    gainsDb = saved.gainsDb,
                    preampDb = saved.preampDb,
                    presetId = saved.presetId
                )
            } catch (_: Throwable) {
                // Unreadable store (first run / corruption) — stay flat and off.
            }
            apply()
        }
    }

    /** Single choke point pushing state into the audio pipeline. */
    private fun apply() {
        val s = _state.value
        try {
            processor.applySettings(s.enabled, s.gainsDb, s.preampDb)
        } catch (_: Throwable) {
            // DSP must never take down the app; UI state is still valid and
            // will be re-applied on the next change.
        }
    }

    private fun updateAndPersist(s: EqState, immediate: Boolean = false) {
        _state.value = s
        apply()
        persistJob?.cancel()
        if (immediate) {
            scope.launch { runCatching { prefs.save(toPersisted(s)) } }
        } else {
            persistJob = scope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                runCatching { prefs.save(toPersisted(s)) }
            }
        }
    }

    private fun toPersisted(s: EqState) = PersistedEq(
        enabled = s.enabled,
        gainsDb = s.gainsDb,
        preampDb = s.preampDb,
        presetId = s.presetId
    )

    fun setEnabled(enabled: Boolean) {
        updateAndPersist(_state.value.copy(enabled = enabled), immediate = true)
    }

    /** Live slider value: applies instantly, persists on release via [commit]. */
    fun previewBand(index: Int, gainDb: Float) {
        if (index !in 0 until EqualizerBands.COUNT) return
        val gains = _state.value.gainsDb.toMutableList()
        gains[index] = EqualizerBands.clampGain(gainDb)
        _state.value = _state.value.copy(gainsDb = gains, presetId = EqPresets.CUSTOM_ID)
        apply()
    }

    fun commitBands() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            runCatching { prefs.save(toPersisted(_state.value)) }
        }
    }

    fun previewPreamp(preampDb: Float) {
        _state.value = _state.value.copy(preampDb = EqualizerBands.clampPreamp(preampDb))
        apply()
    }

    fun applyPreset(preset: EqPreset) {
        updateAndPersist(
            _state.value.copy(gainsDb = preset.gainsDb, presetId = preset.id)
        )
    }

    fun reset() {
        val flat = EqPresets.byId(EqPresets.FLAT_ID) ?: return
        updateAndPersist(
            _state.value.copy(gainsDb = flat.gainsDb, preampDb = 0f, presetId = flat.id)
        )
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 400L
    }
}
