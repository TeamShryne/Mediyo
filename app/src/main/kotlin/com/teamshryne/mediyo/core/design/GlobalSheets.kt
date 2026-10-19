package com.teamshryne.mediyo.core.design

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * Every bottom sheet in the app renders here, at the root overlay layer.
 *
 * Background: `ModalBottomSheet` draws its scrim inline (it is not a separate
 * window), so a sheet composed inside a padded `Scaffold` content box only dims
 * that box. Screens under tab-bar navigation sit inside such a box, which is
 * why their sheets used to leave the content area undimmed. Hosting all sheets
 * in `AppShell`'s root `Box` fixes that for every current and future sheet at
 * once.
 *
 * The status-bar strip is a separate problem the root box does not solve: the
 * scrim stops at the top of the window content there. [SheetStatusBar] paints
 * the same colour into the window's status bar so the two match.
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

/**
 * Matches `ModalBottomSheet`'s default scrim, spelled out rather than read off
 * `ModalBottomSheetDefaults` so it cannot drift with an API change.
 */
private val SheetScrim = Color.Black.copy(alpha = 0.32f)

/**
 * Dim the status-bar strip while a sheet is open.
 *
 * `ModalBottomSheet` draws its scrim inside the composition, and on this theme
 * that stops short of the status-bar strip — so the strip stays at full
 * brightness while everything under it greys out, and the seam is clearly
 * visible. Painting the sheet's own scrim colour into the window's status bar
 * makes the two match exactly, because it *is* the same translucent black.
 *
 * Ignored from Android 15 on, where `statusBarColor` is deprecated and a no-op;
 * the sheet scrim is the only thing drawing there on those releases anyway.
 */
@Composable
fun SheetStatusBar(open: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // Matches what the menu sheets render with, so the nav bar strip the
    // buttons sit on is the same surface as the sheet above it.
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // Without this the platform paints its own contrast veil over a
        // translucent status bar, which is a different shade to our scrim.
        if (window.isStatusBarContrastEnforced) window.isStatusBarContrastEnforced = false
        // Same story for the nav bar, and the reason it matters here: a sheet's
        // list is scrollable, so without an opaque strip its items slide behind
        // the back/home/recents buttons instead of ending above them.
        if (window.isNavigationBarContrastEnforced) window.isNavigationBarContrastEnforced = false
        window.statusBarColor = if (open) SheetScrim.toArgb() else android.graphics.Color.TRANSPARENT
        window.navigationBarColor = if (open) sheetColor.toArgb() else android.graphics.Color.TRANSPARENT
    }
}

/**
 * Nav-bar height for sheet content.
 *
 * Three things had to be ruled out first, all measured on-device from the
 * emulator screenshots:
 *  - `navigationBarsPadding()` on the sheet content: no effect.
 *  - `ModalBottomSheet(windowInsets = WindowInsets.systemBars)`: no effect.
 *  - `WindowInsets.navigationBars` read at the root and passed in: no effect.
 *
 * `ModalBottomSheet` consumes insets before its content sees them, and — the
 * real reason the third attempt failed too — Compose reports
 * `navigationBars` as zero in this app entirely. So read the window directly,
 * which is immune to both, and fall back to the platform's nav-bar dimension
 * when the window reports nothing (immersive mode, some OEM builds).
 *
 * The logged value is deliberate: this bug is invisible to the compiler and to
 * lint, so the next reader needs the number in the log rather than a comment
 * claiming it works.
 */
@Composable
fun navBarInset(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val activity = view.context as? Activity
        val fromWindow = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom ?: 0
        // Platform dimension: 48dp on 3-button, 24dp on gesture nav.
        val fromPlatform = activity?.resources?.let { res ->
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) res.getDimensionPixelSize(id) else 0
        } ?: 0
        val px = maxOf(fromWindow, fromPlatform)
        android.util.Log.i(
            "MediyoSheet",
            "navBarInset window=${fromWindow}px platform=${fromPlatform}px -> ${with(density) { px.toDp() }}"
        )
        with(density) { px.toDp() }
    }
}

/** Render in `AppShell`'s root overlay `Box`, after the queue overlay. */
@Composable
fun GlobalSheets(host: SheetHostVm = hiltViewModel()) {
    val m = host.sheets
    val trackReq by m.trackMenu.collectAsState()
    val addTrack by m.addToPlaylist.collectAsState()
    val mediaReq by m.mediaMenu.collectAsState()
    val commentsVid by m.comments.collectAsState()

    SheetStatusBar(open = trackReq != null || addTrack != null || mediaReq != null || commentsVid != null)

    // Read here, outside the sheet, where the inset is still live.
    val navBottom = navBarInset()

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
            playbackLabel = r.playbackLabel,
            navBarInset = navBottom
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
            onComments = r.onComments,
            navBarInset = navBottom
        )
    }
    commentsVid?.let { vid ->
        CommentsBottomSheet(videoId = vid, onDismiss = m::dismissComments)
    }
}
