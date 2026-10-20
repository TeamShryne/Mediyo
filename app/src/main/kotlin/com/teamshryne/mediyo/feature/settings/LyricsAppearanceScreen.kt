package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.lyrics.LyricsFontWeight
import com.teamshryne.mediyo.data.lyrics.LyricsStyle
import com.teamshryne.mediyo.data.lyrics.LyricsStylePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * How the lyrics are drawn — size, weight and line spacing.
 *
 * Lives under Appearance (`settings/appearance/lyrics`); the *content* side
 * (which source wins) stays in `settings/lyrics`.
 */
@HiltViewModel
class LyricsAppearanceVm @Inject constructor(
    private val stylePrefs: LyricsStylePrefs
) : ViewModel() {
    val styleFlow = stylePrefs.styleFlow

    // Sliders write on every drag frame — debounce so a drag burst doesn't
    // thrash DataStore.
    private var sizeJob: Job? = null
    private var spacingJob: Job? = null

    fun setFontSize(sp: Float) {
        sizeJob?.cancel()
        sizeJob = viewModelScope.launch {
            delay(300)
            stylePrefs.setFontSize(sp)
        }
    }

    fun setWeight(weight: LyricsFontWeight) {
        viewModelScope.launch { stylePrefs.setWeight(weight) }
    }

    fun setLineSpacing(dp: Float) {
        spacingJob?.cancel()
        spacingJob = viewModelScope.launch {
            delay(300)
            stylePrefs.setLineSpacing(dp)
        }
    }

    fun resetStyle() {
        sizeJob?.cancel()
        spacingJob?.cancel()
        viewModelScope.launch { stylePrefs.reset() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAppearanceScreen(
    nav: androidx.navigation.NavController? = null,
    vm: LyricsAppearanceVm = hiltViewModel()
) {
    val style by vm.styleFlow.collectAsState(initial = LyricsStyle())

    Scaffold(
        // Zero insets: this Scaffold is nested inside the outer app Scaffold,
        // whose padding already clears the status bar. Defaults would stack a
        // second status-bar height above the TopAppBar (the visible gap).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Lyrics appearance") },
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
            item(key = "preview") { LyricsStylePreview(style) }

            item(key = "size") {
                LyricsSlider(
                    label = "Text size",
                    valueText = "${style.fontSizeSp.roundToInt()}sp",
                    value = style.fontSizeSp,
                    range = LyricsStyle.MIN_FONT_SIZE_SP..LyricsStyle.MAX_FONT_SIZE_SP,
                    onChange = { vm.setFontSize(it) }
                )
            }

            item(key = "weight_header") {
                Text(
                    "Font weight",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item(key = "weight_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LyricsFontWeight.entries, key = { it.id }) { w ->
                        FilterChip(
                            selected = style.weight == w,
                            onClick = { vm.setWeight(w) },
                            label = { Text(w.label, fontWeight = FontWeight(w.weightValue)) }
                        )
                    }
                }
            }

            item(key = "spacing") {
                LyricsSlider(
                    label = "Line spacing",
                    valueText = "${style.lineSpacingDp.roundToInt()}dp",
                    value = style.lineSpacingDp,
                    range = LyricsStyle.MIN_LINE_SPACING_DP..LyricsStyle.MAX_LINE_SPACING_DP,
                    onChange = { vm.setLineSpacing(it) }
                )
            }

            item(key = "style_reset") {
                OutlinedButton(
                    onClick = { vm.resetStyle() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset appearance")
                }
            }
        }
    }
}

/**
 * Live preview of the lyric look: the active line in full white, the next
 * line dimmed, with the chosen gap between them.
 */
@Composable
private fun LyricsStylePreview(style: LyricsStyle) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(style.lineSpacingDp.dp)
        ) {
            Text(
                text = "Nobody can equal me, I know",
                fontSize = style.fontSizeSp.sp,
                fontWeight = FontWeight(style.weight.weightValue),
                lineHeight = (style.fontSizeSp * 1.22f).sp,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Text(
                text = "I'm gonna sip on this drink when I'm",
                fontSize = (style.fontSizeSp * 0.86f).sp,
                fontWeight = FontWeight(style.weight.weightValue),
                lineHeight = (style.fontSizeSp * 1.22f * 0.86f).sp,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun LyricsSlider(
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
