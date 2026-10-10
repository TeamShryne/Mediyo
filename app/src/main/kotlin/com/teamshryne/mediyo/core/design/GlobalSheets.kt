package com.teamshryne.mediyo.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.teamshryne.mediyo.data.mediyo.FfiSearchResult
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.comments.CommentsBottomSheet
import com.teamshryne.mediyo.feature.playlist.AddToPlaylistSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every bottom sheet in the app renders here, at the root overlay layer —
 * full window, so the dim covers status- and navigation-bar regions too.
 *
 * Background: `ModalBottomSheet` draws its scrim inline (it is not a
 * separate window), so a sheet composed inside a padded `Scaffold` content
 * box only dims that box. Screens under tab-bar navigation sit inside such
 * a box, which is why their sheets used to leave the system-bar strips
 * undimmed. Hosting all sheets in `AppShell`'s root `Box` fixes it for
 * every current and future sheet at once.
 *
 * Screens never hold sheet state anymore: overflow clicks call
 * `sheets.showTrackMenu(...)` etc. via [SheetHostVm], and [GlobalSheets]
 * renders whatever is open. Sheets that already live at root (full
 * player, queue) keep their local state — they were never broken.
 */

/** All of [TrackMenuSheet]'s params minus `show`/`onDismiss` (host-owned). */
data class TrackMenuRequest(
    val track: Track,
    val isLiked: Boolean = false,
    val onLike: () -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    val onAddToQueue: () -> Unit = {},
    val onGoToAlbum: (() -> Unit)? = null,
    val onShowArtist: ((name: String, id: String?) -> Unit)? = null,
    val onOpenChannel: (() -> Unit)? = null,
    val onComments: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onRefetchLyrics: (() -> Unit)? = null,
    val onLyricsSettings: (() -> Unit)? = null,
    val onPlaybackSettings: (() -> Unit)? = null,
    val playbackLabel: String? = null
)

/** All of [MediaMenuSheet]'s params minus `show`/`onDismiss` (host-owned). */
data class MediaMenuRequest(
    val item: FfiSearchResult,
    val nav: androidx.navigation.NavController?,
    val onPlayTracks: (tracks: List<Track>, index: Int, origin: PlayOrigin) -> Unit,
    val onEnqueueTracks: (List<Track>) -> Unit,
    val onAddToPlaylist: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onComments: ((videoId: String) -> Unit)? = null
)

@Singleton
class SheetManager @Inject constructor() {
    private val _trackMenu = MutableStateFlow<TrackMenuRequest?>(null)
    val trackMenu: StateFlow<TrackMenuRequest?> = _trackMenu.asStateFlow()

    private val _addToPlaylist = MutableStateFlow<Track?>(null)
    val addToPlaylist: StateFlow<Track?> = _addToPlaylist.asStateFlow()

    private val _mediaMenu = MutableStateFlow<MediaMenuRequest?>(null)
    val mediaMenu: StateFlow<MediaMenuRequest?> = _mediaMenu.asStateFlow()

    private val _comments = MutableStateFlow<String?>(null)
    val comments: StateFlow<String?> = _comments.asStateFlow()

    fun showTrackMenu(request: TrackMenuRequest) { _trackMenu.value = request }
    fun dismissTrackMenu() { _trackMenu.value = null }

    /** Chain from a menu: closes any menu, opens the playlist picker. */
    fun openAddToPlaylist(track: Track) {
        _trackMenu.value = null
        _mediaMenu.value = null
        _addToPlaylist.value = track
    }
    fun dismissAddToPlaylist() { _addToPlaylist.value = null }

    fun showMediaMenu(request: MediaMenuRequest) { _mediaMenu.value = request }
    fun dismissMediaMenu() { _mediaMenu.value = null }

    fun showComments(videoId: String) { _comments.value = videoId }
    fun dismissComments() { _comments.value = null }
}

@HiltViewModel
class SheetHostVm @Inject constructor(
    val sheets: SheetManager
) : ViewModel() {
    fun showTrackMenu(request: TrackMenuRequest) = sheets.showTrackMenu(request)
    fun openAddToPlaylist(track: Track) = sheets.openAddToPlaylist(track)
    fun showMediaMenu(request: MediaMenuRequest) = sheets.showMediaMenu(request)
    fun showComments(videoId: String) = sheets.showComments(videoId)
}

/** Render in `AppShell`'s root overlay `Box`, after the queue overlay. */
@Composable
fun GlobalSheets(host: SheetHostVm = hiltViewModel()) {
    val m = host.sheets
    val trackReq by m.trackMenu.collectAsState()
    val addTrack by m.addToPlaylist.collectAsState()
    val mediaReq by m.mediaMenu.collectAsState()
    val commentsVid by m.comments.collectAsState()

    trackReq?.let { r ->
        TrackMenuSheet(
            track = r.track, show = true, onDismiss = m::dismissTrackMenu,
            isLiked = r.isLiked,
            onLike = r.onLike,
            onAddToPlaylist = r.onAddToPlaylist,
            onPlayNext = r.onPlayNext,
            onAddToQueue = r.onAddToQueue,
            onGoToAlbum = r.onGoToAlbum,
            onShowArtist = r.onShowArtist,
            onOpenChannel = r.onOpenChannel,
            onComments = r.onComments,
            onRemove = r.onRemove,
            onShare = r.onShare,
            onRefetchLyrics = r.onRefetchLyrics,
            onLyricsSettings = r.onLyricsSettings,
            onPlaybackSettings = r.onPlaybackSettings,
            playbackLabel = r.playbackLabel
        )
    }
    addTrack?.let { t ->
        AddToPlaylistSheet(track = t, onDismiss = m::dismissAddToPlaylist)
    }
    mediaReq?.let { r ->
        MediaMenuSheet(
            item = r.item, show = true, onDismiss = m::dismissMediaMenu, nav = r.nav,
            onPlayTracks = r.onPlayTracks,
            onEnqueueTracks = r.onEnqueueTracks,
            onAddToPlaylist = r.onAddToPlaylist,
            onPlayNext = r.onPlayNext,
            onAddToQueue = r.onAddToQueue,
            onComments = r.onComments
        )
    }
    commentsVid?.let { vid ->
        CommentsBottomSheet(videoId = vid, onDismiss = m::dismissComments)
    }
}
