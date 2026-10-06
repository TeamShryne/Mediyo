package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.mediyo.FfiComment
import com.teamshryne.mediyo.data.mediyo.FfiCommentsPage
import com.teamshryne.mediyo.data.mediyo.FfiCommentSortFilter

interface CommentRepository {
    suspend fun token(videoId: String): String?
    suspend fun page(token: String): FfiCommentsPage
    suspend fun nextPage(token: String): FfiCommentsPage
    /** Sort/filter switch: replaces the list (append tokens must not be used here). */
    suspend fun reload(token: String): FfiCommentsPage
    suspend fun replies(token: String): FfiCommentsPage
}

data class CommentThread(
    val count: String?,
    val comments: List<FfiComment>,
    val continuation: String?,
    val sortFilters: List<FfiCommentSortFilter>
)
