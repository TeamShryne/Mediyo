package com.teamshryne.mediyo.data.lyrics

/**
 * How the synced lyrics are drawn: text size, weight and the gap between
 * lines.
 *
 * Deliberately free of Compose types so it can be persisted as plain values
 * and mapped to UI types where it is rendered.
 */
data class LyricsStyle(
    /** Font size of a lyric line, in sp. */
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    /** Weight of the lyric text. */
    val weight: LyricsFontWeight = LyricsFontWeight.Bold,
    /** Gap between two lyric lines, in dp. */
    val lineSpacingDp: Float = DEFAULT_LINE_SPACING_DP
) {
    companion object {
        const val DEFAULT_FONT_SIZE_SP = 24f
        const val DEFAULT_LINE_SPACING_DP = 10f
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 40f
        const val MIN_LINE_SPACING_DP = 0f
        const val MAX_LINE_SPACING_DP = 48f
    }
}

/**
 * Font weights the user can pick for the lyrics. [weightValue] is the CSS-style
 * numeric weight (100..900) so the UI layer can build a `FontWeight` from it.
 */
enum class LyricsFontWeight(val id: String, val label: String, val weightValue: Int) {
    Medium("medium", "Medium", 500),
    SemiBold("semiBold", "Semi-bold", 600),
    Bold("bold", "Bold", 700),
    ExtraBold("extraBold", "Extra bold", 800),
    Black("black", "Black", 900);

    companion object {
        fun fromId(id: String?): LyricsFontWeight = entries.find { it.id == id } ?: Bold
    }
}
