package com.teamshryne.mediyo.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.audio.SonicAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import com.teamshryne.mediyo.data.cache.DownloadCache
import com.teamshryne.mediyo.data.cache.MediyoDb
import com.teamshryne.mediyo.data.cache.MIGRATION_4_5
import com.teamshryne.mediyo.data.cache.MIGRATION_5_6
import com.teamshryne.mediyo.data.cache.MIGRATION_6_7
import com.teamshryne.mediyo.data.cache.MIGRATION_7_8
import com.teamshryne.mediyo.data.cache.PlayerCache
import com.teamshryne.mediyo.data.cache.StoragePrefs
import com.teamshryne.mediyo.data.cache.StreamDataSource
import com.teamshryne.mediyo.data.cache.StreamUrlCache
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideDatabaseProvider(@ApplicationContext ctx: Context): DatabaseProvider =
        StandaloneDatabaseProvider(ctx)

    /**
     * Transient song cache: every streamed byte lands here, oldest evicted
     * first. Sized once from prefs — size-slider changes apply on next start.
     */
    @Provides @Singleton @PlayerCache
    fun providePlayerCache(
        @ApplicationContext ctx: Context,
        databaseProvider: DatabaseProvider,
        storagePrefs: StoragePrefs
    ): Cache {
        val mb = runCatching { kotlinx.coroutines.runBlocking { storagePrefs.load().maxSongCacheMb } }
            .getOrDefault(1024).coerceIn(0, 8192)
        return SimpleCache(
            ctx.filesDir.resolve("mediyo_playback"),
            LeastRecentlyUsedCacheEvictor(mb * 1024 * 1024L),
            databaseProvider
        )
    }

    /** Permanent offline store: only the download manager writes here, never evicted. */
    @Provides @Singleton @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext ctx: Context,
        databaseProvider: DatabaseProvider
    ): Cache = SimpleCache(
        ctx.filesDir.resolve("mediyo_downloads"),
        NoOpCacheEvictor(),
        databaseProvider
    )

    @Provides @Singleton fun provideStreamUrlCache(): StreamUrlCache = StreamUrlCache()

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
        streams: StreamDataSource,
        eqProcessor: com.teamshryne.mediyo.playback.EqAudioProcessor
    ): ExoPlayer {
        // Queue lives in ExoPlayer as MediaItems with placeholder URIs (mediyo://videoId)
        // plus customCacheKey = videoId. StreamDataSource turns them into bytes:
        // permanent downloads → transient song cache → network, all keyed by
        // videoId so cached bytes survive signed-URL expiry.
        val mediaSourceFactory = DefaultMediaSourceFactory(streams.resolvingFactory())
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
        // 30s back-buffer (kept from keyframes) sits on top of the disk cache,
        // so rewinds inside the current song are instant.
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(30_000, true)
            .build()
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
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
