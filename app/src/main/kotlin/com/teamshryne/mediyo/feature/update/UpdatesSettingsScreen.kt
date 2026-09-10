package com.teamshryne.mediyo.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.teamshryne.mediyo.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    nav: NavController? = null,
    vm: UpdateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App updates") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Installed version", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Updates come from GitHub releases and are verified before installing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { vm.manualCheck() },
                        enabled = state !is UpdateViewModel.State.Checking,
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Check for updates")
                    }
                    when (val s = state) {
                        is UpdateViewModel.State.Checking -> CircularProgressIndicator()
                        is UpdateViewModel.State.UpToDate -> StatusLine(
                            Icons.Filled.CheckCircle, "You're on the latest version",
                            MaterialTheme.colorScheme.primary
                        )
                        is UpdateViewModel.State.Error -> StatusLine(
                            Icons.Filled.ErrorOutline, s.message,
                            MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                }
            }
        }
    }

    if (state is UpdateViewModel.State.Available) {
        UpdateDialog(vm)
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
