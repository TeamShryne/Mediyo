package com.teamshryne.mediyo.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.model.toDomainTrack
import com.teamshryne.mediyo.domain.model.toDomainTracks
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.SavedCollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uniffi.mediyo_ffi.FfiSearchResult
import javax.inject.Inject

// ── Type helpers (Rust Category Debug strings: Song/Video/Album/Artist/Playlist/Episode/Podcast) ──

fun FfiSearchResult.isArtist(): Boolean = category.contains("Artist", true)
fun FfiSearchResult.isAlbum(): Boolean = category.contains("Album", true)
fun FfiSearchResult.isPlaylist(): Boolean = category.contains("Playlist", true)
fun FfiSearchResult.isPodcast(): Boolean = category.contains("Podcast", true)
fun FfiSearchResult.isEpisode(): Boolean = category.contains("Episode", true)
fun FfiSearchResult.isSongLike(): Boolean = videoId != null

/** Browseable collections (albums, playlists, podcasts, generic lists). */
fun FfiSearchResult.isCollection(): Boolean =
    isAlbum() || isPlaylist() || isPodcast() || (browseId != null && !isArtist() && videoId == null)

fun FfiSearchResult.typeLabel(): String = when {
    isArtist() -> "Artist"
    isAlbum() -> "Album"
    isPlaylist() -> "Playlist"
    isPodcast() -> "Podcast"
    isEpisode() -> "Episode"
    else -> category
}

/** Open destination for a browseable item, mirroring each screen's open() logic. */
fun openRoute(item: FfiSearchResult): String? = when {
    item.isArtist() && item.browseId != null -> "artist/${item.browseId}"
    item.isAlbum() && item.browseId != null -> "album/${item.browseId}"
    item.isPlaylist() -> (item.playlistId ?: item.browseId)?.let { "playlist/$it" }
    item.isPodcast() && item.browseId != null -> "podcast/${item.browseId}"
    item.browseId != null -> {
        val p = item.browseParams?.takeIf { it.isNotBlank() }?.let { "?params=${android.net.Uri.encode(it)}" } ?: ""
        "list/${item.browseId}$p"
    }
    else -> null
}

/** Library kind for a savable public collection, null when not savable. */
fun FfiSearchResult.libraryKind(): String? = when {
    isAlbum() -> com.teamshryne.mediyo.data.local.SavedCollectionEntity.ALBUM
    isPlaylist() -> com.teamshryne.mediyo.data.local.SavedCollectionEntity.PLAYLIST
    isPodcast() -> com.teamshryne.mediyo.data.local.SavedCollectionEntity.PODCAST
    // Generic browseable lists (mixes, charts…) behave like public playlists.
    browseId != null && !isArtist() && videoId == null ->
        com.teamshryne.mediyo.data.local.SavedCollectionEntity.PLAYLIST
    else -> null
}

/** Stable library id for a savable collection: browseId preferred, playlistId fallback. */
fun FfiSearchResult.libraryId(): String? = browseId ?: playlistId

/** Subtitle snapshot for the library row (artist names / info). */
fun FfiSearchResult.librarySubtitle(): String? {
    val a = artists.joinToString().ifBlank { album }
    val i = info?.takeIf { it.isNotBlank() }
    return when {
        a != null && a.isNotBlank() && i != null -> "$a • $i"
        a != null && a.isNotBlank() -> a
        else -> i
    }
}

/** Playback origin matching the collection type. */
fun originFor(item: FfiSearchResult): PlayOrigin {
    val id = item.browseId ?: item.playlistId ?: item.videoId ?: item.title
    return when {
        item.isAlbum() -> PlayOrigin.Album(id, item.title)
        item.isPlaylist() -> PlayOrigin.Playlist(id, item.title, item.playlistId)
        item.isPodcast() -> PlayOrigin.Podcast(id)
        item.isArtist() -> PlayOrigin.ArtistTop(id, item.title)
        else -> PlayOrigin.GenericList(id)
    }
}

// ── ViewModel: resolves + fetches whatever a menu action needs ───────────────

