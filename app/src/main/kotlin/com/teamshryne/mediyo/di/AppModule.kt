package com.teamshryne.mediyo.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.teamshryne.mediyo.data.cache.MediyoDb
import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): MediyoDb =
        Room.databaseBuilder(ctx, MediyoDb::class.java, "mediyo.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideLocalPlaylistDao(db: MediyoDb): LocalPlaylistDao = db.localPlaylistDao()
    @Provides fun provideLocalPlaylistEntryDao(db: MediyoDb): LocalPlaylistEntryDao = db.localPlaylistEntryDao()
    @Provides fun provideLikedDao(db: MediyoDb): LikedTrackDao = db.likedDao()
    @Provides fun provideHistoryDao(db: MediyoDb): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext ctx: Context): ExoPlayer =
        ExoPlayer.Builder(ctx).build()
}
