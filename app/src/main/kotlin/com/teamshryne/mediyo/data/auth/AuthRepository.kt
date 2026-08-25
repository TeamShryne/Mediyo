package com.teamshryne.mediyo.data.auth

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ds by preferencesDataStore("mediyo_auth")

data class AuthState(
    val cookies: String = "",
    val sapisid: String = "",
    val visitorData: String = "",
    val pageId: String = "",
    val isLoggedIn: Boolean = false
)

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_COOKIES = stringPreferencesKey("cookies")
    private val K_SAPISID = stringPreferencesKey("sapisid")
    private val K_VISITOR = stringPreferencesKey("visitor")
    private val K_PAGEID = stringPreferencesKey("pageId")

    val flow: Flow<AuthState> = ctx.ds.data.map { p ->
        val c = p[K_COOKIES].orEmpty()
        AuthState(c, p[K_SAPISID].orEmpty(), p[K_VISITOR].orEmpty(), p[K_PAGEID].orEmpty(), c.isNotEmpty())
    }

    suspend fun save(cookies: String, sapisid: String, visitor: String, pageId: String) {
        ctx.ds.edit { it[K_COOKIES]=cookies; it[K_SAPISID]=sapisid; it[K_VISITOR]=visitor; it[K_PAGEID]=pageId }
    }
    suspend fun saveAnonVisitor(visitor: String) {
        ctx.ds.edit { it[K_VISITOR] = visitor }
    }
    suspend fun clear() { ctx.ds.edit { it.clear() } }
}
