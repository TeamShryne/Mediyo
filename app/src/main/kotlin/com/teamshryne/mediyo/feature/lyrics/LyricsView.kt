package com.teamshryne.mediyo.feature.lyrics

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamshryne.mediyo.core.design.fadingEdge
import com.teamshryne.mediyo.data.lyrics.LyricLine
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.feature.lyrics.animation.LyricsAnimationConfig
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun findCurrentLine(lines: List<LyricLine>, posMs: Long): Int {
    if (lines.isEmpty()) return -1
    if (posMs < lines.first().beginMs) return -1
    for (i in lines.indices) {
        val l = lines[i]
        if (posMs in l.beginMs until l.endMs) return i
        if (i < lines.lastIndex) {
            val next = lines[i + 1]
            if (posMs >= l.endMs && posMs < next.beginMs) return i
        }
    }
    return if (posMs >= lines.last().beginMs) lines.lastIndex else -1
}

// ── Entry ────────────────────────────────────────────────────────────────────

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
        LyricsUiState.NotFound -> LyricsEmpty("No synced lyrics for this track", onRetry, modifier)
        LyricsUiState.NeedsApiKey -> LyricsEmpty("Not cached yet\nPlay once via BetterLyrics extension to prime cache", onRetry, modifier)
        is LyricsUiState.Error -> LyricsEmpty(state.message, onRetry, modifier)
        LyricsUiState.RateLimited -> LyricsEmpty("Rate limited — try again shortly", onRetry, modifier)
    }
}

@Composable
private fun LyricsLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = androidx.compose.ui.Modifier.padding(bottom = 12.dp))
            Text("Fetching lyrics…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LyricsEmpty(msg: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onRetry, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)) {
                Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp))
                Text("Retry")
            }
        }
    }
}

// ── Core content — cloned from Metrolist ExperimentalLyrics anchor physics ───

