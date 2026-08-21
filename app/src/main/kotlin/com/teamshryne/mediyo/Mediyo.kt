package com.teamshryne.mediyo

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

// UniFFI-generated bindings for mediyo-ffi (full coverage)
// Build: cargo build -p mediyo-ffi --release
// Generate: cargo run --bin uniffi-bindgen -- generate --library target/release/libmediyo_ffi.so --language kotlin --out-dir bindings/kotlin

data class FfiThumbnail(val url: String, val width: UInt, val height: UInt)
data class FfiSearchResult(val title: String, val videoId: String?, val browseId: String?, val playlistId: String?, val category: String, val year: String?, val duration: String?, val explicit: Boolean)
data class FfiSearchFilter(val label: String, val query: String, val params: String?)
data class FfiSearchResponse(val results: List<FfiSearchResult>, val filters: List<FfiSearchFilter>, val continuation: String?)
data class FfiCarousel(val title: String, val items: List<FfiSearchResult>, val continuation: String?)
data class FfiViewAll(val browseId: String, val params: String?)
data class FfiWatchEndpoint(val videoId: String?, val playlistId: String, val params: String?)
data class FfiHomePage(val carousels: List<FfiCarousel>, val continuation: String?)
data class FfiExplorePage(val navButtons: List<FfiNavButton>, val carousels: List<FfiCarousel>)
data class FfiNavButton(val label: String, val browseId: String, val params: String?)
data class FfiArtistPage(val name: String, val subscriberCount: String?, val monthlyAudience: String?, val description: String?, val topSongs: List<FfiSearchResult>, val carousels: List<FfiCarousel>, val continuation: String?, val playButton: FfiWatchEndpoint?, val radioButton: FfiWatchEndpoint?, val shareEntity: String?)
data class FfiAlbumPage(val title: String, val artist: String?, val year: String?, val tracks: List<FfiSearchResult>, val carousels: List<FfiCarousel>, val continuation: String?)
data class FfiPlaylistPage(val title: String, val trackCount: String?, val tracks: List<FfiSearchResult>, val continuation: String?)
data class FfiListPage(val items: List<FfiSearchResult>, val continuation: String?)
data class FfiQueueItem(val title: String, val videoId: String, val artists: List<String>)
data class FfiQueue(val playlistId: String, val isInfinite: Boolean, val items: List<FfiQueueItem>, val continuation: String?)
data class FfiSong(val title: String, val videoId: String, val artists: List<String>, val album: String?, val lyricsBrowseId: String?)
data class FfiLyrics(val lines: List<String>)
data class FfiComment(val content: String, val author: String, val publishedTime: String, val likeCount: String?, val replyCount: String?, val repliesContinuation: String?)
data class FfiCommentSortFilter(val title: String, val selected: Boolean, val continuationToken: String)
data class FfiCommentsPage(val count: String?, val comments: List<FfiComment>, val continuation: String?, val sortFilters: List<FfiCommentSortFilter>)
data class FfiAccountInfo(val name: String, val handle: String?, val photoUrl: String?)
data class FfiLibraryPage(val titles: List<String>, val browseIds: List<String?>, val continuation: String?)
enum class FfiLikeStatus { Like, Dislike, Indifferent }
class MediyoException(message: String) : Exception(message)

