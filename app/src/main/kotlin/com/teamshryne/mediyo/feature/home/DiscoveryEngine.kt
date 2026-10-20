package com.teamshryne.mediyo.feature.home

import com.teamshryne.mediyo.domain.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The discovery graph.
 *
 * A queue/radio endpoint is a graph: every track has a neighbourhood of
 * similar tracks. One neighbourhood is not discovery, it is a mirror of your
 * taste. Real exploration comes from *walking* that graph:
 *
 * 1. seed with several **different** directions (your top tracks, one per
 *    artist) so the branches don't all collapse into one genre;
 * 2. queue from a song that came out of a previous queue — the second hop
 *    reaches tracks the first hop never would;
 * 3. **interleave** the branches instead of concatenating them, so no single
 *    neighbourhood can take over the shelf;
 * 4. cap how many tracks one artist may contribute.
 *
 * The result is breadth-first, not depth-first: familiar seeds, unfamiliar
 * neighbours, mixed. Pure Kotlin and side-effect free so the ranking can be
 * reasoned about (and tested) without a network.
 */

/** A branch point in the graph: a track we can queue from. */
data class DiscoverySeed(
    val videoId: String,
    val title: String = "",
    val artist: String = "",
    /** Higher = explore this direction first. */
    val affinity: Double = 0.0,
    /** Graph distance from the original taste seed (0 = the seed itself). */
    val depth: Int = 0,
    /** The taste seed this branch grew from. */
    val rootSeedId: String = videoId
)

/** One discovered track, with the graph context it was found in. */
data class DiscoveryCandidate(
    val track: Track,
    val depth: Int,
    val rootSeedId: String,
    /** The track we queued from to find this one. */
    val parentVideoId: String,
    /** Position inside the source queue — the provider's own confidence order. */
    val rank: Int
)

/**
 * Network budget for one expansion round. Every branch costs one queue call
 * (which is two requests: the automix hop plus the neighbour list), so the
 * budget is deliberately small and per-round, not per-session.
 */
data class ExpansionBudget(
    /** Distinct taste seeds to branch from. */
    val maxSeeds: Int = 4,
    /** Queue calls per round. */
    val maxBranches: Int = 4,
    /** How many hops outward from the seed. */
    val maxDepth: Int = 3,
    /** Upper bound on returned candidates. */
    val maxCandidates: Int = 24,
    /** How many tracks one artist may contribute to the shelf. */
    val maxPerArtist: Int = 2
)

object DiscoveryEngine {

    /**
     * Expand the graph from [seeds].
     *
     * [exclude] holds every id that must never come back: what the user
     * already played, what they liked, and what we already showed them.
     * [radio] fetches the neighbours of a track — the only network call here.
     */
    suspend fun expand(
        seeds: List<DiscoverySeed>,
        exclude: Set<String>,
        radio: suspend (String) -> List<Track>,
        budget: ExpansionBudget = ExpansionBudget()
    ): List<DiscoveryCandidate> {
        val roots = seeds.filter { it.videoId.isNotBlank() }.distinctBy { it.videoId }
        if (roots.isEmpty() || budget.maxBranches <= 0) return emptyList()

        // Seen = excluded + everything we find during this expansion, so the
        // same track can never show up twice in one shelf.
        val seen = HashSet(exclude)
        val found = mutableListOf<DiscoveryCandidate>()
        var frontier = roots.sortedByDescending { it.affinity }.take(budget.maxSeeds)
        var wave = 0
        var calls = 0

        while (frontier.isNotEmpty() && wave < budget.maxDepth && calls < budget.maxBranches) {
            // One wave = one hop outward. Waves run in parallel: the branches
            // are independent, so there is no reason to serialise them.
            val batch = frontier.take(budget.maxBranches - calls)
            calls += batch.size
            val expanded = coroutineScope {
                batch.map { seed ->
                    async {
                        val neighbours = try {
                            radio(seed.videoId)
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        seed to neighbours
                    }
                }.awaitAll()
            }

            val byRoot = LinkedHashMap<String, MutableList<DiscoveryCandidate>>()
            val nextFrontier = mutableListOf<DiscoverySeed>()

            for ((seed, neighbours) in expanded) {
                val fresh = mutableListOf<DiscoveryCandidate>()
                neighbours.forEachIndexed { index, track ->
                    val vid = track.videoId ?: return@forEachIndexed
                    if (vid.isBlank() || !seen.add(vid)) return@forEachIndexed
                    fresh += DiscoveryCandidate(
                        track = track,
                        // Depth is absolute: a seed already sitting one hop
                        // out produces candidates two hops out, so the pool
                        // keeps a true distance from the taste seed.
                        depth = seed.depth + 1,
                        rootSeedId = seed.rootSeedId,
                        parentVideoId = seed.videoId,
                        rank = index
                    )
                }
                if (fresh.isEmpty()) continue
                byRoot.getOrPut(seed.rootSeedId) { mutableListOf() }.addAll(fresh)

                // The second hop: queue from a song that came out of a queue.
                // Only a couple per branch — otherwise the graph collapses
                // into one neighbourhood and everything sounds the same.
                fresh.take(2).forEach { c ->
                    nextFrontier += DiscoverySeed(
                        videoId = c.track.videoId!!,
                        title = c.track.title,
                        artist = c.track.artists.joinToString(),
                        affinity = seed.affinity * 0.7,
                        depth = c.depth,
                        rootSeedId = seed.rootSeedId
                    )
                }
            }

            found += interleave(
                groups = byRoot.values.map { it.toList() },
                maxPerArtist = budget.maxPerArtist,
                limit = budget.maxCandidates - found.size
            )
            frontier = nextFrontier
            wave++
        }
        return found.take(budget.maxCandidates)
    }

    /**
     * Mix the branches: round-robin over roots so one neighbourhood can't
     * take the whole shelf, with a cap on how many tracks one artist may
     * contribute. Within a branch the provider's own order is kept — early
     * queue positions are the most confident matches.
     */
    fun interleave(
        groups: List<List<DiscoveryCandidate>>,
        maxPerArtist: Int,
        limit: Int
    ): List<DiscoveryCandidate> {
        if (groups.isEmpty() || limit <= 0) return emptyList()
        val cursors = IntArray(groups.size)
        val artistHits = HashMap<String, Int>()
        val out = ArrayList<DiscoveryCandidate>()

        while (out.size < limit) {
            var progressed = false
            for (g in groups.indices) {
                val group = groups[g]
                // Skip over tracks whose artist is already over the cap.
                while (cursors[g] < group.size) {
                    val candidate = group[cursors[g]]
                    cursors[g]++
                    val key = artistKey(candidate.track)
                    val hits = artistHits.getOrDefault(key, 0)
                    if (hits >= maxPerArtist) continue
                    artistHits[key] = hits + 1
                    out += candidate
                    progressed = true
                    break
                }
                if (out.size >= limit) break
            }
            if (!progressed) break
        }
        return out
    }

    /** Artist identity for the cap: the credited name, normalised. */
    fun artistKey(track: Track): String =
        track.artists.firstOrNull { it.isNotBlank() }?.trim()?.lowercase()
            ?: track.title.trim().lowercase()
}
