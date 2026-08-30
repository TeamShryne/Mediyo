package com.teamshryne.mediyo.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.teamshryne.mediyo.core.design.DominantColors
import com.teamshryne.mediyo.core.design.GlowingLoadingTitle
import com.teamshryne.mediyo.core.design.MarqueeText
import com.teamshryne.mediyo.core.design.TrackMenuSheet
import com.teamshryne.mediyo.core.design.formatTime
import com.teamshryne.mediyo.core.design.immersiveBrush
import com.teamshryne.mediyo.core.design.rememberDominantColors
import com.teamshryne.mediyo.domain.model.ART_HERO_PX
import com.teamshryne.mediyo.domain.model.ART_ROW_PX
import com.teamshryne.mediyo.domain.model.thumbSized

// ─────────────────────────────────────────────────────────────────────────────
// Mini player — floating pill above the nav bar (Spotify style)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MiniPlayer(state: PlayerState, onToggle: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, sleepBadge: String? = null) {
    if (state.title.isEmpty()) return
    Card(
        onClick = onExpand,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
    ) {
        Column {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = state.artwork.thumbSized(ART_ROW_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(state.title, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onSurface)
                    MarqueeText(state.artist, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sleepBadge != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Bedtime, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(sleepBadge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            LinearProgressIndicator(
                progress = { state.progress },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full player — immersive, artwork-tinted (Apple Music / Spotify hybrid)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FullPlayer(
    state: PlayerState,
    contextLabel: String,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit = {},
    onShowComments: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    playerVm: PlayerViewModel? = null
) {
    val dominant: DominantColors = rememberDominantColors(state.artwork)
    var showAddSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val trackForMenu = remember(state.videoId, state.title, state.artist, state.artwork) {
        com.teamshryne.mediyo.domain.model.Track(
            videoId = state.videoId, title = state.title,
            artists = if (state.artist.isBlank()) emptyList() else listOf(state.artist),
            artworkUrl = state.artwork
        )
    }
    var liked by remember { mutableStateOf(false) }
    LaunchedEffect(state.videoId) {
        if (playerVm != null && state.videoId != null) {
            try {
                liked = playerVm.isCurrentLiked()
            } catch (_: Throwable) { liked = false }
        } else liked = false
    }
    // also collect flow for realtime updates when available
    if (playerVm != null && state.videoId != null) {
        val likedFlow = remember(state.videoId) { playerVm.isLikedFlow(state.videoId!!) }
        val likedCollect by likedFlow.collectAsState(initial = liked)
        LaunchedEffect(likedCollect) { liked = likedCollect }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(immersiveBrush(dominant))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {} // consume clicks, prevent pass-through to underlying nav
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
                Text(contextLabel, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
            }
            // sleep timer quick access + overflow
            val topSleep = playerVm?.sleepState?.collectAsState()?.value
            val topActive = topSleep?.isActive == true
            BadgedBox(badge = { if (topActive) Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(6.dp)) }) {
                IconButton(onClick = onShowSleepTimer) {
                    Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer", tint = if (topActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f))
                }
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White.copy(alpha = 0.85f))
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Artwork — no placeholder chrome while loading: the glowing song title
        // takes its place until the image actually decodes.
        val artShape = RoundedCornerShape(18.dp)
        val artPainter = rememberAsyncImagePainter(
            model = state.artwork.thumbSized(ART_HERO_PX),
            contentScale = ContentScale.Crop
        )
        val artReady = artPainter.state is AsyncImagePainter.State.Success
        val artAlpha by animateFloatAsState(
            targetValue = if (artReady) 1f else 0f,
            animationSpec = tween(350),
            label = "artAlpha"
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (artReady) {
                        Modifier
                            .shadow(32.dp, artShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(artShape)
                    } else Modifier
                )
        ) {
            Image(
                painter = artPainter,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(artAlpha)
            )
            if (!artReady) {
                GlowingLoadingTitle(
                    title = state.title,
                    artist = state.artist,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Title / artist / like
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                MarqueeText(state.title, MaterialTheme.typography.headlineSmall, Color.White)
                Spacer(Modifier.height(2.dp))
                MarqueeText(state.artist, MaterialTheme.typography.bodyMedium, Color.White.copy(alpha = 0.72f))
            }
            IconButton(onClick = {
                if (playerVm != null) playerVm.toggleLikeCurrent() else liked = !liked
            }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // quick actions row: Queue / Comments / Add / Sleep
        // collect sleep state for badge
        val sleepState = playerVm?.sleepState?.collectAsState()?.value
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onShowQueue) { Icon(Icons.Filled.QueueMusic, null, tint = Color.White.copy(0.85f)); Spacer(Modifier.width(6.dp)); Text("Queue", color = Color.White.copy(0.85f)) }
            TextButton(onClick = onShowComments) { Icon(Icons.Filled.Comment, null, tint = Color.White.copy(0.85f)); Spacer(Modifier.width(6.dp)); Text("Comments", color = Color.White.copy(0.85f)) }
            TextButton(onClick = { showAddSheet = true }) { Icon(Icons.Filled.PlaylistAdd, null, tint = Color.White.copy(0.85f)); Spacer(Modifier.width(6.dp)); Text("Add", color = Color.White.copy(0.85f)) }
            // Sleep timer with active indicator
            val sleepActive = sleepState?.isActive == true
            BadgedBox(badge = {
                if (sleepActive) Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp))
            }) {
                TextButton(onClick = onShowSleepTimer) {
                    Icon(Icons.Filled.Bedtime, null, tint = if (sleepActive) MaterialTheme.colorScheme.primary else Color.White.copy(0.85f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            !sleepActive -> "Sleep"
                            sleepState!!.mode.name == "TIMER" -> {
                                val s = sleepState.remainingMs / 1000
                                if (s >= 3600) "%d:%02d:%02d".format(s/3600, (s%3600)/60, s%60) else "%d:%02d".format(s/60, s%60)
                            }
                            sleepState.mode.name == "END_OF_TRACK" -> "EOT"
                            else -> "EOQ"
                        },
                        color = if (sleepActive) MaterialTheme.colorScheme.primary else Color.White.copy(0.85f)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Scrubber
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(0f) }
        val shown = if (dragging) dragValue else state.progress
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue); dragging = false },
            colors = SliderDefaults.colors(
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                thumbColor = Color.White,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().height(26.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val dur = state.durationMs
            Text(
                formatTime(if (dragging) (dur * dragValue).toLong() else state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(formatTime(dur), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        }

        // Transport controls
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shuffleActive by animateColorAsState(
                if (state.shuffle) Color.White else Color.White.copy(alpha = 0.55f), label = "shuffle"
            )
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = shuffleActive)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            FilledIconButton(
                onClick = onToggle,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.size(74.dp)
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Color.Black, strokeWidth = 2.5.dp)
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = onToggleRepeat) {
                Icon(
                    if (state.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatOne) Color.White else Color.White.copy(alpha = 0.55f)
                )
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Bottom utilities
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onShowQueue),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.queueSize > 1) {
                Text(
                    "${state.queueIndex + 1} of ${state.queueSize} • ${contextLabel} • Autoplay",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
            } else {
                Text(
                    state.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1
                )
            }
        }
        }
        if (showAddSheet && state.videoId != null) {
            com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet(track = trackForMenu, onDismiss = { showAddSheet = false })
        }
        if (showMenu && state.videoId != null) {
            // same overflow menu every other screen uses for tracks
            TrackMenuSheet(
                track = trackForMenu,
                show = true,
                onDismiss = { showMenu = false },
                isLiked = liked,
                onLike = { if (playerVm != null) playerVm.toggleLikeCurrent() else liked = !liked },
                onAddToPlaylist = { showAddSheet = true },
                onPlayNext = { playerVm?.addNext(trackForMenu) },
                onAddToQueue = { playerVm?.addToQueue(trackForMenu) },
                onComments = { onShowComments() }
            )
        }
    }
}
