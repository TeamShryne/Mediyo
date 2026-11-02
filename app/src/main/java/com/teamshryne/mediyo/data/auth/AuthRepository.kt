package com.teamshryne.mediyo.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.teamshryne.mediyo.music.constants.VisitorDataKey
import com.teamshryne.mediyo.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy auth store used by the Go core bridge.
 *
 * The previous app kept its visitor identity in `mediyo_auth`. Reading that
 * file keeps an existing install's anonymous identity, and every write is
 * mirrored into the old app's `settings` DataStore so InnerTube and the Go
 * core stay on the same visitor.
 */
private val Context.legacyAuth by preferencesDataStore("mediyo_auth")

data class AuthState(
    val cookies: String = "",
    val sapisid: String = "",
    val visitorData: String = "",
    val pageId: String = "",
    val isLoggedIn: Boolean = false
)

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val K_COOKIES = stringPreferencesKey("cookies")
    private val K_SAPISID = stringPreferencesKey("sapisid")
    private val K_VISITOR = stringPreferencesKey("visitor")
    private val K_PAGEID = stringPreferencesKey("pageId")

    val flow: Flow<AuthState> = ctx.legacyAuth.data.map { p ->
        val c = p[K_COOKIES].orEmpty()
        AuthState(
            cookies = c,
            sapisid = p[K_SAPISID].orEmpty(),
            visitorData = p[K_VISITOR].orEmpty(),
            pageId = p[K_PAGEID].orEmpty(),
            isLoggedIn = c.isNotEmpty()
        )
    }

    suspend fun saveAnonVisitor(visitor: String) {
        if (visitor.isBlank()) return
        ctx.legacyAuth.edit { it[K_VISITOR] = visitor }
        // Keep InnerTube (old player/search fallbacks) on the same identity.
        runCatching { ctx.dataStore.edit { it[VisitorDataKey] = visitor } }
    }

    /** Seeded from the old app's settings when the legacy store is empty. */
    suspend fun currentVisitorOrNull(): String? {
        val legacy = flow.first().visitorData
        if (legacy.isNotBlank()) return legacy
        return runCatching {
            ctx.dataStore.data.first()[VisitorDataKey]?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }
}
