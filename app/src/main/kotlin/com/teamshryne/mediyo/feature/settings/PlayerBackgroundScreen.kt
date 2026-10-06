package com.teamshryne.mediyo.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamshryne.mediyo.core.design.PlayerBgConfig
import com.teamshryne.mediyo.core.design.PlayerBackground
import com.teamshryne.mediyo.core.design.SamplePlayerColors
import com.teamshryne.mediyo.core.design.SectionHeader
import com.teamshryne.mediyo.data.appearance.PlayerBgStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBackgroundScreen(
    nav: androidx.navigation.NavController? = null,
    vm: AppearanceVm = hiltViewModel()
) {
    val style by vm.bgStyle.collectAsState()
    val blur by vm.bgBlur.collectAsState()
    val dim by vm.bgDim.collectAsState()
    val depth by vm.bgDepth.collectAsState()
    val tint by vm.bgTint.collectAsState()
    val config = PlayerBgConfig(style, blur, dim, depth, tint)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Player background") },
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
            contentPadding = PaddingValues(top = 12.dp, bottom = com.teamshryne.mediyo.core.design.LocalOverlayBottom.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "preview") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PhonePreview(config = config)
                }
            }

            item(key = "style_header") { SectionHeader("Style") }

            item(key = "style_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(PlayerBgStyle.entries, key = { it.id }) { s ->
                        BgStyleCard(
                            style = s,
                            // Preview each style with the current tune values.
                            config = config.copy(style = s),
                            selected = s == style,
                            onClick = { vm.selectBg(s) }
                        )
                    }
                }
            }

            item(key = "tune_header") { SectionHeader("Tune") }

            item(key = "tune_sliders") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (style) {
                        PlayerBgStyle.Gradient -> TuneSlider(
                            label = "Depth",
                            valueText = "${(depth * 100).roundToInt()}%",
                            value = depth, range = 0f..1f,
                            onChange = { vm.setDepth(it) }
                        )
                        PlayerBgStyle.Blur -> TuneSlider(
                            label = "Blur",
                            valueText = "${blur.roundToInt()}dp",
                            value = blur, range = 4f..36f,
                            onChange = { vm.setBlur(it) }
                        )
                        PlayerBgStyle.Solid -> Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Artwork tint", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Wash the backdrop with artwork colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = tint, onCheckedChange = { vm.setTint(it) })
                        }
                    }
                    TuneSlider(
                        label = "Shade",
                        valueText = "${(dim * 100).roundToInt()}%",
                        value = dim, range = 0f..0.8f,
                        onChange = { vm.setDim(it) }
                    )
                }
            }
        }
    }
}

/** Faux artwork: vivid gradient standing in for track art (no network). */
@Composable
private fun FauxArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    SamplePlayerColors.deep,
                    SamplePlayerColors.container,
                    Color(0xFFE2689C)
                )
            )
        )
    )
}

/** Phone-like frame with a live mock player over the real background. */
@Composable
private fun PhonePreview(config: PlayerBgConfig) {
    Box(
        Modifier.width(208.dp).aspectRatio(9f / 18.6f)
            .clip(RoundedCornerShape(38.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(38.dp))
            .padding(3.dp)
            .clip(RoundedCornerShape(35.dp))
    ) {
        AnimatedContent(
            targetState = config.style,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "bgPreview"
        ) { style ->
            PlayerBackground(
                config = config.copy(style = style),
                dominant = SamplePlayerColors,
                artwork = { FauxArtwork(Modifier.fillMaxSize()) }
            )
        }
        // Mock player chrome.
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(width = 56.dp, height = 4.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 52.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(width = 34.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(118.dp).clip(RoundedCornerShape(20.dp))
            ) {
                FauxArtwork(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(width = 104.dp, height = 9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(width = 66.dp, height = 7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White))
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(width = 72.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
        }
    }
}

@Composable
private fun BgStyleCard(
    style: PlayerBgStyle,
    config: PlayerBgConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(width = 104.dp, height = 132.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(
                    if (selected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(20.dp)
                    ) else Modifier
                )
                .clickable(onClick = onClick)
        ) {
            PlayerBackground(
                config = config,
                dominant = SamplePlayerColors,
                artwork = { FauxArtwork(Modifier.fillMaxSize()) }
            )
            if (selected) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
                    }
                }
            }
        }
        Text(
            style.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TuneSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
