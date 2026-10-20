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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
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
import com.teamshryne.mediyo.data.lyrics.LyricSegment
import com.teamshryne.mediyo.data.lyrics.LyricSegmentSplitter
import com.teamshryne.mediyo.data.lyrics.LyricTrack
import com.teamshryne.mediyo.data.lyrics.LyricsSource
import com.teamshryne.mediyo.data.lyrics.LyricsStyle
import com.teamshryne.mediyo.feature.lyrics.animation.LyricsAnimationConfig
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * The lines that are *live* at [posMs] — the lines that are being sung right
 * now.
 *
 * More than one line can be live at the same time: a lead vocal and a backing
 * vocal often overlap, and a chorus repeats the same words over two lines.
 * Every live line has to be highlighted — picking only the first match is what
 * made the second line jump in late, or skip when its words were already
 * over.
 *
 * The lines are expected to be sorted by [LyricLine.beginMs].
 */
private fun findLiveLines(lines: List<LyricLine>, posMs: Long): Set<Int> {
    if (lines.isEmpty()) return emptySet()
    val live = mutableSetOf<Int>()
    var lastStarted = -1
    var lastStartedBegin = Long.MIN_VALUE
    for (i in lines.indices) {
        val l = lines[i]
        if (l.beginMs > posMs) continue
        if (l.beginMs >= lastStartedBegin) {
            lastStartedBegin = l.beginMs
            lastStarted = i
        }
        if (posMs < l.endMs) live += i
    }
    // Between two lines there is a gap where nothing is sung: the line that
    // was just sung keeps the highlight so the screen does not blink.
    if (live.isEmpty() && lastStarted >= 0) live += lastStarted
    return live
}

// ── Line measurement ─────────────────────────────────────────────────────────

/** Horizontal padding of a lyric line, in dp. */
private const val LYRIC_HORIZONTAL_PADDING_DP = 18

/** Vertical padding of a lyric line, in dp. */
private const val LYRIC_VERTICAL_PADDING_DP = 6

/**
 * The text style of a lyric line.
 *
 * Nothing is italic: a line that carries a parenthetical aside used to be
 * drawn in italic because a single word of it was flagged as a background
 * vocal. The aside gets a row of its own instead (see [LyricRow.Aside]).
 */
private fun lyricTextStyle(style: LyricsStyle, fontScale: Float = 1f): TextStyle {
    val fontSize = (style.fontSizeSp * fontScale).sp
    return TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight(style.weight.weightValue),
        fontStyle = FontStyle.Normal,
        lineHeight = (fontSize.value * 1.22f).sp,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
    )
}

// ── Rows ─────────────────────────────────────────────────────────────────────

/**
 * One visual row of the lyric stack.
 *
 * A line is normally a single row. A line that mixes the lead vocal with a
 * parenthetical aside is split: the lead vocal keeps the row it had, the
 * aside gets a row of its own below it, so the aside can slide in when it
 * starts and slide back out when it is over.
 */
private sealed interface LyricRow {
    val lineIndex: Int
    val segment: LyricSegment

    /** Layout used for single-line (centered) text. */
    val centerLayout: TextLayoutResult

    /** Layout used for wrapped text, which is left aligned. */
    val startLayout: TextLayoutResult

    /** True when the text wraps over more than one visual line. */
    val isMultiline: Boolean

    /** Rendered height in px, including the vertical padding around the text. */
    val heightPx: Float

    /** The lead vocal of a line. */
    data class Main(
        override val lineIndex: Int,
        override val segment: LyricSegment,
        override val centerLayout: TextLayoutResult,
        override val startLayout: TextLayoutResult,
        override val isMultiline: Boolean,
        override val heightPx: Float,
        /**
         * True for a line that is a backing vocal from top to bottom. It is
         * still a line of its own in the stack, it is only drawn a bit
         * smaller so it reads as a second voice.
         */
        val isSecondary: Boolean = false,
    ) : LyricRow

    /** A parenthetical aside — hidden until it starts, then it slides in. */
    data class Aside(
        override val lineIndex: Int,
        override val segment: LyricSegment,
        override val centerLayout: TextLayoutResult,
        override val startLayout: TextLayoutResult,
        override val isMultiline: Boolean,
        override val heightPx: Float,
    ) : LyricRow
}

/**
 * Builds the rows of the lyric stack.
 *
 * A line is split into a lead row and aside rows only when the aside has a
 * timing of its own — otherwise both would start and stop together and
 * there is nothing to slide.
 */
