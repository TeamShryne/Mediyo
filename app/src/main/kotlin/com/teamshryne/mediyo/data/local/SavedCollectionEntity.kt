package com.teamshryne.mediyo.data.local

import androidx.room.*

/**
 * Public (read-only) collections the user saved to their library:
 * albums, YT playlists and podcasts. Unlike [LocalPlaylistEntity] the user
 * cannot add tracks — the entry is just a bookmark (browseId + snapshot
 * metadata) that opens the live public page. Metadata is refreshed in the
 * background whenever the library loads or the detail page is viewed.
 */
@Entity(tableName = "saved_collections")
data class SavedCollectionEntity(
    @PrimaryKey val browseId: String,
    /** ALBUM | PLAYLIST | PODCAST */
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val trackCountText: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ALBUM = "ALBUM"
        const val PLAYLIST = "PLAYLIST"
        const val PODCAST = "PODCAST"
    }
}

@Dao
interface SavedCollectionDao {
    @Query("SELECT * FROM saved_collections ORDER BY savedAt DESC")
    fun flowAll(): kotlinx.coroutines.flow.Flow<List<SavedCollectionEntity>>

    @Query("SELECT * FROM saved_collections WHERE browseId = :browseId LIMIT 1")
    suspend fun getById(browseId: String): SavedCollectionEntity?

    @Query("SELECT * FROM saved_collections WHERE browseId = :browseId LIMIT 1")
    fun flowById(browseId: String): kotlinx.coroutines.flow.Flow<SavedCollectionEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_collections WHERE browseId = :browseId)")
    fun isSavedFlow(browseId: String): kotlinx.coroutines.flow.Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_collections WHERE browseId = :browseId)")
    suspend fun isSaved(browseId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedCollectionEntity)

    @Query("DELETE FROM saved_collections WHERE browseId = :browseId")
    suspend fun remove(browseId: String)

    @Query("UPDATE saved_collections SET title = :title, subtitle = :subtitle, artworkUrl = :artwork, trackCountText = :count, updatedAt = :now WHERE browseId = :browseId")
    suspend fun updateMeta(
        browseId: String,
        title: String,
        subtitle: String?,
        artwork: String?,
        count: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM saved_collections")
    fun countFlow(): kotlinx.coroutines.flow.Flow<Int>
}
