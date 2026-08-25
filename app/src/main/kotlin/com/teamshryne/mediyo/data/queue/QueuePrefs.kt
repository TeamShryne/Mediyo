package com.teamshryne.mediyo.data.queue

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.queuePrefs by preferencesDataStore("queue_prefs")

@Singleton
class QueuePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_LOCK = booleanPreferencesKey("lockQueue")

    val lockFlow: Flow<Boolean> = ctx.queuePrefs.data.map { it[K_LOCK] ?: false }

    suspend fun setLock(locked: Boolean) {
        ctx.queuePrefs.edit { it[K_LOCK] = locked }
    }
}
