package com.teamshryne.mediyo.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * adb entry point. Registered **only** in `src/debug/AndroidManifest.xml`, so it
 * does not exist in a release APK at all.
 *
 * Registered exported, which means any app on the device can send it too. That
 * is the accepted tradeoff for a debug-only build: release has no receiver to
 * find. Nothing destructive is exposed here — the worst a caller can do is
 * navigate, search or play.
 *
 * ```bash
 * adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
 *     --es cmd play --es query "daft punk one more time"
 * ```
 *
 * Replies as the broadcast result (`result=0` ok, `result=1` rejected), and
 * always to logcat under `MediyoAdb`. `am` runs an ordered broadcast, so
 * `setResultData` shows up inline in the shell output.
 */
class AdbControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val at = System.currentTimeMillis()
        val cmd = intent.getStringExtra(EXTRA_CMD)?.trim()?.lowercase()
        val route = intent.getStringExtra(EXTRA_ROUTE)?.trim().orEmpty()
        val arg = intent.getStringExtra(EXTRA_ARG)?.trim().orEmpty()
        val query = intent.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.trim().orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()

        // `go --es route artist --es arg UCxxx` is the same as
        // `go --es route artist/UCxxx`, so callers don't have to know the
        // `{id}` placeholder syntax of every route.
        val target = when {
            route.isEmpty() || arg.isEmpty() -> route
            route.contains('/') -> route
            else -> "$route/$arg"
        }

        val (code, reply) = when (cmd) {
            "go" -> if (target.isEmpty()) {
                reject("--es route <name|route> required (add --es arg <id> for a detail page)")
            } else {
                bringToFront(context)
                accept(AdbCommand.Go(at, target), "go $target")
            }

            "search" -> if (query.isEmpty()) {
                reject("--es query required")
            } else {
                bringToFront(context)
                accept(AdbCommand.Search(at, query), "search '$query'")
            }

            "play" -> when {
                videoId.isNotEmpty() -> accept(
                    AdbCommand.Play(at, query = null, videoId = videoId, title = title.ifEmpty { null }),
                    "play videoId=$videoId"
                )
                query.isNotEmpty() -> accept(
                    AdbCommand.Play(at, query = query, videoId = null, title = null),
                    "play query='$query'"
                )
                else -> reject("--es query or --es videoId required")
            }

            "dump" -> accept(AdbCommand.Dump(at), "dump (see logcat -s $AdbControl.TAG)")

            else -> reject("--es cmd must be one of: go, search, play, dump")
        }

        AdbControl.log("$cmd -> $reply")
        if (isOrderedBroadcast) {
            setResultCode(code)
            setResultData(reply)
        }
    }

    private fun accept(command: AdbCommand, message: String) =
        0 to "ok: $message".also { AdbControl.send(command) }

    private fun reject(message: String) = 1 to "error: $message"

    /**
     * Best effort only. Android 10+ blocks background activity starts, so this
     * is a convenience, never a requirement — the command is buffered either
     * way and runs as soon as the activity is composed.
     */
    private fun bringToFront(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            context.startActivity(launch)
        } catch (_: Throwable) {
            // Blocked background start — caller should `am start` first.
        }
    }

    companion object {
        const val ACTION = "com.teamshryne.mediyo.adb"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ARG = "arg"
        const val EXTRA_QUERY = "query"
        const val EXTRA_VIDEO_ID = "videoId"
        const val EXTRA_TITLE = "title"
    }
}