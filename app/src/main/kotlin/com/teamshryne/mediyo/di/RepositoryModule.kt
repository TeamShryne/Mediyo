package com.teamshryne.mediyo.di

import com.teamshryne.mediyo.data.repository.CommentRepositoryImpl
import com.teamshryne.mediyo.data.repository.HistoryRepositoryImpl
import com.teamshryne.mediyo.data.repository.LikeRepositoryImpl
import com.teamshryne.mediyo.data.repository.LocalPlaylistRepositoryImpl
import com.teamshryne.mediyo.domain.repository.CommentRepository
import com.teamshryne.mediyo.domain.repository.HistoryRepository
import com.teamshryne.mediyo.domain.repository.LikeRepository
import com.teamshryne.mediyo.domain.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPlaylist(repo: LocalPlaylistRepositoryImpl): PlaylistRepository
    @Binds @Singleton abstract fun bindLike(repo: LikeRepositoryImpl): LikeRepository
    @Binds @Singleton abstract fun bindHistory(repo: HistoryRepositoryImpl): HistoryRepository
    @Binds @Singleton abstract fun bindComment(repo: CommentRepositoryImpl): CommentRepository
}
