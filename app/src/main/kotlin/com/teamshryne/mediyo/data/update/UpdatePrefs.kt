package com.teamshryne.mediyo.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updatePrefs by preferencesDataStore("update_prefs")

/**
 * Which release track this install follows.
 *
 * The two tracks are separate update feeds rather than one feed with a
 * preference on it, because a nightly must never be pushed to a stable user:
 * GitHub's `releases/latest` resolves to the newest *non*-prerelease, so
 * nightlies published as prereleases are invisible to the stable feed.
 */
enum class UpdateChannel(val id: String, val label: String) {
    /** Signed releases, cut from tags. */
    STABLE("stable", "Stable"),

    /** Daily builds from main, published as prereleases. */
    NIGHTLY("nightly", "Nightly");

    companion object {
        fun from(id: String?): UpdateChannel = entries.firstOrNull { it.id == id } ?: STABLE
    }
}

@Singleton
class UpdatePrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val K_CHANNEL = stringPreferencesKey("channel")

    val channelFlow: Flow<UpdateChannel> =
        ctx.updatePrefs.data.map { UpdateChannel.from(it[K_CHANNEL]) }

    /**
     * The channel as it is stored, read fresh.
     *
     * [channelFlow] starts on Stable and only moves to the stored value once
     * the first read from disk lands, which is after anything that starts at
     * launch has already begun. A check that asks for the channel the way it
     * wants it -- the way the user chose it -- has to ask for it here.
     */
    suspend fun currentChannel(): UpdateChannel = channelFlow.first()

    suspend fun setChannel(channel: UpdateChannel) {
        ctx.updatePrefs.edit { it[K_CHANNEL] = channel.id }
    }
}
