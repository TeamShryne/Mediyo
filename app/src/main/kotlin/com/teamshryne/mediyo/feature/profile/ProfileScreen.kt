package com.teamshryne.mediyo.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.immersiveBrush
import com.teamshryne.mediyo.core.design.rememberDominantColors
import com.teamshryne.mediyo.data.auth.AuthRepository
import com.teamshryne.mediyo.data.auth.AuthState
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileVm @Inject constructor(
    private val bridge: MediyoBridge,
    private val auth: AuthRepository
) : ViewModel() {
    val authFlow = auth.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState())

    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var handle by mutableStateOf<String?>(null)
    var photo by mutableStateOf<String?>(null)
    var visitorData by mutableStateOf("")
    var pageId by mutableStateOf<String?>(null)
    var rotating by mutableStateOf(false)

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val a = auth.flow.first()
                val vd = try { bridge.currentVisitorData() } catch (_: Throwable) { a.visitorData }
                visitorData = vd
                pageId = a.pageId.ifEmpty { null }
                if (a.isLoggedIn) {
                    try {
                        val acc = bridge.account()
                        name = acc.name
                        handle = acc.handle
                        photo = acc.photoUrl
                    } catch (e: Throwable) {
                        name = ""
                        error = e.message
                    }
                } else {
                    name = "Anonymous"
                    handle = null
                    photo = null
                }
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun rotateVisitor(onDone: (String) -> Unit = {}) {
        if (rotating) return
        rotating = true
        viewModelScope.launch {
            try {
                val newVd = bridge.rotateVisitorData()
                visitorData = newVd
                onDone(newVd)
            } catch (e: Throwable) {
                error = e.message
            } finally {
                rotating = false
            }
        }
    }

    fun clearAuth() {
        viewModelScope.launch {
            auth.clear()
            bridge.clearAnonCache()
            load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: androidx.navigation.NavController? = null, vm: ProfileVm = hiltViewModel()) {
    val authState by vm.authFlow.collectAsState()
    val isLoggedIn = authState.isLoggedIn
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showVisitorDialog by remember { mutableStateOf(false) }

    BackHandler { nav?.popBackStack() }
    LaunchedEffect(Unit) { vm.load() }

    val dominant = rememberDominantColors(vm.photo)
    val displayName = if (isLoggedIn && vm.name.isNotBlank()) vm.name else "Anonymous user"
    val displaySubtitle = if (isLoggedIn) vm.handle?.takeIf { it.isNotBlank() } ?: "Signed in" else "Local visitor"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when {
            vm.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            vm.error != null && !isLoggedIn && vm.visitorData.isEmpty() -> ErrorState(vm.error ?: "Failed to load") { vm.load() }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header with status bar handling ──
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(immersiveBrush(dominant))
                            .statusBarsPadding()
                            .padding(bottom = 20.dp)
                    ) {
                        // Top bar inside header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { nav?.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.size(48.dp))
                        }
                        // Avatar + name
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (vm.photo != null && isLoggedIn) {
                                    AsyncImage(
                                        model = vm.photo,
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(displaySubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (isLoggedIn) Icons.Filled.VerifiedUser else Icons.Filled.PersonOff,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (isLoggedIn) "YouTube Music" else "Anonymous",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Visitor ──
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Fingerprint, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Visitor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Powers recommendations & pagination", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        vm.visitorData.ifEmpty { "Not generated" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        copyToClipboard(context, vm.visitorData)
                                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showVisitorDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Icon(Icons.Filled.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("View") }
                                FilledTonalButton(
                                    onClick = { vm.rotateVisitor { scope.launch { snackbarHostState.showSnackbar("Visitor rotated") } } },
                                    enabled = !vm.rotating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (vm.rotating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rotate")
                                }
                            }
                            if (vm.pageId != null) Text("Page ID: ${vm.pageId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Account ──
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.AccountCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (isLoggedIn) "Account" else "Sign in", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(if (isLoggedIn) "YouTube Music account" else "Sync library & history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                            if (isLoggedIn) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(vm.name.ifEmpty { "Account" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(vm.handle ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Button(
                                    onClick = {
                                        vm.clearAuth()
                                        scope.launch { snackbarHostState.showSnackbar("Signed out") }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) { Icon(Icons.Filled.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sign out") }
                            } else {
                                Text("Browse as local visitor. Sign in to sync your library, history and mixes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(
                                    onClick = { scope.launch { snackbarHostState.showSnackbar("Sign-in coming soon") } },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Icon(Icons.Filled.Login, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sign in") }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Visitor is created once via POST /visitor_id and saved locally. Rotate replaces it. Library is stateless — platform owns it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        if (showVisitorDialog) {
            AlertDialog(
                onDismissRequest = { showVisitorDialog = false },
                title = { Text("Visitor data") },
                text = {
                    Column {
                        Text(vm.visitorData.ifEmpty { "No visitor data" }, style = MaterialTheme.typography.bodySmall)
                        if (vm.pageId != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Page ID: ${vm.pageId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        copyToClipboard(context, vm.visitorData)
                        showVisitorDialog = false
                    }) { Text("Copy") }
                },
                dismissButton = { TextButton(onClick = { showVisitorDialog = false }) { Text("Close") } }
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("visitorData", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
