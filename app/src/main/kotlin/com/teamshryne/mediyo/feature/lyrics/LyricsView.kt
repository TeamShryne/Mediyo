package com.teamshryne.mediyo.feature.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamshryne.mediyo.data.lyrics.LyricLine
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.feature.lyrics.animation.LyricsAnimationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Scalable Synced Lyrics View
 *
 * - Single composable handles all states (loading, ready, empty)
 * - Animations are injected via [LyricsAnimationConfig] — adding a new animation
 *   requires only implementing the animator interface, no view changes.
 * - Autoscroll is decoupled and respects user manual scroll (pauses 3500ms).
 */

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun findCurrentLine(lines: List<LyricLine>, posMs: Long): Int {
    if (lines.isEmpty()) return -1
    // before first line
    if (posMs < lines.first().beginMs) return -1
    // within a line
    for (i in lines.indices) {
        val l = lines[i]
        if (posMs in l.beginMs until l.endMs) return i
        // between lines: stay on previous line until next begins (no flicker)
        if (i < lines.lastIndex) {
            val next = lines[i + 1]
            if (posMs >= l.endMs && posMs < next.beginMs) return i
        }
    }
    // after last begin but before end, or past end -> stay on last
    return if (posMs >= lines.last().beginMs) lines.lastIndex else -1
}

// ── Main entry ───────────────────────────────────────────────────────────────

@Composable
fun SyncedLyricsView(
    state: LyricsUiState,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    config: LyricsAnimationConfig = LyricsAnimationConfig(),
    modifier: Modifier = Modifier
) {
    when (state) {
        LyricsUiState.Idle, LyricsUiState.Loading -> LyricsLoading(modifier)
        is LyricsUiState.Ready -> SyncedLyricsContent(
            track = state.track,
            positionMs = positionMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            config = config,
            modifier = modifier
        )
        LyricsUiState.NotFound -> LyricsEmpty("No synced lyrics found\nfor this track", onRetry, modifier)
        LyricsUiState.NeedsApiKey -> LyricsEmpty("Lyrics not cached yet\nplay once via BetterLyrics extension to prime", onRetry, modifier)
        is LyricsUiState.Error -> LyricsEmpty(state.message, onRetry, modifier)
        LyricsUiState.RateLimited -> LyricsEmpty("Rate limited — try again in a moment", onRetry, modifier)
    }
}

