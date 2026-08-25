package com.teamshryne.mediyo.feature.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
                // keep sortFilters as is, update selected
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
                // dedupe by content+author for safety (no stable id exposed besides content)
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
                val page = repo.nextPage(c)
                replies = replies + page.comments
                continuation = page.continuation
            } catch (_: Throwable) { continuation = null } finally { loadingMore = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    videoId: String,
    onDismiss: () -> Unit,
    vm: CommentsVm = hiltViewModel()
) {
    LaunchedEffect(videoId) { vm.load(videoId) }
    val listState = rememberLazyListState()
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = Modifier.fillMaxHeight(0.92f)) {
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(vm.comments.size, key = { i -> vm.comments[i].let { it.author + it.content.hashCode() + i } }) { i ->
                            val c = vm.comments[i]
                            CommentRow(comment = c, vm = vm)
                        }
                        item { LoadingFooter(vm.loadingMore) }
                    }
                    InfiniteScrollHandler(listState = listState, itemCount = vm.comments.size + 1, enabled = vm.continuation != null && !vm.loading && !vm.loadingMore) { vm.loadMore() }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: FfiComment, vm: CommentsVm) {
    var expanded by remember { mutableStateOf(false) }
    val hasReplies = comment.repliesContinuation != null || (comment.replyCount != null && comment.replyCount != "0")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(comment.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(comment.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    comment.likeCount?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (hasReplies) {
                        Text(
                            if (expanded) "Hide replies" else "${comment.replyCount ?: "Replies"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                expanded = !expanded
                            }
                        )
                    }
                }
                if (expanded && comment.repliesContinuation != null) {
                    RepliesSection(token = comment.repliesContinuation!!)
                }
            }
        }
    }
}

@Composable
private fun RepliesSection(token: String, vm: RepliesVm = hiltViewModel()) {
    LaunchedEffect(token) { vm.load(token) }
    Column(Modifier.padding(top = 8.dp).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            vm.loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            vm.error != null -> Text(vm.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> {
                vm.replies.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 12.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Text(r.author.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(r.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(r.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(r.content, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (vm.continuation != null) {
                    TextButton(onClick = { vm.loadMore() }, modifier = Modifier.padding(start = 12.dp)) {
                        if (vm.loadingMore) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Show more replies")
                    }
                }
            }
        }
    }
}


