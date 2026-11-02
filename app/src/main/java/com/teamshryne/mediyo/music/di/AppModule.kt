package com.teamshryne.mediyo.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.lyrics.KugouApi
import com.teamshryne.mediyo.data.lyrics.LrcLibApi
import com.teamshryne.mediyo.data.lyrics.LyricsPlusApi
import com.teamshryne.mediyo.data.lyrics.PaxsenixApi
import com.teamshryne.mediyo.data.repository.LyricsRepositoryImpl
import com.teamshryne.mediyo.domain.repository.LyricsRepository
import com.teamshryne.mediyo.music.constants.MaxSongCacheSizeKey
import com.teamshryne.mediyo.music.db.InternalDatabase
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    // Lyrics providers: the ported stack talks to these directly; the old
    // provider list and its settings are gone.
    @Singleton @Provides fun provideBetterLyricsApi() = BetterLyricsApi()
    @Singleton @Provides fun provideLyricsPlusApi() = LyricsPlusApi()
    @Singleton @Provides fun providePaxsenixApi() = PaxsenixApi()
    @Singleton @Provides fun provideKugouApi() = KugouApi()
    @Singleton @Provides fun provideLrcLibApi() = LrcLibApi()

    @Singleton
    @Provides
    fun provideLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository = impl

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): SimpleCache {
        val constructor = {
            SimpleCache(
                context.filesDir.resolve("exoplayer"),
                when (val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024) {
                    -1 -> NoOpCacheEvictor()
                    else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
                },
                databaseProvider
            )
        }
        constructor().release()
        return constructor()
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): SimpleCache {
        val constructor = {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }
        constructor().release()
        return constructor()
    }
}
