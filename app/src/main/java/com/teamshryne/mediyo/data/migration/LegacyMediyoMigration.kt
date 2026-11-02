package com.teamshryne.mediyo.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import com.teamshryne.mediyo.data.auth.AuthRepository
import com.teamshryne.mediyo.data.mediyo.parseDurationSeconds
import com.teamshryne.mediyo.music.constants.InnerTubeCookieKey
import com.teamshryne.mediyo.music.constants.LegacyMigratedKey
import com.teamshryne.mediyo.music.constants.VisitorDataKey
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot import of the previous Mediyo app's database.
 *
 * This app (the old UI) stores everything in `song.db`; the previous app used
 * `mediyo.db`. An existing install of that app must keep its likes, playlists,
 * plays, downloads and library bookmarks, so this reads the legacy file and
 * copies what maps cleanly, then removes it.
 *
 * Rules that keep it safe to retry:
 * - the legacy file is read from a copy, never opened in place, so a WAL file
 *   left behind by a killed process cannot make the read fail;
 * - the whole import is one transaction, so a failure writes nothing at all;
 * - every insert is idempotent (conflict-ignore, replace-by-playlist, and
 *   events deduplicated by their exact values), so a crash between the
 *   transaction and the cleanup cannot double the user's history;
 * - a failure leaves the legacy file in place for the next launch and never
 *   throws into `onCreate`.
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

        /** Tables that mean this really is the previous app's database. */
        val LEGACY_TABLES = listOf(
            "liked_tracks",
            "local_playlists",
            "local_playlist_entries",
            "history_entries",
            "downloaded_tracks",
            "saved_collections",
            "followed_artists",
        )
    }

    fun runOnce() = runBlocking(Dispatchers.IO) { run() }

    suspend fun run() {
        if (context.dataStore.data.first()[LegacyMigratedKey] == true) return

        val legacyDb = context.getDatabasePath(LEGACY_DB)
        if (!legacyDb.exists()) {
            // Fresh install, or the import already ran and was cleaned up.
            markDone()
            runCatching { migrateAuth() }.onFailure { Timber.e(it) }
            return
        }

        val data = runCatching { readLegacy(legacyDb) }
            .onFailure { Timber.e(it) }
            .getOrNull()
            ?: return

        if (!runCatching { writeAll(data) }.onFailure { Timber.e(it) }.isSuccess) return

        // The database is committed. Move the bytes, keep the identity, and
        // only then remove the files — the order matters if the process dies
        // half-way: a retry re-runs the idempotent import instead of losing
        // the only copy of the user's library.
        val downloadsMoved = runCatching { moveDownloadCache() }
            .onFailure { Timber.e(it) }
            .getOrDefault(false)
        runCatching { migrateAuth() }.onFailure { Timber.e(it) }

        // If the download bytes could not move (a non-empty target or a failed
        // rename), keep the legacy database and do not mark the import done so
        // the next launch tries again. Everything written above is idempotent.
        if (!downloadsMoved) {
            Timber.w("Legacy downloads still in place; retrying the import next launch")
            return
        }

        runCatching {
            deleteDatabaseFiles(legacyDb)
            context.filesDir.resolve(LEGACY_PLAYBACK_CACHE).deleteRecursively()
        }.onFailure { Timber.e(it) }

        markDone()
    }

    private suspend fun markDone() {
        context.dataStore.edit { it[LegacyMigratedKey] = true }
    }

    // ── reading ────────────────────────────────────────────────────────────

    /**
     * Opens a private copy of the legacy database. Copying `-wal` and `-shm`
     * along with it means a database that was not checkpointed still opens
     * with all committed rows, and opening the copy read-write lets SQLite
     * recover it without touching the original.
     */
    private fun readLegacy(source: File): LegacyData {
        val copy = File(context.cacheDir, "legacy-mediyo-copy.db")
        deleteDatabaseFiles(copy)
        source.copyTo(copy, overwrite = true)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(source.path + suffix)
                .takeIf { it.exists() }
                ?.copyTo(File(copy.path + suffix), overwrite = true)
        }
        return try {
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { legacy ->
                    if (LEGACY_TABLES.none { legacy.hasTable(it) }) {
                        // Not our database (or one too old to know). Leave it
                        // alone and try again next launch rather than deleting
                        // something we did not understand.
                        error("mediyo.db has none of the expected legacy tables")
                    }
                    readTables(legacy)
                }
        } finally {
            deleteDatabaseFiles(copy)
        }
    }

    private fun readTables(legacy: SQLiteDatabase): LegacyData {
        val songs = LinkedHashMap<String, LegacySong>()
        val aggregates = HashMap<String, LegacyAggregate>()
        val playlists = mutableListOf<LegacyPlaylist>()
        val playlistEntries = linkedMapOf<String, MutableList<Pair<String, Int>>>()
        val albumBookmarks = mutableListOf<LegacyBookmark>()
        val artistBookmarks = mutableListOf<LegacyBookmark>()

        // Liked songs are both the liked list and library membership.
        if (legacy.hasTable("liked_tracks")) {
            legacy.query("liked_tracks").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("videoId") ?: continue
                    songs.seed(
                        id = id,
                        title = c.str("title"),
                        artist = c.str("artist"),
                        artistIds = c.str("artistIds"),
                        artworkUrl = c.str("artworkUrl"),
                        album = c.str("album"),
                        albumId = c.str("albumId"),
                        duration = c.str("duration"),
                        likedAt = c.long("likedAt"),
                        inLibraryAt = c.long("likedAt"),
                    )
                }
            }
        }

        // Downloads are library rows too; their bytes move later.
        if (legacy.hasTable("downloaded_tracks")) {
            legacy.query("downloaded_tracks").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("videoId") ?: continue
                    songs.seed(
                        id = id,
                        title = c.str("title"),
                        artist = c.str("artist"),
                        artworkUrl = c.str("artworkUrl"),
                        inLibraryAt = c.long("downloadedAt"),
                    )
                }
            }
        }

        // Local playlists: keep the name and the order. Every entry carries a
        // snapshot of the track, so an entry whose song is not liked or played
        // still gets a row.
        if (legacy.hasTable("local_playlists")) {
            legacy.query("local_playlists").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("id") ?: continue
                    val name = c.str("title").orEmpty().ifBlank { "Playlist" }
                    playlists += LegacyPlaylist(id, name)
                }
            }
        }
        if (legacy.hasTable("local_playlist_entries")) {
            legacy.query("local_playlist_entries").use { c ->
                while (c.moveToNext()) {
                    val playlistId = c.str("playlistId") ?: continue
                    val id = c.str("trackVideoId") ?: continue
                    songs.seed(
                        id = id,
                        title = c.str("title"),
                        artist = c.str("artist"),
                        artistIds = c.str("artistIds"),
                        artworkUrl = c.str("artworkUrl"),
                        album = c.str("album"),
                        albumId = c.str("albumId"),
                        duration = c.str("duration"),
                    )
                    playlistEntries.getOrPut(playlistId) { mutableListOf() }
                        .add(id to (c.int("position") ?: 0))
                }
            }
        }

        // Play history. `history_play_events` has one row per play and is the
        // closest thing to the old app's `event` table; `history_entries` is
        // the aggregate and is used both as a metadata fallback and to keep
        // the accumulated listening time when the per-play rows are missing.
        if (legacy.hasTable("history_entries")) {
            legacy.query("history_entries").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("videoId") ?: continue
                    songs.seed(
                        id = id,
                        title = c.str("title"),
                        artist = c.str("artist"),
                        artistIds = c.str("artistIds"),
                        artworkUrl = c.str("artworkUrl"),
                        album = c.str("album"),
                        albumId = c.str("albumId"),
                        duration = c.str("duration"),
                    )
                    aggregates[id] = LegacyAggregate(
                        lastPlayedAt = c.long("lastPlayedAt") ?: 0L,
                        totalPlayMs = c.long("totalPlayMs") ?: 0L,
                        playCount = c.int("playCount") ?: 0,
                    )
                }
            }
        }

        val events = mutableListOf<LegacyEvent>()
        if (legacy.hasTable("history_play_events")) {
            legacy.query("history_play_events").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("videoId") ?: continue
                    // The metadata tables above cover every track the previous
                    // app showed, but a play row is real history even if it is
                    // the only mention of the track; keep it with the id as a
                    // placeholder title rather than silently dropping it.
                    songs.seed(id = id)
                    val at = c.long("playedAt") ?: continue
                    events += LegacyEvent(id, at, c.long("playDurationMs") ?: 0L)
                }
            }
        }

        // Recent searches lived in `user_events` rows of type `search`, with
        // the query in `label`. Insert oldest first so the old `search_history`
        // table (which orders by id) keeps the most recent query on top.
        val searchQueries = LinkedHashMap<String, Unit>()
        if (legacy.hasTable("user_events")) {
            legacy.rawQuery(
                "SELECT label FROM user_events WHERE type = 'search' AND label IS NOT NULL " +
                    "ORDER BY createdAt ASC",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val query = c.getString(0).trim()
                    if (query.isEmpty()) continue
                    // Re-searching a term moves it to the top, matching how the
                    // old table orders by insertion id.
                    searchQueries.remove(query)
                    searchQueries[query] = Unit
                    if (searchQueries.size > 500) searchQueries.remove(searchQueries.keys.first())
                }
            }
        }

        val songsWithEvents = events.mapTo(HashSet()) { it.songId }
        aggregates.forEach { (id, aggregate) ->
            val song = songs[id] ?: return@forEach
            song.totalPlayMs = maxOf(song.totalPlayMs, aggregate.totalPlayMs)
            if (id !in songsWithEvents && aggregate.lastPlayedAt > 0L) {
                // No raw play rows: keep one aggregate event so the song still
                // appears in history and its listening time still counts.
                events += LegacyEvent(id, aggregate.lastPlayedAt, aggregate.totalPlayMs)
            }
        }
        events.groupByTo(HashMap()) { it.songId }
            .forEach { (id, songEvents) ->
                songs[id]?.let { song ->
                    song.totalPlayMs = maxOf(song.totalPlayMs, songEvents.sumOf { it.playTime })
                }
            }

        // Saved albums and followed artists are bookmarks in the old app, so
        // they map exactly. Saved playlists and podcasts have no equivalent
        // library entry in this UI and are intentionally left behind.
        if (legacy.hasTable("saved_collections")) {
            legacy.query("saved_collections").use { c ->
                while (c.moveToNext()) {
                    if (c.str("kind") != "ALBUM") continue
                    val id = c.str("browseId") ?: continue
                    albumBookmarks += LegacyBookmark(
                        id = id,
                        name = c.str("title").orEmpty(),
                        artworkUrl = c.str("artworkUrl"),
                        savedAt = c.long("savedAt") ?: 0L,
                    )
                }
            }
        }
        if (legacy.hasTable("followed_artists")) {
            legacy.query("followed_artists").use { c ->
                while (c.moveToNext()) {
                    val id = c.str("browseId") ?: continue
                    artistBookmarks += LegacyBookmark(
                        id = id,
                        name = c.str("name").orEmpty().ifBlank { "Unknown artist" },
                        artworkUrl = c.str("artworkUrl"),
                        savedAt = c.long("followedAt") ?: 0L,
                    )
                }
            }
        }

        // Drop entries that point at playlists the legacy store no longer has.
        val playlistIds = playlists.mapTo(HashSet()) { it.id }
        playlistEntries.keys.retainAll(playlistIds)

        return LegacyData(
            songs = songs,
            playlists = playlists,
            playlistEntries = playlistEntries,
            events = events,
            searchQueries = searchQueries.keys.toList(),
            albumBookmarks = albumBookmarks,
            artistBookmarks = artistBookmarks,
        )
    }

    // ── writing ────────────────────────────────────────────────────────────

    private fun writeAll(data: LegacyData) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val albumStats = HashMap<String, AlbumStats>()
            writeSongs(db, data.songs.values, albumStats)
            writeAlbums(db, albumStats)
            writePlaylists(db, data.playlists, data.playlistEntries)
            writeEvents(db, data.events)
            writeSearchHistory(db, data.searchQueries)
            writeAlbumBookmarks(db, data.albumBookmarks)
            writeArtistBookmarks(db, data.artistBookmarks)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writeSongs(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        songs: Collection<LegacySong>,
        albumStats: MutableMap<String, AlbumStats>,
    ) {
        songs.forEach { song ->
            val libraryAt = song.inLibraryAt ?: song.likedAt
            val values = ContentValues().apply {
                put("id", song.id)
                put("title", song.title.ifBlank { song.id })
                put("duration", song.duration)
                put("thumbnailUrl", song.artworkUrl)
                put("albumId", song.albumId)
                put("albumName", song.album)
                put("liked", if (song.likedAt != null) 1 else 0)
                put("totalPlayTime", song.totalPlayMs)
                put("inLibrary", libraryAt)
            }
            db.insert("song", SQLiteDatabase.CONFLICT_IGNORE, values)

            // A row that already existed (a retry, or a track that came from
            // another table) still needs the stronger flags.
            db.execSQL(
                "UPDATE song SET liked = MAX(liked, ?), inLibrary = COALESCE(inLibrary, ?), " +
                    "totalPlayTime = MAX(totalPlayTime, ?), thumbnailUrl = COALESCE(thumbnailUrl, ?), " +
                    "albumId = COALESCE(albumId, ?), albumName = COALESCE(albumName, ?) WHERE id = ?",
                arrayOf<Any?>(
                    if (song.likedAt != null) 1 else 0,
                    libraryAt,
                    song.totalPlayMs,
                    song.artworkUrl,
                    song.albumId,
                    song.album,
                    song.id,
                )
            )

            // The album row must exist before `album_artist_map` can point at
            // it, because both tables are foreign-key constrained.
            writeAlbum(db, song, albumStats)
            writeArtists(db, song)
        }
    }

    private fun writeArtists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        song: LegacySong,
    ) {
        val ids = song.artistIds.orEmpty().split(",").map { it.trim() }
            .filter { it.isNotEmpty() }
        val raw = song.artist.trim()
        if (raw.isEmpty()) return
        // The joined display string is ambiguous — "Earth, Wind & Fire" is one
        // artist while "A, B" is two. The parallel id list settles the common
        // single-artist case; otherwise split on the separator the old UI
        // itself uses to build the string.
        val names = if (ids.size == 1) {
            listOf(raw)
        } else {
            raw.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        }

        names.forEachIndexed { index, name ->
            // YouTube channel ids pass through; anything else becomes a local
            // artist. The fallback id is derived from the name so the same
            // artist does not fragment into one row per song.
            val id = ids.getOrNull(index)?.takeIf { it.isNotBlank() } ?: localArtistId(name)
            db.insert(
                "artist",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", id)
                    put("name", name)
                    put("thumbnailUrl", song.artworkUrl)
                    put("lastUpdateTime", System.currentTimeMillis())
                    put("bookmarkedAt", null as Long?)
                }
            )
            db.execSQL(
                "UPDATE artist SET thumbnailUrl = COALESCE(thumbnailUrl, ?) WHERE id = ?",
                arrayOf<Any?>(song.artworkUrl, id)
            )
            db.insert(
                "song_artist_map",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("songId", song.id)
                    put("artistId", id)
                    put("position", index)
                }
            )
            // The song's artists are also the best album credit the legacy
            // data has; without this the album screen shows no artist at all.
            song.albumId?.takeIf { it.isNotBlank() }?.let { albumId ->
                db.insert(
                    "album_artist_map",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("albumId", albumId)
                        put("artistId", id)
                        put("order", index)
                    }
                )
            }
        }
    }

    private fun writeAlbum(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        song: LegacySong,
        albumStats: MutableMap<String, AlbumStats>,
    ) {
        val albumId = song.albumId?.takeIf { it.isNotBlank() } ?: return
        db.insert(
            "album",
            SQLiteDatabase.CONFLICT_IGNORE,
            ContentValues().apply {
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
        )
        db.execSQL(
            "UPDATE album SET title = CASE WHEN title = '' THEN ? ELSE title END, " +
                "thumbnailUrl = COALESCE(thumbnailUrl, ?) WHERE id = ?",
            arrayOf<Any?>(song.album, song.artworkUrl, albumId)
        )
        // `index` is a keyword in SQLite, so it has to be quoted here; Room
        // does the same in its generated statements.
        db.execSQL(
            "INSERT OR IGNORE INTO song_album_map (songId, albumId, `index`) VALUES (?, ?, ?)",
            arrayOf<Any>(song.id, albumId, 0)
        )
        albumStats.merge(
            albumId,
            AlbumStats(1, song.duration.coerceAtLeast(0)),
        ) { a, b -> AlbumStats(a.songs + b.songs, a.duration + b.duration) }
    }

    private fun writeAlbums(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        albumStats: Map<String, AlbumStats>,
    ) {
        albumStats.forEach { (id, stats) ->
            db.execSQL(
                "UPDATE album SET songCount = ?, duration = ? WHERE id = ?",
                arrayOf<Any>(stats.songs, stats.duration, id)
            )
        }
    }

    private fun writePlaylists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        playlists: List<LegacyPlaylist>,
        entries: Map<String, List<Pair<String, Int>>>,
    ) {
        playlists.forEach { playlist ->
            val inserted = db.insert(
                "playlist",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("browseId", null as String?)
                }
            )
            // The id already belongs to a playlist this app knows (a retry, or
            // an unchanged row): its entries are already correct, and
            // replacing them would overwrite a playlist we do not own.
            if (inserted == -1L) return@forEach
            entries[playlist.id].orEmpty()
                .sortedBy { it.second }
                .forEachIndexed { index, (songId, _) ->
                    db.insert(
                        "playlist_song_map",
                        SQLiteDatabase.CONFLICT_IGNORE,
                        ContentValues().apply {
                            put("playlistId", playlist.id)
                            put("songId", songId)
                            put("position", index)
                        }
                    )
                }
        }
    }

    private fun writeEvents(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        events: List<LegacyEvent>,
    ) {
        events.forEach { event ->
            // The old table has no natural key, so deduplicate on the exact
            // row an earlier attempt would have written.
            db.execSQL(
                "INSERT INTO event (songId, timestamp, playTime) " +
                    "SELECT ?, ?, ? WHERE NOT EXISTS (" +
                    "SELECT 1 FROM event WHERE songId = ? AND timestamp = ? AND playTime = ?)",
                arrayOf<Any>(
                    event.songId, event.timestamp, event.playTime,
                    event.songId, event.timestamp, event.playTime,
                )
            )
        }
    }

    private fun writeSearchHistory(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        queries: List<String>,
    ) {
        queries.forEach { query ->
            db.insert(
                "search_history",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply { put("query", query) },
            )
        }
    }

    private fun writeAlbumBookmarks(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        bookmarks: List<LegacyBookmark>,
    ) {
        bookmarks.forEach { bookmark ->
            val at = bookmark.savedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            db.insert(
                "album",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", bookmark.id)
                    put("title", bookmark.name)
                    put("year", null as Int?)
                    put("thumbnailUrl", bookmark.artworkUrl)
                    put("themeColor", null as Int?)
                    put("songCount", 0)
                    put("duration", 0)
                    put("lastUpdateTime", at)
                    put("bookmarkedAt", at)
                }
            )
            db.execSQL(
                "UPDATE album SET bookmarkedAt = COALESCE(bookmarkedAt, ?), " +
                    "thumbnailUrl = COALESCE(thumbnailUrl, ?) WHERE id = ?",
                arrayOf<Any?>(at, bookmark.artworkUrl, bookmark.id)
            )
        }
    }

    private fun writeArtistBookmarks(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        bookmarks: List<LegacyBookmark>,
    ) {
        bookmarks.forEach { bookmark ->
            val at = bookmark.savedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            db.insert(
                "artist",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", bookmark.id)
                    put("name", bookmark.name)
                    put("thumbnailUrl", bookmark.artworkUrl)
                    put("lastUpdateTime", at)
                    put("bookmarkedAt", at)
                }
            )
            db.execSQL(
                "UPDATE artist SET bookmarkedAt = COALESCE(bookmarkedAt, ?), " +
                    "thumbnailUrl = COALESCE(thumbnailUrl, ?) WHERE id = ?",
                arrayOf<Any?>(at, bookmark.artworkUrl, bookmark.id)
            )
        }
    }

    /**
     * The download bytes live in a Media3 cache. Its index is keyed by a UID
     * file inside the directory, and the metadata lives in the shared
     * `exoplayer_internal.db`, so renaming the directory keeps the UID, the
     * index and the download rows the old app's DownloadManager reads.
     */
    private fun moveDownloadCache(): Boolean {
        val from = context.filesDir.resolve(LEGACY_DOWNLOAD_CACHE)
        val to = context.filesDir.resolve(DOWNLOAD_CACHE)
        if (!from.exists()) return true
        if (to.exists()) {
            // Only an empty directory is safe to remove; merging two caches
            // whose UID does not match the index would make both unreadable,
            // and deleting real downloads to make room would lose them.
            if (!to.listFiles().isNullOrEmpty()) {
                Timber.w("Download cache target is not empty; leaving both in place")
                return false
            }
            if (!to.deleteRecursively()) {
                Timber.w("Could not clear the empty download cache target")
                return false
            }
        }
        return from.renameTo(to).also {
            if (!it) Timber.w("Could not move the legacy download cache")
        }
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

    // ── helpers ────────────────────────────────────────────────────────────

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private fun SQLiteDatabase.query(table: String): Cursor = rawQuery("SELECT * FROM $table", null)

    private fun Cursor.str(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Cursor.int(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    /**
     * Merges a track row from another legacy table into the map. The first
     * source wins for metadata; later ones only fill blanks, because a liked
     * row is richer than a download row but a download row may be the only
     * place the artwork survived.
     */
    private fun LinkedHashMap<String, LegacySong>.seed(
        id: String,
        title: String? = null,
        artist: String? = null,
        artistIds: String? = null,
        artworkUrl: String? = null,
        album: String? = null,
        albumId: String? = null,
        duration: String? = null,
        likedAt: Long? = null,
        inLibraryAt: Long? = null,
    ): LegacySong {
        val song = getOrPut(id) { LegacySong(id = id, title = title.orEmpty(), artist = artist.orEmpty()) }
        if (song.title.isBlank() && !title.isNullOrBlank()) song.title = title
        if (song.artist.isBlank() && !artist.isNullOrBlank()) song.artist = artist
        if (song.artistIds.isNullOrBlank()) song.artistIds = artistIds
        if (song.artworkUrl.isNullOrBlank()) song.artworkUrl = artworkUrl
        if (song.album.isNullOrBlank()) song.album = album
        if (song.albumId.isNullOrBlank()) song.albumId = albumId
        if (song.duration <= 0) song.duration = parseDurationSeconds(duration) ?: song.duration
        if (likedAt != null && likedAt > 0 && (song.likedAt ?: 0) < likedAt) song.likedAt = likedAt
        if (inLibraryAt != null && inLibraryAt > 0 && (song.inLibraryAt ?: 0) < inLibraryAt) {
            song.inLibraryAt = inLibraryAt
        }
        return song
    }

    /**
     * Deterministic local-artist id. The old app uses random ids when it
     * creates one, but during an import a random id per song would split one
     * artist into as many rows as they have songs.
     */
    private fun localArtistId(name: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(name.trim().lowercase().toByteArray())
        return "LA" + digest.take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    // ── models ─────────────────────────────────────────────────────────────

    private data class LegacyData(
        val songs: LinkedHashMap<String, LegacySong>,
        val playlists: List<LegacyPlaylist>,
        val playlistEntries: Map<String, List<Pair<String, Int>>>,
        val events: List<LegacyEvent>,
        val searchQueries: List<String>,
        val albumBookmarks: List<LegacyBookmark>,
        val artistBookmarks: List<LegacyBookmark>,
    )

    private data class LegacySong(
        val id: String,
        var title: String,
        var artist: String,
        var artistIds: String? = null,
        var artworkUrl: String? = null,
        var album: String? = null,
        var albumId: String? = null,
        var duration: Int = -1,
        var likedAt: Long? = null,
        var inLibraryAt: Long? = null,
        var totalPlayMs: Long = 0L,
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

    private data class LegacyAggregate(
        val lastPlayedAt: Long,
        val totalPlayMs: Long,
        val playCount: Int,
    )

    private data class LegacyBookmark(
        val id: String,
        val name: String,
        val artworkUrl: String?,
        val savedAt: Long,
    )

    private data class AlbumStats(
        val songs: Int,
        val duration: Int,
    )
}
