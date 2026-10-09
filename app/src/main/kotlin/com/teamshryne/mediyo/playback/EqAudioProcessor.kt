package com.teamshryne.mediyo.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.teamshryne.mediyo.data.equalizer.EqualizerBands
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * In-pipeline graphic EQ: 5 biquad bands applied to PCM inside ExoPlayer's
 * own audio chain (no system effect HAL involved, so it behaves identically
 * on every device and survives speed/pitch processing, track changes and
 * service restarts).
 *
 * Owned as a Hilt singleton and handed to both the player's audio sink and
 * [com.teamshryne.mediyo.data.equalizer.EqualizerManager]. Settings applied
 * before the first audio format arrives are held as pending and installed
 * in [onConfigure], so startup ordering can never drop the user's curve.
 */
@OptIn(UnstableApi::class)
@Singleton
class EqAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    @Volatile private var sampleRateHz = 0
    @Volatile private var channelCount = 0

    private var enabled = false
    private var gainsDb: List<Float> = List(EqualizerBands.COUNT) { 0f }
    private var preampGain = 1.0
    private var filters: List<BiquadFilter> = emptyList()
    private var pending = false

    /**
     * Install a new curve. Cheap (a handful of biquads) and safe to call at
     * slider rate from any thread; takes effect on the very next buffer.
     */
    @Synchronized
    fun applySettings(enabled: Boolean, gainsDb: List<Float>, preampDb: Float) {
        this.enabled = enabled
        this.gainsDb = gainsDb.toList()
        this.preampGain = 10.0.pow(preampDb / 20.0)
        if (sampleRateHz == 0) {
            // No format yet — onConfigure picks these up.
            pending = true
            return
        }
        rebuild()
    }

    @Synchronized
    private fun rebuild() {
        filters = if (!enabled) {
            emptyList()
        } else {
            gainsDb.mapIndexedNotNull { i, gain ->
                if (gain == 0f) return@mapIndexedNotNull null
                val freq = EqualizerBands.FREQUENCIES.getOrElse(i) { return@mapIndexedNotNull null }
                if (freq >= sampleRateHz / 2.0) return@mapIndexedNotNull null
                BiquadFilter(
                    sampleRateHz = sampleRateHz,
                    frequencyHz = freq,
                    gainDb = gain.toDouble(),
                    q = 1.0,
                    type = when (i) {
                        0 -> BiquadType.LOW_SHELF
                        EqualizerBands.COUNT - 1 -> BiquadType.HIGH_SHELF
                        else -> BiquadType.PEAK
                    }
                )
            }
        }
        pending = false
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Fail closed on formats we don't handle: the sink renegotiates
        // instead of us corrupting float/multichannel audio.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        synchronized(this) {
            if (pending || filters.isEmpty()) rebuild()
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val active = synchronized(this) { enabled && (filters.isNotEmpty() || preampGain != 1.0) }
        if (!active) {
            output.put(inputBuffer)
        } else {
            process(inputBuffer, output)
        }
        output.flip()
    }

    private fun process(input: ByteBuffer, output: ByteBuffer) {
        val filtersSnapshot: List<BiquadFilter>
        val preamp: Double
        val channels: Int
        synchronized(this) {
            filtersSnapshot = filters
            preamp = preampGain
            channels = channelCount
        }
        val frames = input.remaining() / 2 / channels.coerceAtLeast(1)
        repeat(frames) {
            if (channels == 1) {
                var s = input.short.toDouble() / 32768.0
                for (f in filtersSnapshot) s = f.processMono(s)
                output.putShort(((s * preamp * 32768.0).coerceIn(-32768.0, 32767.0)).toInt().toShort())
            } else {
                var l = input.short.toDouble() / 32768.0
                var r = input.short.toDouble() / 32768.0
                for (f in filtersSnapshot) {
                    val (pl, pr) = f.processStereo(l, r)
                    l = pl
                    r = pr
                }
                output.putShort(((l * preamp * 32768.0).coerceIn(-32768.0, 32767.0)).toInt().toShort())
                output.putShort(((r * preamp * 32768.0).coerceIn(-32768.0, 32767.0)).toInt().toShort())
            }
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        synchronized(this) { filters.forEach { it.reset() } }
    }

    override fun onReset() {
        sampleRateHz = 0
        channelCount = 0
        synchronized(this) { filters.forEach { it.reset() } }
    }

}
