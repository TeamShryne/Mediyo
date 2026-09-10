package com.teamshryne.mediyo.data.lyrics

/**
 * Single place that knows how to turn any cached lyrics payload back into a
 * [LyricTrack]. Payloads come from different providers:
 * - TTML XML (BetterLyrics, LyricsPlus TTML, Paxsenix Apple) → [TtmlParser]
 * - Lyricsfile YAML (LRCLIB `lyricsfile`) → [LyricsfileParser]
 * - LRC text (Kugou, LRCLIB `syncedLyrics`) → [LrcParser]
 * - LyricsPlus v2 JSON (`{"type":…,"lyrics":[…]}`) → [LyricsPlusParser]
 *
 * Order matters: each branch falls back to the next parser before giving up,
 * so a mis-sniffed payload still gets a chance.
 */
object CachedLyricsParser {

    private val LRC_TS = Regex("\\[\\d{1,3}:\\d{2}[.:]\\d{1,3}]")

    fun parse(cached: String): LyricTrack {
        if (cached.isBlank()) return LyricTrack(emptyList())
        val head = cached.trimStart()
        return when {
            // Lyricsfile YAML
            head.startsWith("version:") || cached.contains("start_ms:") ->
                LyricsfileParser.parse(cached)
                    .ifEmpty { TtmlParser.parse(cached) }
                    .ifEmpty { LyricsPlusParser.parse(cached) }
                    .ifEmpty { LrcParser.parse(cached) }
            // LyricsPlus v2 JSON
            head.startsWith("{") && (cached.contains("\"syllabus\"") || cached.contains("KpoeTools")) ->
                LyricsPlusParser.parse(cached)
                    .ifEmpty { TtmlParser.parse(cached) }
            // LRC ([mm:ss.xx] timestamps, incl. [ti:]/[ar:] headers)
            LRC_TS.containsMatchIn(cached.take(2000)) ->
                LrcParser.parse(cached)
                    .ifEmpty { TtmlParser.parse(cached) }
            // Default: TTML, then everything else
            else ->
                TtmlParser.parse(cached)
                    .ifEmpty { LyricsfileParser.parse(cached) }
                    .ifEmpty { LyricsPlusParser.parse(cached) }
                    .ifEmpty { LrcParser.parse(cached) }
        }
    }

    private fun LyricTrack.ifEmpty(next: () -> LyricTrack): LyricTrack =
        if (!isEmpty) this else next()
}
