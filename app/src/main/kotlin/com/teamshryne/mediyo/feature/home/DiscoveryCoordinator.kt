package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.local.DiscoveryItemEntity
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbArtistIds
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.model.toDomainTracks
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.DiscoveryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * One direction of discovery: the tracks found by branching from a single
 * taste seed. Read back as a "Because you heard X" shelf.
 */
data class DiscoveryShelf(
    /** The seed's title — the shelf says it heard this. */
    val title: String,
    val seedVideoId: String,
    val tracks: List<Track>
)

/**
 * Grows the discovery catalogue.
 *
 * Local taste decides *where* to look (the seeds), the queue graph decides
 * *what* we find, and the catalogue on disk decides what the user gets to see
 * and what never comes back. The three layers are kept apart on purpose:
 *
 * - seeds come from scored history (or, on a cold start, from the artists the
 *   user follows) so discovery still starts from taste;
 * - candidates come from queueing — and from queueing *again* from inside a
 *   previous queue, which is what reaches past the first neighbourhood;
 * - everything already played, liked or found is excluded, so the shelves keep
 *   producing material the user has never seen.
 */
@Singleton
class DiscoveryCoordinator @Inject constructor(
    private val bridge: MediyoBridge,
    private val store: DiscoveryRepository,
    private val home: HomeRepository,
    private val artists: ArtistRepository
) {

    /** Taste seeds already branched from this session: don't pay for them twice. */
    private val spentRoots = mutableSetOf<String>()

    /**
     * Expand the graph one round. Returns how many new tracks were found.
     *
     * The catalogue is what the exclusion set is built from, so a track that
     * has been found is never found again: every round reaches further out.
     */
    suspend fun expand(): Int {
        val known = home.flowKnownIds().first()
        val found = store.knownIds()
        // The seeds are known tracks on purpose — they are where we start
        // from. The exclusion set applies to what we *find*, never to where
        // we start.
        val exclude = known + found

        val seeds = pickSeeds(known)
        if (seeds.isEmpty()) return 0

        val candidates = DiscoveryEngine.expand(
            seeds = seeds,
            exclude = exclude,
            radio = { videoId -> radioTracks(videoId) },
            budget = ExpansionBudget(
                maxSeeds = MAX_SEEDS,
                maxBranches = MAX_BRANCHES,
                maxDepth = 2,
                maxCandidates = MAX_CANDIDATES,
                maxPerArtist = 2
            )
        )
        if (candidates.isEmpty()) return 0

        store.save(candidates.map { it.toEntity() })
        // The tracks we queued *from* are spent: expanding them again would
        // return the same neighbourhood.
        store.markExpanded(candidates.map { it.parentVideoId }.distinct())

        // The catalogue only grows; old branches can go eventually.
        store.evict(System.currentTimeMillis() - CATALOGUE_RETENTION_MS)
        return candidates.size
    }

    /**
     * The catalogue, one shelf per taste seed, biggest first.
     *
     * The shelf's first track (the one the carousel shows as its headline)
     * is left out here, so no track appears in two places.
     */
    suspend fun catalogue(): List<DiscoveryShelf> {
        val pool = store.all()
        return pool.groupBy { it.rootSeedId }
            .map { (root, items) ->
                val sorted = items.sortedWith(compareBy({ it.depth }, { it.rank }))
                val headline = sorted.firstOrNull { it.depth == 1 && it.rank == 0 }
                DiscoveryShelf(
                    title = items.firstOrNull()?.rootTitle?.takeIf { it.isNotBlank() }.orEmpty(),
                    seedVideoId = root,
                    tracks = sorted.filter { it.videoId != headline?.videoId }.map { it.toTrack() }
                )
            }
            .filter { it.tracks.isNotEmpty() }
            .sortedByDescending { it.tracks.size }
            .take(MAX_SHELVES)
    }

    /**
     * One track per direction: the shelf's headline, which the quick-picks
     * carousel shows as its discovery column.
     */
    suspend fun headlines(): List<Track> =
        store.all()
            .filter { it.depth == 1 && it.rank == 0 }
            .distinctBy { it.rootSeedId }
            .map { it.toTrack() }

    /**
     * Seeds for the next hop.
     *
     * Two kinds of branch point, and the mix is the point:
     * - the taste roots (your top tracks, one per artist) keep the discovery
     *   anchored to what you actually listen to;
     * - the frontier (tracks we found earlier but never branched from) are
     *   further out, so their neighbourhoods reach past the first circle.
     *
     * Both fit in the same round: the frontier keeps the graph growing
     * outward while the roots keep it on-taste.
     */
    private suspend fun pickSeeds(known: Set<String>): List<DiscoverySeed> {
        val seeds = LinkedHashMap<String, DiscoverySeed>()

        // The frontier is where the branching happens: a track we already
        // know still has unexplored neighbours.
        val frontier = store.frontier(MAX_FRONTIER)
            .filter { it.videoId !in known }
            .map {
                DiscoverySeed(
                    videoId = it.videoId,
                    title = it.title,
                    artist = it.artist,
                    affinity = FRONTIER_AFFINITY,
                    depth = it.depth,
                    rootSeedId = it.rootSeedId,
                    rootTitle = it.rootTitle.orEmpty()
                )
            }
        frontier.forEach { seeds[it.videoId] = it }

        // Leave room for the frontier: the roots must not crowd it out.
        val rootSlots = (MAX_SEEDS - seeds.size).coerceAtLeast(1)
        tasteRoots().take(rootSlots).forEach { seed ->
            if (seeds.containsKey(seed.videoId)) return@forEach
            seeds[seed.videoId] = seed
        }

        return seeds.values.toList()
    }

    /**
     * Taste seeds: the top of the scored pool, one per artist. With no
     * history yet we fall back to the artists the user follows — otherwise a
     * new user never leaves the empty state.
     */
    private suspend fun tasteRoots(): List<DiscoverySeed> {
        val scored = home.flowScored().first()
        val roots = LinkedHashMap<String, DiscoverySeed>()
        for (s in scored) {
            val videoId = s.track.videoId ?: continue
            val artist = s.track.artists.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val key = artist.lowercase().ifBlank { videoId }
            if (roots.containsKey(key)) continue
            roots[key] = DiscoverySeed(
                videoId = videoId,
                title = s.track.title,
                artist = artist,
                // Offset above the frontier so taste always outranks "found
                // earlier" when the engine picks its branches.
                affinity = 1.0 + s.score,
                rootTitle = s.track.title
            )
            if (roots.size >= MAX_ROOTS) break
        }
        if (roots.isNotEmpty()) {
            // Prefer roots we haven't branched from yet this session.
            val fresh = roots.values.filter { it.videoId !in spentRoots }
            val chosen = if (fresh.isEmpty()) roots.values.toList() else fresh
            chosen.forEach { spentRoots.add(it.videoId) }
            return chosen
        }

        // Cold start: no history. Start from the artists the user follows.
        val followed = artists.flowFollowed().first()
        for (f in followed.take(MAX_ROOTS)) {
            val top = try {
                bridge.artist(f.browseId).topSongs.toDomainTracks()
            } catch (_: Throwable) {
                emptyList()
            }
            val track = top.firstOrNull() ?: continue
            val videoId = track.videoId ?: continue
            roots[videoId] = DiscoverySeed(
                videoId = videoId,
                title = track.title,
                artist = track.artists.joinToString(),
                affinity = 2.0,
                rootTitle = track.title
            )
        }
        return roots.values.toList()
    }

    /** The neighbours of a track: radio of the track, minus anything unusable. */
    private suspend fun radioTracks(videoId: String): List<Track> = try {
        bridge.radioFor(videoId)?.items
            ?.map { it.toDomainTrack() }
            ?.filter { !it.videoId.isNullOrBlank() }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun DiscoveryCandidate.toEntity() = DiscoveryItemEntity(
        videoId = track.videoId!!,
        title = track.title,
        artist = track.artists.joinToString(),
        artistIds = track.dbArtistIds(),
        artworkUrl = track.artworkUrl,
        album = track.album,
        duration = track.duration,
        depth = depth,
        rootSeedId = rootSeedId,
        rootTitle = rootTitle,
        parentVideoId = parentVideoId,
        rank = rank
    )

    private fun DiscoveryItemEntity.toTrack() = Track(
        videoId = videoId,
        title = title,
        artists = artist.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        artistIds = artistIds.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() },
        album = album,
        artworkUrl = artworkUrl,
        duration = duration,
        category = "Song"
    )

    private companion object {
        /** Taste seeds per round. */
        const val MAX_ROOTS = 4

        /** Deeper branches per round (found, never branched from). */
        const val MAX_FRONTIER = 2

        /** Branch points per round (roots + frontier). */
        const val MAX_SEEDS = 4

        /**
         * Queue calls per round. Each one is two requests. Four branch the
         * round's seeds; the remaining two queue from inside a queue, which
         * is the hop that reaches past the first neighbourhood.
         */
        const val MAX_BRANCHES = 6

        /** Candidates kept per round. */
        const val MAX_CANDIDATES = 40

        /** How many "because you heard X" shelves home shows at once. */
        const val MAX_SHELVES = 4

        /** Frontier tracks are interesting, but less than the taste roots. */
        const val FRONTIER_AFFINITY = 0.5

        const val CATALOGUE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