private fun buildLyricRows(
    lines: List<LyricLine>,
    style: LyricsStyle,
    measurer: TextMeasurer,
    measureWidth: Int,
    verticalPaddingPx: Float,
): List<LyricRow> {
    val rows = mutableListOf<LyricRow>()
    lines.forEachIndexed { index, line ->
        val segments = LyricSegmentSplitter.split(line)
        val lead = segments.firstOrNull { !it.isParenthetical }
        val asides = segments.filter { it.isParenthetical }
        when {
            // The whole line is a backing vocal: it is a line of its own, so
            // it keeps its place in the stack and is only drawn smaller.
            lead == null && asides.isNotEmpty() -> {
                asides.forEach { aside ->
                    rows += measureMain(
                        index, aside, style, measurer, measureWidth, verticalPaddingPx,
                        secondary = true
                    )
                }
            }
            // A lead vocal with asides that have a timing of their own. The
            // asides get a row of their own, in reading order, so they can
            // slide in and out on their own timing.
            lead != null && asides.isNotEmpty() && asides.all { hasOwnTiming(lead, it) } -> {
                segments.forEach { segment ->
                    if (segment.isParenthetical) {
                        rows += measureAside(index, segment, style, measurer, measureWidth, verticalPaddingPx)
                    } else {
                        rows += measureMain(index, segment, style, measurer, measureWidth, verticalPaddingPx)
                    }
                }
            }
            // Everything else stays on a single row, asides included.
            else -> {
                rows += measureMain(
                    index,
                    LyricSegment(line.words, isParenthetical = false),
                    style, measurer, measureWidth, verticalPaddingPx
                )
            }
        }
    }
    return rows
}

/** True when [aside] starts or stops on its own, apart from [lead]. */
private fun hasOwnTiming(lead: LyricSegment, aside: LyricSegment): Boolean =
    aside.beginMs != lead.beginMs || aside.endMs != lead.endMs

private fun measureMain(
    index: Int,
    segment: LyricSegment,
    style: LyricsStyle,
    measurer: TextMeasurer,
    measureWidth: Int,
    verticalPaddingPx: Float,
    secondary: Boolean = false,
): LyricRow.Main {
    // A backing vocal is a second voice: a bit smaller than the lead vocal,
    // but the same weight and never italic.
    val layouts = measureSegment(segment, style, measurer, measureWidth, fontScale = if (secondary) 0.88f else 1f)
    return LyricRow.Main(
        lineIndex = index,
        segment = segment,
        centerLayout = layouts.first,
        startLayout = layouts.second,
        isMultiline = layouts.first.lineCount > 1,
        heightPx = layouts.first.size.height + verticalPaddingPx,
        isSecondary = secondary
    )
}

private fun measureAside(
    index: Int,
    segment: LyricSegment,
    style: LyricsStyle,
    measurer: TextMeasurer,
    measureWidth: Int,
    verticalPaddingPx: Float,
): LyricRow.Aside {
    // An aside is a second voice: a bit smaller than the lead vocal, but the
    // same weight and never italic.
    val layouts = measureSegment(segment, style, measurer, measureWidth, fontScale = 0.88f)
    return LyricRow.Aside(
        lineIndex = index,
        segment = segment,
        centerLayout = layouts.first,
        startLayout = layouts.second,
        isMultiline = layouts.first.lineCount > 1,
        heightPx = layouts.first.size.height + verticalPaddingPx
    )
}

