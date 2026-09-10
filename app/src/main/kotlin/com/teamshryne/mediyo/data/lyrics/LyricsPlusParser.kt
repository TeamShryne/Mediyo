package com.teamshryne.mediyo.data.lyrics

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * LyricsPlus `/v2/lyrics/get` JSON → [LyricTrack].
 *
 * Shape (verified live):
 * ```
 * {"type":"Word", "metadata":{"language":"en",…},
 *  "lyrics":[{"time":19733,"duration":3335,"text":"We're no strangers to love",
 *             "syllabus":[{"text":"We're ","time":19733,"duration":362},…]}]}
 * ```
 * - `time`/`duration` are milliseconds.
 * - `type` is `"Word"` when `syllabus` timings exist, `"Line"` otherwise.
 * - Syllabus texts carry their own trailing spaces (`"We're "`), so they are
 *   trimmed and re-expressed via [LyricWord.hasTrailingSpace] to stay
 *   consistent with [TtmlParser] output (no double spaces in [LyricLine.text],
 *   correct `indexOf` mapping in the karaoke renderer).
 * - Lines without usable syllabus degrade to a single word per line
 *   (renders as whole-line highlight, same as LRC).
 */
object LyricsPlusParser {
    private const val TAG = "LyricsPlusParser"

    fun parse(json: String): LyricTrack {
        if (json.isBlank()) return LyricTrack(emptyList())
        return try {
            parseInternal(JSONObject(json))
        } catch (e: Exception) {
            Log.w(TAG, "parse failed", e)
            LyricTrack(emptyList())
        }
    }

    private fun parseInternal(root: JSONObject): LyricTrack {
        val arr: JSONArray = root.optJSONArray("lyrics") ?: return LyricTrack(emptyList())
        val language: String? = root.optJSONObject("metadata")?.optString("language")?.takeIf { it.isNotBlank() }
        val lines = mutableListOf<LyricLine>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("text").trim()
            if (text.isEmpty()) continue
            val begin = o.optLong("time", -1L).let { if (it < 0) null else it } ?: continue
            var end = if (o.has("duration")) begin + o.optLong("duration", 0L) else -1L
            if (end <= begin) end = -1L // inferred below
            lines.add(
                LyricLine(
                    index = lines.size,
                    beginMs = begin,
                    endMs = end, // fixed up below if unknown
                    words = parseWords(o.optJSONArray("syllabus"), fallbackText = text, lineBegin = begin, lineEnd = end)
                )
            )
        }
        if (lines.isEmpty()) return LyricTrack(emptyList(), language = language)
        // Infer missing ends from next line start (same convention as LRC fallback)
        val fixed = lines.mapIndexed { idx, l ->
            val end = if (l.endMs > l.beginMs) l.endMs else {
                val nextStart = lines.getOrNull(idx + 1)?.beginMs ?: (l.beginMs + 3000)
                maxOf(l.beginMs + 800, nextStart).coerceAtLeast(l.beginMs + 500)
            }
            val words = if (l.words.size == 1 && l.words[0].endMs <= l.words[0].beginMs) {
                listOf(l.words[0].copy(endMs = end))
            } else l.words
            l.copy(endMs = end, words = words)
        }
        return LyricTrack(lines = fixed, language = language)
    }

    private fun parseWords(
        syllabus: JSONArray?,
        fallbackText: String,
        lineBegin: Long,
        lineEnd: Long
    ): List<LyricWord> {
        if (syllabus == null || syllabus.length() == 0) {
            return listOf(LyricWord(text = fallbackText, beginMs = lineBegin, endMs = lineEnd))
        }
        val words = mutableListOf<LyricWord>()
        for (i in 0 until syllabus.length()) {
            val w = syllabus.optJSONObject(i) ?: continue
            val raw = w.optString("text")
            if (raw.isEmpty()) continue
            val hadTrailingSpace = raw.last().isWhitespace()
            val text = raw.trim()
            if (text.isEmpty()) continue
            val b = w.optLong("time", lineBegin)
            val e = if (w.has("duration")) b + w.optLong("duration", 0L) else lineEnd
            words.add(
                LyricWord(
                    text = text,
                    beginMs = b.coerceAtLeast(0),
                    endMs = if (e > b) e else lineEnd,
                    hasTrailingSpace = hadTrailingSpace
                )
            )
        }
        if (words.isEmpty()) {
            return listOf(LyricWord(text = fallbackText, beginMs = lineBegin, endMs = lineEnd))
        }
        // Last word never carries a trailing space (matches LyricLine.text contract)
        words[words.lastIndex] = words.last().copy(hasTrailingSpace = false)
        return words
    }
}
