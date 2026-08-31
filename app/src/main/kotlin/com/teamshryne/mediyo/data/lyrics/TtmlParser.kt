package com.teamshryne.mediyo.data.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Maintainable TTML parser — single responsibility: TTML XML → LyricTrack.
 * Handles:
 * - word-level <span begin/end> (timed)
 * - wrapper <span ttm:role="x-bg"> (background)
 * - time formats: 00:01:30.500 | 1:16.656 | 27.395 | 3:21.570
 * - namespaces agnostic (localName fallback)
 * - lyricOffset from <audio lyricOffset="1.115">
 * - whitespace preservation between spans is via span textContent itself
 *
 * Adding new TTML dialects: extend [parseTime] or override [TRANSIENT_TAGS].
 */
object TtmlParser {

    private const val TAG_TT = "tt"
    private const val TAG_BODY = "body"
    private const val TAG_DIV = "div"
    private const val TAG_P = "p"
    private const val TAG_SPAN = "span"

    fun parse(ttml: String, score: Int? = null): LyricTrack {
        if (ttml.isBlank()) return LyricTrack(emptyList())
        return try {
            parseInternal(ttml, score)
        } catch (e: Exception) {
            // fallback: treat whole ttml as plain lines if parse fails
            LyricTrack(emptyList())
        }
    }

    private fun parseInternal(ttml: String, score: Int?): LyricTrack {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(ttml))

        var language: String? = null
        var lyricOffsetMs = 0L
        var bodyDurMs: Long? = null
        val lines = mutableListOf<LyricLine>()

