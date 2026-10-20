package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.data.local.DiscoveryItemEntity
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.dbArtistIds
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.DiscoveryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Grows the discovery pool.
 *
 * Local taste decides *where* to look (the seeds), the queue graph decides
 * *what* we find, and the pool on disk decides what the user gets to see and
 * what never comes back. The three layers are kept apart on purpose:
 *
 * - seeds come from scored history (or, on a cold start, from the artists the
 *   user follows) so discovery still starts from taste;
 * - candidates come from queueing — and from queueing *again* from inside a
 *   previous queue, which is what reaches past the first neighbourhood;
 * - everything already played, liked or shown is excluded, so the shelf keeps
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
     * Hand out up to [want] unseen tracks, expanding the graph first if the
     * pool cannot cover the request. Returns only tracks the user has not
     * been shown before.
     */
    suspend fun grow(want: Int = 24): List<Track> {
        var pending = store.unserved(want)
        if (pending.size < MIN_POOL) {
            expand()
            pending = store.unserved(want)
        }
        return serve(pending)
    }

    /** Mark the batch as shown and map it back to playable tracks. */
    private suspend fun serve(items: List<DiscoveryItemEntity>): List<Track> {
        if (items.isEmpty()) return emptyList()
        store.markServed(items.map { it.videoId })
        return items.mapNotNull { it.toTrack() }
    }

    /** One expansion round: branch out, persist, remember what we branched from. */
    private suspend fun expand() {
        val known = home.flowKnownIds().first()
        val served = store.servedIds()
        // The seeds are known tracks on purpose — they are where we start
        // from. The exclusion set applies to what we *find*, never to where
        // we start.
        val exclude = known + served

        val seeds = pickSeeds(known)
        if (seeds.isEmpty()) return

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
        if (candidates.isEmpty()) return

        store.save(candidates.map { it.toEntity() })
        // The tracks we queued *from* are spent: expanding them again would
        // return the same neighbourhood.
        store.markExpanded(candidates.map { it.parentVideoId }.distinct())

        // Served rows exist only as an exclusion set; they can go eventually.
        store.evictServedBefore(System.currentTimeMillis() - SERVED_RETENTION_MS)
    }

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
                    rootSeedId = it.rootSeedId
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
                affinity = 1.0 + s.score
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
                affinity = 2.0
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

        /** Below this many unseen tracks we go to the network. */
        const val MIN_POOL = 6

        /** Frontier tracks are interesting, but less than the taste roots. */
        const val FRONTIER_AFFINITY = 0.5

        const val SERVED_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
