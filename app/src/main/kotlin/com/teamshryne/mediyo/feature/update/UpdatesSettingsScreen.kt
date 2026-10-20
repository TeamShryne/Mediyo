package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.teamshryne.mediyo.BuildConfig
import com.teamshryne.mediyo.data.update.UpdateChannel

/**
 * Picks the update track. The two are separate feeds rather than one feed
 * with a preference: nightlies are published as prereleases, so they never
 * appear in the stable feed at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelCard(
    channel: UpdateChannel,
    onSelect: (UpdateChannel) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Update channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UpdateChannel.entries.forEach { c ->
                    val selected = c == channel
                    Surface(
                        onClick = { onSelect(c) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            c.label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                when (channel) {
                    UpdateChannel.STABLE -> "Signed releases. Nightly builds are never pushed to this channel."
                    UpdateChannel.NIGHTLY -> "Daily builds from main. Untested by definition — only use this if you are happy to hit a broken build."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    nav: NavController? = null,
    vm: UpdateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val channel by vm.channel.collectAsState()
    val changelog by vm.changelog.collectAsState()

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
            item {
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
                            Text("Mediyo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                                    // A nightly build should say so: it is a build
                                    // of main, not a release.
                                    if (BuildConfig.CHANNEL == "nightly") " • nightly" else "",
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
            item {
                ChannelCard(
                    channel = channel,
                    onSelect = { vm.setChannel(it) },
                )
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
                        is UpdateViewModel.State.UpToDate -> "You're on the latest version" to MaterialTheme.colorScheme.primary
                        is UpdateViewModel.State.Error -> s.message to MaterialTheme.colorScheme.error
                        else -> null
                    }
                    msg?.let { (text, color) ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }
    }

    if (!BuildConfig.DEBUG && state is UpdateViewModel.State.Available) {
        UpdateDialog(vm)
    }
}