        var currentDivPart: String? = null
        var pLine: MutableLine? = null
        val spanStack = ArrayDeque<SpanContext>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when (name) {
                        TAG_TT -> {
                            language = parser.getAttributeValue(null, "lang")
                                ?: parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                            // also check xml:lang via raw
                            if (language == null) {
                                for (i in 0 until parser.attributeCount) {
                                    if (parser.getAttributeName(i) == "lang") {
                                        language = parser.getAttributeValue(i)
                                        break
                                    }
                                }
                            }
                        }
                        "audio" -> {
                            parser.getAttributeValue(null, "lyricOffset")?.let {
                                lyricOffsetMs = (it.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
                            }
                        }
                        TAG_BODY -> {
                            parser.getAttributeValue(null, "dur")?.let { bodyDurMs = parseTime(it) }
                        }
                        TAG_DIV -> {
                            currentDivPart = parser.getAttributeValue(null, "songPart")
                                ?: parser.getAttributeValue("http://music.apple.com/lyric-ttml-internal", "songPart")
                        }
                        TAG_P -> {
                            val begin = parseTime(parser.getAttributeValue(null, "begin")) ?: 0L
                            val end = parseTime(parser.getAttributeValue(null, "end")) ?: (begin + 2000)
                            val agent = parser.getAttributeValue(null, "agent")
                                ?: parser.getAttributeValue("http://www.w3.org/ns/ttml#metadata", "agent")
                                ?: attrByLocal(parser, "agent")
                            val key = attrByLocal(parser, "key")
                            pLine = MutableLine(
                                begin = begin,
                                end = end,
                                agent = agent,
                                songPart = currentDivPart,
                                indexHint = key
                            )
                            spanStack.clear()
                            // push p-level bg context false
                            spanStack.addLast(SpanContext(isBg = false))
                        }
                        TAG_SPAN -> {
                            val parentBg = spanStack.lastOrNull()?.isBg ?: false
                            val role = parser.getAttributeValue(null, "role")
                                ?: attrByLocal(parser, "role")
                            val isBg = parentBg || role == "x-bg"
                            val beginAttr = parser.getAttributeValue(null, "begin")
                            val endAttr = parser.getAttributeValue(null, "end")

                            // wrapper span (no begin) -> push context and recurse
                            if (beginAttr == null) {
                                spanStack.addLast(SpanContext(isBg = isBg, isWrapper = true))
                                // we need to capture text inside? wrapper text nodes handled in TEXT event
                            } else {
                                val b = parseTime(beginAttr) ?: 0L
                                val e = parseTime(endAttr) ?: (b + 300)
                                // create pending word, text will be filled on TEXT event
                                val agent = pLine?.agent
                                spanStack.addLast(SpanContext(isBg = isBg, word = MutableWord(beginMs = b, endMs = e, isBackground = isBg, agentId = agent)))
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (pLine != null && text.isNotEmpty()) {
                        val top = spanStack.lastOrNull()
                        if (top?.word != null && top.word.text == null) {
                            top.word.text = text
                        } else if (text.trim().isEmpty()) {
                            // inter-span whitespace (e.g. "<span>Be</span> <span>gging</span>" has " " here)
                            // mark previous syllable as having trailing space so rendering keeps word gap
                            // but syllables within same lexical word have no whitespace -> keep false
                            if (pLine.pendingWords.isNotEmpty()) {
                                val last = pLine.pendingWords.last()
                                if (!last.hasTrailingSpace) {
                                    pLine.pendingWords[pLine.pendingWords.lastIndex] =
                                        last.copy(hasTrailingSpace = true)
                                }
                            }
                        } else if (top?.isWrapper == true) {
                            // text directly inside wrapper without inner span – ignore
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when (name) {
                        TAG_SPAN -> {
                            val popped = if (spanStack.isNotEmpty()) spanStack.removeLast() else null
                            popped?.word?.let { mw ->
                                val txt = mw.text ?: ""
                                // Important: preserve trailing spaces – textContent includes spaces
                                // parser trims? XmlPull may not include trailing spaces if they are between tags as separate TEXT nodes.
                                // We rely on txt containing the word plus maybe space. If empty, skip.
                                if (txt.isNotEmpty()) {
                                    pLine?.pendingWords?.add(
                                        LyricWord(
                                            text = txt,
                                            beginMs = mw.beginMs,
                                            endMs = mw.endMs,
                                            isBackground = mw.isBackground,
                                            agentId = mw.agentId
                                        )
                                    )
                                }
                            }
                        }
                        TAG_P -> {
                            pLine?.let { ml ->
                                if (ml.pendingWords.isNotEmpty()) {
                                    val idx = lines.size
                                    lines.add(
                                        LyricLine(
                                            index = idx,
                                            beginMs = ml.begin,
                                            endMs = ml.end,
                                            words = ml.pendingWords.toList(),
                                            agentId = ml.agent,
                                            isBackground = false,
                                            songPart = ml.songPart
                                        )
                                    )
                                }
                            }
                            pLine = null
                            spanStack.clear()
                        }
                        TAG_DIV -> {
                            currentDivPart = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Apply lyricOffset if non-zero (BetterLyrics uses it for sync adjustment)
        // Offset is added to all timings so that player ms aligns.
        val adjusted = if (lyricOffsetMs != 0L) {
            lines.map { it.copy(beginMs = (it.beginMs + lyricOffsetMs).coerceAtLeast(0), endMs = (it.endMs + lyricOffsetMs).coerceAtLeast(0), words = it.words.map { w -> w.copy(beginMs = (w.beginMs + lyricOffsetMs).coerceAtLeast(0), endMs = (w.endMs + lyricOffsetMs).coerceAtLeast(0)) }) }
        } else lines

        // Sort by begin defensively
        val sorted = adjusted.sortedBy { it.beginMs }
        // Re-index
        val reindexed = sorted.mapIndexed { i, l -> l.copy(index = i) }

        return LyricTrack(
            lines = reindexed,
            durationMs = bodyDurMs,
            lyricOffsetMs = lyricOffsetMs,
            language = language,
            score = score
        )
    }

    private fun attrByLocal(parser: XmlPullParser, local: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).endsWith(local)) return parser.getAttributeValue(i)
        }
        return null
    }

    // Handles "HH:MM:SS.mmm" | "M:SS.mmm" | "SS.mmm" | "27.395"
    fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return try {
            when {
                s.contains(":") -> {
                    val parts = s.split(":")
                    val seconds: Double = when (parts.size) {
                        3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                        2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                        else -> parts[0].toDouble()
                    }
                    (seconds * 1000).toLong()
                }
                else -> (s.toDouble() * 1000).toLong()
            }
        } catch (_: Exception) { null }
    }

    private data class SpanContext(
        val isBg: Boolean = false,
        val isWrapper: Boolean = false,
        val word: MutableWord? = null
    )
    private data class MutableWord(
        val beginMs: Long,
        val endMs: Long,
        val isBackground: Boolean,
        val agentId: String?,
        var text: String? = null
    )
    private data class MutableLine(
        val begin: Long,
        val end: Long,
        val agent: String?,
        val songPart: String?,
        val indexHint: String?,
        val pendingWords: MutableList<LyricWord> = mutableListOf()
    )
}
