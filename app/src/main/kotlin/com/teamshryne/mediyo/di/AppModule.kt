package com.teamshryne.mediyo.di

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.audio.SonicAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import com.teamshryne.mediyo.data.cache.MediyoDb
import com.teamshryne.mediyo.data.cache.MIGRATION_4_5
import com.teamshryne.mediyo.data.cache.MIGRATION_5_6
import com.teamshryne.mediyo.data.cache.MIGRATION_6_7
import com.teamshryne.mediyo.data.local.HistoryDao
import com.teamshryne.mediyo.data.local.UserEventDao
import com.teamshryne.mediyo.data.local.LikedTrackDao
import com.teamshryne.mediyo.data.local.LocalPlaylistDao
import com.teamshryne.mediyo.data.local.LocalPlaylistEntryDao
import com.teamshryne.mediyo.data.lyrics.BetterLyricsApi
import com.teamshryne.mediyo.data.lyrics.KugouApi
import com.teamshryne.mediyo.data.lyrics.LrcLibApi
import com.teamshryne.mediyo.data.lyrics.LyricsPlusApi
import com.teamshryne.mediyo.data.lyrics.PaxsenixApi
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideLocalPlaylistDao(db: MediyoDb): LocalPlaylistDao = db.localPlaylistDao()
    @Provides fun provideLocalPlaylistEntryDao(db: MediyoDb): LocalPlaylistEntryDao = db.localPlaylistEntryDao()
    @Provides fun provideLikedDao(db: MediyoDb): LikedTrackDao = db.likedDao()
    @Provides fun provideHistoryDao(db: MediyoDb): HistoryDao = db.historyDao()
    @Provides fun provideUserEventDao(db: MediyoDb): UserEventDao = db.userEventDao()
    @Provides fun provideFollowedArtistDao(db: MediyoDb): com.teamshryne.mediyo.data.local.FollowedArtistDao = db.followedArtistDao()
    @Provides fun provideSavedCollectionDao(db: MediyoDb): com.teamshryne.mediyo.data.local.SavedCollectionDao = db.savedCollectionDao()

    @Provides @Singleton fun provideBetterLyricsApi(): BetterLyricsApi = BetterLyricsApi()
    @Provides @Singleton fun provideLyricsPlusApi(): LyricsPlusApi = LyricsPlusApi()
    @Provides @Singleton fun providePaxsenixApi(): PaxsenixApi = PaxsenixApi()
    @Provides @Singleton fun provideKugouApi(): KugouApi = KugouApi()
    @Provides @Singleton fun provideLrcLibApi(): LrcLibApi = LrcLibApi()

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext ctx: Context,
        resolver: NewPipeResolver,
        eqProcessor: com.teamshryne.mediyo.playback.EqAudioProcessor
    ): ExoPlayer {
        // Queue lives in ExoPlayer as MediaItems with placeholder URIs (mediyo://videoId).
        // The actual stream URL is resolved lazily on the loader thread when ExoPlayer
        // needs the bytes, so the notification updates instantly without clearing.
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
        // In-pipeline EQ (MetroList-style): our own biquad processor rides in
        // the audio sink ahead of the stock chain, so it behaves identically
        // on every device with no system effect HAL involved. The trailing
        // processors are ExoPlayer's own defaults (trimming, disabled
        // silence-skipper, Sonic for speed/pitch) — playback behaviour is
        // otherwise unchanged.
        val renderersFactory = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(ctx)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        arrayOf(eqProcessor),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor()
                    )
                )
                .build()
        }
        // Audio focus + noisy handling — pauses for calls/other media and resumes
        // afterwards, and stops when headphones are unplugged.
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // handleAudioFocus — ExoPlayer's auto handling covers the required behaviour
            )
            .build()
    }
}
