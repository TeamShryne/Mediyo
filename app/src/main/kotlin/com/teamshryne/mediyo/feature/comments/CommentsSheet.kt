package com.teamshryne.mediyo.feature.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.mediyo.core.design.ErrorState
import com.teamshryne.mediyo.core.design.InfiniteScrollHandler
import com.teamshryne.mediyo.core.design.LoadingFooter
import com.teamshryne.mediyo.domain.repository.CommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uniffi.mediyo_ffi.FfiComment
import uniffi.mediyo_ffi.FfiCommentSortFilter
import javax.inject.Inject

@HiltViewModel
class CommentsVm @Inject constructor(private val repo: CommentRepository) : ViewModel() {
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var count by mutableStateOf<String?>(null)
    var comments by mutableStateOf<List<FfiComment>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var sortFilters by mutableStateOf<List<FfiCommentSortFilter>>(emptyList())
    var selectedSort by mutableStateOf<String?>(null)
    var token by mutableStateOf<String?>(null)

    fun load(videoId: String) {
        loading = true; error = null; comments = emptyList(); continuation = null; sortFilters = emptyList()
        viewModelScope.launch {
            try {
                val t = repo.token(videoId)
                token = t
                if (t == null) {
                    error = "Comments unavailable"
                    return@launch
                }
                val page = repo.page(t)
                count = page.count
                comments = page.comments
                continuation = page.continuation
                sortFilters = page.sortFilters
                selectedSort = sortFilters.firstOrNull { it.selected }?.title ?: sortFilters.firstOrNull()?.title
            } catch (e: Throwable) {
                error = e.message ?: "Failed to load comments"
            } finally { loading = false }
        }
    }

    fun switchSort(filter: FfiCommentSortFilter) {
        selectedSort = filter.title
        loading = true; error = null
        viewModelScope.launch {
            try {
                val page = repo.page(filter.continuationToken)
                count = page.count
                comments = page.comments
                continuation = page.continuation
            } catch (e: Throwable) {
                error = e.message
            } finally { loading = false }
        }
    }

    fun loadMore() {
        val c = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val page = repo.nextPage(c)
                val seen = comments.map { it.content + it.author }.toHashSet()
                val fresh = page.comments.filter { seen.add(it.content + it.author) }
                comments = comments + fresh
                continuation = if (fresh.isEmpty()) null else page.continuation
            } catch (_: Throwable) {
                continuation = null
            } finally { loadingMore = false }
        }
    }
}

@HiltViewModel
class RepliesVm @Inject constructor(private val repo: CommentRepository) : ViewModel() {
    var replies by mutableStateOf<List<FfiComment>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun load(token: String) {
        if (loading) return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val page = repo.replies(token)
                replies = page.comments
                continuation = page.continuation
            } catch (e: Throwable) {
                error = e.message
            } finally { loading = false }
        }
    }
    fun loadMore() {
        val c = continuation ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                // replies pagination uses same endpoint as replies (parse_reply_continuation)
                val page = repo.replies(c)
                replies = replies + page.comments
                continuation = page.continuation
            } catch (_: Throwable) {
                // fallback to generic next
                try {
                    val page = repo.nextPage(c)
                    replies = replies + page.comments
                    continuation = page.continuation
                } catch (_: Throwable) { continuation = null }
            } finally { loadingMore = false }
        }
    }
    fun reset() { replies = emptyList(); continuation = null; loading = false; loadingMore = false; error = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    videoId: String,
    onDismiss: () -> Unit,
    vm: CommentsVm = hiltViewModel()
) {
    LaunchedEffect(videoId) { vm.load(videoId) }
    var selectedThread by remember { mutableStateOf<FfiComment?>(null) }

    val listState = rememberLazyListState()
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = Modifier.fillMaxHeight(0.92f)) {
        if (selectedThread != null) {
            val thread = selectedThread!!
            CommentThreadView(comment = thread, onBack = { selectedThread = null })
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Comments", style = MaterialTheme.typography.titleLarge)
                        vm.count?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
                }
                if (vm.sortFilters.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.sortFilters.forEach { f ->
                            FilterChip(
                                selected = vm.selectedSort == f.title,
                                onClick = { if (vm.selectedSort != f.title) vm.switchSort(f) },
                                label = { Text(f.title) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
                when {
                    vm.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    vm.error != null -> ErrorState(vm.error ?: "Failed") { vm.load(videoId) }
                    vm.comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Comment, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No comments yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(vm.comments.size, key = { i -> vm.comments[i].let { it.author + it.content.hashCode() + i } }) { i ->
                                val c = vm.comments[i]
                                CommentRow(
                                    comment = c,
                                    onOpenThread = { selectedThread = c },
                                    onRepliesClick = { selectedThread = c }
                                )
                            }
                            item { LoadingFooter(vm.loadingMore) }
                        }
                        InfiniteScrollHandler(listState = listState, itemCount = vm.comments.size + 1, enabled = vm.continuation != null && !vm.loading && !vm.loadingMore) { vm.loadMore() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: FfiComment,
    onOpenThread: () -> Unit,
    onRepliesClick: () -> Unit = onOpenThread
) {
    var expandedContent by remember { mutableStateOf(false) }
    val isLong = comment.content.length > 180 || comment.content.count { it == '\n' } > 3
    val hasReplies = comment.repliesContinuation != null || (comment.replyCount != null && comment.replyCount != "0" && comment.replyCount != "0 replies")

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenThread)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Text(comment.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // space between username and message — 6.dp via Column spacing already, plus explicit Spacer for visual breathing
                Spacer(Modifier.height(2.dp))
                Text(
                    comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expandedContent) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLong) {
                    Text(
                        if (expandedContent) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expandedContent = !expandedContent }.padding(top = 2.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    comment.likeCount?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (hasReplies) {
                        Text(
                            "${comment.replyCount ?: "Replies"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRepliesClick)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentThreadView(
    comment: FfiComment,
    onBack: () -> Unit,
    vm: RepliesVm = hiltViewModel()
) {
    val token = comment.repliesContinuation
    val listState = rememberLazyListState()

    LaunchedEffect(token) {
        if (token != null) vm.load(token) else vm.reset()
    }
    // if no continuation but comment has no replies token, we still show thread with zero replies
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Thread", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${comment.replyCount ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                // original comment fully expanded
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(comment.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                    comment.likeCount?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            when {
                token == null -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No replies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                vm.loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) } }
                vm.error != null -> item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text(vm.error ?: "Failed", color = MaterialTheme.colorScheme.error) } }
                vm.replies.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No replies yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    items(vm.replies.size, key = { i -> vm.replies[i].let { it.author + it.content.hashCode() + i } }) { i ->
                        val r = vm.replies[i]
                        var expanded by remember { mutableStateOf(false) }
                        val isLongR = r.content.length > 180
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Text(r.author.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Text(r.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(r.content, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                                if (isLongR) {
                                    Text(
                                        if (expanded) "Show less" else "Show more",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { expanded = !expanded }
                                    )
                                }
                                r.likeCount?.let {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                        Icon(Icons.Filled.ThumbUp, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (vm.continuation != null || vm.loadingMore) {
                        item { LoadingFooter(vm.loadingMore) }
                    }
                }
            }
        }
        InfiniteScrollHandler(listState = listState, itemCount = vm.replies.size + 3, enabled = vm.continuation != null && !vm.loading && !vm.loadingMore) { vm.loadMore() }
    }
}
