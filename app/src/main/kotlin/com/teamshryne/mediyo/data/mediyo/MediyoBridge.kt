package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mediyo.AlbumPage
import mediyo.ArtistCard
import mediyo.ArtistPage
import mediyo.ArtistSectionPage
import mediyo.ChannelCard
import mediyo.ChannelPage
import mediyo.ChannelSectionPage
import mediyo.CommentsPage
import mediyo.PlaylistPage
import mediyo.PlaylistTrack
import mediyo.PodcastEpisode
import mediyo.PodcastPage
import mediyo.QueueItem
import mediyo.QueuePage
import mediyo.SearchPage
import mediyo.SearchResult
import mediyo.Session
import javax.inject.Inject
import javax.inject.Singleton

/** Continuation page for a podcast show; [reloaded] means replace, not append. */
data class FfiPodcastNext(
    val items: List<FfiSearchResult>,
    val continuation: String?,
    val reloaded: Boolean,
    val sorts: List<FfiPodcastOption> = emptyList(),
    val filters: List<FfiPodcastOption> = emptyList()
)

private fun String.emptyToNull(): String? = ifEmpty { null }

private fun thumbsOf(count: Long, url: (Long) -> String?, width: (Long) -> Long, height: (Long) -> Long): List<FfiThumbnail> {
    if (count <= 0) return emptyList()
    return (0 until count).mapNotNull { i ->
        val u = url(i) ?: return@mapNotNull null
        if (u.isEmpty()) return@mapNotNull null
        FfiThumbnail(u, width(i).toUInt(), height(i).toUInt())
    }
}

/** Go Category int → the display string the UI matches on (`isArtist()`, ...). */
private fun categoryOf(c: Long): String = when (c.toInt()) {
    1 -> "Song"
    2 -> "Video"
    3 -> "Album"
    4 -> "Artist"
    5, 6, 7, 11 -> "Playlist"
    8 -> "Podcast"
    9 -> "Episode"
    10 -> "Profile"
    else -> "Unknown"
}

/** Artist/discography card kinds that address an album-style page. */
private fun cardCategoryOf(kind: String): String = when {
    kind.equals("Single", true) || kind.equals("EP", true) -> "Album"
    kind.isBlank() -> "Unknown"
    else -> kind
}

private fun SearchResult.toModel(top: Boolean = false): FfiSearchResult {
    val artists = (0 until artistCount()).map { artistName(it) }
    val artistIds = (0 until artistCount()).map { artistID(it) }
    return FfiSearchResult(
        title = title(),
        videoId = videoID().emptyToNull(),
        browseId = browseID().emptyToNull(),
        browseParams = browseParams().emptyToNull(),
        playlistId = playlistID().emptyToNull(),
        category = categoryOf(category()),
        year = year().emptyToNull(),
        duration = duration().emptyToNull(),
        explicit = explicit(),
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight),
        artists = artists,
        artistIds = artistIds,
        album = albumName().emptyToNull(),
        albumId = (albumID().ifEmpty { albumIDFromMenu() }).emptyToNull(),
        info = subtitle().emptyToNull(),
        isTopResult = top,
        channelName = channelName().emptyToNull(),
        channelId = channelID().emptyToNull(),
        pageType = browsePageType()
    ).also { close() }
}

private fun PlaylistTrack.toModel(): FfiSearchResult = result().toModel()

private fun ArtistCard.toModel(): FfiSearchResult {
    val kind = cardCategoryOf(kind())
    return FfiSearchResult(
        title = title(),
        videoId = videoID().emptyToNull(),
        browseId = browseID().emptyToNull(),
        browseParams = params().emptyToNull(),
        playlistId = playlistID().emptyToNull(),
        category = kind,
        year = year().emptyToNull(),
        duration = null,
        explicit = explicit(),
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight),
        artists = listOfNotNull(artistName().emptyToNull()),
        artistIds = listOf(artistID()),
        album = null,
        albumId = null,
        info = subtitle().emptyToNull(),
        isTopResult = false,
        channelName = null,
        channelId = null,
        pageType = pageType()
    ).also { close() }
}

