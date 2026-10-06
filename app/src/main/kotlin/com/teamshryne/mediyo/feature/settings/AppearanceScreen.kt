package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.data.appearance.AppearancePrefs
import com.teamshryne.mediyo.data.appearance.PlayerBgStyle
import com.teamshryne.mediyo.data.appearance.TabStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceVm @Inject constructor(
    private val prefs: AppearancePrefs
) : ViewModel() {
    val style: StateFlow<TabStyle> = prefs.tabStyleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TabStyle.Docked)

    fun select(style: TabStyle) {
        viewModelScope.launch { prefs.setTabStyle(style) }
    }

    val bgStyle: StateFlow<PlayerBgStyle> = prefs.bgStyleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerBgStyle.Gradient)
    // Tunables are instant in-memory state (sliders stay glued to the
    // finger); persistence is debounced so fast drags don't thrash DataStore.
    private val _bgBlur = MutableStateFlow(22f)
    val bgBlur: StateFlow<Float> = _bgBlur
    private val _bgDim = MutableStateFlow(0.35f)
    val bgDim: StateFlow<Float> = _bgDim
    private val _bgDepth = MutableStateFlow(0.75f)
    val bgDepth: StateFlow<Float> = _bgDepth
    val bgTint: StateFlow<Boolean> = prefs.bgTintFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            _bgBlur.value = prefs.bgBlurFlow.first()
            _bgDim.value = prefs.bgDimFlow.first()
            _bgDepth.value = prefs.bgDepthFlow.first()
        }
    }

    fun selectBg(style: PlayerBgStyle) {
        viewModelScope.launch { prefs.setBgStyle(style) }
    }
    private var blurJob: Job? = null
    private var dimJob: Job? = null
    private var depthJob: Job? = null
    fun setBlur(dp: Float) {
        _bgBlur.value = dp
        blurJob?.cancel()
        blurJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            prefs.setBgBlur(dp)
        }
    }
    fun setDim(dim: Float) {
        _bgDim.value = dim
        dimJob?.cancel()
        dimJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            prefs.setBgDim(dim)
        }
    }
    fun setDepth(depth: Float) {
        _bgDepth.value = depth
        depthJob?.cancel()
        depthJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            prefs.setBgDepth(depth)
        }
    }
    fun setTint(tint: Boolean) {
        viewModelScope.launch { prefs.setBgTint(tint) }
    }
}

/** Hub: tab bar style + player background, each opening its own screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    nav: androidx.navigation.NavController? = null,
    vm: AppearanceVm = hiltViewModel()
) {
    val tab by vm.style.collectAsState()
    val bg by vm.bgStyle.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Appearance") },
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
                    Column {
                        AppearanceHubRow(
                            icon = Icons.Filled.Dashboard,
                            title = "Tab bar style",
                            subtitle = tab.title,
                            onClick = { nav?.navigate("settings/appearance/tabs") }
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        AppearanceHubRow(
                            icon = Icons.Filled.BlurOn,
                            title = "Player background",
                            subtitle = bg.title,
                            onClick = { nav?.navigate("settings/appearance/player") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceHubRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
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
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}
