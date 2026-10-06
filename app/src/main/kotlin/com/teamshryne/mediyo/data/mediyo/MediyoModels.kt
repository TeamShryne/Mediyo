package com.teamshryne.mediyo.data.mediyo

/**
 * Plain Kotlin models returned by [MediyoBridge].
 *
 * Names keep the historical `Ffi` prefix on purpose: the previous UniFFI core
 * exposed the same type/field shapes, so keeping them makes the core swap an
 * import-only change in most UI files instead of a rename across ~25 files.
 * These are ordinary data classes now — no native handles, no close discipline
 * (the bridge converts Go handles and closes them before returning).
 */
data class FfiThumbnail(
    val url: String,
    val width: UInt,
    val height: UInt
)

data class FfiSearchResult(
    val title: String,
    val videoId: String?,
    val browseId: String?,
    val browseParams: String?,
    val playlistId: String?,
    val category: String,
    val year: String?,
    val duration: String?,
    val explicit: Boolean,
    val thumbnails: List<FfiThumbnail>,
    val artists: List<String>,
    /** Parallel to [artists]; "" when the browseId is unknown. */
    val artistIds: List<String>,
    val album: String?,
    val albumId: String?,
    /** Loose trailing info (subscriber counts, view counts, ...). */
    val info: String?,
    val isTopResult: Boolean,
    /** Uploader channel behind a video; null for songs. */
    val channelName: String?,
    /** Raw UC browseId for "Open channel" (a channel page, never an artist page). */
    val channelId: String?,
    /** Raw MUSIC_PAGE_TYPE_* of the destination; used for open() fallback routing. */
    val pageType: String = "",
    /** Podcast episode detail id (MPED…); used for the episode screen. */
    val detailId: String = "",
    /** Episode-only: fully played marker. */
    val played: Boolean = false,
    /** Episode-only: listen progress percent. */
    val progress: Int = 0
)

data class FfiSearchFilter(
    val label: String,
    val query: String,
    val params: String?
)

data class FfiSearchResponse(
    val results: List<FfiSearchResult>,
    val filters: List<FfiSearchFilter>,
    val continuation: String?
)

data class FfiViewAll(
    val browseId: String,
    val params: String?
)

data class FfiCarousel(
    val title: String,
    val items: List<FfiSearchResult>,
    val viewAll: FfiViewAll?,
    val continuation: String?
)

data class FfiAlbumPage(
    val title: String,
    val artist: String?,
    val artistId: String?,
    val artistAvatar: String?,
    val kind: String?,
    val year: String?,
    val stats: String?,
    val description: String?,
    val thumbnails: List<FfiThumbnail>,
    val tracks: List<FfiSearchResult>,
    val carousels: List<FfiCarousel>,
    val continuation: String?,
    val radioPlaylistId: String? = null
)

data class FfiArtistPage(
    val name: String,
    val subscriberCount: String?,
    val monthlyAudience: String?,
    val description: String?,
    val thumbnails: List<FfiThumbnail>,
    val topSongs: List<FfiSearchResult>,
    val topSongsViewAll: FfiViewAll?,
    val carousels: List<FfiCarousel>,
    val continuation: String?
)

data class FfiPlaylistPage(
    val title: String,
    val trackCount: String?,
    val thumbnails: List<FfiThumbnail>,
    val tracks: List<FfiSearchResult>,
    val continuation: String?,
    val radioPlaylistId: String? = null,
    val owner: String? = null,
    val ownerAvatar: String? = null,
    val description: String? = null,
    val totalDuration: String? = null
)

data class FfiListPage(
    val items: List<FfiSearchResult>,
    val continuation: String?,
    /** True when the page replaces (sort/filter reload), not appends. */
    val reloaded: Boolean = false
)

data class FfiPodcastOption(
    val label: String,
    val selected: Boolean,
    val token: String
)

data class FfiPodcastPage(
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val description: String?,
    val items: List<FfiSearchResult>,
    val continuation: String?,
    val sorts: List<FfiPodcastOption> = emptyList(),
    val filters: List<FfiPodcastOption> = emptyList()
)

data class FfiQueueItem(
    val title: String,
    val videoId: String,
    val artists: List<String>,
    /** Parallel to [artists]; "" when the browseId is unknown. */
    val artistIds: List<String>,
    val album: String?,
    val duration: String?,
    val thumbnails: List<FfiThumbnail>
)

data class FfiQueue(
    val playlistId: String,
    val isInfinite: Boolean,
    val items: List<FfiQueueItem>,
    val continuation: String?
)

data class FfiComment(
    val content: String,
    val author: String,
    val publishedTime: String,
    val likeCount: String?,
    val replyCount: String?,
    val repliesContinuation: String?
)

data class FfiCommentSortFilter(
    val title: String,
    val selected: Boolean,
    val continuationToken: String
)

data class FfiCommentsPage(
    val count: String?,
    val comments: List<FfiComment>,
    val continuation: String?,
    val sortFilters: List<FfiCommentSortFilter>
)

data class FfiChannelPage(
    val title: String,
    val subscriberCount: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val sections: List<FfiCarousel>,
    val emptyMessage: String?
)

data class FfiChapter(
    val text: String,
    val startSeconds: Long
)

data class FfiEpisodePage(
    val id: String,
    val videoId: String?,
    val title: String,
    val showName: String?,
    val showId: String?,
    val date: String?,
    val stats: String?,
    val description: String?,
    val duration: String?,
    val artworkUrl: String?,
    val played: Boolean,
    val progress: Int,
    val chapters: List<FfiChapter>
)

data class FfiSuggestText(
    val text: String,
    val query: String
)

data class FfiSuggestResponse(
    val texts: List<FfiSuggestText>,
    val entities: List<FfiSearchResult>
)
