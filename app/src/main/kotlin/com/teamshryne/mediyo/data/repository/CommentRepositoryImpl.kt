package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.repository.CommentRepository
import uniffi.mediyo_ffi.FfiCommentsPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val bridge: MediyoBridge
) : CommentRepository {
    override suspend fun token(videoId: String): String? = bridge.commentsToken(videoId)
    override suspend fun page(token: String): FfiCommentsPage = bridge.commentsPage(token)
    override suspend fun nextPage(token: String): FfiCommentsPage = bridge.commentsNextPage(token)
    override suspend fun replies(token: String): FfiCommentsPage = bridge.commentsReplies(token)
}
