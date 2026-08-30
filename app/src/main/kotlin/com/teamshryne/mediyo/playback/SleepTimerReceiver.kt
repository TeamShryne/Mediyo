package com.teamshryne.mediyo.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import com.teamshryne.mediyo.data.sleeptimer.SleepTimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: SleepTimerManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SleepTimerManager.ACTION_SLEEP_TIMEOUT) return
        // goAsync to allow suspend
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                manager.onTimeout()
            } catch (_: Throwable) {}
            finally { pending.finish() }
        }
    }
}
