package com.teamshryne.mediyo.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetDataStore by preferencesDataStore(name = "mediyo_widget_state")

/**
 * Persists the last-known now-playing snapshot.
 *
 * [MediyoWidgetManager] reads this on every render so widgets paint instantly,
 * even when the app process is dead.
 */
@Singleton
class WidgetStateRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private object Keys {
        val VIDEO_ID = stringPreferencesKey("np_video_id")
        val TITLE = stringPreferencesKey("np_title")
        val ARTIST = stringPreferencesKey("np_artist")
        val ARTWORK = stringPreferencesKey("np_artwork")
        val IS_PLAYING = booleanPreferencesKey("np_is_playing")
        val IS_BUFFERING = booleanPreferencesKey("np_is_buffering")
        val LIKED = booleanPreferencesKey("np_liked")
        val PROGRESS = floatPreferencesKey("np_progress")
        val POSITION_MS = longPreferencesKey("np_position_ms")
        val DURATION_MS = longPreferencesKey("np_duration_ms")
        val UPDATED_AT = longPreferencesKey("np_updated_at")
    }

    val nowPlaying: Flow<WidgetNowPlaying> = ctx.widgetDataStore.data.map { p ->
        WidgetNowPlaying(
            videoId = p[Keys.VIDEO_ID],
            title = p[Keys.TITLE].orEmpty(),
            artist = p[Keys.ARTIST].orEmpty(),
            artworkUrl = p[Keys.ARTWORK],
            isPlaying = p[Keys.IS_PLAYING] == true,
            isBuffering = p[Keys.IS_BUFFERING] == true,
            liked = p[Keys.LIKED] == true,
            progress = p[Keys.PROGRESS] ?: 0f,
            positionMs = p[Keys.POSITION_MS] ?: 0L,
            durationMs = p[Keys.DURATION_MS] ?: 0L,
            updatedAt = p[Keys.UPDATED_AT] ?: 0L
        )
    }

    suspend fun read(): WidgetNowPlaying = nowPlaying.first()

    suspend fun save(state: WidgetNowPlaying) {
        ctx.widgetDataStore.edit { p ->
            if (state.videoId == null) p.remove(Keys.VIDEO_ID) else p[Keys.VIDEO_ID] = state.videoId
            p[Keys.TITLE] = state.title
            p[Keys.ARTIST] = state.artist
            if (state.artworkUrl == null) p.remove(Keys.ARTWORK) else p[Keys.ARTWORK] = state.artworkUrl
            p[Keys.IS_PLAYING] = state.isPlaying
            p[Keys.IS_BUFFERING] = state.isBuffering
            p[Keys.LIKED] = state.liked
            p[Keys.PROGRESS] = state.progress.coerceIn(0f, 1f)
            p[Keys.POSITION_MS] = state.positionMs
            p[Keys.DURATION_MS] = state.durationMs
            p[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markPaused() {
        ctx.widgetDataStore.edit {
            it[Keys.IS_PLAYING] = false
            it[Keys.IS_BUFFERING] = false
            it[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }
}
