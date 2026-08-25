package com.teamshryne.mediyo.data.cache

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cachePrefs by preferencesDataStore("cache_prefs")

data class CachePrefs(
    val maxBytes: Long = 512L*1024*1024,
    val ttlMs: Long = 7*24*60*60*1000L,
    val wifiOnly: Boolean = false,
    val offlineOnly: Boolean = false
)

@Singleton
class CacheRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: MediyoDb
) {
    private val dao get() = db.kv()

    private val K_MAX = longPreferencesKey("maxBytes")
    private val K_TTL = longPreferencesKey("ttlMs")
    private val K_WIFI = booleanPreferencesKey("wifiOnly")
    private val K_OFFLINE = booleanPreferencesKey("offlineOnly")

    val prefs: Flow<CachePrefs> = ctx.cachePrefs.data.map {
        CachePrefs(it[K_MAX] ?: 512L*1024*1024, it[K_TTL] ?: 7*24*60*60*1000L, it[K_WIFI] ?: false, it[K_OFFLINE] ?: false)
    }

    suspend fun setPrefs(p: CachePrefs) { ctx.cachePrefs.edit { it[K_MAX]=p.maxBytes; it[K_TTL]=p.ttlMs; it[K_WIFI]=p.wifiOnly; it[K_OFFLINE]=p.offlineOnly } }

    suspend fun put(key: String, type: String, json: String) { dao.put(KvCache(key, type, json, json.toByteArray().size)) }
    suspend fun get(key: String): String? = dao.get(key)?.json
    suspend fun clearType(type: String) = dao.clearType(type)
    suspend fun clearAll() = dao.clearAll()
    suspend fun evictExpired(ttlMs: Long) = dao.evictOlderThan(System.currentTimeMillis()-ttlMs)
    suspend fun stats(): CacheStats {
        val rows = dao.stats(); val total = dao.totalBytes() ?: 0L
        return CacheStats(total, rows.associate { it.type to (it.cnt to (it.bytes ?: 0L)) })
    }
}
