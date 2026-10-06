package com.teamshryne.mediyo.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.teamshryne.mediyo.data.appearance.TabStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceVm @Inject constructor(
    private val prefs: AppearancePrefs
) : ViewModel() {
    val style: StateFlow<TabStyle> = prefs.tabStyleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TabStyle.Classic)

    fun select(style: TabStyle) {
        viewModelScope.launch { prefs.setTabStyle(style) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    nav: androidx.navigation.NavController? = null,
    vm: AppearanceVm = hiltViewModel()
) {
    val current by vm.style.collectAsState()

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
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(TabStyle.entries, key = { it.id }) { style ->
                StyleOptionCard(
                    style = style,
                    selected = style == current,
                    onClick = { vm.select(style) }
                )
            }
        }
    }
}

@Composable
private fun StyleOptionCard(style: TabStyle, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(style.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        style.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            TabStylePreview(style = style)
        }
    }
}

/** Scaled-down mock of each bar style — 4 tabs, second one active. */
@Composable
private fun TabStylePreview(style: TabStyle) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    when (style) {
        TabStyle.Classic -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(track).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(4) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(width = 40.dp, height = 22.dp).clip(CircleShape)
                            .background(if (i == 1) active.copy(alpha = 0.35f) else idle.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (i == 1) active else idle))
                    }
                    Box(Modifier.size(width = 20.dp, height = 4.dp).clip(CircleShape).background(idle.copy(alpha = 0.5f)))
                }
            }
        }
        TabStyle.Docked -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(track),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier.padding(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(if (i == 1) active else idle.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (i == 1) MaterialTheme.colorScheme.onPrimary else idle))
                    }
                }
            }
        }
        TabStyle.Minimal -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(track).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(4) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(if (i == 1) active else idle))
                    if (i == 1) Box(Modifier.size(5.dp).clip(CircleShape).background(active))
                    else Box(Modifier.size(5.dp))
                }
            }
        }
        TabStyle.Capsule -> Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(track).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(26.dp).clip(CircleShape)
                        .background(if (i == 1) active else idle.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (i == 1) MaterialTheme.colorScheme.onPrimary else idle))
                }
            }
        }
    }
}