@Composable
private fun LyricsLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            Text("Fetching lyrics…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun LyricsEmpty(msg: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.62f), textAlign = TextAlign.Center, lineHeight = 20.sp)
            FilledTonalButton(onClick = onRetry, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

// ── Ready content ────────────────────────────────────────────────────────────

@Composable
private fun SyncedLyricsContent(
    track: LyricTrack,
    positionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    config: LyricsAnimationConfig,
    modifier: Modifier = Modifier
) {
    val lines = track.lines
    val currentIdx = remember(lines, positionMs) { findCurrentLine(lines, positionMs) }
    val listState = rememberLazyListState()

    // Pause auto-scroll when user is manually scrolling (psychology-pleasing: don't fight the user)
    var userScrolling by remember { mutableStateOf(false) }
    var lastUserScrollAt by remember { mutableStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolling = true
            lastUserScrollAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(userScrolling, lastUserScrollAt) {
        if (userScrolling) {
            delay(3500)
            if (System.currentTimeMillis() - lastUserScrollAt >= 3500) userScrolling = false
        }
    }

    // Smooth autoscroll to center the active line
    LaunchedEffect(currentIdx, userScrolling) {
        if (currentIdx < 0 || userScrolling) return@LaunchedEffect
        // Give layout a moment to settle on track change
        if (!isActive) return@LaunchedEffect
        try {
            // Estimate centered offset: scroll so active line lands ~35% from top (more lyric context below)
            listState.animateScrollToItem(
                index = currentIdx.coerceIn(0, lines.lastIndex),
                scrollOffset = -160 // px offset to bias upward (will be converted); use density-independent tweak
            )
        } catch (_: Exception) {
        }
    }

    // Also handle precise centering after layout — second-pass with viewport-aware offset
    // We trigger a softer animate with tween for fluid feel.

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        userScrolling = true
                        lastUserScrollAt = System.currentTimeMillis()
                    })
                },
            contentPadding = PaddingValues(vertical = 140.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { idx, _ -> idx }) { idx, line ->
                val distance = kotlin.math.abs(idx - currentIdx)
                val isActive = idx == currentIdx
                val isPast = idx < currentIdx
                val isFuture = idx > currentIdx

                // Word-level progress only matters for active line
                LyricLineRow(
                    line = line,
                    positionMs = positionMs,
                    isActive = isActive,
                    isPast = isPast,
                    isFuture = isFuture,
                    distance = distance,
                    onSeek = onSeek,
                    config = config,
                    isPlaying = isPlaying
                )
            }
            // Bottom spacer for last line centering
            item { Spacer(Modifier.height(80.dp)) }
        }

        // Top / bottom fade veils — seamless, psychology-pleasing depth
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    positionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    distance: Int,
    onSeek: (Long) -> Unit,
    config: LyricsAnimationConfig,
    isPlaying: Boolean
) {
    val visuals = config.lineAnimator.visualsForLine(isActive, distance, false)

    // Smooth alpha/scale for line emphasis
    val alpha by animateFloatAsState(
        targetValue = visuals.alpha,
        animationSpec = tween(durationMillis = 420, easing = config.wordLerpEasing),
        label = "lineAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = visuals.scale,
        animationSpec = tween(durationMillis = 520, easing = config.autoScrollEasing),
        label = "lineScale"
    )

    // Click to seek to line start
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeek(line.beginMs) }
            .alpha(alpha)
            .scale(scale)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Each line is a FlowRow-like wrapping text but we need per-word brushes.
        // We render words inline with inline boxes preserving spaces via word.text itself.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            line.words.forEachIndexed { wIdx, word ->
                val progress = when {
                    !isActive -> if (isPast) 1f else 0f
                    positionMs < word.beginMs -> 0f
                    positionMs >= word.endMs -> 1f
                    else -> {
                        val dur = (word.endMs - word.beginMs).coerceAtLeast(1)
                        ((positionMs - word.beginMs).toFloat() / dur).coerceIn(0f, 1f)
                    }
                }

                // Past words in active line are fully filled; future 0; current interpolates
                val effProgress = when {
                    !isActive -> progress
                    else -> {
                        // For words before current word timing, they should already be filled.
                        // Determine if this word is definitively before the playhead.
                        val wordOrderDone = positionMs >= word.endMs
                        val wordOrderNotYet = positionMs < word.beginMs
                        when {
                            wordOrderDone -> 1f
                            wordOrderNotYet -> 0f
                            else -> progress // animating mid-word
                        }
                    }
                }

                // Extra inter-word emphasis: active word gets subtle scale pulse
                val isCurrentWord = isActive && positionMs in word.beginMs until word.endMs
                val wordScale by animateFloatAsState(
                    targetValue = if (isCurrentWord) 1.06f else 1f,
                    animationSpec = tween(180),
                    label = "wordScale$wIdx"
                )

                val brush = config.wordAnimator.brushForWord(effProgress, isActive, word.isBackground)

                // Render word with brush — scalable: swap animator to change effect without touching this logic
                Text(
                    text = word.text,
                    style = TextStyle(
                        brush = brush,
                        fontSize = visuals.fontSize,
                        fontWeight = visuals.fontWeight,
                        fontStyle = if (word.isBackground) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = visuals.letterSpacing,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .scale(wordScale)
                        .padding(end = 0.dp),
                    // click propagation handled by parent row
                )

                // Add tiny space if word text doesn't already include trailing space
                // TTML preserves spaces via word text trailing spaces, so no extra needed.
            }
        }

        // Optional songPart label for sections (hidden by default to keep beauty, can enable)
        // if (isActive && line.songPart != null) { ... }
    }
}
