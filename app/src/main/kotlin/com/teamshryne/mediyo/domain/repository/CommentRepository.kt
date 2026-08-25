package com.teamshryne.mediyo.domain.repository

import uniffi.mediyo_ffi.FfiComment
import uniffi.mediyo_ffi.FfiCommentsPage
import uniffi.mediyo_ffi.FfiCommentSortFilter

interface CommentRepository {
    suspend fun token(videoId: String): String?
    suspend fun page(token: String): FfiCommentsPage
    suspend fun nextPage(token: String): FfiCommentsPage
    suspend fun replies(token: String): FfiCommentsPage
}

data class CommentThread(
    val count: String?,
    val comments: List<FfiComment>,
    val continuation: String?,
    val sortFilters: List<FfiCommentSortFilter>
)
