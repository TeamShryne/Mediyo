package com.teamshryne.mediyo.feature.settings

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.LocalOverlayBottom
import com.teamshryne.mediyo.core.design.Motion
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.appearance.ScreenMotionStyle
import kotlinx.coroutines.delay

/**
 * Motion is the one place that decides how the app moves: the switch turns
 * screen changes on or off, and the style picker chooses the shape of the
 * change. Both the transitions and the predictive back preview read the same
 * choice, so what the gesture shows is what the animation does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionScreen(
    nav: androidx.navigation.NavController? = null,
    vm: AppearanceVm = hiltViewModel()
) {
    val motion by vm.motion.collectAsState()
    val style by vm.screenStyle.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Motion") },
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
                bottom = LocalOverlayBottom.current + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Short, quick movement keeps the app feeling light. Turn it off if you prefer screens to change instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                StylePreview(style = style, lively = motion)
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    MotionSwitchRow(
                        icon = Icons.Filled.Animation,
                        title = "Lively transitions",
                        subtitle = "Screens move into place instead of snapping",
                        checked = motion,
                        onCheckedChange = vm::setMotion
                    )
                }
            }

            item { SectionHeader("Screen change") }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column {
                        ScreenMotionStyle.entries.forEachIndexed { i, entry ->
                            StyleRow(
                                style = entry,
                                selected = entry == style,
                                onClick = { vm.setScreenStyle(entry) }
                            )
                            if (i < ScreenMotionStyle.entries.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Dragging back from the edge shows the same motion as you move, so you can feel it before letting go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun MotionSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StyleRow(
    style: ScreenMotionStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(style.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                style.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * A two-card miniature of the real navigation pair: the back card is the
 * screen being left, the front card is the one arriving. Both run the same
 * timing tokens as the NavHost, and switching style restarts the loop so the
 * change is felt rather than read.
 */
@Composable
private fun StylePreview(style: ScreenMotionStyle, lively: Boolean) {
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(style, lively) {
        progress = 0f
        if (!lively) {
            progress = 1f
            return@LaunchedEffect
        }
        while (true) {
            animate(
                0f,
                1f,
                animationSpec = tween(Motion.SlideEnter, easing = EaseOutExpo)
            ) { value, _ -> progress = value }
            delay(520)
            progress = 0f
            delay(260)
        }
    }
    val p = progress
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back card: the screen being left. The arriving card is inset, so a
        // frame of this one stays visible and the preview reads as two screens
        // rather than one card appearing out of nowhere.
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Box(Modifier.fillMaxSize())
        }
        // Front card: the screen arriving, moved by the style.
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .graphicsLayer {
                    val settle = 1f - (1f - p) * (1f - p)
                    when (style) {
                        ScreenMotionStyle.Slide -> {
                            translationX = (1f - settle) * size.width * 0.18f
                            alpha = 0.35f + 0.65f * settle
                        }
                        ScreenMotionStyle.Fade -> {
                            alpha = 0.15f + 0.85f * settle
                        }
                        ScreenMotionStyle.Zoom -> {
                            alpha = 0.3f + 0.7f * settle
                            val s = 0.90f + 0.10f * settle
                            scaleX = s
                            scaleY = s
                        }
                        ScreenMotionStyle.Rise -> {
                            translationY = (1f - settle) * size.height * 0.35f
                            alpha = 0.35f + 0.65f * settle
                        }
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // A cover and two lines: enough shape for the eye to track the
                // movement, no text to read while it moves.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PreviewBar(widthFraction = 0.86f)
                        PreviewBar(widthFraction = 0.54f)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    style.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewBar(widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
    )
}
