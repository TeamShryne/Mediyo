package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.data.update.UpdateChannel

/**
 * What this install is, and where its updates come from.
 *
 * The screen is ordered by how often each part matters: what you are
 * running, where it comes from, what changed, and then the one action that
 * does something. Nothing here is a surprise to anyone who did not ask for
 * it, which is the point of the channel picker below being an opt-in
 * rather than a switch somebody else flipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    nav: NavController? = null,
    vm: UpdateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val channel by vm.channel.collectAsState()
    val changelog by vm.changelog.collectAsState()
    // Turning the nightly channel on is a question the screen asks, and the
    // answer is the user's. It stays a local question until they answer it.
    var askForNightly by remember { mutableStateOf(false) }

    // The notes are worth having without being asked for, and they are small:
    // load them when the screen opens, once.
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) vm.loadChangelog()
    }

    Scaffold(
        // Zero insets: nested inside the outer app Scaffold, whose padding
        // already clears the status bar (same fix as LyricsSettingsScreen).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("App updates") },
                navigationIcon = {
                    IconButton(onClick = { nav?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { InstalledCard(state = state) }
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text(
                        "Update channel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(10.dp))
                    when (val ch = channel) {
                        null -> ChannelPlaceholder()
                        else -> ChannelPicker(
                            channel = ch,
                            onSelect = { vm.setChannel(it) },
                            onRequestNightly = { askForNightly = true },
                            onLeaveNightly = { vm.setChannel(UpdateChannel.STABLE) },
                        )
                    }
                }
            }
            item {
                WhatsNewCard(
                    state = changelog,
                    onReload = { vm.loadChangelog() },
                )
            }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { vm.manualCheck() },
                        enabled = !BuildConfig.DEBUG && state !is UpdateViewModel.State.Checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                        Text(if (BuildConfig.DEBUG) "Updates disabled in debug" else "Check for updates")
                    }
                    val msg = when (val s = state) {
                        is UpdateViewModel.State.UpToDate -> when {
                            // A nightly build is numbered above the release it
                            // is built on, so going back to Stable does not
                            // take the app back: the next release does.
                            channel == UpdateChannel.NIGHTLY -> "You're on the latest nightly build"
                            BuildConfig.CHANNEL == "nightly" ->
                                "This is a nightly build. The next stable release replaces it."
                            else -> "You're on the latest version"
                        }
                        is UpdateViewModel.State.Error -> s.message
                        else -> null
                    }
                    msg?.let { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state is UpdateViewModel.State.Error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (askForNightly) {
        NightlyOptInDialog(
            onConfirm = {
                askForNightly = false
                vm.setChannel(UpdateChannel.NIGHTLY)
            },
            onDismiss = { askForNightly = false },
        )
    }
}

/**
 * The app as it is installed right now: which build, and whether a newer
 * one is waiting.
 */
@Composable
private fun InstalledCard(state: UpdateViewModel.State) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.SystemUpdate, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mediyo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    // A nightly build should say so: it is a build of main,
                    // not a release, and the person reading this is the one
                    // who has to know which one they are running.
                    if (BuildConfig.CHANNEL == "nightly") {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                        ) {
                            Text(
                                "Nightly build",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (val s = state) {
                is UpdateViewModel.State.Checking -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                is UpdateViewModel.State.UpToDate ->
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                is UpdateViewModel.State.Error ->
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                else -> Unit
            }
        }
    }
}
