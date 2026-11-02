package com.teamshryne.mediyo.data.lyrics

/**
 * A contiguous group of words inside a [LyricLine].
 *
 * Lines often mix the lead vocal with a parenthetical aside — for example
 * "never trade you for nothing (Baby, oh, no, baby, oh, no)". The aside is
 * a second voice: it has its own timing and it should not be welded to the
 * lead vocal. Splitting the line into segments lets the renderer give the
 * aside a row of its own, so it can appear (and disappear) on its own
 * timing instead of being drawn in italic in the middle of the line.
 */
data class LyricSegment(
    val words: List<LyricWord>,
    /** True for a parenthetical aside / backing vocal. */
    val isParenthetical: Boolean,
) {
    /** The text of the segment, words joined by their trailing space. */
    val text: String get() = buildString {
        for ((i, w) in words.withIndex()) {
            append(w.text)
            if (w.hasTrailingSpace && i != words.lastIndex) append(' ')
        }
    }.trim()

    /** Timing of the first word of the segment. */
    val beginMs: Long get() = words.firstOrNull()?.beginMs ?: 0L

    /** Timing of the last word of the segment. */
    val endMs: Long get() = words.lastOrNull()?.endMs ?: 0L
}

/**
 * Splits a [LyricLine] into a lead segment and the parenthetical asides it
 * contains.
 *
 * Two things mark a word as an aside:
 * - it sits inside brackets — `(Baby, oh, no)`;
 * - it is a background vocal — a TTML span with `ttm:role="x-bg"`.
 *
 * The brackets themselves are only delimiters, so they are stripped from the
 * aside: what is shown is the aside, not the punctuation around it.
 */
object LyricSegmentSplitter {

    /**
     * @return the segments of [line], in reading order. A line without any
     *   aside yields a single segment with [LyricSegment.isParenthetical]
     *   set to `false`.
     */
    fun split(line: LyricLine): List<LyricSegment> {
        val text = line.text
        if (text.isEmpty() || line.words.isEmpty()) return emptyList()

        val inBrackets = bracketedChars(text)
        val segments = mutableListOf<MutableSegment>()
        var cursor = 0
        for (word in line.words) {
            if (word.text.isEmpty()) continue
            val start = text.indexOf(word.text, cursor)
            if (start < 0) {
                cursor += word.text.length
                continue
            }
            val end = start + word.text.length
            cursor = end + if (word.hasTrailingSpace) 1 else 0

            // A word can straddle the edge of a bracket ("nothing (Baby,"),
            // so it is cut into chunks that all belong to the same kind.
            var chunkStart = start
            while (chunkStart < end) {
                val isAside = isAside(chunkStart, inBrackets, word.isBackground)
                var chunkEnd = chunkStart + 1
                while (chunkEnd < end && isAside(chunkEnd, inBrackets, word.isBackground) == isAside) {
                    chunkEnd++
                }
                var chunkText = text.substring(chunkStart, chunkEnd)
                if (isAside) chunkText = chunkText.trimStart('(').trimEnd(')')
                if (chunkText.isNotEmpty()) {
                    val chunk = word.copy(
                        text = chunkText,
                        // Only the tail of a word carries the space towards
                        // the next one.
                        hasTrailingSpace = chunkEnd >= end && word.hasTrailingSpace
                    )
                    val last = segments.lastOrNull()
                    if (last != null && last.isParenthetical == isAside) {
                        last.words += chunk
                    } else {
                        segments += MutableSegment(isAside, mutableListOf(chunk))
                    }
                }
                chunkStart = chunkEnd
            }
        }
        return segments.map { LyricSegment(it.words.toList(), it.isParenthetical) }
    }

    /** True when the character at [index] belongs to an aside. */
    private fun isAside(index: Int, inBrackets: BooleanArray, isBackground: Boolean): Boolean =
        isBackground || (index < inBrackets.size && inBrackets[index])

    /** Marks every character that sits inside a pair of brackets. */
    private fun bracketedChars(text: String): BooleanArray {
        val flags = BooleanArray(text.length)
        var open = -1
        var depth = 0
        for (i in text.indices) {
            when (text[i]) {
                '(' -> {
                    if (depth == 0) open = i
                    depth++
                }
                ')' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && open >= 0) {
                            for (j in open..i) flags[j] = true
                        }
                    }
                }
            }
        }
        return flags
    }

    private class MutableSegment(
        val isParenthetical: Boolean,
        val words: MutableList<LyricWord>,
    )
}
