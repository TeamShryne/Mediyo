package com.teamshryne.mediyo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.data.local.FollowedArtistEntity
import com.teamshryne.mediyo.data.local.LocalPlaylistEntity
import com.teamshryne.mediyo.domain.model.PlayOrigin
import com.teamshryne.mediyo.domain.model.Track
import com.teamshryne.mediyo.domain.model.upscaledThumbUrl
import com.teamshryne.mediyo.domain.repository.ArtistRepository
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class LibFilter { Playlists, Songs, Artists }
private enum class LibSort { Recent, Name }

// ── ViewModel: Room is the single source of truth (100% local) ───────────────

@HiltViewModel
class LibraryVm @Inject constructor(
    private val playlistRepo: PlaylistRepository,
    private val artistRepo: ArtistRepository,
    private val likeRepo: LikeRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {
    val playlists = playlistRepo.flowPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artists = artistRepo.flowFollowed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedCount = likeRepo.countFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val historyCount = historyRepo.countFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var filter by mutableStateOf<LibFilter?>(null) // null = All (YT Music default)
    var sort by mutableStateOf(LibSort.Recent)
    var query by mutableStateOf("")
    var showSearch by mutableStateOf(false)
    var showCreateDialog by mutableStateOf(false)
    var newTitle by mutableStateOf("")
    var newDesc by mutableStateOf("")

    var renameTarget by mutableStateOf<LocalPlaylistEntity?>(null)
    var renameTitle by mutableStateOf("")
    var renameDesc by mutableStateOf("")
    var deleteTarget by mutableStateOf<LocalPlaylistEntity?>(null)
    var unfollowTarget by mutableStateOf<FollowedArtistEntity?>(null)

    fun createPlaylist(onCreated: (String) -> Unit = {}) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepo.create(t, newDesc.ifBlank { null })
            newTitle = ""; newDesc = ""; showCreateDialog = false
            onCreated(id)
        }
    }

    fun confirmRename() {
        val target = renameTarget ?: return
        val t = renameTitle.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            playlistRepo.rename(target.id, t, renameDesc.ifBlank { null })
            renameTarget = null
        }
    }

    fun confirmDelete() {
        val target = deleteTarget ?: return
        viewModelScope.launch {
            playlistRepo.delete(target.id)
            deleteTarget = null
        }
    }

    fun confirmUnfollow() {
        val target = unfollowTarget ?: return
        viewModelScope.launch {
            artistRepo.unfollow(target.browseId)
            unfollowTarget = null
        }
    }

    fun playPlaylist(
        playlist: LocalPlaylistEntity,
        player: com.teamshryne.mediyo.feature.player.PlayerViewModel?,
        shuffle: Boolean
    ) {
        viewModelScope.launch {
            val entries = playlistRepo.getEntries(playlist.id)
            if (entries.isEmpty()) return@launch
            val tracks = entries.map {
                Track(
                    videoId = it.trackVideoId, title = it.title,
                    artists = if (it.artist.isBlank()) emptyList() else it.artist.split(",").map { a -> a.trim() },
                    artworkUrl = it.artworkUrl.upscaledThumbUrl(), album = it.album,
                    duration = it.duration, category = it.category
                )
            }.let { if (shuffle) it.shuffled() else it }
            player?.playTracks(tracks, 0, PlayOrigin.LocalPlaylist(playlist.id, playlist.title))
        }
    }

    fun playLiked(player: com.teamshryne.mediyo.feature.player.PlayerViewModel?, shuffle: Boolean) {
        viewModelScope.launch {
            val liked = likeRepo.getLiked()
            if (liked.isEmpty()) return@launch
            val tracks = liked.map {
                Track(
                    videoId = it.videoId, title = it.title,
                    artists = if (it.artist.isBlank()) emptyList() else it.artist.split(",").map { a -> a.trim() },
                    artworkUrl = it.artworkUrl.upscaledThumbUrl(), album = it.album,
                    duration = it.duration, category = it.category
                )
            }.let { if (shuffle) it.shuffled() else it }
            player?.playTracks(tracks, 0, PlayOrigin.Liked(tracks.size))
        }
    }
}