private const val ANCHOR_RATIO = 0.35f
private val FALLBACK_H_DP = 64.dp
private val GAP_DP = 14.dp
private val FADE_TOP_DP = 90.dp
private val FADE_BOTTOM_DP = 120.dp

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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // smoothPosition — frame-level interpolation like Metrolist (withFrameNanos)
    // Use UpdatedState so the frame loop sees fresh positionMs/isPlaying without restarting
    val posState by rememberUpdatedState(positionMs)
    val playingState by rememberUpdatedState(isPlaying)
    var smoothPos by remember { mutableLongStateOf(positionMs) }
    var lastPlayerPos by remember { mutableLongStateOf(positionMs) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(positionMs, isPlaying) {
        // any external tick/seek/paused update instantly re-bases
        lastPlayerPos = positionMs
        lastUpdateTime = System.currentTimeMillis()
        smoothPos = positionMs
    }
    LaunchedEffect(track) {
        // fresh track → reset
        lastPlayerPos = posState
        lastUpdateTime = System.currentTimeMillis()
        smoothPos = posState
        while (isActive) {
            withFrameNanos { _ ->
                val now = System.currentTimeMillis()
                val curPos = posState
                val curPlaying = playingState
                // detect tick or seek jump (position jumps > 800ms or new value)
                if (curPos != lastPlayerPos) {
                    lastPlayerPos = curPos
                    lastUpdateTime = now
                    smoothPos = curPos
                } else if (curPlaying) {
                    val elapsed = now - lastUpdateTime
                    smoothPos = lastPlayerPos + elapsed
                } else {
                    smoothPos = lastPlayerPos
                }
            }
        }
    }

    var currentIdx by remember { mutableIntStateOf(findCurrentLine(lines, positionMs)) }
    LaunchedEffect(smoothPos, lines) {
        currentIdx = findCurrentLine(lines, smoothPos)
    }

    // heights & positions — Metrolist anchor logic
    val itemHeights = remember(track) { mutableStateMapOf<Int, Int>() }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var userManualOffset by remember { mutableFloatStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val decaySpec = remember { exponentialDecay<Float>(frictionMultiplier = 1.8f) }
    var isInitialLayout by remember(track) { mutableStateOf(true) }

    LaunchedEffect(track) {
        isAutoScrollEnabled = true
        userManualOffset = 0f
        isInitialLayout = true
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .fadingEdge(top = FADE_TOP_DP, bottom = FADE_BOTTOM_DP)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        val boxConstraints = constraints
        val maxHeightPx = boxConstraints.maxHeight.toFloat()
        val maxWidthPx = boxConstraints.maxWidth
        val anchorY = maxHeightPx * ANCHOR_RATIO
        val fallbackH = with(density) { FALLBACK_H_DP.toPx() }
        val gapPx = with(density) { GAP_DP.toPx() }

        // positions map relative to active index
        val positions = remember(itemHeights.toMap(), currentIdx, lines) {
            val map = mutableMapOf<Int, Float>()
            if (currentIdx == -1 || lines.isEmpty()) return@remember map
            map[currentIdx] = 0f
            var y = 0f
            for (i in currentIdx - 1 downTo 0) {
                val h = itemHeights[i]?.toFloat() ?: fallbackH
                y -= (h + gapPx)
                map[i] = y
            }
            y = 0f
            for (i in currentIdx until lines.size - 1) {
                val h = itemHeights[i]?.toFloat() ?: fallbackH
                y += (h + gapPx)
                map[i + 1] = y
            }
            map
        }

        val minOffset = remember(itemHeights.toMap(), lines, currentIdx, anchorY) {
            if (lines.isEmpty() || currentIdx == -1) return@remember 0f
            var totalBelow = 0f
            for (i in currentIdx until lines.size - 1) {
                val h = itemHeights[i]?.toFloat() ?: fallbackH
                totalBelow += h + gapPx
            }
            val lastH = itemHeights[lines.size - 1]?.toFloat() ?: fallbackH
            with(density) { 80.dp.toPx() } - anchorY - totalBelow - lastH
        }
        val maxOffset = remember(itemHeights.toMap(), lines, currentIdx, maxHeightPx, anchorY) {
            if (lines.isEmpty() || currentIdx == -1) return@remember 0f
            var totalAbove = 0f
            for (i in 0 until currentIdx) {
                val h = itemHeights[i]?.toFloat() ?: fallbackH
                totalAbove += h + gapPx
            }
            maxHeightPx - with(density) { 80.dp.toPx() } - anchorY + totalAbove
        }
        val clampMin = minOf(minOffset, maxOffset)
        val clampMax = maxOf(minOffset, maxOffset)

        LaunchedEffect(clampMin, clampMax) {
            userManualOffset = userManualOffset.coerceIn(clampMin, clampMax)
        }
        LaunchedEffect(isAutoScrollEnabled) {
            if (isAutoScrollEnabled && kotlin.math.abs(userManualOffset) > 1f) {
                val start = userManualOffset
                val anim = androidx.compose.animation.core.Animatable(start)
                var last = start
                anim.animateTo(0f, tween((abs(start) / 4f).toInt().coerceIn(200, 600), easing = FastOutSlowInEasing)) {
                    userManualOffset += (value - last)
                    last = value
                }
                userManualOffset = 0f
            }
        }

        // auto-scroll without LazyColumn — direct offset per line, staggered
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                            // Drag source in Compose 1.6 is `Drag`, newer is UserInput — handle both via != Fling
                            isAutoScrollEnabled = false
                            return super.onPostScroll(consumed, available, source)
                        }
                        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            isAutoScrollEnabled = false
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (isInitialLayout) continue
                            velocityTracker.resetTracking()
                            isAutoScrollEnabled = false
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            verticalDrag(down.id) { change ->
                                userManualOffset = (userManualOffset + change.positionChange().y).coerceIn(clampMin, clampMax)
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                            }
                            val vy = velocityTracker.calculateVelocity().y
                            scope.launch {
                                val animState = AnimationState(initialValue = userManualOffset, initialVelocity = vy)
                                animState.animateDecay(decaySpec) {
                                    val clamped = value.coerceIn(clampMin, clampMax)
                                    userManualOffset = clamped
                                    if (value != clamped) cancelAnimation()
                                }
                            }
                        }
                    }
                }
        ) {
            // reveal stagger
            lines.forEachIndexed { idx, line ->
                val distance = abs(idx - currentIdx)
                val targetOffset = anchorY + (positions[idx] ?: ((idx - currentIdx) * (fallbackH + gapPx)))
                val animatedOffset by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = if (isInitialLayout) tween(0) else tween(680, (distance * 28).coerceAtMost(200), FastOutSlowInEasing),
                    label = "stagger$idx"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { itemHeights[idx] = it.height }
                        .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) }
                ) {
                    val isActive = idx == currentIdx
                    val displayedIdx = if (isAutoScrollEnabled) currentIdx else -1
                    // pass available width to fix hidden words on right — measure with real constraints, not 1080
                    val availableWidth = maxWidthPx
                    MetrolistLine(
                        line = line,
                        index = idx,
                        isActive = isActive,
                        isAutoScrollEnabled = isAutoScrollEnabled,
                        displayedIdx = displayedIdx,
                        smoothPos = smoothPos,
                        availableWidthPx = availableWidth,
                        onSeek = onSeek
                    )
                }
            }
            LaunchedEffect(lines.size, itemHeights.size) {
                if (itemHeights.size >= minOf(6, lines.size) && isInitialLayout) {
                    isInitialLayout = false
                }
            }
        }
    }
}

