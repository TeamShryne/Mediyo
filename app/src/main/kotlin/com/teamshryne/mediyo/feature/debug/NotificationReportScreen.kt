package com.teamshryne.mediyo.feature.debug

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.teamshryne.mediyo.core.design.EaseOutExpo
import com.teamshryne.mediyo.core.design.LocalOverlayBottom
import java.io.File
import kotlin.math.roundToInt

/**
 * Debug-only diagnostic for the one bug that cannot be reproduced from a
 * desk: "the playback notification does not appear on my phone".
 *
 * The screen runs the checks in [NotificationReportRunner], shows each one as
 * it lands, and leaves a text file behind. The file is the deliverable — a
 * user can share it and the answer is in it, instead of a thread of "what does
 * your settings screen say".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationReportScreen(
    nav: NavController? = null,
    vm: NotificationReportViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // The report is why the screen exists; opening it is the request to run it.
    LaunchedEffect(Unit) { vm.run() }

    Scaffold(
        // Nested inside the app shell's Scaffold, which already clears the
        // status bar — same reason as the other settings screens.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Playback notification") },
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
                top = 8.dp,
                bottom = LocalOverlayBottom.current + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ReportHero(state) }

            item {
                Text(
                    "Checks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            items(state.checks, key = { it.id }) { result -> CheckRow(result) }

            if (state.running) {
                item { PendingRow(state.runningTitle ?: "Preparing") }
            }

            state.failure?.let { message ->
                item { FailureCard(message) }
            }

            item {
                Actions(
                    state = state,
                    onRun = { vm.run() },
                    onShare = { state.report?.let { shareReport(context, it) } }
                )
            }

            item {
                Text(
                    "Debug builds only. The report is written on this device and leaves it only when you share it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun ReportHero(state: NotificationReportViewModel.UiState) {
    val done = state.checks.size
    val progress by animateFloatAsState(
        targetValue = if (state.total == 0) 0f else (done.toFloat() / state.total).coerceIn(0f, 1f),
        animationSpec = tween(360, easing = EaseOutExpo),
        label = "reportProgress"
    )
    val worst = state.checks.worstStatus()
    val accent = when {
        state.running -> MaterialTheme.colorScheme.primary
        worst == CheckStatus.FAIL -> MaterialTheme.colorScheme.error
        worst == CheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
        worst == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(
                    running = state.running,
                    status = worst,
                    progress = progress,
                    accent = accent
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Playback notification",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        heroSummary(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.running) "Running check ${(done + 1).coerceAtMost(state.total)} of ${state.total}"
                    else "$done of ${state.total} checks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.report?.let {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun heroSummary(state: NotificationReportViewModel.UiState): String = when {
    state.failure != null -> "The report could not be finished"
    state.running -> state.runningTitle?.let { "Checking: $it" } ?: "Starting the checks"
    state.checks.isEmpty() -> "Starting the checks"
    state.checks.any { it.status == CheckStatus.FAIL } ->
        "Something on this device stops the media card — share the report"
    state.checks.any { it.status == CheckStatus.WARN } ->
        "Everything essential is in place, with warnings worth reading"
    else -> "All checks passed"
}

/**
 * The one piece of motion on the screen that carries meaning: it spins while
 * the checks run and settles into the result, so "is it still working?" never
 * needs a second glance.
 */
@Composable
private fun StatusOrb(
    running: Boolean,
    status: CheckStatus?,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "spin"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val scale = if (running) breathe else 1f
            val scaledSize = Size(arcSize.width * scale, arcSize.height * scale)
            val scaledTopLeft = Offset(
                (size.width - scaledSize.width) / 2f,
                (size.height - scaledSize.height) / 2f
            )
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = scaledTopLeft,
                size = scaledSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = if (running) spin else -90f,
                sweepAngle = if (running) 86f else 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = scaledTopLeft,
                size = scaledSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        if (running) {
            Text(
                "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        } else {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 320f), initialScale = 0.4f) +
                    fadeIn(tween(180))
            ) {
                Icon(
                    imageVector = status.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

// ── Rows ─────────────────────────────────────────────────────────────────────

@Composable
private fun CheckRow(result: CheckResult) {
    var visible by remember(result.id) { mutableStateOf(false) }
    var expanded by remember(result.id) { mutableStateOf(false) }
    LaunchedEffect(result.id) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = EaseOutExpo)) +
            slideInVertically(tween(220, easing = EaseOutExpo)) { it / 3 }
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable(enabled = result.details.isNotEmpty()) { expanded = !expanded }
                .animateContentSize(tween(180, easing = EaseOutExpo)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusBadge(result.status)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        result.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Spacer(Modifier.size(6.dp))
                            result.details.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
                if (result.details.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: CheckStatus) {
    val tint = status.tint()
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(status.icon(), contentDescription = status.name, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PendingRow(title: String) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Running…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FailureCard(message: String) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ── Actions ──────────────────────────────────────────────────────────────────

@Composable
private fun Actions(
    state: NotificationReportViewModel.UiState,
    onRun: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = onRun,
            enabled = !state.running,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.checks.isEmpty()) "Run checks" else "Run again")
        }
        Button(
            onClick = onShare,
            enabled = state.report != null && !state.running,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share report")
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun shareReport(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mediyo playback notification report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share report"))
    } catch (_: Throwable) {
        // No share target or the provider is misconfigured — the file is
        // still on disk and its name is on the screen.
    }
}

private fun List<CheckResult>.worstStatus(): CheckStatus? = when {
    any { it.status == CheckStatus.FAIL } -> CheckStatus.FAIL
    any { it.status == CheckStatus.WARN } -> CheckStatus.WARN
    isEmpty() -> null
    else -> CheckStatus.PASS
}

private fun CheckStatus?.icon(): ImageVector = when (this) {
    CheckStatus.PASS -> Icons.Filled.Check
    CheckStatus.WARN -> Icons.Filled.Warning
    CheckStatus.FAIL -> Icons.Filled.ErrorOutline
    CheckStatus.INFO, null -> Icons.Filled.Info
}

@Composable
private fun CheckStatus?.tint() = when (this) {
    CheckStatus.PASS -> MaterialTheme.colorScheme.primary
    CheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
    CheckStatus.FAIL -> MaterialTheme.colorScheme.error
    CheckStatus.INFO, null -> MaterialTheme.colorScheme.onSurfaceVariant
}
