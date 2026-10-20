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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayout
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
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.data.lyrics.LyricsStyle
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

// ── Line measurement ─────────────────────────────────────────────────────────

/** Horizontal padding of a lyric line, in dp. */
private const val LYRIC_HORIZONTAL_PADDING_DP = 18

/** Vertical padding of a lyric line, in dp. */
private const val LYRIC_VERTICAL_PADDING_DP = 6

/**
 * Rendering data of a single lyric line, measured *before* the first frame.
 *
 * Measuring up front is what keeps the stack of lines from collapsing: a
 * lyric line can wrap over several visual lines, so its height is only known
 * once it has been measured. Guessing a constant height made long lines draw
 * on top of the lines below them.
 */
private class MeasuredLyricLine(
    /** Layout used for single-line (centered) lyrics. */
    val centerLayout: TextLayout,
    /** Layout used for wrapped lyrics, which are left aligned. */
    val startLayout: TextLayout,
    /** True when the line wraps over more than one visual line. */
    val isMultiline: Boolean,
    /** Rendered height in px, including the vertical padding around the text. */
    val heightPx: Float
)

/**
 * The text style of a lyric line. Background vocals keep a smaller, italic
 * style so they read as a second voice.
 */
private fun lyricTextStyle(style: LyricsStyle, isBackground: Boolean): TextStyle {
    val fontSize = if (isBackground) (style.fontSizeSp * 0.86f).sp else style.fontSizeSp.sp
    return TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight(style.weight.weightValue),
        fontStyle = if (isBackground) FontStyle.Italic else FontStyle.Normal,
        lineHeight = (fontSize.value * 1.22f).sp,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
    )
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
    modifier: Modifier = Modifier,
    /**
     * Current playback rate (1.0 = normal). The word highlight advances with
     * this rate, so slowing the song down does not make the lyrics lag or
     * snap back.
     */
    playbackSpeed: Float = 1f,
    /**
     * Wall-clock time the [positionMs] sample was taken at
     * (`SystemClock.elapsedRealtime()`); 0 when unknown.
     */
    positionSampleElapsedMs: Long = 0L,
    /** Size / weight / line spacing of the lyrics. */
    style: LyricsStyle = LyricsStyle()
) {
    when (state) {
        LyricsUiState.Idle, LyricsUiState.Loading -> LyricsLoading(modifier)
        is LyricsUiState.Ready -> SyncedLyricsContent(
            track = state.track,
            provider = state.provider,
            positionMs = positionMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            config = config,
            modifier = modifier,
            playbackSpeed = playbackSpeed,
            positionSampleElapsedMs = positionSampleElapsedMs,
            style = style
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
private val FADE_TOP_DP = 90.dp
private val FADE_BOTTOM_DP = 120.dp

@Composable
private fun SyncedLyricsContent(
    track: LyricTrack,
    provider: LyricsSource?,
    positionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    config: LyricsAnimationConfig,
    modifier: Modifier = Modifier,
    playbackSpeed: Float = 1f,
    positionSampleElapsedMs: Long = 0L,
    style: LyricsStyle = LyricsStyle()
) {
    val lines = track.lines
    // Provider credit renders as a trailing footer item after the last line.
    val hasCredit = provider != null
    val creditIdx = lines.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ── Smooth, rate-aware position ────────────────────────────────────────
    // The player publishes a position every ~500 ms. [LyricsClock] anchors on
    // every sample and interpolates with the *playback rate*, so the word
    // highlight moves at the speed of the music — no more running ahead and
    // snapping back when the song is slowed down.
    val posState by rememberUpdatedState(positionMs)
    val sampleAtState by rememberUpdatedState(positionSampleElapsedMs)
    val playingState by rememberUpdatedState(isPlaying)
    val speedState by rememberUpdatedState(playbackSpeed)
    val linesState by rememberUpdatedState(lines)

    var smoothPos by remember { mutableLongStateOf(positionMs) }
    var currentIdx by remember { mutableIntStateOf(findCurrentLine(lines, positionMs)) }
    val clock = remember { LyricsClock() }

    LaunchedEffect(track) {
        // fresh track → reset the clock and the anchor
        clock.reset()
        smoothPos = posState
        currentIdx = findCurrentLine(linesState, smoothPos)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {
                val now = android.os.SystemClock.elapsedRealtime()
                val p = clock.positionAt(
                    samplePos = posState,
                    sampleElapsedMs = sampleAtState,
                    isPlaying = playingState,
                    nowElapsedMs = now,
                    declaredRate = speedState
                )
                smoothPos = p
                val idx = findCurrentLine(linesState, p)
                if (idx != currentIdx) currentIdx = idx
            }
        }
    }

    // positions — Metrolist anchor logic
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
        // Placeholder height, only used if a line could not be measured.
        val fallbackH = with(density) {
            maxOf(FALLBACK_H_DP.value, style.fontSizeSp * 1.8f + 16f).dp.toPx()
        }
        // Distance between two lyric lines — user setting.
        val gapPx = with(density) { style.lineSpacingDp.dp.toPx() }

        // ── Measure the lines before the first frame ────────────────────────
        // A lyric line can wrap over several visual lines, so its height is
        // only known once it has been measured. Measuring up front (instead of
        // waiting for the onSizeChanged callback of every line) means the
        // stack is laid out correctly from the very first frame — including
        // before the song starts, when no line is active yet.
        val textMeasurer = rememberTextMeasurer()
        val measureWidth = remember(maxWidthPx, density) {
            val pad = with(density) { LYRIC_HORIZONTAL_PADDING_DP.dp.toPx().toInt() * 2 }
            (maxWidthPx - pad).coerceAtLeast(200)
        }
        val verticalPaddingPx = with(density) { (LYRIC_VERTICAL_PADDING_DP * 2).dp.toPx() }
        val measuredLines = remember(lines, style, measureWidth, density) {
            lines.map { line ->
                val isBg = line.words.any { it.isBackground }
                val centerStyle = lyricTextStyle(style, isBg)
                val startStyle = centerStyle.copy(textAlign = TextAlign.Start)
                val centerLayout = textMeasurer.measure(
                    text = line.text,
                    style = centerStyle,
                    constraints = Constraints(maxWidth = measureWidth)
                )
                val startLayout = textMeasurer.measure(
                    text = line.text,
                    style = startStyle,
                    constraints = Constraints(maxWidth = measureWidth)
                )
                MeasuredLyricLine(
                    centerLayout = centerLayout,
                    startLayout = startLayout,
                    isMultiline = centerLayout.lineCount > 1,
                    heightPx = centerLayout.size.height + verticalPaddingPx
                )
            }
        }
        // Provider credit footer — measured the same way, it is the last item.
        val creditTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
        val creditHeightPx = remember(provider?.label, creditTextStyle, measureWidth, density) {
            if (provider == null) {
                0f
            } else {
                val layout = textMeasurer.measure(
                    text = "Lyrics from ${provider.label}",
                    style = creditTextStyle,
                    constraints = Constraints(maxWidth = measureWidth)
                )
                layout.size.height + with(density) { 20.dp.toPx() }
            }
        }
        // Height of every item of the stack: the lyric lines, then the credit.
        val itemHeights = remember(measuredLines, creditHeightPx, hasCredit) {
            val heights = measuredLines.map { it.heightPx }.toMutableList()
            if (hasCredit) heights.add(creditHeightPx)
            heights
        }

        // The line that sits on the anchor point and drives the layout. Before
        // the very first lyric line starts there is no active line (-1); the
        // first upcoming line is used as the anchor so the lines still stack
        // up instead of falling back to a guessed height.
        val anchorIdx = remember(lines, currentIdx) {
            when {
                lines.isEmpty() -> -1
                currentIdx < 0 -> 0
                else -> currentIdx.coerceAtMost(lines.lastIndex)
            }
        }

        // positions map relative to the anchor index (credit footer counts as last item)
        val positions = remember(itemHeights, anchorIdx, gapPx) {
            val map = mutableMapOf<Int, Float>()
            if (anchorIdx == -1 || itemHeights.isEmpty()) return@remember map
            map[anchorIdx] = 0f
            var y = 0f
            for (i in anchorIdx - 1 downTo 0) {
                y -= (itemHeights[i] + gapPx)
                map[i] = y
            }
            y = 0f
            for (i in anchorIdx until itemHeights.lastIndex) {
                y += (itemHeights[i] + gapPx)
                map[i + 1] = y
            }
            map
        }

        val minOffset = remember(itemHeights, lines, currentIdx, anchorY, hasCredit, gapPx) {
            if (lines.isEmpty() || currentIdx == -1) return@remember 0f
            var totalBelow = 0f
            for (i in currentIdx until itemHeights.lastIndex) {
                totalBelow += itemHeights[i] + gapPx
            }
            val lastH = itemHeights.last()
            with(density) { 80.dp.toPx() } - anchorY - totalBelow - lastH
        }
        val maxOffset = remember(itemHeights, lines, currentIdx, maxHeightPx, anchorY, gapPx) {
            if (lines.isEmpty() || currentIdx == -1) return@remember 0f
            var totalAbove = 0f
            for (i in 0 until currentIdx) {
                totalAbove += itemHeights[i] + gapPx
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
                val targetOffset = anchorY + (positions[idx] ?: ((idx - anchorIdx) * (fallbackH + gapPx)))
                val animatedOffset by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = if (isInitialLayout) tween(0) else tween(680, (distance * 28).coerceAtMost(200), FastOutSlowInEasing),
                    label = "stagger$idx"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) }
                ) {
                    val isActive = idx == currentIdx
                    val displayedIdx = if (isAutoScrollEnabled) currentIdx else -1
                    MetrolistLine(
                        line = line,
                        measured = measuredLines[idx],
                        index = idx,
                        isActive = isActive,
                        isAutoScrollEnabled = isAutoScrollEnabled,
                        displayedIdx = displayedIdx,
                        smoothPos = smoothPos,
                        onSeek = onSeek
                    )
                }
            }
            LaunchedEffect(track, lines.size, measuredLines.size) {
                if (measuredLines.size >= minOf(6, lines.size) && isInitialLayout) {
                    isInitialLayout = false
                }
            }
            // Provider credit footer — scrolls with the lyrics, sits after the last line
            if (hasCredit && provider != null) {
                val distance = abs(creditIdx - currentIdx)
                val targetOffset = anchorY + (positions[creditIdx] ?: ((creditIdx - anchorIdx) * (fallbackH + gapPx)))
                val animatedOffset by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = if (isInitialLayout) tween(0) else tween(680, (distance * 28).coerceAtMost(200), FastOutSlowInEasing),
                    label = "staggerCredit"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Lyrics from ${provider.label}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetrolistLine(
    line: LyricLine,
    measured: MeasuredLyricLine,
    index: Int,
    isActive: Boolean,
    isAutoScrollEnabled: Boolean,
    displayedIdx: Int,
    smoothPos: Long,
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

    val density = LocalDensity.current

    // mainText with syllable-accurate spaces — uses hasTrailingSpace from parser (no gap for Be+gging)
    val mainText = remember(line) { line.text }
    // words for mapping — use parser's hasTrailingSpace so Be+gging stays together without gap
    val wordsForCanvas = remember(line) {
        line.words.map { w ->
            Triple(
                w.text,
                w.beginMs,
                w.endMs
            ) to w.hasTrailingSpace
        }
    }

    val inactiveColor = Color.White.copy(alpha = animatedAlpha * 0.42f)
    val activeColor = Color.White

    // Line-level lyrics (LRC / Lyricsfile fallback) carry no per-word timings —
    // exactly 1 word per line, or all words sharing identical timings.
    // Highlight the whole ongoing line at once instead of a gradual word sweep.
    val isLineLevel = remember(line) {
        if (line.words.size <= 1) true
        else line.words.map { it.beginMs }.toSet().size <= 1 &&
            line.words.map { it.endMs }.toSet().size <= 1
    }

    // Unified rendering — Canvas for both active/inactive to keep exact center alignment (no Text vs Canvas jump)
    // fixes short single lines snapping to left when becoming active
    // Wrapped lines (>1 visual line) are left-aligned for readability; single lines stay centered.
    // The layout was measured by the caller, so the height of the line is
    // known before the first frame — no guessing, no overlapping lines.
    val layout = if (measured.isMultiline) measured.startLayout else measured.centerLayout
    val height = with(density) { measured.centerLayout.size.height.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LYRIC_HORIZONTAL_PADDING_DP.dp, vertical = LYRIC_VERTICAL_PADDING_DP.dp)
            .clickable { onSeek(line.beginMs) },
        contentAlignment = if (measured.isMultiline) Alignment.CenterStart else Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            // dim base — all lines draw same centered layout, no alignment switch
            drawText(layout, color = inactiveColor)
            if (isActive) {
                if (isLineLevel) {
                    // line-level: no word timings → full-line highlight at once, no gradual fill
                    drawText(layout, color = activeColor)
                } else {
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
}