@Composable
private fun MetrolistLine(
    line: LyricLine,
    index: Int,
    isActive: Boolean,
    isAutoScrollEnabled: Boolean,
    displayedIdx: Int,
    smoothPos: Long,
    availableWidthPx: Int,
    onSeek: (Long) -> Unit
) {
    // alpha like Metrolist — psychology-pleasing fade distance
    val targetAlpha = when {
        line.words.any { it.isBackground } && !isActive -> 0.45f
        isActive -> 1f
        isAutoScrollEnabled && displayedIdx >= 0 -> when (abs(index - displayedIdx)) {
            1 -> 0.55f
            2 -> 0.32f
            3 -> 0.18f
            4 -> 0.10f
            else -> 0.06f
        }
        else -> 0.22f
    }
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(260), label = "alpha$index")

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // mainText with single spaces — fixes “no spaces” bug (TTML inter-span spaces lost)
    val mainText = remember(line) { line.words.joinToString(" ") { it.text.trim() }.trim() }

    // words for mapping — trimmed text, keep original timing, mark hasTrailingSpace except last
    val wordsForCanvas = remember(line) {
        line.words.mapIndexed { idx, w ->
            Triple(
                w.text.trim(),
                w.beginMs,
                w.endMs
            ) to (idx < line.words.lastIndex)
        }
    }

    // no size pop — keep uniform size, only alpha/word-glow animates (per user request)
    val isBg = line.words.any { it.isBackground }
    val baseFontSize = if (isBg) 19.sp else 22.sp
    val style = TextStyle(
        fontSize = baseFontSize,
        fontWeight = FontWeight.Bold,
        fontStyle = if (isBg) FontStyle.Italic else FontStyle.Normal,
        lineHeight = (baseFontSize.value * 1.28f).sp,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
    )

    val inactiveColor = Color.White.copy(alpha = animatedAlpha * 0.42f)
    val activeColor = Color.White

    // Unified rendering — Canvas for both active/inactive to keep exact center alignment (no Text vs Canvas jump)
    // fixes short single lines snapping to left when becoming active
    val horizPadPx = with(density) { 18.dp.toPx().toInt() * 2 }
    val measureWidth = (availableWidthPx - horizPadPx).coerceAtLeast(200)
    val layout = remember(mainText, style, measureWidth) {
        textMeasurer.measure(text = mainText, style = style, constraints = Constraints(maxWidth = measureWidth))
    }
    val height = with(density) { layout.size.height.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clickable { onSeek(line.beginMs) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            // dim base — all lines draw same centered layout, no alignment switch
            drawText(layout, color = inactiveColor)
            if (isActive) {
                var charCursor = 0
                wordsForCanvas.forEach { (wordTriple, hasSpace) ->
                    val (wText, wBegin, wEnd) = wordTriple
                    val wStartIdx = mainText.indexOf(wText, charCursor)
                    if (wStartIdx == -1) {
                        charCursor += wText.length + if (hasSpace) 1 else 0
                        return@forEach
                    }
                    val wEndIdx = wStartIdx + wText.length
                    var left = Float.MAX_VALUE
                    var right = Float.MIN_VALUE
                    var top = Float.MAX_VALUE
                    var bottom = Float.MIN_VALUE
                    var found = false
                    for (i in wStartIdx until wEndIdx) {
                        val box = layout.getBoundingBox(i)
                        left = minOf(left, box.left)
                        right = maxOf(right, box.right)
                        top = minOf(top, box.top)
                        bottom = maxOf(bottom, box.bottom)
                        found = true
                    }
                    if (!found) {
                        charCursor = wEndIdx + if (hasSpace) 1 else 0
                        return@forEach
                    }
                    val isWordSung = smoothPos >= wEnd
                    val isWordActive = smoothPos in wBegin until wEnd
                    val sungFactor = when {
                        isWordSung -> 1f
                        isWordActive -> ((smoothPos - wBegin).toFloat() / (wEnd - wBegin).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    if (isWordSung) {
                        clipRect(left = left, top = top, right = right, bottom = bottom) {
                            drawText(layout, color = activeColor)
                        }
                    } else if (isWordActive && sungFactor > 0f) {
                        val fillWidth = (right - left) * sungFactor
                        val soft = 18f * density.density
                        clipRect(left = left, top = top, right = left + fillWidth, bottom = bottom) {
                            drawText(layout, color = activeColor)
                        }
                        val edgeLeft = (left + fillWidth - soft / 2).coerceAtLeast(left)
                        val edgeRight = (left + fillWidth + soft / 2).coerceAtMost(right)
                        if (edgeRight > edgeLeft) {
                            for (k in 0 until 6) {
                                val segL = edgeLeft + k * (edgeRight - edgeLeft) / 6f
                                val segR = edgeLeft + (k + 1) * (edgeRight - edgeLeft) / 6f
                                val alpha = 1f - (k + 0.5f) / 6f
                                clipRect(left = segL, top = top, right = segR, bottom = bottom) {
                                    drawText(layout, color = activeColor.copy(alpha = alpha * 0.85f))
                                }
                            }
                        }
                    }
                    charCursor = wEndIdx + if (hasSpace) 1 else 0
                }
            }
        }
    }
}