/** Measures a segment twice: centered and left aligned. */
private fun measureSegment(
    segment: LyricSegment,
    style: LyricsStyle,
    measurer: TextMeasurer,
    measureWidth: Int,
    fontScale: Float = 1f,
): Pair<TextLayoutResult, TextLayoutResult> {
    val base = lyricTextStyle(style, fontScale)
    val center = measurer.measure(
        text = segment.text,
        style = base,
        constraints = Constraints(maxWidth = measureWidth)
    )
    val start = measurer.measure(
        text = segment.text,
        style = base.copy(textAlign = TextAlign.Start),
        constraints = Constraints(maxWidth = measureWidth)
    )
    return center to start
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

// ── Core content ─────────────────────────────────────────────────────────────

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
    // Every line that is sung at the current position. There can be more than
    // one — they all have to be highlighted at the same time.
    var liveLines by remember { mutableStateOf(findLiveLines(lines, positionMs)) }
    val clock = remember { LyricsClock() }

    LaunchedEffect(track) {
        // fresh track → reset the clock and the anchor
        clock.reset()
        smoothPos = posState
        liveLines = findLiveLines(linesState, smoothPos)
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
                val live = findLiveLines(linesState, p)
                if (live != liveLines) liveLines = live
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
        // Placeholder height, only used if a row could not be measured.
        val fallbackH = with(density) {
            maxOf(FALLBACK_H_DP.value, style.fontSizeSp * 1.8f + 16f).dp.toPx()
        }
        // Distance between two lyric rows — user setting.
        val gapPx = with(density) { style.lineSpacingDp.dp.toPx() }

        // ── Measure the rows before the first frame ─────────────────────────
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
        val rows = remember(lines, style, measureWidth, density) {
            buildLyricRows(lines, style, textMeasurer, measureWidth, verticalPaddingPx)
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
        // Height of every item of the stack: the lyric rows, then the credit.
        val itemHeights = remember(rows, creditHeightPx, hasCredit) {
            val heights = rows.map { it.heightPx }.toMutableList()
            if (hasCredit) heights.add(creditHeightPx)
            heights
        }
        val creditIdx = rows.size

        // The row that sits on the anchor point and drives the layout. Before
        // the very first lyric line starts there is no active line (-1); the
        // first upcoming line is used as the anchor so the lines still stack
        // up instead of falling back to a guessed height.
        val anchorIdx = remember(rows, liveLines) {
            val first = liveLines.minOrNull()
            when {
                rows.isEmpty() -> -1
                first == null -> 0
                else -> rows.indexOfFirst { it.lineIndex == first }.let { if (it < 0) 0 else it }
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

        val minOffset = remember(itemHeights, lines, liveLines, anchorY, hasCredit, gapPx) {
            if (lines.isEmpty() || liveLines.isEmpty()) return@remember 0f
            var totalBelow = 0f
            for (i in anchorIdx until itemHeights.lastIndex) {
                totalBelow += itemHeights[i] + gapPx
            }
            val lastH = itemHeights.last()
            with(density) { 80.dp.toPx() } - anchorY - totalBelow - lastH
        }
        val maxOffset = remember(itemHeights, lines, liveLines, maxHeightPx, anchorY, gapPx) {
            if (lines.isEmpty() || liveLines.isEmpty()) return@remember 0f
            var totalAbove = 0f
            for (i in 0 until anchorIdx) {
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

        // auto-scroll without LazyColumn — direct offset per row, staggered
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
            rows.forEachIndexed { idx, row ->
                val distance = abs(idx - anchorIdx)
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
                    when (row) {
                        is LyricRow.Main -> LyricMainRow(
                            row = row,
                            rowIdx = idx,
                            isActive = row.lineIndex in liveLines,
                            isAutoScrollEnabled = isAutoScrollEnabled,
                            anchorIdx = anchorIdx,
                            smoothPos = smoothPos,
                            onSeek = onSeek
                        )
                        is LyricRow.Aside -> LyricAsideRow(
                            row = row,
                            // The aside slides in from the row it comes from:
                            // the line it belongs to, or the line above it
                            // when it is a backing vocal of its own.
                            slideDistancePx = if (idx == 0) 0f
                            else (positions[idx] ?: 0f) - (positions[idx - 1] ?: 0f),
                            smoothPos = smoothPos,
                            onSeek = onSeek
                        )
                    }
                }
            }
            LaunchedEffect(track, lines.size, rows.size) {
                if (rows.size >= minOf(6, lines.size) && isInitialLayout) {
                    isInitialLayout = false
                }
            }
            // Provider credit footer — scrolls with the lyrics, sits after the last line
            if (hasCredit && provider != null) {
                val distance = abs(creditIdx - anchorIdx)
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

/**
 * The lead vocal of a line.
 *
 * Every live line is drawn with the active color — several lines can be sung
 * at the same time and all of them have to light up.
 */
@Composable
private fun LyricMainRow(
    row: LyricRow.Main,
    rowIdx: Int,
    isActive: Boolean,
    isAutoScrollEnabled: Boolean,
    anchorIdx: Int,
    smoothPos: Long,
    onSeek: (Long) -> Unit
) {
    // alpha like Metrolist — psychology-pleasing fade distance
    val targetAlpha = when {
        isActive -> 1f
        isAutoScrollEnabled && anchorIdx >= 0 -> when (abs(rowIdx - anchorIdx)) {
            1 -> 0.55f
            2 -> 0.32f
            3 -> 0.18f
            4 -> 0.10f
            else -> 0.06f
        }
        else -> 0.22f
    }.let { if (row.isSecondary && !isActive) minOf(it, 0.45f) else it }
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(260), label = "alpha$rowIdx")

    LyricText(
        segment = row.segment,
        layout = if (row.isMultiline) row.startLayout else row.centerLayout,
        heightPx = row.centerLayout.size.height.toFloat(),
        isMultiline = row.isMultiline,
        alpha = animatedAlpha,
        isActive = isActive,
        smoothPos = smoothPos,
        onSeek = onSeek
    )
}

/**
 * A parenthetical aside.
 *
 * The aside is hidden until it starts. Then it slides in from the row it
 * comes from and it is sung like any other line — and when it is over it
 * slides back out.
 */
@Composable
private fun LyricAsideRow(
    row: LyricRow.Aside,
    slideDistancePx: Float,
    smoothPos: Long,
    onSeek: (Long) -> Unit
) {
    val started = smoothPos >= row.segment.beginMs
    val finished = smoothPos > row.segment.endMs
    val ongoing = started && !finished
    val animatedAlpha by animateFloatAsState(
        targetValue = if (ongoing) 1f else 0f,
        animationSpec = tween(260),
        label = "asideAlpha"
    )
    val animatedSlide by animateFloatAsState(
        targetValue = if (ongoing) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "asideSlide"
    )
    // 0 = still on the row it comes from, 1 = on its own row.
    val slideOffsetY = -(1f - animatedSlide) * slideDistancePx

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                translationY = slideOffsetY
            }
    ) {
        LyricText(
            segment = row.segment,
            layout = if (row.isMultiline) row.startLayout else row.centerLayout,
            heightPx = row.centerLayout.size.height.toFloat(),
            isMultiline = row.isMultiline,
            alpha = 1f,
            isActive = true,
            smoothPos = smoothPos,
            onSeek = onSeek
        )
    }
}

/**
 * Draws one row of lyrics.
 *
 * Unified rendering — Canvas for both active/inactive to keep exact center
 * alignment (no Text vs Canvas jump). Wrapped lines (>1 visual line) are
 * left-aligned for readability; single lines stay centered. The layout was
 * measured by the caller, so the height of the row is known before the first
 * frame — no guessing, no overlapping lines.
 */
@Composable
private fun LyricText(
    segment: LyricSegment,
    layout: TextLayoutResult,
    heightPx: Float,
    isMultiline: Boolean,
    alpha: Float,
    isActive: Boolean,
    smoothPos: Long,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current

    val inactiveColor = Color.White.copy(alpha = alpha * 0.42f)
    val activeColor = Color.White.copy(alpha = alpha)

    // Line-level lyrics (LRC / Lyricsfile fallback) carry no per-word timings —
    // exactly 1 word per line, or all words sharing identical timings.
    // Highlight the whole ongoing line at once instead of a gradual word sweep.
    val isLineLevel = remember(segment) {
        if (segment.words.size <= 1) true
        else segment.words.map { it.beginMs }.toSet().size <= 1 &&
            segment.words.map { it.endMs }.toSet().size <= 1
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LYRIC_HORIZONTAL_PADDING_DP.dp, vertical = LYRIC_VERTICAL_PADDING_DP.dp)
            .clickable { onSeek(segment.beginMs) },
        contentAlignment = if (isMultiline) Alignment.CenterStart else Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { heightPx.toDp() })
        ) {
            // dim base — all rows draw the same centered layout, no alignment switch
            drawText(layout, color = inactiveColor)
            if (isActive) {
                if (isLineLevel) {
                    // line-level: no word timings → full-line highlight at once, no gradual fill
                    drawText(layout, color = activeColor)
                } else {
                    drawWordSweep(layout, segment, smoothPos, activeColor, density.density)
                }
            }
        }
    }
}

/**
 * Draws the sung part of a word-synced segment: the words that are already
 * sung are drawn in the active color, the word being sung is filled up to
 * the playhead with a soft edge.
 */
private fun DrawScope.drawWordSweep(
    layout: TextLayoutResult,
    segment: LyricSegment,
    smoothPos: Long,
    activeColor: Color,
    density: Float
) {
    val mainText = segment.text
    var charCursor = 0
    for (word in segment.words) {
        val wText = word.text
        val wStartIdx = mainText.indexOf(wText, charCursor)
        if (wStartIdx == -1) {
            charCursor += wText.length + if (word.hasTrailingSpace) 1 else 0
            continue
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
            charCursor = wEndIdx + if (word.hasTrailingSpace) 1 else 0
            continue
        }
        val isWordSung = smoothPos >= word.endMs
        val isWordActive = smoothPos in word.beginMs until word.endMs
        val sungFactor = when {
            isWordSung -> 1f
            isWordActive -> ((smoothPos - word.beginMs).toFloat() / (word.endMs - word.beginMs).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
        if (isWordSung) {
            clipRect(left = left, top = top, right = right, bottom = bottom) {
                drawText(layout, color = activeColor)
            }
        } else if (isWordActive && sungFactor > 0f) {
            val fillWidth = (right - left) * sungFactor
            val soft = 18f * density
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
        charCursor = wEndIdx + if (word.hasTrailingSpace) 1 else 0
    }
}
