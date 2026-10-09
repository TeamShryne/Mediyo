package com.teamshryne.mediyo.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamshryne.mediyo.data.cache.StoragePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Storage & downloads: one honest page.
 * - Song cache (transient): everything streamed is kept up to the limit,
 *   oldest evicted first. Replays and seek-backs cost zero data.
 * - Downloads (permanent): only what the user explicitly downloads, never
 *   evicted, playable offline.
 * Size changes apply on next start (the evictor is sized once) — said in
 * the UI instead of surprising the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    nav: androidx.navigation.NavController? = null,
    vm: DownloadVm = hiltViewModel()
) {
    val ctx = LocalContext.current
    val settings by vm.storageSettings.collectAsState(initial = com.teamshryne.mediyo.data.cache.SongCacheSettings())
    val sizes by vm.sizes.collectAsState()
    val tracks by vm.tracks.collectAsState(initial = emptyList())

    // Live sizes while open.
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.refreshSizes()
            delay(2000)
        }
    }

    val steps = remember { StoragePrefs.SIZE_STEPS_MB }
    val stepIndex = remember(settings.maxSongCacheMb) {
        steps.indexOf(settings.maxSongCacheMb).takeIf { it >= 0 } ?: steps.indexOf(1024)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Storage & downloads") },
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
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Song cache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Keeps recently played songs for instant replay and offline. Oldest removed first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Enabled",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = settings.enableSongCache, onCheckedChange = vm::setCacheEnabled)
                        }
                        val limitBytes = settings.maxSongCacheMb * 1024 * 1024L
                        Text(
                            if (settings.maxSongCacheMb == 0) "Off"
                            else Formatter.formatShortFileSize(ctx, limitBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = stepIndex.toFloat(),
                            onValueChange = { vm.setMaxMb(steps[it.roundToInt().coerceIn(steps.indices)]) },
                            valueRange = 0f..(steps.size - 1).toFloat(),
                            steps = steps.size - 2,
                            enabled = settings.enableSongCache,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (limitBytes > 0) (sizes.songBytes.toFloat() / limitBytes).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${Formatter.formatShortFileSize(ctx, sizes.songBytes)} used" +
                                if (settings.maxSongCacheMb > 0) " of ${Formatter.formatShortFileSize(ctx, limitBytes)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Size changes apply after restarting the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        TextButton(onClick = vm::clearSongCache, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear song cache")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Songs you explicitly download. Never auto-removed, playable offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${tracks.size} songs • ${Formatter.formatShortFileSize(ctx, sizes.downloadBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { nav?.navigate("downloads") }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open downloads")
                            }
                        }
                        TextButton(onClick = { vm.removeAll() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Remove all downloads")
                        }
                    }
                }
            }
        }
    }
}
