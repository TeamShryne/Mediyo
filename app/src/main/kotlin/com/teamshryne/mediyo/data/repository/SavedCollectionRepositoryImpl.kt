package com.teamshryne.mediyo.data.repository

import com.teamshryne.mediyo.data.local.SavedCollectionDao
import com.teamshryne.mediyo.data.local.SavedCollectionEntity
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import com.teamshryne.mediyo.domain.model.bestThumbUrl
import com.teamshryne.mediyo.domain.repository.SavedCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedCollectionRepositoryImpl @Inject constructor(
    private val dao: SavedCollectionDao,
    private val bridge: MediyoBridge
) : SavedCollectionRepository {
    override fun flowAll(): Flow<List<SavedCollectionEntity>> = dao.flowAll()
    override fun flowById(browseId: String): Flow<SavedCollectionEntity?> = dao.flowById(browseId)
    override fun isSavedFlow(browseId: String): Flow<Boolean> = dao.isSavedFlow(browseId)
    override suspend fun isSaved(browseId: String): Boolean = dao.isSaved(browseId)

    override suspend fun save(
        browseId: String,
        kind: String,
        title: String,
        subtitle: String?,
        artworkUrl: String?,
        trackCountText: String?
    ) {
        if (browseId.isBlank()) return
        val existing = dao.getById(browseId)
        dao.upsert(
            SavedCollectionEntity(
                browseId = browseId,
                kind = kind,
                title = title.ifBlank { existing?.title ?: "Untitled" },
                subtitle = subtitle ?: existing?.subtitle,
                artworkUrl = artworkUrl ?: existing?.artworkUrl,
                trackCountText = trackCountText ?: existing?.trackCountText,
                savedAt = existing?.savedAt ?: System.currentTimeMillis()
            )
        )
    }

    override suspend fun remove(browseId: String) { dao.remove(browseId) }

    override suspend fun toggle(
        browseId: String,
        kind: String,
        title: String,
        subtitle: String?,
        artworkUrl: String?,
        trackCountText: String?
    ): Boolean {
        if (browseId.isBlank()) return false
        return if (dao.isSaved(browseId)) {
            dao.remove(browseId); false
        } else {
            save(browseId, kind, title, subtitle, artworkUrl, trackCountText); true
        }
    }

    override suspend fun refreshAll() {
        // Snapshot ids first so a concurrent unsave can't crash the loop.
        val all: List<SavedCollectionEntity> = try {
            // flowAll is a Flow; read one-shot via getById loop is awkward —
            // expose via a direct query fallback: reuse flow first emission.
            first(flowAll())
        } catch (_: Throwable) { return }
        for (e in all) {
            try { refreshOne(e.browseId) } catch (_: Throwable) { }
        }
    }

    override suspend fun refreshOne(browseId: String) {
        val e = dao.getById(browseId) ?: return
        try {
            when (e.kind) {
                SavedCollectionEntity.ALBUM -> {
                    val p = bridge.album(browseId)
                    dao.updateMeta(
                        browseId, p.title.ifBlank { e.title },
                        p.artist ?: e.subtitle,
                        p.thumbnails.bestThumbUrl() ?: e.artworkUrl,
                        if (p.tracks.isNotEmpty()) "${p.tracks.size} songs" else e.trackCountText
                    )
                }
                SavedCollectionEntity.PLAYLIST -> {
                    val p = bridge.playlist(browseId)
                    dao.updateMeta(
                        browseId, p.title.ifBlank { e.title }, e.subtitle,
                        p.thumbnails.bestThumbUrl() ?: e.artworkUrl,
                        p.trackCount ?: e.trackCountText
                    )
                }
                SavedCollectionEntity.PODCAST -> {
                    val p = bridge.podcast(browseId)
                    dao.updateMeta(
                        browseId, e.title, e.subtitle, e.artworkUrl,
                        if (p.items.isNotEmpty()) "${p.items.size} episodes" else e.trackCountText
                    )
                }
            }
        } catch (_: Throwable) {
            // Offline / deleted upstream — keep the local snapshot.
        }
    }
}
