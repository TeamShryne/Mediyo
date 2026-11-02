package com.teamshryne.mediyo.music.ui.screens.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.teamshryne.mediyo.R
import com.teamshryne.mediyo.data.mediyo.FfiEpisodePage
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.data.mediyo.parseDurationSeconds
import com.teamshryne.mediyo.music.LocalPlayerAwareWindowInsets
import com.teamshryne.mediyo.music.LocalPlayerConnection
import com.teamshryne.mediyo.music.constants.AppBarHeight
import com.teamshryne.mediyo.music.extensions.toMediaItem
import com.teamshryne.mediyo.music.extensions.togglePlayPause
import com.teamshryne.mediyo.music.models.MediaMetadata
import com.teamshryne.mediyo.music.playback.queues.ListQueue
import com.teamshryne.mediyo.music.ui.component.NavigationTitle
import com.teamshryne.mediyo.music.ui.utils.backToMain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    private val bridge: MediyoBridge,
) : ViewModel() {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var page by mutableStateOf<FfiEpisodePage?>(null)

    fun load(id: String) {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                page = bridge.episode(id)
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    navController: NavController,
    episodeId: String,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: EpisodeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    val page = viewModel.page

    Box(Modifier.fillMaxSize()) {
        when {
            viewModel.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            viewModel.error != null || page == null -> Box(
                Modifier.fillMaxSize().padding(32.dp), Alignment.Center
            ) {
                Text(viewModel.error ?: "Failed to load episode")
            }
            else -> LazyColumn(
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .add(WindowInsets(top = -WindowInsets.systemBars.asPaddingValues().calculateTopPadding() - AppBarHeight))
                    .asPaddingValues()
            ) {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        AsyncImage(
                            model = page.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            page.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        page.showName?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(page.date, page.stats, page.duration)
                                .filter { it.isNotBlank() }
                                .joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(16.dp))
                        val active = page.videoId != null && page.videoId == mediaMetadata?.id
                        Button(
                            onClick = {
                                if (active) {
                                    playerConnection?.player?.togglePlayPause()
                                } else {
                                    val videoId = page.videoId ?: return@Button
                                    val metadata = MediaMetadata(
                                        id = videoId,
                                        title = page.title,
                                        artists = listOfNotNull(
                                            page.showName?.takeIf { it.isNotBlank() }
                                                ?.let { MediaMetadata.Artist(null, it) }
                                        ),
                                        duration = parseDurationSeconds(page.duration) ?: -1,
                                        thumbnailUrl = page.artworkUrl
                                    )
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = page.showName ?: page.title,
                                            items = listOf(metadata.toMediaItem())
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painterResource(if (active && isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (active && isPlaying) "Pause" else "Play episode")
                        }
                    }
                }

                page.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item(key = "description") {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                if (page.chapters.isNotEmpty()) {
                    item(key = "chapters_title") {
                        NavigationTitle(title = "Chapters")
                    }
                    items(page.chapters, key = { "${it.startSeconds}-${it.text}" }) { chapter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerConnection?.player?.seekTo(chapter.startSeconds * 1000L)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                chapter.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                formatTime(chapter.startSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = { Text(page?.showName?.ifBlank { "Episode" } ?: "Episode") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
