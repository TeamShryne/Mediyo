package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.teamshryne.mediyo.BuildConfig

/**
 * Shown on startup (via AppShell) and from the App updates settings page
 * whenever an update is Available. Handles download → verify → install.
 */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val s = state as? UpdateViewModel.State.Available ?: return
    val updater = vm.updater

    AlertDialog(
        onDismissRequest = { if (!s.downloading) vm.dismiss() },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Mediyo v${s.info.version} is ready (you're on v${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodyMedium
                )
                when {
                    s.downloaded != null -> Text(
                        "Downloaded and verified. Install now?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    s.downloading -> {
                        if (s.progress == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            if (s.progress == null) "Downloading…" else "Downloading… ${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> Text(
                        "Downloads from GitHub and verifies the file before installing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                s.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                s.downloaded != null -> Button(onClick = {
                    if (updater.needsUnknownSourcesPermission()) {
                        context.startActivity(updater.unknownSourcesIntent())
                    } else {
                        context.startActivity(updater.installIntent(s.downloaded))
                    }
                }) { Text("Install") }
                !s.downloading -> Button(onClick = { vm.download() }) { Text("Download") }
            }
        },
        dismissButton = {
            if (!s.downloading) {
                TextButton(onClick = { vm.dismiss() }) { Text("Later") }
            } else {
                Spacer(Modifier.height(0.dp))
            }
        }
    )
}
