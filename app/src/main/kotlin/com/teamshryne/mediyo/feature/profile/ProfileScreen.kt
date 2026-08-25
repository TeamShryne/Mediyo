package com.teamshryne.mediyo.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                // Load visitorData via bridge (ensures platform-owned caching)
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
                        // If account fetch fails, fallback to anonymous display but keep auth state
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

    LaunchedEffect(Unit) { vm.load() }

    val dominant = rememberDominantColors(vm.photo)
    val displayName = when {
        isLoggedIn && vm.name.isNotBlank() -> vm.name
        isLoggedIn -> "Account"
        else -> "Anonymous user"
    }
    val displayHandle = when {
        isLoggedIn -> vm.handle ?: authState.handle.ifEmpty { null } ?: "Signed in"
        else -> "Local visitor • Not signed in"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            vm.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            vm.error != null && !isLoggedIn && vm.visitorData.isEmpty() -> ErrorState(vm.error ?: "Failed to load") { vm.load() }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Hero header ──
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(immersiveBrush(dominant))
                            .padding(bottom = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = "Avatar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                displayHandle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (isLoggedIn) "Signed in" else "Anonymous") },
                                    leadingIcon = {
                                        Icon(
                                            if (isLoggedIn) Icons.Filled.VerifiedUser else Icons.Filled.PersonOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                                if (isLoggedIn) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("YouTube Music") },
                                        leadingIcon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp)) }
                                    )
                                } else {
                                    AssistChip(
                                        onClick = { showVisitorDialog = true },
                                        label = { Text("Local") },
                                        leadingIcon = { Icon(Icons.Filled.PhoneAndroid, null, Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Visitor Data (always visible) ──
                item {
                    SectionCard(
                        icon = Icons.Filled.Fingerprint,
                        title = "Visitor identity",
                        subtitle = if (isLoggedIn) "Your signed-in visitor is derived from your Google account" else "Used for recommendations and pagination. Stored locally."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // VisitorData field
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Visitor data",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            vm.visitorData.ifEmpty { "— not generated yet —" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = if (showVisitorDialog) 10 else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (vm.pageId != null) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Page ID: ${vm.pageId}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        copyToClipboard(context, vm.visitorData)
                                        scope.launch { snackbarHostState.showSnackbar("Visitor data copied") }
                                    }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showVisitorDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("View")
                                }
                                FilledTonalButton(
                                    onClick = { vm.rotateVisitor { scope.launch { snackbarHostState.showSnackbar("Visitor rotated") } } },
                                    enabled = !vm.rotating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (vm.rotating) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rotate")
                                }
                            }
                            Text(
                                "Rotating creates a fresh visitor identity and saves it locally. Your recommendations and continuations will use the new identity. No server data is deleted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Account actions ──
                item {
                    SectionCard(
                        icon = Icons.Filled.AccountCircle,
                        title = if (isLoggedIn) "Account" else "Sign in",
                        subtitle = if (isLoggedIn) "Manage your YouTube Music account" else "Sign in to sync library, history and personal mixes"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isLoggedIn) {
                                ListItemCard(
                                    icon = Icons.Filled.Person,
                                    title = vm.name.ifEmpty { "Account" },
                                    subtitle = vm.handle ?: authState.handle ?: "—",
                                    onClick = {}
                                )
                                ListItemCard(
                                    icon = Icons.Filled.Cookie,
                                    title = "Cookies",
                                    subtitle = if (authState.cookies.isNotEmpty()) "${authState.cookies.length} chars • SAPISID present" else "No cookies",
                                    onClick = {}
                                )
                                FilledButtonWithIcon(
                                    icon = Icons.AutoMirrored.Filled.Logout,
                                    text = "Sign out",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    onClick = {
                                        vm.clearAuth()
                                        scope.launch { snackbarHostState.showSnackbar("Signed out") }
                                    }
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text("Anonymous mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("You’re browsing as a local visitor. Sign in to sync your library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                FilledButtonWithIcon(
                                    icon = Icons.Filled.Login,
                                    text = "Sign in with YouTube Music",
                                    onClick = {
                                        scope.launch { snackbarHostState.showSnackbar("Sign-in coming soon — WebView auth in next update") }
                                    }
                                )
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, vm.visitorData)
                                        scope.launch { snackbarHostState.showSnackbar("Visitor data copied — keep it to restore session") }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy visitor data")
                                }
                            }
                        }
                    }
                }

                // ── App info ──
                item {
                    SectionCard(
                        icon = Icons.Filled.Info,
                        title = "About",
                        subtitle = "How visitor identity works"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BulletText("Visitor data is a YouTube-generated ID that personalizes home, search and continuations.")
                            BulletText("For anonymous users it’s created once via POST /visitor_id and saved in DataStore — tap Rotate to replace it.")
                            BulletText("For signed-in users it comes from your Google cookies (SAPISID) and is managed by the platform, not the Rust library.")
                            BulletText("Library is stateless: it never saves visitor data itself, platform (MediyoBridge) owns it.")
                        }
                    }
                }

                item {
                    Text(
                        "Mediyo • Native Kotlin • mediyo-core (Rust) • NewPipe streams",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
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
                        Text(vm.visitorData.ifEmpty { "No visitor data yet" }, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            content()
        }
    }
}

@Composable
private fun ListItemCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FilledButtonWithIcon(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun BulletText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("visitorData", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
