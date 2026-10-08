package com.teamshryne.mediyo.crash

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Crash capture. Installed once from `MediyoApp.onCreate`.
 *
 * Design notes:
 * - Zero cost while running: nothing here executes until a thread actually
 *   dies. Logs are pulled from the system buffer *at crash time* rather than
 *   intercepted per call, so there is no per-log cost and no code churn.
 * - The handler always chains to the previous/default handler, so Play Console
 *   and any other installed handler still see the crash.
 * - Everything runs under hard time budgets. A crash reporter that hangs is
 *   worse than no reporter, so every blocking read is capped and we degrade to
 *   "less logs" rather than "no screen".
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val DIR = "crashes"
    /** Lines of app log pulled from the system buffer. */
    private const val LOG_LINES = 1500
    /** Hard cap on the logcat read. Exceeding it logs what we have. */
    private const val LOGCAT_BUDGET_MS = 2500L
    /** Hard cap on writing the report file. */
    private const val WRITE_BUDGET_MS = 1500L
    /** Same crash this many times in a row: stop relaunching, just die. */
    private const val LOOP_LIMIT = 3
    private const val LOOP_WINDOW_MS = 60_000L
    private const val PREFS = "crash_state"

    /** Set by the activity so a crash inside the crash screen can't loop. */
    @Volatile
    private var crashScreenShowing = false

    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                handle(app, thread, error)
            } catch (_: Throwable) {
                // A failure inside the reporter must never mask the real crash.
            } finally {
                // Always hand the crash to the platform (Play Console, etc).
                previous?.uncaughtException(thread, error) ?: run {
                    Process.killProcess(Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    private fun handle(app: Context, thread: Thread, error: Throwable) {
        val dir = File(app.cacheDir, DIR).apply { mkdirs() }
        pruneOld(dir)

        val report = buildReport(app, thread, error)
        val file = File(dir, "crash-${System.currentTimeMillis()}.log")
        writeCapped(file, report)

        // Mark it pending so a crash that also kills the relaunch (OOM, or a
        // crash inside the crash screen) still surfaces next app start.
        try {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING, file.absolutePath)
                .apply()
        } catch (_: Throwable) { }

        if (crashScreenShowing || isCrashLoop(app, error)) {
            // Relaunching into the same crash would trap the user in a loop.
            return
        }
        crashScreenShowing = true
        try {
            val intent = Intent(app, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashActivity.EXTRA_PATH, file.absolutePath)
            }
            app.startActivity(intent)
        } catch (_: Throwable) {
            // No launcher available (rare) — the file is still on disk.
        }
    }

    /** Most recent report, if any, so a crash that killed its own screen still shows. */
    fun pendingReport(context: Context): File? {
        val prefs = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            return null
        }
        // Read first: clearing before reading would throw the report away.
        val named = prefs.getString(KEY_PENDING, null)
        prefs.edit().remove(KEY_PENDING).apply()
        return named?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
    }

    fun latestReport(context: Context): File? =
        crashDir(context).listFiles { f -> f.isFile && f.name.startsWith("crash-") }
            ?.maxByOrNull { it.lastModified() }

    fun crashDir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    fun markScreenShowing() { crashScreenShowing = true }

    /** Keeps a few reports around; logs are small but unbounded if never pruned. */
    private fun pruneOld(dir: File) {
        try {
            dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
        } catch (_: Throwable) { }
    }

    /**
     * Same exception class within a minute means a deterministic crash: show
     * the screen once, then let it die rather than looping forever.
     */
    private fun isCrashLoop(app: Context, error: Throwable): Boolean {
        return try {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val sig = error.javaClass.name
            val same = prefs.getString(KEY_LOOP_SIG, null) == sig
            val count = if (same && now - prefs.getLong(KEY_LOOP_AT, 0L) < LOOP_WINDOW_MS) {
                prefs.getInt(KEY_LOOP_COUNT, 0) + 1
            } else {
                1
            }
            prefs.edit()
                .putString(KEY_LOOP_SIG, sig)
                .putLong(KEY_LOOP_AT, now)
                .putInt(KEY_LOOP_COUNT, count)
                .apply()
            count >= LOOP_LIMIT
        } catch (_: Throwable) {
            false
        }
    }

    // ── report building ──────────────────────────────────────────────────

    private fun buildReport(app: Context, thread: Thread, error: Throwable): String = buildString {
        appendLine("Mediyo crash report")
        appendLine("===================")
        appendLine("When      : ${ts()}")
        appendLine("Where     : ${appCrash(app)}")
        appendLine()
        appendLine("Device")
        appendLine("------")
        appendLine("Manufacturer : ${Build.MANUFACTURER}")
        appendLine("Brand        : ${Build.BRAND}")
        appendLine("Model        : ${Build.MODEL}")
        appendLine("Device       : ${Build.DEVICE}")
        appendLine("Android      : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABI          : ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Locale       : ${Locale.getDefault()}")
        appendLine("Fingerprint  : ${Build.FINGERPRINT}")
        appendLine()
        appendLine("Crash")
        appendLine("-----")
        appendLine("Thread  : ${thread.name}")
        appendLine("Type    : ${error.javaClass.name}")
        appendLine("Message : ${error.message ?: "(none)"}")
        appendLine()
        appendLine(stackTrace(error))
        appendLine()
        appendLine("App log (last $LOG_LINES lines)")
        appendLine("---------------------------")
        appendLine(pullLogcat())
    }

    private fun stackTrace(error: Throwable): String = try {
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        sw.toString()
    } catch (_: Throwable) {
        error.javaClass.name + ": " + (error.message ?: "")
    }

    /**
     * Reads our own process's logcat buffer. Free (no per-call interception),
     * needs no permission (same UID reads its own logs), and is capped by both
     * a line count and a wall-clock budget.
     */
    @SuppressLint("NewApi")
    private fun pullLogcat(): String = try {
        val cmd = arrayOf("logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString(), "--pid=${Process.myPid()}")
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = proc.inputStream.bufferedReader()
        val readerThread = Thread {
            try {
                reader.forEachLine { line ->
                    synchronized(out) { out.appendLine(line) }
                }
            } catch (_: Throwable) { }
        }
        readerThread.isDaemon = true
        readerThread.start()
        // Hard budget: a wedged logcat must not wedge the crash screen.
        proc.waitFor(LOGCAT_BUDGET_MS, TimeUnit.MILLISECONDS)
        readerThread.join(200)
        val text = synchronized(out) { out.toString() }
        if (text.isBlank()) "(logcat returned nothing — device may block log access)" else text
    } catch (e: Throwable) {
        "(logcat unavailable: ${e.javaClass.simpleName})"
    }

    /** Writes with a budget: a full disk shouldn't hang the dying process. */
    private fun writeCapped(file: File, text: String) {
        try {
            val t = Thread {
                try {
                    file.writeText(text)
                } catch (_: Throwable) { }
            }
            t.isDaemon = true
            t.start()
            t.join(WRITE_BUDGET_MS)
        } catch (_: Throwable) { }
    }

    private fun ts(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

    private fun appCrash(app: Context): String = try {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${app.packageName} v${info.versionName} ($code)"
    } catch (_: Throwable) {
        app.packageName
    }

    const val KEY_PENDING = "pending_report"
    private const val KEY_LOOP_SIG = "loop_sig"
    private const val KEY_LOOP_AT = "loop_at"
    private const val KEY_LOOP_COUNT = "loop_count"
}
