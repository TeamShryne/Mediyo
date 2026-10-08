package com.teamshryne.mediyo.crash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder

/**
 * Crash screen: what crashed, the log, and a one-tap issue report.
 *
 * The issue link carries device info plus the log in the body — a browser
 * cannot attach a file, so inlining is the honest version of "attached".
 * The full file is one tap away via share/copy for the rare big report.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.markScreenShowing()

        // A crash that killed its own relaunch still lands here.
        val file: File? = intent.getStringExtra(EXTRA_PATH)?.let { File(it) }
            ?.takeIf { it.isFile }
            ?: CrashHandler.pendingReport(this)
            ?: CrashHandler.latestReport(this)
        val text = try {
            file?.readText().orEmpty()
        } catch (_: Throwable) {
            "(could not read the crash report)"
        }

        setContent {
            MediyoCrashTheme {
                CrashScreen(
                    report = text,
                    file = file,
                    onReport = { openIssue(text) },
                    onShare = { shareReport(file) },
                    onCopy = { copyReport(text) },
                    onClose = { finishAndExit() }
                )
            }
        }
    }

    private fun finishAndExit() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun openIssue(report: String) {
        val title = issueTitle(report)
        val body = issueBody(report)
        val url = "https://github.com/$REPO/issues/new?title=" +
            enc(title) + "&body=" + enc(body) + "&labels=crash"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser: fall back to the clipboard so nothing is lost.
            copyReport(body)
        }
    }

    private fun shareReport(file: File?) {
        if (file == null || !file.isFile) return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mediyo crash log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share crash log"))
        } catch (_: Throwable) {
            // Provider misconfigured or no target app — clipboard still works.
        }
    }

    private fun copyReport(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Mediyo crash log", text))
        } catch (_: Throwable) { }
    }

    companion object {
        const val EXTRA_PATH = "crash_path"
        private const val REPO = "TeamShryne/Mediyo"

        /**
         * Browsers and Android intents get unreliable past a few KB, so the
         * body is budgeted: metadata always, log newest-first from the end
         * (the crash itself is at the tail), and an explicit note when the
         * tail was cut so the reporter knows a longer log exists.
         */
        private const val BODY_BUDGET = 6000

        fun issueTitle(report: String): String {
            val type = report.lineSequence()
                .firstOrNull { it.startsWith("Type    :") }
                ?.substringAfter(":")?.trim().orEmpty()
            val msg = report.lineSequence()
                .firstOrNull { it.startsWith("Message :") }
                ?.substringAfter(":")?.trim().orEmpty()
            val short = msg.take(80).ifBlank { "no message" }
            return if (type.isBlank()) "Crash: $short" else "Crash: ${type.substringAfterLast('.')}: $short"
        }

        fun issueBody(report: String): String {
            val head = report.substringBefore("App log (last").trimEnd()
            val log = report.substringAfter("---------------------------", "").trimStart()
            val tail = if (log.length > BODY_BUDGET) {
                val cut = log.takeLast(BODY_BUDGET)
                "(earlier lines trimmed here — attach the full .log file from the crash screen if needed)\n\n$cut"
            } else {
                log
            }
            return buildString {
                appendLine("<!-- Prefilled by Mediyo's crash reporter. -->")
                appendLine("**What I was doing:** <!-- please fill in -->")
                appendLine()
                appendLine("### Crash details")
                appendLine("```")
                appendLine(head)
                appendLine("```")
                appendLine()
                appendLine("### App log")
                appendLine("```")
                appendLine(tail)
                appendLine("```")
            }
        }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}

@Composable
private fun CrashScreen(
    report: String,
    file: File?,
    onReport: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1800)
            copied = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Mediyo crashed", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Sorry about this. The report below is ready to send — it includes your device details and the app log leading up to the crash.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                Text("Report this crash")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Text(if (copied) " Copied" else " Copy log")
                }
                OutlinedButton(
                    onClick = onShare,
                    enabled = file != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, null)
                    Text(" Share file")
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    report.ifBlank { "(no report text)" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            Text(
                file?.let { "${it.name} • ${it.length() / 1024} KB" } ?: "No report file",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun MediyoCrashTheme(content: @Composable () -> Unit) {
    // Reuse the app theme so the screen doesn't flash a different palette.
    com.teamshryne.mediyo.core.design.MediyoTheme {
        content()
    }
}
