package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.local.FollowedArtistEntity
import com.teamshryne.mediyo.data.local.HistoryEntryEntity
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbIdList
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import com.teamshryne.mediyo.domain.repository.SavedCollectionRepository
import com.teamshryne.mediyo.domain.repository.UserEventRepository
import com.teamshryne.mediyo.domain.repository.UserEventTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DAY_MS = 86_400_000L

/**
 * Local-only home feed: every section is computed from Room (history, likes,
 * follows, saves, playlists, event log), so home opens instantly and offline.
 * Network shelves (radio, releases, similar artists) live in [HomeVm] and
 * load lazily underneath this content.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val history: HistoryRepository,
    private val saved: SavedCollectionRepository,
    private val liked: LikeRepository,
    private val artists: ArtistRepository,
    private val playlists: PlaylistRepository,
    private val events: UserEventRepository,
) {
    fun flowRecent(limit: Int = 20): Flow<List<Track>> =
        history.flowHistory().map { rows ->
            rows.take(limit).map { it.toHomeTrack() }
        }

    fun flowSaved(): Flow<List<SavedCollectionEntity>> = saved.flowAll()

    fun flowLikedCount(): Flow<Int> = liked.flowLiked().map { it.size }
    fun flowPlaylistCount(): Flow<Int> = playlists.flowPlaylists().map { it.size }

    /** Every known video id: radio shelves filter these out (novelty must be novel). */
    fun flowKnownIds(): Flow<Set<String>> = combine(
        history.flowHistory(), liked.flowLiked()
    ) { h, l -> h.map { it.videoId }.toSet() + l.map { it.videoId }.toSet() }

    /** Scored song pool, episodes excluded — the single ranking everything exploits. */
    fun flowScored(): Flow<List<ScoredTrack>> = history.flowHistory().map { rows ->
        val now = System.currentTimeMillis()
        rows.filter { !it.isEpisode() && it.videoId.isNotBlank() }
            .map { ScoredTrack(it.toHomeTrack(), scoreHistory(it, now)) }
            .sortedByDescending { it.score }
    }

    /**
     * Quick picks: top 6 of the scored pool. Six tiles = one glance, zero
     * scrolling (decision fatigue: more options, fewer taps).
     */
    fun flowQuickPicks(): Flow<List<Track>> = flowScored().map { it.take(6).map { s -> s.track } }

    /**
     * Heavy rotation: most plays started in the last 7 days. Identity shelf —
     * "my week in music" — recency-bounded so last year's phase can't squat it.
     */
    fun flowWeekTop(): Flow<List<WeekTop>> = combine(
        history.flowHistory(), history.flowRecentEvents()
    ) { rows, evts ->
        val weekAgo = System.currentTimeMillis() - 7 * DAY_MS
        val counts = evts.filter { it.playedAt >= weekAgo }.groupingBy { it.videoId }.eachCount()
        val byId = rows.associateBy { it.videoId }
        counts.entries.sortedByDescending { it.value }
            .mapNotNull { e -> byId[e.key]?.takeIf { !it.isEpisode() }?.let { WeekTop(it.toHomeTrack(), e.value) } }
            .take(10)
    }

    /**
     * On repeat: devotion, not volume — ranked by completions (full listens),
     * so the song you finish every time beats the one that merely autoplayed.
     */
    fun flowOnRepeat(): Flow<List<Track>> = history.flowHistory().map { rows ->
        rows.filter { !it.isEpisode() && it.completions >= 2 }
            .sortedByDescending { it.completions }.take(8).map { it.toHomeTrack() }
    }

    /**
     * Rediscover: loved once (real lifetime minutes), untouched 30+ days.
     * Nostalgia is the strongest music emotion — spaced repetition as a shelf.
     */
    fun flowRediscover(): Flow<List<Track>> = history.flowHistory().map { rows ->
        val cutoff = System.currentTimeMillis() - 30 * DAY_MS
        rows.filter { !it.isEpisode() && it.lastPlayedAt < cutoff && it.playCount >= 3 }
            .sortedByDescending { it.totalPlayMs }.take(10).map { it.toHomeTrack() }
    }

    /**
     * Top artists: summed track scores per artist (one obsession beats ten
     * casuals), followed artists flagged — follows are explicit identity.
     */
    /**
     * Top artists: one entry per real artist.
     *
     * History stores a single byline string per track, and that string can
     * hold several names ("Arijit Singh, Pritam") with browse ids stored
     * parallel to them. Grouping on the raw byline therefore invented fake
     * "artists" made of whole collaborations, and crediting each of them with
     * just the first id made the tap target the wrong person. So: split the
     * byline, pair names with ids by position, and group on the browse id
     * when we have one (the stable identity) and the normalised name when we
     * don't (rows saved before ids were stored).
     */
    fun flowTopArtists(): Flow<List<ArtistAffinity>> = combine(
        history.flowHistory(), artists.flowFollowed()
    ) { rows: List<HistoryEntryEntity>, followed: List<FollowedArtistEntity> ->
        val now = System.currentTimeMillis()
        class Acc(
            var name: String,
            var id: String?,
            var score: Double,
            var plays: Int,
            var art: String?,
            var tracks: Int
        )
        val acc = linkedMapOf<String, Acc>()
        rows.filter { !it.isEpisode() }.forEach { row ->
            val names = row.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (names.isEmpty()) return@forEach
            val ids = row.artistIds.dbIdList()
            names.forEachIndexed { i, name ->
                // Only UC… ids are artist pages; anything else would open a
                // broken screen, so treat it as unknown and resolve by name.
                val id = ids.getOrNull(i)?.trim()?.takeIf { it.startsWith("UC") }
                val key = id ?: "n:" + name.lowercase()
                val a = acc.getOrPut(key) { Acc(name, id, 0.0, 0, null, 0) }
                a.score += scoreHistory(row, now)
                a.plays += row.playCount
                a.tracks++
                if (a.art == null) a.art = row.artworkUrl
                if (a.id == null && id != null) a.id = id
            }
        }
        val followedById = followed.associateBy { it.browseId }
        val out = acc.values.map { a ->
            ArtistAffinity(
                name = a.name,
                artistId = a.id,
                artworkUrl = a.art,
                score = a.score,
                followed = a.id != null && followedById.containsKey(a.id),
                trackCount = a.tracks
            )
        }.sortedByDescending { it.score }.toMutableList()
        // Followed-but-unheard artists still belong here (explicit taste), and
        // their stored name/art are cleaner than anything history inferred.
        followed.forEach { f ->
            val existing = out.indexOfFirst { it.artistId == f.browseId }
            if (existing >= 0) {
                out[existing] = out[existing].copy(
                    name = f.name,
                    artworkUrl = f.artworkUrl ?: out[existing].artworkUrl,
                    followed = true
                )
            } else {
                out.add(ArtistAffinity(f.name, f.browseId, f.artworkUrl, 0.0, true, 0))
            }
        }
        out.sortedByDescending { it.score }.take(10)
    }

    /**
     * Mix seeds: one representative track per top-3 affinity artist. Tapping a
     * mix walks that track's radio — the seed plays first (familiar), then
     * the queue drifts adjacent (novel). No fetch until tapped.
     */
    fun flowMixSeeds(): Flow<List<HomeMix>> = combine(
        flowScored(), flowTopArtists()
    ) { scored, topArtists ->
        topArtists.take(3).mapNotNull { a ->
            // Names are per-artist now, so match loosely: a track credited to
            // several artists still seeds each of them.
            val seed = scored.firstOrNull { s ->
                s.track.artists.any { it.trim().equals(a.name.trim(), ignoreCase = true) }
            }?.track ?: return@mapNotNull null
            HomeMix(a.name, seed.artworkUrl, seed)
        }
    }

    /**
     * Jump back in: latest episodes (resume-driven, not craving-driven —
     * separate pool from songs, latest-first, deduped).
     */
    fun flowEpisodes(): Flow<List<Track>> = history.flowHistory().map { rows ->
        rows.filter { it.isEpisode() }.take(6).map { it.toHomeTrack() }
    }

    /**
     * Interrupted intent: distinct recent search queries as chips. Tapping
     * re-runs the search — cheaper than retyping, catches dropped sessions.
     */
    fun flowRecentSearches(): Flow<List<String>> = events.flowRecent(60).map { evts ->
        evts.filter { it.type == UserEventTypes.SEARCH && !it.label.isNullOrBlank() }
            .map { it.label!!.trim() }.distinct().take(8)
    }

    /** Latest query powers the spotlight card ("because you searched X"). */
    fun flowLatestSearch(): Flow<String?> = flowRecentSearches().map { it.firstOrNull() }

    /**
     * Week in numbers: plays + minutes from raw events (exact), not lifetime
     * aggregates. A Wrapped-style micro-moment; makes the app feel alive.
     */
    fun flowWeekStats(): Flow<WeekStats> = history.flowRecentEvents().map { evts ->
        val weekAgo = System.currentTimeMillis() - 7 * DAY_MS
        val week = evts.filter { it.playedAt >= weekAgo }
        WeekStats(week.size, (week.sumOf { it.playDurationMs } / 60000).toInt())
    }

    /**
     * Finish the album: saved albums 30–90% played (endowed progress —
     * started collections beg finishing). Totals come from parsing the saved
     * count text, not a fetch.
     */
    fun flowFinishAlbums(): Flow<List<AlbumProgress>> = combine(
        saved.flowAll(), history.flowHistory()
    ) { all, rows ->
        val byAlbum = rows.filter { !it.albumId.isNullOrBlank() }
            .groupBy { it.albumId!! }.mapValues { (_, v) -> v.map { it.videoId }.toSet().size }
        all.filter { it.kind == SavedCollectionEntity.ALBUM }.mapNotNull { s ->
            val total = parseTotalTracks(s.trackCountText)
            val done = byAlbum[s.browseId] ?: 0
            if (total <= 0 || done == 0) return@mapNotNull null
            val ratio = done.toFloat() / total
            if (ratio < 0.3f || ratio >= 1f) return@mapNotNull null
            AlbumProgress(s.browseId, s.title, s.artworkUrl, done, total)
        }.sortedByDescending { it.ratio }.take(5)
    }

    /**
     * The clock is a genre: night (21–5) vs morning (5–11) from play-event
     * hours, joined back to tracks. Label follows the clock.
     */
    fun flowDaypart(): Flow<DaypartShelves> = combine(
        history.flowHistory(), history.flowRecentEvents()
    ) { rows, evts ->
        val byId = rows.associateBy { it.videoId }
        fun at(hours: Set<Int>): List<Track> = evts.filter { it.hourOfDay in hours }
            .groupingBy { it.videoId }.eachCount()
            .entries.sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }
            .filter { !it.isEpisode() }.distinctBy { it.videoId }
            .take(8).map { it.toHomeTrack() }
        DaypartShelves(
            night = at((21..23).toSet() + (0..4).toSet()),
            morning = at((5..10).toSet())
        )
    }
}
