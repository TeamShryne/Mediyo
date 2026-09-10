package com.teamshryne.mediyo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Player widget host. Buttons address [com.teamshryne.mediyo.playback.PlaybackService]
 *  directly; this receiver only (re)renders. */
class MediyoPlayerReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, mgr, appWidgetId, newOptions)
        render(context)
    }

    private fun render(context: Context) {
        val manager = widgetManagerOf(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { manager.renderPlayer() }
        }
    }
}