private fun ChannelCard.toModel(): FfiSearchResult {
    val v = videoID().emptyToNull()
    val b = browseID().emptyToNull() ?: playlistID().emptyToNull()
    return FfiSearchResult(
        title = title(),
        videoId = v,
        browseId = if (v != null) null else b,
        browseParams = params().emptyToNull(),
        playlistId = playlistID().emptyToNull(),
        category = kind().ifBlank { if (v != null) "Video" else "Playlist" },
        year = null,
        duration = null,
        explicit = false,
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight),
        artists = emptyList(),
        artistIds = emptyList(),
        album = null,
        albumId = null,
        info = subtitle().emptyToNull(),
        isTopResult = false,
        channelName = null,
        channelId = null,
        pageType = pageType()
    ).also { close() }
}

private fun PodcastEpisode.toModel(showTitle: String, author: String): FfiSearchResult {
    return FfiSearchResult(
        title = title(),
        videoId = videoID().emptyToNull(),
        browseId = null,
        browseParams = null,
        playlistId = null,
        category = "Episode",
        year = null,
        duration = duration().emptyToNull(),
        explicit = false,
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight),
        artists = listOfNotNull(author.emptyToNull()),
        artistIds = emptyList(),
        album = showTitle.emptyToNull(),
        albumId = null,
        info = date().emptyToNull(),
        isTopResult = false,
        channelName = null,
        channelId = null,
        pageType = episodePageType(),
        detailId = detailID(),
        played = played(),
        progress = progress().toInt()
    ).also { close() }
}

private fun QueueItem.toModel(): FfiQueueItem {
    val artists = (0 until artistCount()).map { artistName(it) }
    val artistIds = (0 until artistCount()).map { artistID(it) }
    return FfiQueueItem(
        title = title(),
        videoId = videoID(),
        artists = artists,
        artistIds = artistIds,
        album = albumName().emptyToNull(),
        duration = duration().emptyToNull(),
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight)
    ).also { close() }
}

private fun QueuePage.toModel(): FfiQueue {
    val items = (0 until itemCount()).mapNotNull { item(it)?.toModel() }
    return FfiQueue(
        playlistId = playlistID(),
        isInfinite = isInfinite(),
        items = items,
        continuation = continuation().emptyToNull()
    ).also { close() }
}

private fun CommentsPage.toModel(): FfiCommentsPage {
    val comments = (0 until commentCount()).mapNotNull { i ->
        val c = comment(i) ?: return@mapNotNull null
        FfiComment(
            content = c.content(),
            author = c.authorName(),
            publishedTime = c.publishedTime(),
            likeCount = c.likeCount().emptyToNull(),
            replyCount = c.replyCount().emptyToNull(),
            repliesContinuation = c.repliesToken().emptyToNull()
        ).also { c.close() }
    }
    val sorts = (0 until sortCount()).map {
        FfiCommentSortFilter(sortTitle(it), sortSelected(it), sortToken(it))
    }
    return FfiCommentsPage(
        count = count().emptyToNull(),
        comments = comments,
        continuation = continuation().emptyToNull(),
        sortFilters = sorts
    ).also { close() }
}

private fun SearchPage.toModel(): FfiSearchResponse {
    val out = ArrayList<FfiSearchResult>(resultCount().toInt() + 8)
    topResult()?.let { out.add(it.toModel(top = true)) }
    (0 until topResultCount()).mapNotNullTo(out) { topResultItem(it)?.toModel(top = true) }
    (0 until resultCount()).mapNotNullTo(out) { results(it)?.toModel() }
    val filters = (0 until filterCount()).mapNotNull {
        val f = filters(it) ?: return@mapNotNull null
        FfiSearchFilter(f.label(), f.query(), f.params().emptyToNull()).also { f.close() }
    }
    return FfiSearchResponse(out, filters, continuation().emptyToNull()).also { close() }
}

