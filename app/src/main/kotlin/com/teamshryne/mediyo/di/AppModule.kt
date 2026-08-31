package com.teamshryne.mediyo.di

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import com.teamshryne.mediyo.data.cache.MediyoDb
import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.playback.NewPipeResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

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

    @Provides @Singleton fun provideBetterLyricsApi(): BetterLyricsApi = BetterLyricsApi()

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext ctx: Context,
        resolver: NewPipeResolver
    ): ExoPlayer {
        // Like Metrolist/Innertune: the queue lives in ExoPlayer as MediaItems
        // with placeholder URIs (mediyo://videoId). The actual googlevideo URL is
        // resolved lazily on the loader thread when ExoPlayer needs the bytes, so
        // the notification never flickers — new title/artwork appears instantly.
        val upstreamFactory = DefaultDataSource.Factory(ctx)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val videoId = dataSpec.key?.takeIf { it.isNotBlank() } ?: run {
                val u = dataSpec.uri.toString()
                when {
                    u.startsWith("mediyo://") -> u.removePrefix("mediyo://")
                    u.contains("watch?v=") -> u.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
                    else -> u.substringAfterLast("/").substringBefore("?").substringBefore("&")
                }
            }
            // If it's already an http googlevideo URL, don't re-resolve
            if (videoId.startsWith("http://") || videoId.startsWith("https://")) return@Factory dataSpec
            if (videoId.isBlank()) return@Factory dataSpec
            val url = runBlocking { resolver.resolveStreamUrl(videoId) }
                ?: throw java.io.IOException("No stream for $videoId")
            dataSpec.withUri(Uri.parse(url))
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory)
        // Audio focus + noisy handling exactly like Metrolist/Innertune — music
        // pauses for calls/other media and resumes afterwards, and stops when
        // headphones are unplugged.
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // handleAudioFocus — Metrolist uses false + manual focus, but ExoPlayer's auto handling is enough for the requested behaviour
            )
            .build()
    }
}
