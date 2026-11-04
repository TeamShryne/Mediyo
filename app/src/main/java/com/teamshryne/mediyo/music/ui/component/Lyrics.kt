package com.teamshryne.mediyo.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.feature.lyrics.LyricsSearchSheet
import com.teamshryne.mediyo.feature.lyrics.LyricsViewModel
import com.teamshryne.mediyo.feature.lyrics.SyncedLyricsView
import com.teamshryne.mediyo.music.LocalPlayerConnection
import com.teamshryne.mediyo.music.ui.menu.LyricsMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Player lyrics surface.
 *
 * The rendering, provider stack, caching and animations all come from the
 * ported lyrics feature; this only adapts the old app's player connection to
 * it (position sampling, seek, current media) and adds the overflow menu the
 * lyrics had in the previous app: refetch from the providers, search another
 * source, and the size / weight / spacing screen.
 */
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    onOpenAppearance: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val vm: LyricsViewModel = hiltViewModel()

    val state by vm.state.collectAsState()
    val style by vm.style.collectAsState()

    val track = remember(mediaMetadata?.id) {
        mediaMetadata?.let {
            Track(
                videoId = it.id,
                title = it.title,
                artists = it.artists.map { artist -> artist.name },
                artistIds = it.artists.map { artist -> artist.id.orEmpty() },
                album = it.album?.title,
                albumId = it.album?.id,
                artworkUrl = it.thumbnailUrl,
                duration = it.duration.takeIf { d -> d > 0 }?.let { d ->
                    "%d:%02d".format(d / 60, d % 60)
                },
                explicit = it.explicit
            )
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    val menuState = LocalMenuState.current

    LaunchedEffect(track?.uniqueKey()) {
        track?.let { vm.load(it, mediaMetadata?.duration?.takeIf { d -> d > 0 }?.times(1000L)) }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mediaMetadata?.id, isPlaying) {
        while (isActive) {
            val preview = sliderPositionProvider()
            positionMs = preview ?: playerConnection.player.currentPosition
            durationMs = playerConnection.player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: (mediaMetadata?.duration?.takeIf { d -> d > 0 }?.times(1000L) ?: 0L)
            delay(if (isPlaying) 50 else 200)
        }
    }

    Box(modifier.fillMaxSize()) {
        SyncedLyricsView(
            state = state,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            onSeek = { playerConnection.player.seekTo(it) },
            onRetry = { track?.let { vm.retry(it, mediaMetadata?.duration?.takeIf { d -> d > 0 }?.times(1000L)) } },
            onSearch = { showSearch = true },
            playbackSpeed = playerConnection.player.playbackParameters.speed,
            style = style,
            modifier = Modifier.fillMaxSize()
        )

        // The same three-dot menu the previous app had while lyrics were on
        // screen, shown as a bottom sheet like every other menu in the app.
        // Kept here rather than in the lyrics feature so it can reach
        // the player's navigation and current duration.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 4.dp, end = 8.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.22f), CircleShape)
        ) {
            IconButton(onClick = {
                menuState.show {
                    LyricsMenu(
                        onRefetch = {
                            track?.let { current ->
                                vm.refetch(current, durationMs.takeIf { d -> d > 0 })
                            }
                        },
                        onSearch = { showSearch = true },
                        onAppearance = onOpenAppearance,
                        onDismiss = menuState::dismiss,
                    )
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Lyrics options",
                    tint = Color.White
                )
            }
        }
    }

    if (showSearch && track != null) {
        LyricsSearchSheet(
            track = track,
            durationMs = mediaMetadata?.duration?.takeIf { d -> d > 0 }?.times(1000L),
            vm = vm,
            onDismiss = { showSearch = false }
        )
    }
}
