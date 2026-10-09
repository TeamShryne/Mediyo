package com.teamshryne.mediyo.feature.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.teamshryne.mediyo.data.equalizer.EqualizerBands
import com.teamshryne.mediyo.data.equalizer.EqualizerManager
import com.teamshryne.mediyo.data.equalizer.EqPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class EqualizerVm @Inject constructor(
    private val manager: EqualizerManager
) : ViewModel() {
    val state = manager.state

    fun setEnabled(enabled: Boolean) = manager.setEnabled(enabled)
    fun previewBand(index: Int, gainDb: Float) = manager.previewBand(index, gainDb)
    fun commitBands() = manager.commitBands()
    fun previewPreamp(preampDb: Float) = manager.previewPreamp(preampDb)
    fun commitPreamp() = manager.commitBands()
    fun applyPreset(id: String) {
        EqPresets.byId(id)?.let { manager.applyPreset(it) }
    }
    fun reset() = manager.reset()
}

private fun formatDb(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return if (r == 0f) "0 dB" else "%+.0f dB".format(Locale.US, r)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    nav: androidx.navigation.NavController? = null,
    vm: EqualizerVm = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    // 1 dB granularity over ±15 dB → 30 intervals; preamp ±12 dB → 24.
    val bandSteps = remember { ((EqualizerBands.MAX_GAIN_DB - EqualizerBands.MIN_GAIN_DB)).roundToInt() - 1 }
    val preampSteps = remember { ((EqualizerBands.MAX_PREAMP_DB - EqualizerBands.MIN_PREAMP_DB)).roundToInt() - 1 }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Equalizer") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Enabled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = state.enabled, onCheckedChange = vm::setEnabled)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            for (preset in EqPresets.ALL) {
                                FilterChip(
                                    selected = state.presetId == preset.id,
                                    onClick = { vm.applyPreset(preset.id) },
                                    label = { Text(preset.title) }
                                )
                            }
                        }
                        for (i in 0 until EqualizerBands.COUNT) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    EqualizerBands.LABELS[i],
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatDb(state.gainsDb.getOrElse(i) { 0f }),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = state.gainsDb.getOrElse(i) { 0f },
                                onValueChange = { vm.previewBand(i, it) },
                                onValueChangeFinished = { vm.commitBands() },
                                valueRange = EqualizerBands.MIN_GAIN_DB..EqualizerBands.MAX_GAIN_DB,
                                steps = bandSteps,
                                enabled = state.enabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (!state.isFlat) {
                            TextButton(
                                onClick = vm::reset,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset") }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Preamp",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatDb(state.preampDb),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = state.preampDb,
                            onValueChange = vm::previewPreamp,
                            onValueChangeFinished = { vm.commitPreamp() },
                            valueRange = EqualizerBands.MIN_PREAMP_DB..EqualizerBands.MAX_PREAMP_DB,
                            steps = preampSteps,
                            enabled = state.enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}
