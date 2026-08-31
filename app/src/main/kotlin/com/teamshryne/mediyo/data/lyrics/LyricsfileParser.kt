package com.teamshryne.mediyo.data.lyrics

import android.util.Log

/**
 * LRCLIB Lyricsfile YAML → LyricTrack.
 * Lyricsfile is line-level: each line has text, start_ms, optional end_ms.
 * Word-level data is not present in production (words: optional) — we emit 1 word per line.
 * For word-synced use BetterLyrics TTML; LrcLib is fallback with line timings.
 */
object LyricsfileParser {
    private const val TAG = "LyricsfileParser"

    fun parse(yaml: String): LyricTrack {
        if (yaml.isBlank()) return LyricTrack(emptyList())
        return try {
            parseInternal(yaml)
        } catch (e: Exception) {
            Log.w(TAG, "parse failed", e)
            LyricTrack(emptyList())
        }
    }

    private fun parseInternal(yaml: String): LyricTrack {
        // Minimal YAML parser without extra dep — handle known Lyricsfile shape.
        // Structure:
        // version: '1.0'
        // metadata: {title, artist, album, duration_ms, instrumental}
        // lines: [{text, start_ms, end_ms}]
        // plain: |
        val linesBlock = extractLinesBlock(yaml)
        val language = extractMetadataString(yaml, "language")
        val durationMs = extractMetadataLong(yaml, "duration_ms")

        if (linesBlock.isEmpty()) return LyricTrack(emptyList(), durationMs, language = language)

        val parsedLines = mutableListOf<LyricLine>()
        // Each entry: - text: ...\n  start_ms: ...\n  end_ms: ...
        // text may be quoted or plain; also '' for blank lines
        // We do simple per-entry parse
        for (entry in linesBlock) {
            val text = entry["text"] ?: continue
            // Skip truly empty markers but keep '' as blank line? LRCLIB uses '' for gaps — skip to avoid empty lines in UI
            if (text == "''" || text == "\"\"") continue
            val rawText = unquote(text)
            if (rawText.isEmpty()) continue
            val startMs = entry["start_ms"]?.toLongOrNull() ?: continue
            val endMsRaw = entry["end_ms"]?.toLongOrNull()
            // end inferred later if null; avoid 0
            parsedLines.add(
                LyricLine(
                    index = parsedLines.size,
                    beginMs = startMs.coerceAtLeast(0),
                    endMs = (endMsRaw ?: -1),
                    words = listOf(
                        LyricWord(
                            text = rawText,
                            beginMs = startMs.coerceAtLeast(0),
                            endMs = (endMsRaw ?: startMs + 2000),
                        )
                    )
                )
            )
        }

        // Infer missing end_ms from next start
        val withEnds = parsedLines.mapIndexed { idx, l ->
            val end = if (l.endMs >= 0) l.endMs else {
                val nextStart = parsedLines.getOrNull(idx + 1)?.beginMs ?: (durationMs ?: l.beginMs + 3000)
                maxOf(l.beginMs + 800, nextStart).coerceAtLeast(l.beginMs + 500)
            }
            val adjustedEnd = maxOf(end, l.beginMs + 500)
            // also fix word end
            l.copy(
                endMs = adjustedEnd,
                words = l.words.map { w -> w.copy(endMs = adjustedEnd) }
            )
        }

        val sorted = withEnds.sortedBy { it.beginMs }.mapIndexed { i, l -> l.copy(index = i) }
        return LyricTrack(
            lines = sorted,
            durationMs = durationMs,
            language = language
        )
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if ((t.startsWith("'") && t.endsWith("'") && t.length >= 2) ||
            (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2)
        ) {
            return t.substring(1, t.length - 1)
                .replace("''", "'")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
        }
        return t
    }

    private fun extractMetadataLong(yaml: String, key: String): Long? {
        val re = Regex("""(?m)^\s*$key:\s*(\d+)\s*$""")
        return re.find(yaml)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractMetadataString(yaml: String, key: String): String? {
        val re = Regex("""(?m)^\s*$key:\s*(.+?)\s*$""")
        val raw = re.find(yaml)?.groupValues?.get(1)?.trim() ?: return null
        if (raw == "null" || raw == "~") return null
        return unquote(raw)
    }

    private fun extractLinesBlock(yaml: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        // Find lines: section
        val linesIdx = yaml.indexOf("\nlines:")
        if (linesIdx == -1) return emptyList()
        val after = yaml.substring(linesIdx + "\nlines:".length)
        // Split by entries starting with "\n- "
        val entries = after.split(Regex("""(?m)^- """))
        // First split part is before first entry (maybe empty/contains newline)
        for (i in 1 until entries.size) {
            val chunk = entries[i]
            // chunk may contain next keys or plain: section — cut at next "^plain:" or "^metadata?"? just parse lines until plain
            // Extract up to next entry's delimiter already handled; but chunk may contain "\nplain:" trailing
            val cleanChunk = if (chunk.contains("\nplain:")) chunk.substringBefore("\nplain:") else chunk
            val map = mutableMapOf<String, String>()
            // Match key: value per line indented
            val lineRe = Regex("""(?m)^\s*(\w+):\s*(.*)\s*$""")
            for (m in lineRe.findAll(cleanChunk)) {
                val k = m.groupValues[1]
                val v = m.groupValues[2].trim()
                // Only capture known keys, but allow others ignoring
                if (k in setOf("text", "start_ms", "end_ms")) {
                    map[k] = v
                }
            }
            if (map.isNotEmpty()) result.add(map)
        }
        return result
    }
}

object LrcParser {
    private val tsRe = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): LyricTrack {
        if (lrc.isBlank()) return LyricTrack(emptyList())
        val entries = mutableListOf<Pair<Long, String>>()
        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = tsRe.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val ms = tsToMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                entries.add(ms to text)
            }
        }
        entries.sortBy { it.first }
        val lines = entries.mapIndexed { idx, (start, text) ->
            val end = entries.getOrNull(idx + 1)?.first ?: (start + 3000)
            LyricLine(
                index = idx,
                beginMs = start,
                endMs = maxOf(end, start + 800),
                words = listOf(LyricWord(text, start, maxOf(end, start + 800)))
            )
        }
        return LyricTrack(lines)
    }

    private fun tsToMs(min: String, sec: String, frac: String): Long {
        val m = min.toLongOrNull() ?: 0
        val s = sec.toLongOrNull() ?: 0
        val ms = when (frac.length) {
            0 -> 0
            1 -> frac.toInt() * 100
            2 -> frac.toInt() * 10
            3 -> frac.toInt()
            else -> frac.take(3).toIntOrNull() ?: 0
        }
        return m * 60_000 + s * 1000 + ms
    }
}

object PlainLyricsParser {
    fun parse(plain: String): LyricTrack {
        val nonEmpty = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return LyricTrack(emptyList())
        // No timings — create untimed lines for display fallback (not synced)
        // We'll assign pseudo timings so UI can show static list; Queried as non-synced
        // For now return empty to let UI show NotFound? Instead return lines with 0 timings but UI will still render via text fallback.
        // Choices: return lines with index but begin 0 — LyricsView handles it via NotFound vs Ready distinction.
        // Better to return empty so caller can treat as NotFound.
        return LyricTrack(emptyList())
    }
}
