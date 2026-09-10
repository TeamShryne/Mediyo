package com.teamshryne.mediyo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Launcher widget host. Rows address [com.teamshryne.mediyo.playback.PlaybackService]
 *  directly; this receiver only (re)renders. */
class PlaylistLauncherReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val manager = widgetManagerOf(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { manager.renderLauncher() }
        }
    }
}
