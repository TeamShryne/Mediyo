package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import kotlinx.coroutines.flow.Flow

/** Read-only public collections (albums / playlists / podcasts) saved to the library. */
interface SavedCollectionRepository {
    fun flowAll(): Flow<List<SavedCollectionEntity>>
    fun flowById(browseId: String): Flow<SavedCollectionEntity?>
    fun isSavedFlow(browseId: String): Flow<Boolean>
    suspend fun isSaved(browseId: String): Boolean
    suspend fun save(
        browseId: String,
        kind: String,
        title: String,
        subtitle: String? = null,
        artworkUrl: String? = null,
        trackCountText: String? = null
    )
    suspend fun remove(browseId: String)
    /** Returns new saved state. */
    suspend fun toggle(
        browseId: String,
        kind: String,
        title: String,
        subtitle: String? = null,
        artworkUrl: String? = null,
        trackCountText: String? = null
    ): Boolean
    /** Re-fetch metadata for every saved item and update the local snapshot. */
    suspend fun refreshAll()
    /** Refresh a single saved item (no-op when not saved). */
    suspend fun refreshOne(browseId: String)
}
