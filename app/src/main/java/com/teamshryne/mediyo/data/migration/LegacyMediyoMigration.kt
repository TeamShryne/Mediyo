package com.teamshryne.mediyo.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import com.teamshryne.mediyo.data.auth.AuthRepository
import com.teamshryne.mediyo.data.mediyo.parseDurationSeconds
import com.teamshryne.mediyo.music.constants.InnerTubeCookieKey
import com.teamshryne.mediyo.music.constants.LegacyMigratedKey
import com.teamshryne.mediyo.music.constants.VisitorDataKey
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot import of the previous Mediyo app's database.
 *
 * This app (the old UI) stores everything in `song.db`; the previous app used
 * `mediyo.db`. An existing install of that app must keep its likes, playlists,
 * history and downloads, so this reads the legacy file directly, copies what
 * maps cleanly, then removes it. It runs before the UI touches the database.
 *
 * Migration is deliberately forgiving: a malformed legacy row is skipped, and
 * a failure anywhere leaves the legacy file in place so the next launch can
 * try again. The app must never crash because of this.
 */
@Singleton
class LegacyMediyoMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val auth: AuthRepository,
) {
    private companion object {
        const val LEGACY_DB = "mediyo.db"
        const val LEGACY_PLAYBACK_CACHE = "mediyo_playback"
        const val LEGACY_DOWNLOAD_CACHE = "mediyo_downloads"
        const val DOWNLOAD_CACHE = "download"
    }

    fun runOnce() = runBlocking(Dispatchers.IO) { run() }

    suspend fun run() {
        if (context.dataStore.get(LegacyMigratedKey, false)) return

        val legacyDb = context.getDatabasePath(LEGACY_DB)
        if (!legacyDb.exists()) {
            // Fresh install, or the import already ran and was cleaned up.
            markDone()
            runCatching { migrateAuth() }.onFailure { Timber.e(it) }
            return
        }

        val imported = runCatching { importFrom(legacyDb) }
            .onFailure { Timber.e(it) }
            .isSuccess

        if (!imported) return

        runCatching { moveDownloadCache() }.onFailure { Timber.e(it) }
        runCatching { migrateAuth() }.onFailure { Timber.e(it) }

        // Only clean up after the import committed. The download cache was
        // moved above, so deleting the legacy playback cache is safe.
        runCatching {
            deleteDatabaseFiles(legacyDb)
            context.filesDir.resolve(LEGACY_PLAYBACK_CACHE).deleteRecursively()
        }.onFailure { Timber.e(it) }

        markDone()
    }

    private fun markDone() {
        runBlocking { context.dataStore.edit { it[LegacyMigratedKey] = true } }
    }

    private fun importFrom(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
            if (!legacy.hasTable("liked_tracks")) return

            val songs = LinkedHashMap<String, LegacySong>()
            val playlistEntries = linkedMapOf<String, MutableList<Pair<String, Int>>>()
            val playlists = mutableListOf<LegacyPlaylist>()
            val events = mutableListOf<LegacyEvent>()

            // Liked songs first: they also seed the library.
            legacy.query(
                "liked_tracks",
                arrayOf("videoId", "title", "artist", "artistIds", "artworkUrl", "album", "albumId", "duration", "likedAt"),
                null, null, null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val likedAt = c.getLong(8).takeIf { it > 0 }
                    songs[id] = LegacySong(
                        id = id,
                        title = c.getString(1).orEmpty(),
                        artist = c.getString(2).orEmpty(),
                        artistIds = c.getString(3),
                        artworkUrl = c.getString(4),
                        album = c.getString(5),
                        albumId = c.getString(6),
                        duration = parseDurationSeconds(c.getString(7)) ?: -1,
                        likedAt = likedAt,
                        inLibraryAt = likedAt,
                    )
                }
            }

            // Playlist entries: keep the playlist and its order, and make sure
            // every referenced track exists before the map row is inserted.
            if (legacy.hasTable("local_playlists") && legacy.hasTable("local_playlist_entries")) {
                legacy.query(
                    "local_playlists",
                    arrayOf("id", "title", "description", "createdAt", "updatedAt"),
                    null, null, null, null, "updatedAt DESC"
                ).use { c ->
                    while (c.moveToNext()) {
                        playlists += LegacyPlaylist(
                            id = c.getString(0) ?: continue,
                            name = c.getString(1).orEmpty(),
                        )
                    }
                }

                legacy.query(
                    "local_playlist_entries",
                    arrayOf("playlistId", "trackVideoId", "title", "artist", "artistIds", "artworkUrl", "album", "albumId", "duration", "addedAt", "position"),
                    null, null, null, null, "playlistId, position"
                ).use { c ->
                    while (c.moveToNext()) {
                        val playlistId = c.getString(0) ?: continue
                        val id = c.getString(1) ?: continue
                        if (songs[id] == null) {
                            songs[id] = LegacySong(
                                id = id,
                                title = c.getString(2).orEmpty(),
                                artist = c.getString(3).orEmpty(),
                                artistIds = c.getString(4),
                                artworkUrl = c.getString(5),
                                album = c.getString(6),
                                albumId = c.getString(7),
                                duration = parseDurationSeconds(c.getString(8)) ?: -1,
                            )
                        }
                        // Map rows are written after songs, so only keep a
                        // reference here.
                        playlistEntries.getOrPut(playlistId) { mutableListOf() }
                            .add(id to c.getInt(10))
                    }
                }
            }

            // History: keep the aggregate row as an event. The old app derives
            // its stats from `event`, so one event per played track is enough.
            if (legacy.hasTable("history_entries")) {
                legacy.query(
                    "history_entries",
                    arrayOf("videoId", "title", "artist", "artistIds", "artworkUrl", "album", "albumId", "duration", "lastPlayedAt", "totalPlayMs"),
                    null, null, null, null, "lastPlayedAt DESC"
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        if (songs[id] == null) {
                            songs[id] = LegacySong(
                                id = id,
                                title = c.getString(1).orEmpty(),
                                artist = c.getString(2).orEmpty(),
                                artistIds = c.getString(3),
                                artworkUrl = c.getString(4),
                                album = c.getString(5),
                                albumId = c.getString(6),
                                duration = parseDurationSeconds(c.getString(7)) ?: -1,
                            )
                        }
                        events += LegacyEvent(id, c.getLong(8), c.getLong(9))
                    }
                }
            }

            // Downloads: the metadata row is what makes the offline song show
            // up in the library; the bytes are moved separately.
            if (legacy.hasTable("downloaded_tracks")) {
                legacy.query(
                    "downloaded_tracks",
                    arrayOf("videoId", "title", "artist", "artworkUrl", "downloadedAt"),
                    null, null, null, null, "downloadedAt DESC"
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val downloadedAt = c.getLong(4).takeIf { it > 0 }
                        val existing = songs[id]
                        if (existing == null) {
                            songs[id] = LegacySong(
                                id = id,
                                title = c.getString(1).orEmpty(),
                                artist = c.getString(2).orEmpty(),
                                artworkUrl = c.getString(3),
                                inLibraryAt = downloadedAt,
                            )
                        } else {
                            existing.inLibraryAt = existing.inLibraryAt ?: downloadedAt
                        }
                    }
                }
            }

            writeSongs(songs.values)
            writePlaylists(playlists, playlistEntries)
            writeEvents(events)
        }
    }

    private fun writeSongs(songs: Collection<LegacySong>) {
        if (songs.isEmpty()) return
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            songs.forEach { song ->
                val values = ContentValues().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("duration", song.duration)
                    put("thumbnailUrl", song.artworkUrl)
                    put("albumId", song.albumId)
                    put("albumName", song.album)
                    put("liked", if (song.likedAt != null) 1 else 0)
                    put("totalPlayTime", 0L)
                    put("inLibrary", song.inLibraryAt)
                }
                db.insert("song", SQLiteDatabase.CONFLICT_IGNORE, values)

                // A row that already existed (for example a history row that
                // was written first) still needs the stronger flags.
                song.likedAt?.let {
                    db.execSQL(
                        "UPDATE song SET liked = 1, inLibrary = COALESCE(inLibrary, ?) WHERE id = ?",
                        arrayOf<Any>(it, song.id)
                    )
                }
                song.inLibraryAt?.let {
                    db.execSQL(
                        "UPDATE song SET inLibrary = COALESCE(inLibrary, ?) WHERE id = ?",
                        arrayOf<Any>(it, song.id)
                    )
                }

                writeArtists(db, song)
                writeAlbum(db, song)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writeArtists(db: androidx.sqlite.db.SupportSQLiteDatabase, song: LegacySong) {
        val names = song.artist.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        val ids = song.artistIds.orEmpty().split(",").map { it.trim() }
        names.forEachIndexed { index, name ->
            // Local artists must carry the "LA" prefix: that is how the app
            // tells them apart from YouTube channels.
            val id = ids.getOrNull(index)
                ?.takeIf { it.isNotBlank() }
                ?: ("LA" + java.util.UUID.randomUUID().toString().replace("-", "").take(8))
            val artist = ContentValues().apply {
                put("id", id)
                put("name", name)
                put("thumbnailUrl", song.artworkUrl)
                put("lastUpdateTime", System.currentTimeMillis())
                put("bookmarkedAt", null as Long?)
            }
            db.insert("artist", SQLiteDatabase.CONFLICT_IGNORE, artist)
            val map = ContentValues().apply {
                put("songId", song.id)
                put("artistId", id)
                put("position", index)
            }
            db.insert("song_artist_map", SQLiteDatabase.CONFLICT_IGNORE, map)
        }
    }

    private fun writeAlbum(db: androidx.sqlite.db.SupportSQLiteDatabase, song: LegacySong) {
        val albumId = song.albumId?.takeIf { it.isNotBlank() } ?: return
        val album = ContentValues().apply {
            put("id", albumId)
            put("title", song.album.orEmpty())
            put("year", null as Int?)
            put("thumbnailUrl", song.artworkUrl)
            put("themeColor", null as Int?)
            put("songCount", 0)
            put("duration", 0)
            put("lastUpdateTime", System.currentTimeMillis())
            put("bookmarkedAt", null as Long?)
        }
        db.insert("album", SQLiteDatabase.CONFLICT_IGNORE, album)
        // `index` is a keyword in SQLite, so it has to be quoted here; Room
        // does the same in its generated statements.
        db.execSQL(
            "INSERT OR IGNORE INTO song_album_map (songId, albumId, `index`) VALUES (?, ?, ?)",
            arrayOf<Any>(song.id, albumId, 0)
        )
    }

    private fun writePlaylists(
        playlists: List<LegacyPlaylist>,
        entries: Map<String, List<Pair<String, Int>>>,
    ) {
        if (playlists.isEmpty()) return
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            playlists.forEach { playlist ->
                val values = ContentValues().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("browseId", null as String?)
                }
                db.insert("playlist", SQLiteDatabase.CONFLICT_IGNORE, values)
                entries[playlist.id].orEmpty().forEach { (songId, position) ->
                    val map = ContentValues().apply {
                        put("playlistId", playlist.id)
                        put("songId", songId)
                        put("position", position)
                    }
                    db.insert("playlist_song_map", SQLiteDatabase.CONFLICT_IGNORE, map)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writeEvents(events: List<LegacyEvent>) {
        if (events.isEmpty()) return
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            events.forEach { event ->
                val values = ContentValues().apply {
                    put("songId", event.songId)
                    put("timestamp", event.timestamp)
                    put("playTime", event.playTime)
                }
                db.insert("event", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * The download bytes live in a Media3 cache. Its index is keyed by a UID
     * file inside the directory, and the metadata lives in the shared
     * `exoplayer_internal.db`, so renaming the directory keeps the UID, the
     * index and the download rows the old app's DownloadManager reads.
     */
    private fun moveDownloadCache() {
        val from = context.filesDir.resolve(LEGACY_DOWNLOAD_CACHE)
        val to = context.filesDir.resolve(DOWNLOAD_CACHE)
        if (!from.exists()) return
        if (to.exists()) {
            // Never merge two caches: a directory whose UID does not match the
            // index would be ignored or, worse, misread.
            return
        }
        check(from.renameTo(to)) { "Could not move the legacy download cache" }
    }

    private suspend fun migrateAuth() {
        val legacy = runCatching { auth.flow.first() }.getOrNull() ?: return
        if (legacy.visitorData.isBlank() && legacy.cookies.isBlank()) return
        context.dataStore.edit { prefs ->
            if (legacy.visitorData.isNotBlank() && prefs[VisitorDataKey].isNullOrBlank()) {
                prefs[VisitorDataKey] = legacy.visitorData
            }
            if (legacy.cookies.isNotBlank() && prefs[InnerTubeCookieKey].isNullOrBlank()) {
                prefs[InnerTubeCookieKey] = legacy.cookies
            }
        }
    }

    private fun deleteDatabaseFiles(db: File) {
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal"))
            .forEach { runCatching { it.delete() } }
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private data class LegacySong(
        val id: String,
        val title: String,
        val artist: String,
        val artistIds: String? = null,
        val artworkUrl: String? = null,
        val album: String? = null,
        val albumId: String? = null,
        val duration: Int = -1,
        val likedAt: Long? = null,
        var inLibraryAt: Long? = null,
    )

    private data class LegacyPlaylist(
        val id: String,
        val name: String,
    )

    private data class LegacyEvent(
        val songId: String,
        val timestamp: Long,
        val playTime: Long,
    )
}