class MediyoSession private constructor(private val ptr: Pointer) : AutoCloseable {
    companion object {
        init { System.loadLibrary("mediyo_ffi") }
        @JvmStatic fun new(): MediyoSession = MediyoSession(NativeLibrary.INSTANCE.mediyo_session_new())
        @JvmStatic fun withCookies(cookie: String, sapisid: String?): MediyoSession = MediyoSession(NativeLibrary.INSTANCE.mediyo_session_with_cookies(cookie, sapisid))
        @JvmStatic fun withAll(cookie: String, sapisid: String?, visitorData: String, pageId: String?): MediyoSession = MediyoSession(NativeLibrary.INSTANCE.mediyo_session_with_all(cookie, sapisid, visitorData, pageId))
    }
    fun fetchVisitorData(): String = NativeLibrary.INSTANCE.mediyo_session_fetch_visitor_data(ptr).getOrThrow()
    fun search(query: String): FfiSearchResponse = NativeLibrary.INSTANCE.mediyo_search(ptr, query).getOrThrow()
    fun searchWithParams(query: String, params: String): FfiSearchResponse = NativeLibrary.INSTANCE.mediyo_search_with_params(ptr, query, params).getOrThrow()
    fun searchContinuation(token: String): FfiSearchResponse = NativeLibrary.INSTANCE.mediyo_search_continuation(ptr, token).getOrThrow()
    fun browseHome(): FfiHomePage = NativeLibrary.INSTANCE.mediyo_browse_home(ptr).getOrThrow()
    fun browseHomeContinue(token: String): FfiHomePage = NativeLibrary.INSTANCE.mediyo_browse_home_continue(ptr, token).getOrThrow()
    fun browseExplore(): FfiExplorePage = NativeLibrary.INSTANCE.mediyo_browse_explore(ptr).getOrThrow()
    fun browseArtist(browseId: String): FfiArtistPage = NativeLibrary.INSTANCE.mediyo_browse_artist(ptr, browseId).getOrThrow()
    fun browseAlbum(browseId: String): FfiAlbumPage = NativeLibrary.INSTANCE.mediyo_browse_album(ptr, browseId).getOrThrow()
    fun browsePlaylist(browseId: String): FfiPlaylistPage = NativeLibrary.INSTANCE.mediyo_browse_playlist(ptr, browseId).getOrThrow()
    fun browseListPage(browseId: String, params: String?): FfiListPage = NativeLibrary.INSTANCE.mediyo_browse_list_page(ptr, browseId, params).getOrThrow()
    fun browseNextPage(token: String): FfiListPage = NativeLibrary.INSTANCE.mediyo_browse_next_page(ptr, token).getOrThrow()
    fun browsePodcast(browseId: String): FfiListPage = NativeLibrary.INSTANCE.mediyo_browse_podcast(ptr, browseId).getOrThrow()
    fun watchGetSong(videoId: String, playlistId: String?): FfiSong = NativeLibrary.INSTANCE.mediyo_watch_get_song(ptr, videoId, playlistId).getOrThrow()
    fun watchGetQueue(videoId: String, playlistId: String?): FfiQueue = NativeLibrary.INSTANCE.mediyo_watch_get_queue(ptr, videoId, playlistId).getOrThrow()
    fun watchExtendQueue(token: String): FfiQueue = NativeLibrary.INSTANCE.mediyo_watch_extend_queue(ptr, token).getOrThrow()
    fun watchGetLyrics(browseId: String): FfiLyrics = NativeLibrary.INSTANCE.mediyo_watch_get_lyrics(ptr, browseId).getOrThrow()
    fun commentsToken(videoId: String): String? = NativeLibrary.INSTANCE.mediyo_comments_token(ptr, videoId).getOrThrow()
    fun commentsPage(token: String): FfiCommentsPage = NativeLibrary.INSTANCE.mediyo_comments_page(ptr, token).getOrThrow()
    fun commentsNextPage(token: String): FfiCommentsPage = NativeLibrary.INSTANCE.mediyo_comments_next_page(ptr, token).getOrThrow()
    fun commentsReplies(token: String): FfiCommentsPage = NativeLibrary.INSTANCE.mediyo_comments_replies(ptr, token).getOrThrow()
    fun libraryLanding(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_landing(ptr).getOrThrow()
    fun libraryPlaylists(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_playlists(ptr).getOrThrow()
    fun librarySongs(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_songs(ptr).getOrThrow()
    fun libraryAlbums(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_albums(ptr).getOrThrow()
    fun libraryArtists(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_artists(ptr).getOrThrow()
    fun librarySubscriptions(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_subscriptions(ptr).getOrThrow()
    fun libraryHistory(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_history(ptr).getOrThrow()
    fun libraryPodcasts(): FfiLibraryPage = NativeLibrary.INSTANCE.mediyo_library_podcasts(ptr).getOrThrow()
    fun accountInfo(): FfiAccountInfo = NativeLibrary.INSTANCE.mediyo_account_info(ptr).getOrThrow()
    fun rateSong(videoId: String, status: FfiLikeStatus) = NativeLibrary.INSTANCE.mediyo_rate_song(ptr, videoId, status).getOrThrow()
    fun ratePlaylist(playlistId: String, status: FfiLikeStatus) = NativeLibrary.INSTANCE.mediyo_rate_playlist(ptr, playlistId, status).getOrThrow()
    fun addToPlaylist(playlistId: String, videoId: String) = NativeLibrary.INSTANCE.mediyo_add_to_playlist(ptr, playlistId, videoId).getOrThrow()
    fun addManyToPlaylist(playlistId: String, videoIds: List<String>) = NativeLibrary.INSTANCE.mediyo_add_many_to_playlist(ptr, playlistId, videoIds).getOrThrow()
    fun removeFromPlaylist(playlistId: String, setVideoId: String, videoId: String) = NativeLibrary.INSTANCE.mediyo_remove_from_playlist(ptr, playlistId, setVideoId, videoId).getOrThrow()
    fun createPlaylist(title: String, description: String): String = NativeLibrary.INSTANCE.mediyo_create_playlist(ptr, title, description).getOrThrow()
    fun deletePlaylist(playlistId: String) = NativeLibrary.INSTANCE.mediyo_delete_playlist(ptr, playlistId).getOrThrow()
    override fun close() { NativeLibrary.INSTANCE.mediyo_session_free(ptr) }
}

private interface NativeLibrary : Library {
    fun mediyo_session_new(): Pointer
    fun mediyo_session_with_cookies(cookie: String, sapisid: String?): Pointer
    fun mediyo_session_with_all(cookie: String, sapisid: String?, visitorData: String, pageId: String?): Pointer
    fun mediyo_session_fetch_visitor_data(ptr: Pointer): Result<String>
    fun mediyo_search(ptr: Pointer, query: String): Result<FfiSearchResponse>
    fun mediyo_search_with_params(ptr: Pointer, query: String, params: String): Result<FfiSearchResponse>
    fun mediyo_search_continuation(ptr: Pointer, token: String): Result<FfiSearchResponse>
    fun mediyo_browse_home(ptr: Pointer): Result<FfiHomePage>
    fun mediyo_browse_home_continue(ptr: Pointer, token: String): Result<FfiHomePage>
    fun mediyo_browse_explore(ptr: Pointer): Result<FfiExplorePage>
    fun mediyo_browse_artist(ptr: Pointer, browseId: String): Result<FfiArtistPage>
    fun mediyo_browse_album(ptr: Pointer, browseId: String): Result<FfiAlbumPage>
    fun mediyo_browse_playlist(ptr: Pointer, browseId: String): Result<FfiPlaylistPage>
    fun mediyo_browse_list_page(ptr: Pointer, browseId: String, params: String?): Result<FfiListPage>
    fun mediyo_browse_next_page(ptr: Pointer, token: String): Result<FfiListPage>
    fun mediyo_browse_podcast(ptr: Pointer, browseId: String): Result<FfiListPage>
    fun mediyo_watch_get_song(ptr: Pointer, videoId: String, playlistId: String?): Result<FfiSong>
    fun mediyo_watch_get_queue(ptr: Pointer, videoId: String, playlistId: String?): Result<FfiQueue>
    fun mediyo_watch_extend_queue(ptr: Pointer, token: String): Result<FfiQueue>
    fun mediyo_watch_get_lyrics(ptr: Pointer, browseId: String): Result<FfiLyrics>
    fun mediyo_comments_token(ptr: Pointer, videoId: String): Result<String?>
    fun mediyo_comments_page(ptr: Pointer, token: String): Result<FfiCommentsPage>
    fun mediyo_comments_next_page(ptr: Pointer, token: String): Result<FfiCommentsPage>
    fun mediyo_comments_replies(ptr: Pointer, token: String): Result<FfiCommentsPage>
    fun mediyo_library_landing(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_playlists(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_songs(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_albums(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_artists(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_subscriptions(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_history(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_library_podcasts(ptr: Pointer): Result<FfiLibraryPage>
    fun mediyo_account_info(ptr: Pointer): Result<FfiAccountInfo>
    fun mediyo_rate_song(ptr: Pointer, videoId: String, status: FfiLikeStatus): Result<Unit>
    fun mediyo_rate_playlist(ptr: Pointer, playlistId: String, status: FfiLikeStatus): Result<Unit>
    fun mediyo_add_to_playlist(ptr: Pointer, playlistId: String, videoId: String): Result<Unit>
    fun mediyo_add_many_to_playlist(ptr: Pointer, playlistId: String, videoIds: List<String>): Result<Unit>
    fun mediyo_remove_from_playlist(ptr: Pointer, playlistId: String, setVideoId: String, videoId: String): Result<Unit>
    fun mediyo_create_playlist(ptr: Pointer, title: String, description: String): Result<String>
    fun mediyo_delete_playlist(ptr: Pointer, playlistId: String): Result<Unit>
    fun mediyo_session_free(ptr: Pointer)
    companion object { val INSTANCE: NativeLibrary = Native.load("mediyo_ffi", NativeLibrary::class.java) }
    class Result<T> { fun getOrThrow(): T = throw NotImplementedError("UniFFI Result") }
}
