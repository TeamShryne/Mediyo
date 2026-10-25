package com.teamshryne.mediyo.feature.debug

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.playback.PlaybackDiagnostics
import com.teamshryne.mediyo.playback.PlaybackService
import com.teamshryne.mediyo.playback.PlaybackSessionHub
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class CheckStatus { PASS, WARN, FAIL, INFO }

/**
 * One line of the report. [details] are printed under the line in the file
 * and shown when the row is expanded on screen, so a support thread can see
 * the raw values instead of asking for them one at a time.
 */
data class CheckResult(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val summary: String,
    val details: List<String> = emptyList(),
)

/**
 * Runs the checks that decide whether a media notification *can* appear on
 * this device, and writes them to a file the user can share.
 *
 * The order matters: the first checks are the ones a user can fix in
 * Settings, the middle ones are the ones we own (manifest, service, session)
 * and the last one is the answer — whether a notification with a media
 * session token is actually posted right now.
 *
 * Nothing here changes app state, with one deliberate exception: the
 * controller probe at the end connects to [PlaybackService] the same way a
 * car or SystemUI would. On a build where Media3 never armed its
 * notification pipeline, that connection is what arms it — so the report
 * says so instead of quietly "fixing" the problem and hiding it.
 */
@Singleton
class NotificationReportRunner @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val player: ExoPlayer,
    private val hub: PlaybackSessionHub,
) {

    companion object {
        /** Kept next to [run] so the screen's progress bar cannot drift. */
        const val CHECK_COUNT = 10
    }

    suspend fun run(
        onStart: (String) -> Unit,
        onResult: (CheckResult) -> Unit,
    ): File {
        val results = mutableListOf<CheckResult>()

        // A short beat per step: the checks are instant, and a screen that
        // flashes ten rows in one frame reads as "nothing happened".
        suspend fun step(title: String, block: suspend () -> CheckResult) {
            onStart(title)
            delay(260)
            val result = block()
            results += result
            onResult(result)
        }

        step("Device & build") { deviceCheck() }
        step("Notifications allowed") { allowedCheck() }
        step("Notification permission") { permissionCheck() }
        step("Playback channel") { channelCheck() }
        step("Playback service declared") { serviceDeclaredCheck() }
        step("Media3 notification pipeline") { pipelineCheck() }
        step("Battery optimisation") { batteryCheck() }
        step("Playback state") { playbackCheck() }
        step("Notification posted") { postedCheck() }
        step("Media session reachable") { controllerCheck() }

        return withContext(Dispatchers.IO) { writeReport(results) }
    }

    // ── Checks ───────────────────────────────────────────────────────────

    private suspend fun deviceCheck(): CheckResult {
        val miui = withContext(Dispatchers.IO) {
            systemProperty("ro.miui.ui.version.name") ?: systemProperty("ro.mi.os.version.name")
        }
        val oem = buildString {
            append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            if (miui != null) append(" · MIUI ").append(miui)
        }
        return CheckResult(
            id = "device",
            title = "Device & build",
            status = CheckStatus.INFO,
            summary = "${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            details = listOf(
                "manufacturer: ${Build.MANUFACTURER}",
                "brand/model: ${Build.BRAND} / ${Build.MODEL}",
                "android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                "oem skin: $oem",
                "target sdk: ${ctx.applicationInfo.targetSdkVersion}",
                "app: ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA}) · ${BuildConfig.CHANNEL}",
                "media3: ${BuildConfig.MEDIA3_VERSION}",
                "notification listener access: ${notificationListenerGranted()}",
                "build fingerprint: ${Build.FINGERPRINT}",
            ),
        )
    }

    private fun allowedCheck(): CheckResult {
        val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        return CheckResult(
            id = "allowed",
            title = "Notifications allowed",
            status = if (enabled) CheckStatus.PASS else CheckStatus.FAIL,
            summary = if (enabled) {
                "This app may post notifications"
            } else {
                "Notifications are switched off for Mediyo in the system settings"
            },
            details = listOf("areNotificationsEnabled: $enabled"),
        )
    }

    private fun permissionCheck(): CheckResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return CheckResult(
                id = "permission",
                title = "Notification permission",
                status = CheckStatus.INFO,
                summary = "Not required before Android 13",
            )
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return CheckResult(
            id = "permission",
            title = "Notification permission",
            status = if (granted) CheckStatus.PASS else CheckStatus.FAIL,
            summary = if (granted) "POST_NOTIFICATIONS is granted" else "POST_NOTIFICATIONS was denied",
            details = listOf("POST_NOTIFICATIONS granted: $granted"),
        )
    }

    private fun channelCheck(): CheckResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return CheckResult(
                id = "channel",
                title = "Playback channel",
                status = CheckStatus.INFO,
                summary = "Channels do not exist before Android 8",
            )
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channel = nm?.getNotificationChannel(PlaybackService.CHANNEL_ID)
            ?: return CheckResult(
                id = "channel",
                title = "Playback channel",
                status = CheckStatus.FAIL,
                summary = "The playback channel does not exist yet",
                details = listOf("channel id: ${PlaybackService.CHANNEL_ID}"),
            )
        val importance = channel.importance
        val status = when {
            importance == NotificationManager.IMPORTANCE_NONE -> CheckStatus.FAIL
            // MIUI/HyperOS park low-importance notifications in a collapsed
            // "Silent" section, which is where a playback notification must
            // never end up: it looks exactly like "no notification".
            importance <= NotificationManager.IMPORTANCE_LOW -> CheckStatus.WARN
            else -> CheckStatus.PASS
        }
        return CheckResult(
            id = "channel",
            title = "Playback channel",
            status = status,
            summary = when (status) {
                CheckStatus.FAIL -> "The playback channel is blocked"
                CheckStatus.WARN -> "Channel importance is ${importanceName(importance)} — OEMs may hide it in a silent section"
                else -> "Channel importance is ${importanceName(importance)}"
            },
            details = listOf(
                "channel id: ${channel.id}",
                "importance: ${importanceName(importance)} ($importance)",
                "sound: ${channel.sound ?: "none"}",
                "vibration: ${channel.shouldVibrate()}",
                "lockscreen visibility: ${channel.lockscreenVisibility}",
                "group: ${channel.group ?: "none"}",
            ),
        )
    }

    private fun serviceDeclaredCheck(): CheckResult {
        val pm = ctx.packageManager
        val component = ComponentName(ctx, PlaybackService::class.java)
        val details = mutableListOf<String>()
        val problems = mutableListOf<String>()
        try {
            val info = pm.getServiceInfo(component, PackageManager.GET_META_DATA)
            details += "declared: yes"
            details += "enabled: ${info.enabled}"
            details += "exported: ${info.exported}"
            if (!info.exported) problems += "the service is not exported, so a car or SystemUI cannot bind to it"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaPlayback =
                    (info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) != 0
                details += "foregroundServiceType includes mediaPlayback: $mediaPlayback"
                if (!mediaPlayback) problems += "foregroundServiceType does not declare mediaPlayback"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            details += "declared: no"
            problems += "PlaybackService is not declared in the manifest"
        }

        // The intent filter is what lets Media3 (and the system) find the
        // session service. Without it the service still runs, but nothing
        // that asks "who plays media here?" can see it.
        val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(
                Intent(MediaSessionService.SERVICE_INTERFACE).setPackage(ctx.packageName),
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(
                Intent(MediaSessionService.SERVICE_INTERFACE).setPackage(ctx.packageName),
                0,
            )
        }
        val ours = resolves.any { it.serviceInfo?.name == PlaybackService::class.java.name }
        details += "MediaSessionService intent filter resolves to PlaybackService: $ours"
        if (!ours) problems += "no MediaSessionService intent filter resolves to PlaybackService"

        return CheckResult(
            id = "service",
            title = "Playback service declared",
            status = if (problems.isEmpty()) CheckStatus.PASS else CheckStatus.FAIL,
            summary = if (problems.isEmpty()) {
                "The service is exported and advertises itself as a media session service"
            } else {
                problems.first()
            },
            details = details + problems.map { "problem: $it" },
        )
    }

    /**
     * Media3 only starts building notifications once the session is registered
     * with the service — which happens when a controller connects. If that
     * never happened, no media notification can exist, no matter what the
     * player is doing.
     */
    private fun pipelineCheck(): CheckResult {
        val events = PlaybackDiagnostics.snapshot()
        val armed = events.lastOrNull { it.tag == "session" && it.detail.startsWith("added") }
        val built = events.lastOrNull { it.tag == "provider" }
        val details = buildList {
            add("service events recorded: ${events.size}")
            armed?.let { add("session added at ${it.uptimeMs} ms uptime") }
            built?.let { add("provider built a notification at ${it.uptimeMs} ms uptime") }
            if (events.isEmpty()) add("nothing recorded yet — play a track, then run this again")
        }
        return CheckResult(
            id = "pipeline",
            title = "Media3 notification pipeline",
            status = when {
                armed == null -> CheckStatus.FAIL
                built == null -> CheckStatus.WARN
                else -> CheckStatus.PASS
            },
            summary = when {
                armed == null -> "The session was never added to the service — Media3 cannot build a notification"
                built == null -> "The session is registered, but no notification was built yet"
                else -> "The session is registered and Media3 built a notification"
            },
            details = details,
        )
    }

    private fun batteryCheck(): CheckResult {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
        return CheckResult(
            id = "battery",
            title = "Battery optimisation",
            status = if (ignoring) CheckStatus.PASS else CheckStatus.WARN,
            summary = if (ignoring) {
                "Mediyo is exempt from battery optimisation"
            } else {
                "Not exempt — aggressive OEM power saving can freeze the service"
            },
            details = listOf("isIgnoringBatteryOptimizations: $ignoring"),
        )
    }

    private fun playbackCheck(): CheckResult {
        val snapshot = hub.snapshot.value
        val state = player.playbackState
        val timeline = player.currentTimeline
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val stateName = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> state.toString()
        }
        return CheckResult(
            id = "playback",
            title = "Playback state",
            status = when {
                timeline.isEmpty -> CheckStatus.WARN
                state == Player.STATE_IDLE -> CheckStatus.WARN
                else -> CheckStatus.PASS
            },
            summary = when {
                timeline.isEmpty -> "No media item is loaded — start a track first"
                state == Player.STATE_IDLE -> "A track is loaded but the player is idle"
                else -> "${if (player.isPlaying) "Playing" else "Paused"} · $stateName · ${title.ifEmpty { "no title" }}"
            },
            details = listOf(
                "playbackState: $stateName",
                "playWhenReady: ${player.playWhenReady}",
                "isPlaying: ${player.isPlaying}",
                "timeline items: ${player.mediaItemCount}",
                "current media id: ${player.currentMediaItem?.mediaId ?: "none"}",
                "current title: ${title.ifEmpty { "none" }}",
                "duration: ${player.duration} ms",
                "hub hasTrack: ${snapshot.hasTrack}, isPlaying: ${snapshot.isPlaying}",
            ),
        )
    }

    /**
     * The decisive check. A foreground notification without a media session
     * token is the generic one; a notification *with* the token is the media
     * card. Seeing only the first is the bug this screen exists to catch.
     */
    private fun postedCheck(): CheckResult {
        val nm = ctx.getSystemService(NotificationManager::class.java)
            ?: return CheckResult(
                id = "posted",
                title = "Notification posted",
                status = CheckStatus.FAIL,
                summary = "No notification manager on this device",
            )
        val active = nm.activeNotifications
        val ours = active.filter { it.packageName == ctx.packageName }
        val record = ours.firstOrNull { it.id == PlaybackService.NOTIFICATION_ID }
        val mediaToken = record?.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        val details = buildList {
            add("our notifications: ${ours.size}")
            ours.forEach { sbn ->
                val n = sbn.notification
                // channelId only exists from Android 8; the report has to be
                // readable on a device that predates channels too.
                val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId else "n/a"
                add(
                    "id ${sbn.id}: channel=$channel, mediaSession=${n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION)}, " +
                        "ongoing=${sbn.isOngoing}, actions=${n.actions?.size ?: 0}, group=${n.group ?: "none"}, " +
                        "title=${n.extras?.getCharSequence(Notification.EXTRA_TITLE) ?: "none"}"
                )
            }
            if (record == null) add("no notification with id ${PlaybackService.NOTIFICATION_ID}")
        }
        return CheckResult(
            id = "posted",
            title = "Notification posted",
            status = when {
                record == null -> CheckStatus.FAIL
                mediaToken -> CheckStatus.PASS
                else -> CheckStatus.FAIL
            },
            summary = when {
                record == null -> "Nothing is posted for playback right now"
                mediaToken -> "The playback notification is posted with a media session token"
                else -> "Only the generic foreground notification is posted — no media session token"
            },
            details = details,
        )
    }

    private suspend fun controllerCheck(): CheckResult {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        return try {
            // The connection callback is delivered on the main thread, so the
            // wait has to happen off it or this would deadlock.
            val controller = withContext(Dispatchers.IO) { future.get(4, TimeUnit.SECONDS) }
            try {
                val timeline = controller.currentTimeline
                val state = controller.playbackState
                CheckResult(
                    id = "session",
                    title = "Media session reachable",
                    status = if (timeline.isEmpty) CheckStatus.WARN else CheckStatus.PASS,
                    summary = if (timeline.isEmpty) {
                        "A controller connected, but the session has no media items"
                    } else {
                        "A controller connected and sees ${timeline.windowCount} item(s), state $state"
                    },
                    details = listOf(
                        "isConnected: ${controller.isConnected}",
                        "playbackState: $state",
                        "timeline items: ${timeline.windowCount}",
                        "media id: ${controller.currentMediaItem?.mediaId ?: "none"}",
                        "service package: ${token.packageName}",
                        "note: this connection also arms Media3's notification pipeline on builds that had not armed it",
                    ),
                )
            } finally {
                controller.release()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            future.cancel(true)
            throw e
        } catch (e: Throwable) {
            future.cancel(true)
            CheckResult(
                id = "session",
                title = "Media session reachable",
                status = CheckStatus.FAIL,
                summary = "A controller could not connect to the playback service",
                details = listOf("${e.javaClass.name}: ${e.message}"),
            )
        }
    }

    // ── Report file ──────────────────────────────────────────────────────

    private fun writeReport(results: List<CheckResult>): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val events = PlaybackDiagnostics.snapshot()
        val logcat = logcatTail()
        val text = buildString {
            appendLine("Mediyo playback notification report")
            appendLine("generated  $stamp")
            appendLine("app        ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA}) · ${BuildConfig.CHANNEL}")
            appendLine("device     ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android    ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), target ${ctx.applicationInfo.targetSdkVersion}")
            appendLine("media3     ${BuildConfig.MEDIA3_VERSION}")
            appendLine()
            appendLine("── checks ─────────────────────────────────────────────")
            results.forEach { r ->
                appendLine("[${r.status}] ${r.title}")
                appendLine("        ${r.summary}")
                r.details.forEach { appendLine("        · $it") }
            }
            appendLine()
            appendLine("── playback timeline ─────────────────────────────────")
            if (events.isEmpty()) {
                appendLine("(nothing recorded — the playback service has not run in this process)")
            } else {
                val base = events.first().uptimeMs
                events.forEach { e ->
                    appendLine("+${"%8.3f".format((e.uptimeMs - base) / 1000f)}s  ${e.tag}: ${e.detail}")
                }
            }
            appendLine()
            appendLine("── logcat (this app) ─────────────────────────────────")
            appendLine(logcat.ifBlank { "(no matching log lines)" })
        }
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "playback-notification-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt")
        file.writeText(text)
        return file
    }

    private fun logcatTail(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "600").start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.lineSequence()
            .filter {
                it.contains("PlaybackService") || it.contains("PlayerVM") ||
                    it.contains("MediaSession") || it.contains("MediaNtfMng") ||
                    it.contains("ForegroundService")
            }
            .toList()
            .takeLast(160)
            .joinToString("\n")
    } catch (_: Throwable) {
        ""
    }

    // ── Small helpers ────────────────────────────────────────────────────

    private fun systemProperty(key: String): String? = try {
        val process = ProcessBuilder("getprop", key).start()
        val value = process.inputStream.bufferedReader().use { it.readLine() }?.trim()
        process.waitFor()
        value?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    private fun importanceName(importance: Int): String = when (importance) {
        NotificationManager.IMPORTANCE_NONE -> "NONE"
        NotificationManager.IMPORTANCE_MIN -> "MIN"
        NotificationManager.IMPORTANCE_LOW -> "LOW"
        NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
        NotificationManager.IMPORTANCE_HIGH -> "HIGH"
        NotificationManager.IMPORTANCE_MAX -> "MAX"
        else -> importance.toString()
    }

    /**
     * Not required for a media notification, but it is the permission OEM
     * skins ask for when their own media card is supposed to read sessions,
     * so a report without it is incomplete.
     */
    private fun notificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.contains(ctx.packageName)
    }
}
