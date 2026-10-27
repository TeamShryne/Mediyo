package com.teamshryne.mediyo.feature.settings

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.appearance.PlayerOpenStyle
import kotlinx.coroutines.delay

/**
 * Mini player → full player motion picker.
 *
 * The preview is not a video: it runs the same 0..1 progress value the app
 * uses while the mini player is dragged, so what the user sees here is what
 * the finger will do. Picking a row restarts the loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMotionScreen(
    nav: androidx.navigation.NavController? = null,
    vm: AppearanceVm = hiltViewModel()
) {
    val selected by vm.playerOpenStyle.collectAsState()
    var progress by remember { mutableStateOf(0f) }

    // Loop open → hold → close → hold, restarting whenever the choice changes.
    LaunchedEffect(selected) {
        while (true) {
            animate(progress, 1f, animationSpec = tween(460, easing = EaseOutExpo)) { v, _ -> progress = v }
            delay(700)
            animate(progress, 0f, animationSpec = tween(340, easing = EaseIn)) { v, _ -> progress = v }
            delay(420)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Player animation") },
                navigationIcon = {
                    IconButton(onClick = { nav?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "preview") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MotionPreview(style = selected, progress = progress)
                }
            }

            item(key = "styles_header") { SectionHeader("Motion") }

            item(key = "styles") {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column {
                        PlayerOpenStyle.entries.forEachIndexed { i, style ->
                            MotionRow(
                                style = style,
                                selected = style == selected,
                                onClick = { vm.selectPlayerOpenStyle(style) }
                            )
                            if (i < PlayerOpenStyle.entries.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }

            item(key = "hint") {
                Text(
                    "The mini player follows your finger as you drag it up, whichever motion you pick. " +
                        "Pick the one that feels right and the player opens that way too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun MotionRow(
    style: PlayerOpenStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(style.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(style.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * A miniature of the app: the player fills the frame, the mini pill sits at
 * the bottom. Both are driven by [progress] exactly like the real pair, so
 * the artwork flight can be previewed without going back to the player.
 */
@Composable
private fun MotionPreview(style: PlayerOpenStyle, progress: Float) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3A2540), Color(0xFF0C0B10))
                )
            )
    ) {
        // Size of the artwork slot in the open player.
        val heroSize = 84.dp
        val heroLeft = (maxWidth - heroSize) / 2
        // ── Full player mock ────────────────────────────────────────────────
        val p = progress.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    when (style) {
                        PlayerOpenStyle.Slide -> translationY = (1f - p) * size.height
                        PlayerOpenStyle.Fade -> alpha = p
                        PlayerOpenStyle.Scale -> {
                            alpha = p
                            val s = 0.9f + 0.1f * p
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                        }
                        PlayerOpenStyle.Artwork -> {
                            // Backdrop washes in, artwork is drawn separately.
                            alpha = 1f
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF7E3F6A), Color(0xFF120E18))
                        )
                    )
                    .graphicsLayer { if (style == PlayerOpenStyle.Artwork) alpha = p }
            )
            // Hero art slot in the open player.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .size(heroSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFCE8FB0))
                    .graphicsLayer {
                        if (style == PlayerOpenStyle.Artwork) {
                            // Hidden while the flying copy is on its way.
                            alpha = (p * 3f - 2f).coerceIn(0f, 1f)
                        }
                    }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                Box(Modifier.fillMaxWidth(0.35f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Mini pill mock ──────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - p }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF26242C),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFCE8FB0))
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                        Box(Modifier.fillMaxWidth(0.3f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                    }
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Artwork in flight (Artwork style only) ──────────────────────────
        if (style == PlayerOpenStyle.Artwork) {
            // Start on the pill's cover, land on the player's slot.
            val startX = 14.dp
            val startY = maxHeight - 8.dp - 6.dp - 26.dp
            val t = p * p * (3f - 2f * p) // ease-in-out, matches the eye better than linear
            Box(
                Modifier
                    .offset(
                        x = startX + (heroLeft - startX) * t,
                        y = startY + (26.dp - startY) * t
                    )
                    .size(26.dp + (heroSize - 26.dp) * t)
                    .graphicsLayer { alpha = if (p >= 0.995f) 0f else 1f }
                    .clip(RoundedCornerShape(6.dp + 6.dp * t))
                    .background(Color(0xFFCE8FB0))
            ) {
            }
        }
    }
}