private fun AlbumPage.toModel(): FfiAlbumPage {
    val artists = (0 until artistCount()).map { artistName(it) }
    val artistIds = (0 until artistCount()).map { artistID(it) }
    val headerThumbs = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight)
    val tracks = (0 until trackCount()).mapNotNull { i ->
        val t = track(i) ?: return@mapNotNull null
        try {
            var r = t.result().toModel()
            // Album rows often omit what the header already states: credit the
            // album artists, name the album, and reuse its artwork so queue and
            // player rows never come out blank.
            if (r.artists.isEmpty() && artists.isNotEmpty()) {
                r = r.copy(artists = artists, artistIds = artistIds)
            }
            if (r.album.isNullOrBlank()) {
                r = r.copy(album = title(), albumId = id().emptyToNull())
            }
            if (r.thumbnails.isEmpty() && headerThumbs.isNotEmpty()) {
                r = r.copy(thumbnails = headerThumbs)
            }
            r
        } finally {
            t.close()
        }
    }
    val shelves = (0 until relatedCount()).map { s ->
        val items = (0 until relatedItemCount(s)).mapNotNull { i ->
            FfiSearchResult(
                title = relatedItemTitle(s, i),
                videoId = null,
                browseId = relatedItemBrowseID(s, i).emptyToNull(),
                browseParams = relatedItemParams(s, i).emptyToNull(),
                playlistId = null,
                category = cardCategoryOf(relatedItemKind(s, i)),
                year = relatedItemYear(s, i).emptyToNull(),
                duration = null,
                explicit = false,
                thumbnails = thumbsOf(
                    relatedItemThumbnailCount(s, i).toLong(),
                    { t -> relatedItemThumbnailURL(s, i, t) },
                    { t -> relatedItemThumbnailWidth(s, i, t) },
                    { t -> relatedItemThumbnailHeight(s, i, t) }
                ),
                artists = listOfNotNull(relatedItemArtistName(s, i).emptyToNull()),
                artistIds = listOf(relatedItemArtistID(s, i)),
                album = null,
                albumId = null,
                info = null,
                isTopResult = false,
                channelName = null,
                channelId = null,
                pageType = relatedItemPageType(s, i)
            )
        }
        FfiCarousel(relatedTitle(s), items, null, null)
    }
    return FfiAlbumPage(
        title = title(),
        artist = artists.firstOrNull(),
        artistId = artistIds.firstOrNull()?.emptyToNull(),
        artistAvatar = artistAvatar().emptyToNull(),
        kind = kind().emptyToNull(),
        year = year().emptyToNull(),
        stats = listOfNotNull(totalDuration().emptyToNull(), trackCountText().emptyToNull())
            .joinToString("  •  ").emptyToNull(),
        description = description().emptyToNull(),
        thumbnails = headerThumbs,
        tracks = tracks,
        carousels = shelves,
        continuation = continuation().emptyToNull(),
        radioPlaylistId = radioPlaylistID().emptyToNull()
    ).also { close() }
}

private fun ArtistPage.toModel(): FfiArtistPage {
    val topSongs = (0 until topSongCount()).mapNotNull { topSong(it)?.toModel() }
    val shelves = (0 until sectionCount()).map { s ->
        val items = (0 until sectionItemCount(s)).mapNotNull { sectionItem(s, it)?.toModel() }
        val more = if (sectionHasMore(s)) {
            FfiViewAll(sectionMoreBrowseID(s), sectionMoreParams(s).emptyToNull())
        } else null
        FfiCarousel(sectionTitle(s), items, more, null)
    }
    val viewAll = if (hasTopSongsMore()) {
        FfiViewAll(topSongsMoreBrowseID(), topSongsMoreParams().emptyToNull())
    } else null
    return FfiArtistPage(
        name = title(),
        subscriberCount = subscriberCount().emptyToNull(),
        monthlyAudience = monthlyAudience().emptyToNull(),
        description = description().emptyToNull(),
        thumbnails = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight),
        topSongs = topSongs,
        topSongsViewAll = viewAll,
        carousels = shelves,
        continuation = null
    ).also { close() }
}

private fun ArtistSectionPage.toList(): FfiListPage {
    val items = ArrayList<FfiSearchResult>(trackCount().toInt() + cardCount().toInt())
    (0 until trackCount()).mapNotNullTo(items) { track(it)?.toModel() }
    (0 until cardCount()).mapNotNullTo(items) { card(it)?.toModel() }
    return FfiListPage(items, continuation().emptyToNull(), reloaded()).also { close() }
}

