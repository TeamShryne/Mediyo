package com.teamshryne.mediyo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.MediaCard
import com.teamshryne.mediyo.core.design.MediaCardShimmer
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.domain.model.Track

/** Fake search field: typing beats scrolling, so home offers it before anything else. */
@Composable
fun HomeSearchBar(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                "Search songs, artists, podcasts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One quick-pick tile: thumb + title, dense enough for six in a glance. */
@Composable
private fun QuickPickTile(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Quick picks: 2 columns × up to 3 rows. The whole point is no scrolling —
 * your most-likely-next-tap is always above the fold.
 */
@Composable
fun QuickPickGrid(tracks: List<Track>, modifier: Modifier = Modifier, onPick: (Track) -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tracks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t -> QuickPickTile(t, { onPick(t) }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Rotation feature: the week's #1 gets a hero card (identity — "my week in
 * music"), the rest fall into slim rows elsewhere.
 */
@Composable
fun RotationFeature(track: Track, plays: Int, isPlaying: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (plays > 1) "Played $plays times this week" else "On repeat this week",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artists.joinToString().ifBlank { "Your top track" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** Interrupted intent as chips: re-running beats retyping. */
@Composable
fun RecentSearchChips(queries: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(queries, key = { "q_$it" }) { q ->
            AssistChip(
                onClick = { onPick(q) },
                label = { Text(q, maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

/** Spotlight: the latest query, elevated to a card — "we remember what you wanted". */
@Composable
fun SearchSpotlightCard(query: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Because you searched",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
                "“$query”",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Episode resume row: show name first (podcasts are identified by show, not title). */
@Composable
fun EpisodeResumeRow(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(track.album?.takeIf { it.isNotBlank() }, track.duration?.takeIf { it.isNotBlank() })
                    .joinToString("  •  ").ifBlank { "Episode" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Generated mix card: artist face, "X Mix", made-for-you framing (variable reward). */
@Composable
fun MixCard(mix: HomeMix, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Column(modifier.width(160.dp)) {
        Box {
            AsyncImage(
                model = mix.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onPlay)
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, "Play mix", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${mix.artistName} Mix",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            "Made for you",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Week in numbers: a Wrapped-style micro-moment, computed not fetched. */
@Composable
fun WeekStatsBanner(stats: WeekStats, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AutoAwesome, null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            when {
                stats.plays == 0 -> "A quiet week — press play on anything"
                stats.minutes < 60 -> "${stats.plays} plays this week"
                else -> "${stats.plays} plays · ${stats.minutes / 60}h ${stats.minutes % 60}m this week"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/** Library shortcuts: owned music gets a permanent, countable band. */
@Composable
fun LibraryBand(
    likedCount: Int,
    playlistCount: Int,
    savedCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryTile("Liked", "$likedCount songs", Icons.Filled.Favorite, Modifier.weight(1f), onLiked)
        LibraryTile("Playlists", "$playlistCount", Icons.Filled.LibraryMusic, Modifier.weight(1f), onPlaylists)
        LibraryTile("History", "Replay", Icons.Filled.History, Modifier.weight(1f), onHistory)
    }
}

@Composable
private fun LibraryTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Daypart header icon follows the clock (sun/moon) — the shelf explains itself. */
@Composable
fun DaypartHeader(title: String, night: Boolean, modifier: Modifier = Modifier) {
    SectionHeader(
        title,
        modifier,
        trailing = {
            Icon(
                if (night) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/**
 * Album progress row: thin bar under the title. Started collections beg
 * finishing — the bar is the nudge.
 */
@Composable
fun AlbumProgressRow(item: AlbumProgress, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = item.ratio,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.done} of ${item.total} played",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Lazy-shelf placeholder: network sections appear gracefully, never flashing empty. */
@Composable
fun ShelfLoading(modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(3) { MediaCardShimmer() }
    }
}

/** Artist circles: faces, not squares — people follow people. */
@Composable
fun ArtistCircleRow(
    artists: List<ArtistAffinity>,
    modifier: Modifier = Modifier,
    onOpen: (ArtistAffinity) -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(artists, key = { "ha_${it.artistId ?: it.name}" }) { a ->
            MediaCard(
                title = a.name,
                subtitle = if (a.followed) "Following" else "${a.trackCount} songs",
                artworkUrl = a.artworkUrl,
                round = true,
                size = 124.dp
            ) { onOpen(a) }
        }
    }
}