// ── Screen: YouTube Music-style, minimal, local-only ─────────────────────────

@Composable
fun LibraryScreen(
    nav: androidx.navigation.NavController? = null,
    player: com.teamshryne.mediyo.feature.player.PlayerViewModel? = null,
    vm: LibraryVm = hiltViewModel()
) {
    val playlists by vm.playlists.collectAsState()
    val artists by vm.artists.collectAsState()
    val likedCount by vm.likedCount.collectAsState()
    val historyCount by vm.historyCount.collectAsState()

    val q = vm.query.trim()
    val showSongs = vm.filter == null || vm.filter == LibFilter.Songs
    val showPlaylists = vm.filter == null || vm.filter == LibFilter.Playlists
    val showArtists = vm.filter == null || vm.filter == LibFilter.Artists

    val filteredPlaylists = remember(playlists, q, vm.sort) {
        var list = if (q.isEmpty()) playlists else playlists.filter { it.title.contains(q, true) }
        list = when (vm.sort) {
            LibSort.Recent -> list.sortedByDescending { it.updatedAt }
            LibSort.Name -> list.sortedBy { it.title.lowercase() }
        }
        list
    }
    val filteredArtists = remember(artists, q, vm.sort) {
        var list = if (q.isEmpty()) artists else artists.filter { it.name.contains(q, true) }
        list = when (vm.sort) {
            LibSort.Recent -> list.sortedByDescending { it.followedAt }
            LibSort.Name -> list.sortedBy { it.name.lowercase() }
        }
        list
    }
    val showLikedRow = showSongs && (q.isEmpty() || "liked songs".contains(q, true)) && likedCount > 0
    val showHistoryRow = vm.filter == null && (q.isEmpty() || "history".contains(q, true)) && historyCount > 0
    val isEmpty = !showLikedRow && !showHistoryRow && filteredPlaylists.isEmpty() && filteredArtists.isEmpty()

    val summary = remember(playlists.size, artists.size, likedCount) {
        buildList {
            if (playlists.isNotEmpty()) add("${playlists.size} playlists")
            if (artists.isNotEmpty()) add("${artists.size} artists")
            if (likedCount > 0) add("$likedCount liked")
        }.joinToString(" • ")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header — title + summary + actions (YTM: compact top bar)
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (summary.isNotEmpty()) {
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.showSearch = !vm.showSearch
                        if (!vm.showSearch) vm.query = ""
                    }) {
                        Icon(
                            if (vm.showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search library",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SortMenu(sort = vm.sort, onPick = { vm.sort = it })
                    IconButton(onClick = { vm.showCreateDialog = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "New playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (vm.showSearch) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Search your library") },
                        leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (vm.query.isNotEmpty()) {
                                IconButton(onClick = { vm.query = "" }) {
                                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                // Filter chips — YTM style, single-select (tap active to clear)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = vm.filter == LibFilter.Playlists,
                        onClick = { vm.filter = if (vm.filter == LibFilter.Playlists) null else LibFilter.Playlists },
                        label = { Text("Playlists") }
                    )
                    FilterChip(
                        selected = vm.filter == LibFilter.Songs,
                        onClick = { vm.filter = if (vm.filter == LibFilter.Songs) null else LibFilter.Songs },
                        label = { Text("Songs") }
                    )
                    FilterChip(
                        selected = vm.filter == LibFilter.Artists,
                        onClick = { vm.filter = if (vm.filter == LibFilter.Artists) null else LibFilter.Artists },
                        label = { Text("Artists") }
                    )
                }
            }
        }

        if (isEmpty) {
            item(key = "empty") {
                LibraryEmptyState(
                    filter = vm.filter,
                    hasQuery = q.isNotEmpty(),
                    onCreatePlaylist = { vm.showCreateDialog = true },
                    onBrowse = { nav?.navigate("search") }
                )
            }
        } else {
            if (showLikedRow) {
                item(key = "liked") {
                    LibraryRow(
                        title = "Liked songs",
                        subtitle = "Playlist • $likedCount songs",
                        onClick = { nav?.navigate("liked") },
                        leading = {
                            TileIcon(
                                icon = Icons.Filled.Favorite,
                                contentDescription = "Liked songs"
                            )
                        },
                        trailing = {
                            Row {
                                IconButton(onClick = { vm.playLiked(player, shuffle = false) }) {
                                    Icon(Icons.Filled.PlayArrow, "Play liked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { nav?.navigate("liked") }) {
                                    Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    )
                }
            }
            if (showHistoryRow) {
                item(key = "history") {
                    LibraryRow(
                        title = "History",
                        subtitle = "$historyCount plays",
                        onClick = { nav?.navigate("history") },
                        leading = {
                            TileIcon(
                                icon = Icons.Filled.History,
                                contentDescription = "History"
                            )
                        },
                        trailing = null
                    )
                }
            }

            if (showPlaylists && filteredPlaylists.isNotEmpty()) {
                if (vm.filter == null && (showLikedRow || showHistoryRow || filteredArtists.isNotEmpty())) {
                    item(key = "pl_header") { ListSectionHeader("Playlists", filteredPlaylists.size) }
                }
                items(filteredPlaylists, key = { "pl_${it.id}" }) { pl ->
                    var menu by remember { mutableStateOf(false) }
                    LibraryRow(
                        title = pl.title,
                        subtitle = "Playlist • ${pl.trackCount} songs",
                        onClick = { nav?.navigate("localPlaylist/${pl.id}") },
                        leading = {
                            TileIcon(
                                icon = Icons.Filled.PlaylistPlay,
                                contentDescription = null
                            )
                        },
                        trailing = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Play") },
                                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                        onClick = { menu = false; vm.playPlaylist(pl, player, shuffle = false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Shuffle") },
                                        leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                                        onClick = { menu = false; vm.playPlaylist(pl, player, shuffle = true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                        onClick = {
                                            menu = false
                                            vm.renameTarget = pl
                                            vm.renameTitle = pl.title
                                            vm.renameDesc = pl.description.orEmpty()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { menu = false; vm.deleteTarget = pl }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (showArtists && filteredArtists.isNotEmpty()) {
                if (vm.filter == null && (showLikedRow || showHistoryRow || filteredPlaylists.isNotEmpty())) {
                    item(key = "ar_header") { ListSectionHeader("Artists", filteredArtists.size) }
                }
                items(filteredArtists, key = { "ar_${it.browseId}" }) { a ->
                    var menu by remember { mutableStateOf(false) }
                    LibraryRow(
                        title = a.name,
                        subtitle = buildString {
                            append("Artist")
                            if (!a.subscriberCount.isNullOrBlank()) append(" • ${a.subscriberCount}")
                        },
                        onClick = { nav?.navigate("artist/${a.browseId}") },
                        leading = { ArtistThumb(a) },
                        trailing = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Open artist") },
                                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                                        onClick = { menu = false; nav?.navigate("artist/${a.browseId}") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unfollow") },
                                        leadingIcon = { Icon(Icons.Filled.PersonRemove, null) },
                                        onClick = { menu = false; vm.unfollowTarget = a }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Dialogs ──
    if (vm.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { vm.showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vm.newTitle,
                        onValueChange = { vm.newTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = vm.newDesc,
                        onValueChange = { vm.newDesc = it },
                        label = { Text("Description (optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.createPlaylist() },
                    enabled = vm.newTitle.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { vm.showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    vm.renameTarget?.let {
        AlertDialog(
            onDismissRequest = { vm.renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vm.renameTitle,
                        onValueChange = { vm.renameTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = vm.renameDesc,
                        onValueChange = { vm.renameDesc = it },
                        label = { Text("Description (optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirmRename() }, enabled = vm.renameTitle.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { vm.renameTarget = null }) { Text("Cancel") } }
        )
    }

    vm.deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { vm.deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${pl.title}\" and its ${pl.trackCount} entries will be removed from this device.") },
            confirmButton = {
                Button(
                    onClick = { vm.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { vm.deleteTarget = null }) { Text("Cancel") } }
        )
    }

    vm.unfollowTarget?.let { a ->
        AlertDialog(
            onDismissRequest = { vm.unfollowTarget = null },
            title = { Text("Unfollow artist?") },
            text = { Text("\"${a.name}\" will be removed from your library.") },
            confirmButton = {
                Button(
                    onClick = { vm.confirmUnfollow() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Unfollow") }
            },
            dismissButton = { TextButton(onClick = { vm.unfollowTarget = null }) { Text("Cancel") } }
        )
    }
}

// ── Minimal YTM-style primitives ─────────────────────────────────────────────

@Composable
private fun SortMenu(sort: LibSort, onPick: (LibSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                if (sort == LibSort.Name) Icons.Filled.SortByAlpha else Icons.Filled.Schedule,
                contentDescription = "Sort library",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Recent activity") },
                leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                trailingIcon = { if (sort == LibSort.Recent) Icon(Icons.Filled.Check, null) },
                onClick = { open = false; onPick(LibSort.Recent) }
            )
            DropdownMenuItem(
                text = { Text("Name (A–Z)") },
                leadingIcon = { Icon(Icons.Filled.SortByAlpha, null) },
                trailingIcon = { if (sort == LibSort.Name) Icon(Icons.Filled.Check, null) },
                onClick = { open = false; onPick(LibSort.Name) }
            )
        }
    }
}

@Composable
private fun ListSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun TileIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?
) {
    Box(
        Modifier.size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ArtistThumb(a: FollowedArtistEntity) {
    if (a.artworkUrl.isNullOrBlank()) {
        Box(
            Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
    } else {
        AsyncImage(
            model = a.artworkUrl,
            contentDescription = a.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
private fun LibraryEmptyState(
    filter: LibFilter?,
    hasQuery: Boolean,
    onCreatePlaylist: () -> Unit,
    onBrowse: () -> Unit
) {
    data class EmptyCopy(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val title: String,
        val subtitle: String,
        val cta: String?
    )
    val copy = when {
        hasQuery -> EmptyCopy(
            Icons.Filled.Search, "No matches",
            "Nothing in your library matches this search.", null
        )
        filter == LibFilter.Artists -> EmptyCopy(
            Icons.Filled.Person, "No followed artists",
            "Open any artist and tap Follow — artists stay on this device.", "Browse music"
        )
        filter == LibFilter.Songs -> EmptyCopy(
            Icons.Filled.Favorite, "No liked songs yet",
            "Tap the heart on any song and it will live here.", "Browse music"
        )
        filter == LibFilter.Playlists -> EmptyCopy(
            Icons.Filled.PlaylistPlay, "No playlists yet",
            "Create your first playlist — everything is stored locally.", "New playlist"
        )
        else -> EmptyCopy(
            Icons.Filled.PlaylistAdd, "Your library is empty",
            "Like songs, follow artists, and build playlists — all on this device.", "Browse music"
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(copy.icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(copy.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            copy.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        when (copy.cta) {
            "New playlist" -> Button(onClick = onCreatePlaylist, shape = CircleShape) { Text("New playlist") }
            "Browse music" -> FilledTonalButton(onClick = onBrowse, shape = CircleShape) { Text("Browse music") }
        }
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Download, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Local-only • stored in on-device database",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
