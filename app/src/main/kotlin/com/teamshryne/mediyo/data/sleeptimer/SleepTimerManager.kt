package com.teamshryne.mediyo.data.sleeptimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import com.teamshryne.mediyo.playback.PlaybackQueueManager
import com.teamshryne.mediyo.playback.SleepTimerReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SleepMode { OFF, TIMER, END_OF_TRACK, END_OF_QUEUE }

data class SleepTimerState(
    val mode: SleepMode = SleepMode.OFF,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val endElapsedRealtime: Long = 0L, // monotonic deadline for TIMER
    val fadeOut: Boolean = true
) {
    val isActive: Boolean get() = mode != SleepMode.OFF
    val progress: Float get() = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
}

@Singleton
class SleepTimerManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val player: ExoPlayer,
    private val queueManager: PlaybackQueueManager,
    private val prefs: SleepTimerPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var fadeJob: Job? = null
    private var endOfTrackListenerAttached = false

    private val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // keep original radio enabled state to restore
    private var savedRadioEnabled: Boolean? = null
    @Volatile private var timeoutGuard = false

    companion object {
        const val ACTION_SLEEP_TIMEOUT = "com.teamshryne.mediyo.SLEEP_TIMEOUT"
        const val REQUEST_CODE = 1002
        const val FADE_DURATION_MS = 8000L
    }

    init {
        // restore persisted state
        scope.launch {
            val saved = prefs.load()
            if (saved != null && saved.isActive) {
                when (saved.mode) {
                    SleepMode.TIMER -> {
                        val remaining = saved.endElapsedRealtime - SystemClock.elapsedRealtime()
                        if (remaining > 1000) {
                            _state.value = saved.copy(remainingMs = remaining)
                            applyRadioBlock(true)
                            startTicker()
                            scheduleAlarm(remaining)
                            attachEndListenerIfNeeded()
                        } else {
                            // expired while dead
                            prefs.clear()
                        }
                    }
                    SleepMode.END_OF_TRACK, SleepMode.END_OF_QUEUE -> {
                        _state.value = saved
                        applyRadioBlock(true)
                        attachEndListenerIfNeeded()
                    }
                    else -> {}
                }
            }
        }
        // EOT/EOQ are handled exclusively by PlayerViewModel.shouldBlockAutoNext() to avoid
        // race where manager clears state before ViewModel checks it (which made EOT/EOQ appear broken).
        // Manager only owns TIMER ticker/alarm; no player listener needed for END modes.
    }

    // ── Public API ──
    fun setTimer(durationMs: Long, fadeOut: Boolean = true) {
        if (durationMs <= 0) return
        cancelInternal(scheduleCancel = false)
        val endElapsed = SystemClock.elapsedRealtime() + durationMs
        val st = SleepTimerState(
            mode = SleepMode.TIMER,
            remainingMs = durationMs,
            totalMs = durationMs,
            endElapsedRealtime = endElapsed,
            fadeOut = fadeOut
        )
        _state.value = st
        applyRadioBlock(true)
        scope.launch { prefs.save(st) }
        startTicker()
        scheduleAlarm(durationMs)
        attachEndListenerIfNeeded()
    }

    fun setEndOfTrack(fadeOut: Boolean = true) {
        cancelInternal(scheduleCancel = true)
        val st = SleepTimerState(mode = SleepMode.END_OF_TRACK, fadeOut = fadeOut)
        _state.value = st
        applyRadioBlock(true)
        scope.launch { prefs.save(st) }
        attachEndListenerIfNeeded()
    }

    fun setEndOfQueue(fadeOut: Boolean = true) {
        cancelInternal(scheduleCancel = true)
        val st = SleepTimerState(mode = SleepMode.END_OF_QUEUE, fadeOut = fadeOut)
        _state.value = st
        applyRadioBlock(true)
        scope.launch { prefs.save(st) }
        attachEndListenerIfNeeded()
    }

    fun addFiveMinutes() {
        val cur = _state.value
        if (cur.mode != SleepMode.TIMER) return
        val newRemaining = cur.remainingMs + 5 * 60 * 1000L
        val newTotal = cur.totalMs + 5 * 60 * 1000L
        val newEnd = SystemClock.elapsedRealtime() + newRemaining
        val ns = cur.copy(remainingMs = newRemaining, totalMs = newTotal, endElapsedRealtime = newEnd)
        _state.value = ns
        scope.launch { prefs.save(ns) }
        // restart ticker and alarm
        startTicker()
        scheduleAlarm(newRemaining)
    }

    fun cancel() {
        cancelInternal(scheduleCancel = true)
        scope.launch { prefs.clear() }
    }

    /**
     * Called from ticker, alarm receiver, or END_OF_* listener (via ViewModel).
     * Pauses with optional fade. Guarded against double-fire race between ticker/alarm/ViewModel.
     */
    suspend fun onTimeout() {
        if (timeoutGuard) return
        val cur = _state.value
        if (!cur.isActive) return
        timeoutGuard = true
        try {
            // prevent double fire
            _state.value = cur.copy(remainingMs = 0)
            cancelAlarm()
            tickerJob?.cancel()
            // fade out if enabled and currently playing (for TIMER); for EOT/EOQ track already ended so just pause
            try {
                if (cur.fadeOut && player.isPlaying) {
                    fadePause()
                } else {
                    player.pause()
                    player.volume = 1f
                }
            } catch (_: Throwable) {
                try { player.pause() } catch (_: Throwable) {}
            }
            // clear state after pause
            _state.value = SleepTimerState()
            applyRadioBlock(false)
            scope.launch { prefs.clear() }
        } finally {
            // allow next timer to fire; small delay to swallow duplicate STATE_ENDED events
            scope.launch {
                delay(500)
                timeoutGuard = false
            }
        }
    }

    fun isBlockingRadio(): Boolean = _state.value.isActive

    fun shouldBlockAutoNext(): Boolean {
        // For END_OF_QUEUE at last track, block the auto-next that PlayerViewModel would trigger on STATE_ENDED
        val s = _state.value
        if (s.mode == SleepMode.END_OF_QUEUE) {
            val qs = queueManager.currentState()
            return qs.index == qs.entries.lastIndex && qs.entries.isNotEmpty()
        }
        if (s.mode == SleepMode.END_OF_TRACK) {
            // always block auto-next; we will pause instead
            return true
        }
        return false
    }

    // ── Internals ──
    private fun cancelInternal(scheduleCancel: Boolean) {
        tickerJob?.cancel(); tickerJob = null
        fadeJob?.cancel(); fadeJob = null
        try { player.volume = 1f } catch (_: Throwable) {}
        if (scheduleCancel) cancelAlarm()
        if (_state.value.isActive) {
            _state.value = SleepTimerState()
            applyRadioBlock(false)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val cur = _state.value
                if (cur.mode != SleepMode.TIMER) break
                val rem = cur.endElapsedRealtime - SystemClock.elapsedRealtime()
                if (rem <= 0) {
                    onTimeout()
                    break
                }
                // fade trigger 8s before end
                if (cur.fadeOut && rem <= FADE_DURATION_MS && rem > 0 && fadeJob == null && player.isPlaying) {
                    // start fade concurrently but don't break ticker; timeout will handle pause after fade
                    // we let ticker continue; fade will reduce volume gradually
                    startFade()
                }
                _state.value = cur.copy(remainingMs = rem)
                delay(1000)
            }
        }
    }

    private fun startFade() {
        if (fadeJob?.isActive == true) return
        fadeJob = scope.launch {
            val steps = 16
            val stepMs = FADE_DURATION_MS / steps
            try {
                for (i in 0..steps) {
                    if (!isActive) break
                    if (_state.value.mode == SleepMode.OFF) break
                    val v = 1f - (i.toFloat() / steps)
                    player.volume = v.coerceIn(0f, 1f)
                    delay(stepMs)
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun fadePause() {
        // ensure fade completes then pause
        try {
            val steps = 16
            val stepMs = FADE_DURATION_MS / steps
            for (i in 0..steps) {
                val v = 1f - (i.toFloat() / steps)
                player.volume = v.coerceIn(0f, 1f)
                delay(stepMs)
            }
            player.pause()
            player.volume = 1f
        } catch (_: Throwable) {
            try { player.pause(); player.volume = 1f } catch (_: Throwable) {}
        }
    }

    private fun scheduleAlarm(durationMs: Long) {
        try {
            val intent = Intent(ctx, SleepTimerReceiver::class.java).apply { action = ACTION_SLEEP_TIMEOUT }
            val pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val triggerAt = System.currentTimeMillis() + durationMs
            // Try exact, fallback to inexact if permission denied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } catch (_: Throwable) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun cancelAlarm() {
        try {
            val intent = Intent(ctx, SleepTimerReceiver::class.java).apply { action = ACTION_SLEEP_TIMEOUT }
            val pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pi)
            pi.cancel()
        } catch (_: Throwable) {}
    }

    private fun applyRadioBlock(block: Boolean) {
        try {
            if (block) {
                if (savedRadioEnabled == null) savedRadioEnabled = queueManager.currentState().isRadioEnabled
                queueManager.setRadioEnabled(false)
            } else {
                // restore previous; default true
                val restore = savedRadioEnabled ?: true
                queueManager.setRadioEnabled(restore)
                savedRadioEnabled = null
            }
        } catch (_: Throwable) {}
    }

    private fun attachEndListenerIfNeeded() {
        // already attached global listener; no extra
    }
}