@HiltViewModel
class MediaMenuVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val artistRepo: ArtistRepository,
    private val likeRepo: LikeRepository,
    private val savedRepo: SavedCollectionRepository
) : ViewModel() {
    /** Which action is currently working ("play", "queue", "artist", "album", ...). */
    var busy by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    @JvmName("setBusyState")
    fun setBusy(key: String?) { busy = key; if (key != null) error = null }

    fun isFollowedFlow(browseId: String) = artistRepo.isFollowedFlow(browseId)
    fun isLikedFlow(videoId: String) = likeRepo.isLikedFlow(videoId)

    fun toggleFollow(item: FfiSearchResult) {
        val id = item.browseId ?: return
        viewModelScope.launch {
            try {
                artistRepo.toggle(id, item.title, item.thumbnails.bestThumbUrl(), null)
            } catch (_: Throwable) { }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { try { likeRepo.toggle(track) } catch (_: Throwable) { } }
    }

    fun isSavedFlow(browseId: String) = savedRepo.isSavedFlow(browseId)

    /** Save / unsave a public collection (album, playlist, podcast, list). */
    fun toggleSave(item: FfiSearchResult) {
        val id = item.libraryId() ?: return
        val kind = item.libraryKind() ?: return
        viewModelScope.launch {
            try {
                savedRepo.toggle(
                    browseId = id,
                    kind = kind,
                    title = item.title,
                    subtitle = item.librarySubtitle(),
                    artworkUrl = item.thumbnails.bestThumbUrl()
                )
            } catch (_: Throwable) { }
        }
    }

    private suspend fun searchFirst(query: String, category: String): FfiSearchResult? =
        bridge.search(query).results.firstOrNull {
            it.category.equals(category, true) && it.browseId != null
        }

    /** Resolve an artist browseId: direct for artists, search-by-name otherwise. */
    suspend fun resolveArtistId(item: FfiSearchResult): String? {
        if (item.isArtist()) return item.browseId
        val name = item.artists.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return resolveArtistIdByName(name)
    }

    /** Resolve an artist browseId from a plain name (fallback when no ID survived). */
    suspend fun resolveArtistIdByName(name: String): String? {
        val q = name.trim()
        if (q.isEmpty()) return null
        return searchFirst(q, "Artist")?.browseId
    }

    /** Resolve an album browseId: direct for albums, search-by-name otherwise. */
    suspend fun resolveAlbumId(item: FfiSearchResult): String? {
        if (item.isAlbum()) return item.browseId
        val name = item.album?.takeIf { it.isNotBlank() } ?: return null
        return searchFirst(name, "Album")?.browseId
    }

    /** Fetch playable tracks for an album/playlist/podcast/list page. */
    suspend fun fetchCollection(item: FfiSearchResult): List<Track> {
        val id = item.browseId ?: item.playlistId
            ?: throw IllegalArgumentException("Can't load this item")
        return when {
            item.isAlbum() -> bridge.album(id).tracks.toDomainTracks()
            item.isPlaylist() -> bridge.playlist(id).tracks.toDomainTracks()
            item.isPodcast() -> bridge.podcast(id).items.toDomainTracks()
            else -> bridge.listPage(id, item.browseParams).items.toDomainTracks()
        }
    }

    /** Fetch an artist's top songs as playable tracks. */
    suspend fun fetchArtistTop(item: FfiSearchResult): List<Track> {
        val id = item.browseId ?: throw IllegalArgumentException("Can't load this artist")
        return bridge.artist(id).topSongs.toDomainTracks()
    }
}

// ── Per-type bottom sheet ────────────────────────────────────────────────────

/**
 * The right menu for any [FfiSearchResult]: songs get like/queue/go-to actions,
 * artists get open/follow/play-top-songs, collections get open/play/shuffle/queue-all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaMenuSheet(
    item: FfiSearchResult,
    show: Boolean,
    onDismiss: () -> Unit,
    nav: androidx.navigation.NavController?,
    onPlayTracks: (tracks: List<Track>, index: Int, origin: PlayOrigin) -> Unit,
    onEnqueueTracks: (List<Track>) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onComments: ((videoId: String) -> Unit)? = null,
    vm: MediaMenuVm = hiltViewModel()
) {
    if (!show) return
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuHeader(item = item)
            if (vm.error != null) {
                Text(
                    vm.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when {
                item.isArtist() -> ArtistActions(item, nav, onDismiss, onPlayTracks, onEnqueueTracks, vm, scope)
                item.isCollection() -> CollectionActions(item, nav, onDismiss, onPlayTracks, onEnqueueTracks, vm, scope)
                else -> SongActions(item, nav, onDismiss, onAddToPlaylist, onPlayNext, onAddToQueue, onComments, vm, scope)
            }
        }
    }
}

@Composable
private fun MenuHeader(item: FfiSearchResult) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = item.thumbnails.bestThumbUrl()
        if (item.isArtist()) {
            if (art == null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                AsyncImage(
                    model = art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        } else {
            AsyncImage(
                model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(item.typeLabel())
                    val sub = item.artists.joinToString().ifBlank { item.album.orEmpty() }
                    if (sub.isNotBlank()) append(" • $sub")
                    if (!item.duration.isNullOrBlank() && !item.isArtist() && !item.isCollection()) append(" • ${item.duration}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BusyIcon(busy: String?, key: String, fallback: androidx.compose.ui.graphics.vector.ImageVector) {
    if (busy == key) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        Icon(fallback, contentDescription = null)
    }
}

// ── Song / video / episode ───────────────────────────────────────────────────

@Composable
private fun SongActions(
    item: FfiSearchResult,
    nav: androidx.navigation.NavController?,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onComments: ((String) -> Unit)?,
    vm: MediaMenuVm,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val track = remember(item.videoId, item.title) { item.toDomainTrack() }
    val vid = item.videoId
    val liked by if (vid != null) {
        remember(vid) { vm.isLikedFlow(vid) }.collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }
    val artistEntries = remember(item) {
        item.artists.mapIndexedNotNull { i, n ->
            n.takeIf { it.isNotBlank() }?.let { name ->
                Triple(name, item.artistIds.getOrNull(i)?.takeIf { it.isNotBlank() }, "artist:$name")
            }
        }
    }
    val canResolveAlbum = !item.album.isNullOrBlank()
    var showArtists by remember(item) { mutableStateOf(false) }
    BackHandler(enabled = showArtists) { showArtists = false }

    fun openArtist(name: String, id: String?, key: String, miss: String) {
        scope.launch {
            vm.setBusy(key)
            try {
                // Prefer the parsed ID; fall back to search-by-name.
                val resolved = id ?: vm.resolveArtistIdByName(name)
                if (resolved != null) { onDismiss(); nav?.navigate("artist/$resolved") }
                else vm.error = miss
            } catch (e: Throwable) {
                vm.error = miss
            } finally {
                vm.setBusy(null)
            }
        }
    }

    if (showArtists && artistEntries.size > 1) {
        // ── Artist picker ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { showArtists = false }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Artists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        artistEntries.forEach { (name, id, key) ->
            MenuItem(
                icon = { BusyIcon(vm.busy, key, Icons.Filled.Person) },
                label = name,
                enabled = vm.busy == null,
                onClick = { openArtist(name, id, key, "Couldn't find $name") }
            )
        }
    } else {
        MenuItem(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = if (liked) "Remove from Liked" else "Add to Liked",
            enabled = vid != null,
            onClick = { onDismiss(); if (vid != null) vm.toggleLike(track) }
        )
        MenuItem(icon = Icons.Filled.PlaylistAdd, label = "Add to playlist", onClick = { onDismiss(); onAddToPlaylist(track) })
        MenuItem(icon = Icons.Filled.QueueMusic, label = "Play next", onClick = { onDismiss(); onPlayNext(track) })
        MenuItem(icon = Icons.Filled.PlaylistPlay, label = "Add to queue", onClick = { onDismiss(); onAddToQueue(track) })
        if (artistEntries.size == 1) {
            val (name, id, key) = artistEntries[0]
            MenuItem(
                icon = { BusyIcon(vm.busy, key, Icons.Filled.Person) },
                label = "Show artist",
                enabled = vm.busy == null,
                onClick = { openArtist(name, id, key, "Couldn't find that artist") }
            )
        } else if (artistEntries.isNotEmpty()) {
            MenuItem(icon = Icons.Filled.Person, label = "Show artists", onClick = { showArtists = true })
        }
    if (canResolveAlbum) {
        MenuItem(
            icon = { BusyIcon(vm.busy, "album", Icons.Filled.Album) },
            label = "Go to album",
            enabled = vm.busy == null,
            onClick = {
                scope.launch {
                    vm.setBusy("album")
                    try {
                        val id = item.albumId?.takeIf { it.isNotBlank() }
                            ?: vm.resolveAlbumId(item)
                        if (id != null) { onDismiss(); nav?.navigate("album/$id") }
                        else vm.error = "Couldn't find that album"
                    } catch (e: Throwable) {
                        vm.error = "Couldn't find that album"
                    } finally {
                        vm.setBusy(null)
                    }
                }
            }
        )
    }
    if (onComments != null && vid != null) {
        MenuItem(icon = Icons.Filled.Comment, label = "Comments", onClick = { onDismiss(); onComments(vid) })
    }
    }
}

// ── Artist ───────────────────────────────────────────────────────────────────

@Composable
private fun ArtistActions(
    item: FfiSearchResult,
    nav: androidx.navigation.NavController?,
    onDismiss: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayOrigin) -> Unit,
    onEnqueueTracks: (List<Track>) -> Unit,
    vm: MediaMenuVm,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val browseId = item.browseId
    val followed by if (browseId != null) {
        remember(browseId) { vm.isFollowedFlow(browseId) }.collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }

    MenuItem(
        icon = Icons.Filled.OpenInNew, label = "Open artist",
        enabled = browseId != null,
        onClick = { if (browseId != null) { onDismiss(); nav?.navigate("artist/$browseId") } }
    )
    MenuItem(
        icon = if (followed) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
        label = if (followed) "Unfollow" else "Follow",
        enabled = browseId != null,
        onClick = { vm.toggleFollow(item) }
    )
    MenuItem(
        icon = { BusyIcon(vm.busy, "play", Icons.Filled.PlayArrow) },
        label = "Play top songs",
        enabled = browseId != null && vm.busy == null,
        onClick = {
            scope.launch {
                vm.setBusy("play")
                try {
                    val tracks = vm.fetchArtistTop(item)
                    if (tracks.isEmpty()) vm.error = "No playable songs found"
                    else {
                        val id = item.browseId ?: item.title
                        onDismiss(); onPlayTracks(tracks, 0, PlayOrigin.ArtistTop(id, item.title))
                    }
                } catch (e: Throwable) {
                    vm.error = "Couldn't load top songs"
                } finally {
                    vm.setBusy(null)
                }
            }
        }
    )
    MenuItem(
        icon = { BusyIcon(vm.busy, "queue", Icons.Filled.QueueMusic) },
        label = "Add top songs to queue",
        enabled = browseId != null && vm.busy == null,
        onClick = {
            scope.launch {
                vm.setBusy("queue")
                try {
                    val tracks = vm.fetchArtistTop(item)
                    if (tracks.isEmpty()) vm.error = "No playable songs found"
                    else { onEnqueueTracks(tracks); onDismiss() }
                } catch (e: Throwable) {
                    vm.error = "Couldn't load top songs"
                } finally {
                    vm.setBusy(null)
                }
            }
        }
    )
}

// ── Album / playlist / podcast / list ────────────────────────────────────────

@Composable
private fun CollectionActions(
    item: FfiSearchResult,
    nav: androidx.navigation.NavController?,
    onDismiss: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayOrigin) -> Unit,
    onEnqueueTracks: (List<Track>) -> Unit,
    vm: MediaMenuVm,
    scope: kotlinx.coroutines.CoroutineScope
) {
    fun play(shuffle: Boolean, key: String) {
        scope.launch {
            vm.setBusy(key)
            try {
                val tracks = vm.fetchCollection(item).let { if (shuffle) it.shuffled() else it }
                if (tracks.isEmpty()) vm.error = "No playable tracks found"
                else { onDismiss(); onPlayTracks(tracks, 0, originFor(item)) }
            } catch (e: Throwable) {
                vm.error = e.message?.takeIf { it.isNotBlank() } ?: "Couldn't load this ${item.typeLabel().lowercase()}"
            } finally {
                vm.setBusy(null)
            }
        }
    }

    val route = openRoute(item)
    val saveId = item.libraryId()
    val saved by if (saveId != null) {
        remember(saveId) { vm.isSavedFlow(saveId) }.collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }
    MenuItem(
        icon = Icons.Filled.OpenInNew, label = "Open ${item.typeLabel().lowercase()}",
        enabled = route != null,
        onClick = { if (route != null) { onDismiss(); nav?.navigate(route) } }
    )
    if (saveId != null && item.libraryKind() != null) {
        MenuItem(
            icon = if (saved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
            label = if (saved) "Remove from library" else "Add to library",
            onClick = { vm.toggleSave(item) }
        )
    }
    MenuItem(
        icon = { BusyIcon(vm.busy, "play", Icons.Filled.PlayArrow) },
        label = "Play",
        enabled = vm.busy == null,
        onClick = { play(shuffle = false, key = "play") }
    )
    MenuItem(
        icon = { BusyIcon(vm.busy, "shuffle", Icons.Filled.Shuffle) },
        label = "Shuffle",
        enabled = vm.busy == null,
        onClick = { play(shuffle = true, key = "shuffle") }
    )
    MenuItem(
        icon = { BusyIcon(vm.busy, "queue", Icons.Filled.QueueMusic) },
        label = "Add all to queue",
        enabled = vm.busy == null,
        onClick = {
            scope.launch {
                vm.setBusy("queue")
                try {
                    val tracks = vm.fetchCollection(item)
                    if (tracks.isEmpty()) vm.error = "No playable tracks found"
                    else { onEnqueueTracks(tracks); onDismiss() }
                } catch (e: Throwable) {
                    vm.error = e.message?.takeIf { it.isNotBlank() } ?: "Couldn't load this ${item.typeLabel().lowercase()}"
                } finally {
                    vm.setBusy(null)
                }
            }
        }
    )
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun MenuItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon,
        enabled = enabled,
        onClick = onClick
    )
}