private fun ChannelPage.toModel(): FfiChannelPage {
    val shelves = (0 until sectionCount()).map { s ->
        val items = (0 until sectionItemCount(s)).mapNotNull { sectionItem(s, it)?.toModel() }
        val more = if (sectionHasMore(s)) {
            FfiViewAll(sectionMoreBrowseID(s), sectionMoreParams(s).emptyToNull())
        } else null
        FfiCarousel(sectionTitle(s), items, more, null)
    }
    return FfiChannelPage(
        title = title(),
        subscriberCount = subscriberCount().emptyToNull(),
        avatarUrl = if (avatarCount() > 0) avatarURL(0).emptyToNull() else null,
        bannerUrl = if (bannerCount() > 0) bannerURL(0).emptyToNull() else null,
        sections = shelves,
        emptyMessage = emptyMessage().emptyToNull() ?: ""
    ).also { close() }
}

private fun ChannelSectionPage.toList(): FfiListPage {
    val items = (0 until itemCount()).mapNotNull { item(it)?.toModel() }
    return FfiListPage(items, continuation().emptyToNull()).also { close() }
}

private fun podcastOptions(
    sortCount: Long, sortLabel: (Long) -> String?, sortSelected: (Long) -> Boolean, sortToken: (Long) -> String?,
    filterCount: Long, filterLabel: (Long) -> String?, filterSelected: (Long) -> Boolean, filterToken: (Long) -> String?
): Pair<List<FfiPodcastOption>, List<FfiPodcastOption>> {
    val sorts = (0 until sortCount).mapNotNull { i ->
        val t = sortToken(i) ?: return@mapNotNull null
        if (t.isEmpty()) return@mapNotNull null
        FfiPodcastOption(sortLabel(i) ?: "", sortSelected(i), t)
    }
    val filters = (0 until filterCount).mapNotNull { i ->
        val t = filterToken(i) ?: return@mapNotNull null
        if (t.isEmpty()) return@mapNotNull null
        FfiPodcastOption(filterLabel(i) ?: "", filterSelected(i), t)
    }
    return sorts to filters
}

private fun PodcastPage.toModel(): FfiPodcastPage {
    val title = title()
    val author = authorName().emptyToNull() ?: ""
    val thumbs = thumbsOf(thumbnailCount(), ::thumbnailURL, ::thumbnailWidth, ::thumbnailHeight)
    val items = (0 until episodeCount()).mapNotNull { episode(it)?.toModel(title, author) }
    val (sorts, filters) = podcastOptions(
        sortCount(), ::sortLabel, ::sortSelected, ::sortToken,
        filterCount(), ::filterLabel, ::filterSelected, ::filterToken
    )
    return FfiPodcastPage(
        title = title,
        author = authorName().emptyToNull(),
        artworkUrl = thumbs.maxByOrNull { it.width }?.url ?: thumbs.firstOrNull()?.url,
        description = description().emptyToNull(),
        items = items,
        continuation = continuation().emptyToNull(),
        sorts = sorts,
        filters = filters
    ).also { close() }
}

/**
 * App gateway to mediyo-core (Go). Owns one process-lifetime [Session]:
 * continuations are bound to the identity that minted them, so the session
 * must not be recreated mid-scroll. All calls run on Dispatchers.IO because
 * gomobile bindings block.
 *
 * Auth is anonymous visitor identity only: the persisted visitorData is
 * seeded on start (no I/O); a fresh bootstrap runs only when nothing is
 * stored. Media URLs still come from NewPipe, never from this core.
 */
@Singleton
class MediyoBridge @Inject constructor(private val auth: AuthRepository) {
    private val lock = Mutex()
    private var session: Session? = null

