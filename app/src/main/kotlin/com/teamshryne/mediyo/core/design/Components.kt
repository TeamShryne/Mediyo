package com.teamshryne.mediyo.core.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.mediyo_ffi.FfiSearchResult
import java.util.Calendar

// ── Time helpers ─────────────────────────────────────────────────────────────
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun rememberGreeting(): String {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night listening"
    }
}

// ── Shimmer ──────────────────────────────────────────────────────────────────
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
}

// ── Dominant color extraction (for immersive player backgrounds) ─────────────
data class DominantColors(val container: Color, val deep: Color)

private fun Color.scale(f: Float): Color = Color(
    red = (red * f).coerceIn(0f, 1f),
    green = (green * f).coerceIn(0f, 1f),
    blue = (blue * f).coerceIn(0f, 1f),
    alpha = 1f
)

private fun Color.blend(other: Color, t: Float): Color = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = 1f
)

@Composable
fun rememberDominantColors(url: String?): DominantColors {
    val context = LocalContext.current
    val fallback = DominantColors(MediyoColors.AccentDim.scale(0.7f), MediyoColors.Bg1)
    var colors by remember { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        val u = url?.takeIf { it.isNotBlank() } ?: run { colors = fallback; return@LaunchedEffect }
        withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(u)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bmp != null) {
                    val palette = Palette.from(bmp).maximumColorCount(28).generate()
                    val swatch = palette.vibrantSwatch
                        ?: palette.lightVibrantSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.dominantSwatch
                    if (swatch != null) {
                        val c = Color(swatch.rgb)
                        val base = c.blend(MediyoColors.Accent, 0.12f)
                        colors = DominantColors(
                            container = base.scale(0.82f),
                            deep = base.scale(0.30f).blend(Color.Black, 0.45f)
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
    return colors
}

/** Vertical gradient brush used behind the full player / heroes. */
fun immersiveBrush(colors: DominantColors): Brush = Brush.verticalGradient(
    listOf(colors.container, colors.deep, MediyoColors.Bg0)
)

// ── Text ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    androidx.compose.material3.Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        textAlign = textAlign,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE)
    )
}

// ── Shelf header ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

// ── Media cards (Spotify-style shelves) ──────────────────────────────────────
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    round: Boolean,
    size: Dp = 148.dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(size)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size - 8.dp)
                .let { if (round) it.clip(CircleShape) else it.clip(RoundedCornerShape(12.dp)) }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Wide horizontal tile used for playlists/mixes in compact shelves. */
@Composable
fun MediaTile(title: String, artworkUrl: String?, width: Dp = 180.dp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
fun MediaCardShimmer(round: Boolean = false, size: Dp = 148.dp) {
    Column(Modifier.width(size).padding(4.dp)) {
        Box(
            Modifier
                .size(size - 8.dp)
                .let { if (round) it.clip(CircleShape) else it.clip(RoundedCornerShape(12.dp)) }
                .shimmer()
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
    }
}

// ── Track rows ───────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    item: FfiSearchResult,
    isPlaying: Boolean,
    showArtwork: Boolean = true,
    number: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null && !showArtwork) {
            Text(
                if (isPlaying) "▶" else number.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
        }
        if (showArtwork) {
            AsyncImage(
                model = item.thumbnails.firstOrNull()?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.artists.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(18.dp)
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            val d = item.duration
            if (!d.isNullOrBlank()) {
                Text(
                    d,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

// ── Play / Shuffle actions ───────────────────────────────────────────────────
@Composable
fun ActionPill(text: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (filled) {
        Button(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry, shape = CircleShape) { Text("Try again") }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ── Infinite scroll ──────────────────────────────────────────────────────────

/**
 * Triggers [onLoadMore] when the end of a lazy list approaches.
 * Attach to the same LazyListState used by the list; guard repeated calls in the VM.
 */
@Composable
fun InfiniteScrollHandler(
    listState: LazyListState,
    itemCount: Int,
    enabled: Boolean,
    threshold: Int = 6,
    onLoadMore: () -> Unit
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCount by rememberUpdatedState(itemCount)
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastIdx >= 0 && lastIdx >= (currentCount - threshold).coerceAtLeast(0)
        }.collect { nearEnd ->
            if (nearEnd && currentEnabled) currentLoadMore()
        }
    }
}

/** Slim centered spinner shown at the bottom of a paginating list. */
@Composable
fun LoadingFooter(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
    }
}
