package com.teamshryne.mediyo.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable fun MiniPlayer(state: PlayerState, onToggle: () -> Unit, onExpand: () -> Unit) {
    if (state.title.isEmpty()) return
    Card(
        onClick = onExpand,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = state.artwork, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onToggle) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null) }
        }
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable fun FullPlayer(state: PlayerState, onToggle: () -> Unit, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onCollapse, modifier = Modifier.align(Alignment.Start)) { Text("Minimize") }
        AsyncImage(model = state.artwork, contentDescription = null, modifier = Modifier.size(280.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
        Text(state.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Slider(value = state.progress, onValueChange = {}, modifier = Modifier.fillMaxWidth())
    }
}