    private suspend fun session(): Session = lock.withLock {
        session?.let { return it }
        val s = Session()
        val saved = auth.flow.first()
        if (saved.visitorData.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching { s.seedVisitorData(saved.visitorData, "") }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching { s.bootstrap() }
            }
            val vd = runCatching { s.visitorData() }.getOrDefault("")
            if (vd.isNotEmpty()) auth.saveAnonVisitor(vd)
        }
        session = s
        s
    }

    suspend fun currentVisitorData(): String {
        val a = auth.flow.first()
        if (a.visitorData.isNotEmpty()) return a.visitorData
        return try {
            val vd = withContext(Dispatchers.IO) { session().visitorData() }
            if (vd.isNotEmpty()) vd else a.visitorData
        } catch (_: Throwable) {
            a.visitorData
        }
    }

    suspend fun rotateVisitorData(): String = withContext(Dispatchers.IO) {
        val s = session()
        runCatching { s.rotateVisitorData() }
        val vd = runCatching { s.visitorData() }.getOrDefault("")
        if (vd.isNotEmpty()) auth.saveAnonVisitor(vd)
        android.util.Log.d("MediyoBridge", "rotated visitor ${vd.take(20)}")
        vd
    }

    // ── search (+ suggestions) ───────────────────────────────────────────
    suspend fun search(query: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        session().search(query).toModel()
    }

    suspend fun searchFiltered(query: String, params: String): FfiSearchResponse =
        withContext(Dispatchers.IO) {
            session().searchFiltered(query, params).toModel()
        }

    suspend fun searchNext(token: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        session().searchNext(token).toModel()
    }

    suspend fun suggest(input: String): FfiSuggestResponse = withContext(Dispatchers.IO) {
        val q = input.trim()
        if (q.isEmpty()) return@withContext FfiSuggestResponse(emptyList(), emptyList())
        val page = session().suggest(q)
        try {
            val texts = (0 until page.textCount()).mapNotNull {
                val t = page.text(it) ?: return@mapNotNull null
                FfiSuggestText(t.suggestionText(), t.query()).also { t.close() }
            }
            val entities = (0 until page.entityCount()).mapNotNull {
                page.entity(it)?.toModel()
            }
            FfiSuggestResponse(texts, entities)
        } finally {
            page.close()
        }
    }

    // ── album / artist / playlist / podcast / channel ─────────────────────
    suspend fun album(browseId: String): FfiAlbumPage = withContext(Dispatchers.IO) {
        session().album(browseId).toModel()
    }

    suspend fun albumNext(token: String): FfiListPage = withContext(Dispatchers.IO) {
        val p = session().albumNext(token)
        try {
            val items = (0 until p.trackCount()).mapNotNull { p.track(it)?.toModel() }
            FfiListPage(items, p.continuation().emptyToNull())
        } finally {
            p.close()
        }
    }

    suspend fun artist(browseId: String): FfiArtistPage = withContext(Dispatchers.IO) {
        session().artist(browseId).toModel()
    }

    suspend fun artistSection(browseId: String, params: String?, title: String?): FfiListPage =
        withContext(Dispatchers.IO) {
            session().artistSection(browseId, params ?: "", title ?: "").toList()
        }

    suspend fun artistSectionNext(token: String): FfiListPage = withContext(Dispatchers.IO) {
        session().artistSectionNext(token).toList()
    }

    suspend fun playlist(browseId: String): FfiPlaylistPage = withContext(Dispatchers.IO) {
        val p = session().playlist(browseId)
        try {
            val tracks = (0 until p.trackCount()).mapNotNull { p.track(it)?.toModel() }
            FfiPlaylistPage(
                title = p.title(),
                trackCount = p.trackCountText().emptyToNull(),
                thumbnails = thumbsOf(p.thumbnailCount(), p::thumbnailURL, p::thumbnailWidth, p::thumbnailHeight),
                tracks = tracks,
                continuation = p.continuation().emptyToNull(),
                radioPlaylistId = p.radioPlaylistID().emptyToNull(),
                owner = p.owner().emptyToNull(),
                ownerAvatar = p.ownerAvatar().emptyToNull(),
                description = p.description().emptyToNull(),
                totalDuration = p.totalDuration().emptyToNull()
            )
        } finally {
            p.close()
        }
    }

    suspend fun playlistNext(token: String): FfiListPage = withContext(Dispatchers.IO) {
        val p = session().playlistNext(token)
        try {
            val items = (0 until p.trackCount()).mapNotNull { p.track(it)?.toModel() }
            FfiListPage(items, p.continuation().emptyToNull())
        } finally {
            p.close()
        }
    }

    suspend fun podcast(browseId: String): FfiPodcastPage = withContext(Dispatchers.IO) {
        session().podcastShow(browseId).toModel()
    }

    suspend fun podcastNext(token: String): FfiPodcastNext = withContext(Dispatchers.IO) {
        val p = session().podcastNext(token)
        try {
            val title = p.title()
            val author = p.authorName().ifEmpty { "" }
            val items = (0 until p.episodeCount()).mapNotNull { p.episode(it)?.toModel(title, author) }
            val (sorts, filters) = podcastOptions(
                p.sortCount(), p::sortLabel, p::sortSelected, p::sortToken,
                p.filterCount(), p::filterLabel, p::filterSelected, p::filterToken
            )
            FfiPodcastNext(items, p.continuation().emptyToNull(), p.reloaded(), sorts, filters)
        } finally {
            p.close()
        }
    }

    suspend fun episode(episodeId: String): FfiEpisodePage = withContext(Dispatchers.IO) {
        val p = session().episode(episodeId)
        try {
            val thumbs = thumbsOf(p.thumbnailCount(), p::thumbnailURL, p::thumbnailWidth, p::thumbnailHeight)
            val chapters = (0 until p.chapterCount()).mapNotNull { i ->
                val t = p.chapterText(i)
                if (t.isEmpty()) return@mapNotNull null
                FfiChapter(t, p.chapterStartSeconds(i))
            }
            FfiEpisodePage(
                id = p.id(),
                videoId = p.videoID().emptyToNull(),
                title = p.title(),
                showName = p.showName().emptyToNull(),
                showId = p.showID().emptyToNull(),
                date = p.date().emptyToNull(),
                stats = p.stats().emptyToNull(),
                description = p.description().emptyToNull(),
                duration = p.duration().emptyToNull(),
                artworkUrl = thumbs.maxByOrNull { it.width }?.url ?: thumbs.firstOrNull()?.url,
                played = p.played(),
                progress = p.progress().toInt(),
                chapters = chapters
            )
        } finally {
            p.close()
        }
    }

    suspend fun channel(channelId: String): FfiChannelPage = withContext(Dispatchers.IO) {
        session().channel(channelId).toModel()
    }

    suspend fun channelSection(browseId: String, params: String?, title: String?): FfiListPage =
        withContext(Dispatchers.IO) {
            session().channelSection(browseId, params ?: "", title ?: "").toList()
        }

    suspend fun channelSectionNext(token: String): FfiListPage = withContext(Dispatchers.IO) {
        session().channelSectionNext(token).toList()
    }

    // ── watch / queue ────────────────────────────────────────────────────
    suspend fun getQueue(videoId: String, playlistId: String?): FfiQueue =
        withContext(Dispatchers.IO) {
            session().queue(videoId, playlistId ?: "").toModel()
        }

    suspend fun extendQueue(token: String): FfiQueue = withContext(Dispatchers.IO) {
        session().queueExtend(token).toModel()
    }

    // ── comments ─────────────────────────────────────────────────────────
    suspend fun commentsToken(videoId: String): String? = withContext(Dispatchers.IO) {
        session().commentsToken(videoId).emptyToNull()
    }

    suspend fun commentsPage(token: String): FfiCommentsPage = withContext(Dispatchers.IO) {
        session().commentsPage(token).toModel()
    }

    suspend fun commentsNextPage(token: String): FfiCommentsPage = withContext(Dispatchers.IO) {
        session().commentsNext(token).toModel()
    }

    suspend fun commentsReload(token: String): FfiCommentsPage = withContext(Dispatchers.IO) {
        session().commentsReload(token).toModel()
    }

    suspend fun commentsReplies(token: String): FfiCommentsPage = withContext(Dispatchers.IO) {
        session().commentsReplies(token).toModel()
    }
}
