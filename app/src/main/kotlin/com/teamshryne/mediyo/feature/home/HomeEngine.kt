package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbIdList
import kotlin.math.exp
import kotlin.math.ln

/**
 * On-device taste engine: every home section below is a different question
 * asked of the same local signals (plays, completions, skips, likes,
 * follows, timestamps). No server profile, no tracking — the algorithm is
 * this file plus the queries in [HomeRepository].
 *
 * The governing curve (Berlyne/Wundt): liking rises with familiarity, then
 * falls with boredom. So ~80% of home is familiar (exploit) and ~20% is
 * adjacent-novel (explore via radio seeds). Skips only ever demote — skip
 * research is clear that "skip" usually means "wrong moment", not dislike.
 */

// ── Scoring ──────────────────────────────────────────────────────────────

/** A history row with its exploit score attached. */
data class ScoredTrack(val track: Track, val score: Double)

/**
 * Exploit score: recency decay × log-frequency × completion quality,
 * minus a soft skip penalty. Explicit origins (user searched / picked it)
 * outrank passive ones (radio autoplay) because chosen music predicts
 * future choice better than heard music.
 */
fun scoreHistory(e: HistoryEntryEntity, now: Long): Double {
    if (e.videoId.isBlank()) return 0.0
    val days = ((now - e.lastPlayedAt).coerceAtLeast(0)).toDouble() / 86_400_000.0
    val recency = exp(-days / 21.0) // ~3-week half-attention window
    val frequency = 1.0 + ln(1.0 + e.playCount.toDouble()) // log: 50 plays ≠ 50× love
    val total = (e.completions + e.skips).coerceAtLeast(1)
    val quality = 0.5 + 0.5 * (e.completions.toDouble() / total) // 0.5..1.0
    val skipPenalty = 1.0 / (1.0 + 0.15 * e.skips) // gentle, never a ban
    val originBoost = when (e.lastOriginType) {
        "Search", "Liked", "Single", "HomeShelf", "History" -> 1.25 // explicitly chosen
        "Radio" -> 0.8 // overheard, not chosen
        else -> 1.0
    }
    return recency * frequency * quality * skipPenalty * originBoost
}

/** Artist affinity: summed track scores, so one obsession beats ten casuals. */
data class ArtistAffinity(
    val name: String,
    val artistId: String?,
    val artworkUrl: String?,
    val score: Double,
    val followed: Boolean,
    val trackCount: Int
)

/** A generated mix: three taps max on screen (choice fatigue), seed plays on tap. */
data class HomeMix(val artistName: String, val artworkUrl: String?, val seed: Track)

/** Album-completion shelf item: endowed-progress effect — started collections beg finishing. */
data class AlbumProgress(
    val browseId: String,
    val title: String,
    val artworkUrl: String?,
    val done: Int,
    val total: Int
) {
    val ratio: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/** Week-identity banner inputs: your listening, counted. */
data class WeekStats(val plays: Int, val minutes: Int)

/** Context shelf: music follows the clock (commutes, late nights are real genres). */
data class DaypartShelves(val night: List<Track>, val morning: List<Track>)

/** Radio-grown discovery shelf: familiar seed, unfamiliar tracks. */
data class RadioShelf(val seedTitle: String, val seedVideoId: String?, val tracks: List<Track>)

/** Week-rotation entry: the count is the headline ("played 12 times"). */
data class WeekTop(val track: Track, val plays: Int)

/** "3 songs" → 3. Saved counts are display strings, not ints — parse, don't fetch. */
fun parseTotalTracks(text: String?): Int =
    text?.let { Regex("""(\d[\d,]*)""").find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() } ?: 0

fun HistoryEntryEntity.toHomeTrack(): Track = Track(
    videoId = videoId,
    browseId = null,
    playlistId = null,
    title = title,
    artists = listOf(artist).filter { it.isNotBlank() },
    artistIds = artistIds.dbIdList(),
    album = album,
    albumId = albumId,
    channelName = channelName,
    channelId = channelId,
    artworkUrl = artworkUrl,
    duration = duration,
    category = category
)

/** Episodes are resume-driven, songs are craving-driven — never mix the pools. */
fun HistoryEntryEntity.isEpisode(): Boolean =
    category.equals("Episode", true) || lastOriginType.equals("Podcast", true)

fun Track.isEpisode(): Boolean = category.equals("Episode", true)
