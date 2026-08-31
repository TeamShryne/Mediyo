package com.teamshryne.mediyo.data.lyrics

/**
 * Scalable, maintainable lyric domain models.
 * All timings in milliseconds from track start (0).
 */

data class LyricWord(
    val text: String,
    val beginMs: Long,
    val endMs: Long,
    val isBackground: Boolean = false,
    val agentId: String? = null,
)

data class LyricLine(
    val index: Int,
    val beginMs: Long,
    val endMs: Long,
    val words: List<LyricWord>,
    val agentId: String? = null,
    val isBackground: Boolean = false,
    val songPart: String? = null,
) {
    val text: String get() = words.joinToString("") { it.text }.trim()
    val durationMs: Long get() = (endMs - beginMs).coerceAtLeast(0)
}

data class LyricTrack(
    val lines: List<LyricLine>,
    val durationMs: Long? = null,
    val lyricOffsetMs: Long = 0L,
    val language: String? = null,
    val score: Int? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val isRtl: Boolean get() = language in setOf("ar", "he", "fa", "ur")
}

sealed interface LyricsResult {
    data class Success(val track: LyricTrack, val rawTtml: String) : LyricsResult
    data object NotFound : LyricsResult
    data object RateLimited : LyricsResult
    data object NeedsApiKey : LyricsResult
    data class Error(val message: String, val code: Int? = null) : LyricsResult
}
