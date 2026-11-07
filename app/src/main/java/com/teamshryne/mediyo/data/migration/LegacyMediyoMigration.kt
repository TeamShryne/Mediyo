package com.teamshryne.mediyo.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teamshryne.mediyo.data.auth.AuthRepository
import com.teamshryne.mediyo.data.mediyo.parseDurationSeconds
import com.teamshryne.mediyo.music.constants.InnerTubeCookieKey
import com.teamshryne.mediyo.music.constants.LegacyMigrationAttemptsKey
import com.teamshryne.mediyo.music.constants.LegacyMigratedKey
import com.teamshryne.mediyo.music.constants.VisitorDataKey
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
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
 * - a row that cannot be written is skipped and logged instead of taking the
 *   rest of the library down with it, and an import that skips everything is
 *   treated as a failure rather than a success;
 * - a failure leaves the legacy file in place for the next launch, but the
 *   number of launches is capped: a file that cannot be read will not heal,
 *   and retrying forever slows every start with nothing to show for it;
 * - nothing in here throws into `onCreate`.
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

        /**
         * How many launches the import may fail before it stops trying. A
         * database that cannot be read, or an app database that cannot be
         * written, is not going to heal on its own; the alternative is a
         * broken import that runs on every start forever.
         */
        const val MAX_ATTEMPTS = 3

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

    private val started = AtomicBoolean(false)

    /**
     * Starts the import on a background thread.
     *
     * It used to run with `runBlocking` from `Application.onCreate`, which held
     * the first frame hostage for as long as the import took — a large library
     * could freeze the launch. Nothing here needs to finish before the UI can
     * draw: every write is idempotent, and Room refreshes the screens that are
     * watching `song.db` once the transaction commits.
     */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) { run() }
    }

    suspend fun run() {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return
        if (prefs[LegacyMigratedKey] == true) return

        val legacyDb = context.getDatabasePath(LEGACY_DB)
        if (!legacyDb.exists()) {
            // Fresh install, or the import already ran and was cleaned up.
            markDone()
            runCatching { migrateAuth() }.onFailure { Timber.e(it) }
            return
        }

        val attempt = (prefs[LegacyMigrationAttemptsKey] ?: 0) + 1
        recordAttempt(attempt)

        val data = try {
            readLegacy(legacyDb)
        } catch (e: NotLegacyDatabase) {
            // Not our database (or one too old to know). Leave the file alone
            // and stop looking at it: there is nothing to import from it.
            Timber.w(e, "mediyo.db does not look like the previous app's database")
            giveUp(attempt)
            return
        } catch (e: Exception) {
            Timber.e(e, "Could not read mediyo.db (attempt $attempt of $MAX_ATTEMPTS)")
            giveUp(attempt)
            return
        }

        val songsWritten = try {
            writeAll(data)
        } catch (e: Exception) {
            Timber.e(e, "Could not import mediyo.db (attempt $attempt of $MAX_ATTEMPTS)")
            giveUp(attempt)
            return
        }

        // Every song failing means the mapping itself is wrong, not that the
        // user had an odd row. Retrying is the only honest answer.
        if (data.songs.isNotEmpty() && songsWritten == 0) {
            Timber.e("The import wrote no songs; treating it as a failure")
            giveUp(attempt)
            return
        }

        // The database is committed. Move the bytes, keep the identity, and
        // only then remove the files — the order matters if the process dies
        // half-way: a retry re-runs the idempotent import instead of losing
        // the only copy of the user's library.
        val downloadsMoved = runCatching { moveDownloadCache() }
            .onFailure { Timber.e(it) }
            .getOrDefault(false)
        runCatching { migrateAuth() }.onFailure { Timber.e(it) }

        // A rename that genuinely failed is worth one more go. Everything
        // written above is idempotent, so the retry re-imports over itself.
        if (!downloadsMoved) {
            Timber.w("Legacy downloads still in place; retrying the import next launch")
            giveUp(attempt)
            return
        }

        runCatching {
            deleteDatabaseFiles(legacyDb)
            context.filesDir.resolve(LEGACY_PLAYBACK_CACHE).deleteRecursively()
        }.onFailure { Timber.e(it) }

        markDone()
    }

    /**
     * Stops retrying once the attempt budget is spent, without deleting the
     * legacy file: the import failed, but the user's only copy of it (and any
     * download bytes next to it) stays where it is.
     */
    private suspend fun giveUp(attempt: Int) {
        if (attempt < MAX_ATTEMPTS) return
        Timber.e("Giving up on the mediyo.db import after $attempt attempts; the file is kept")
        runCatching { markDone() }.onFailure { Timber.e(it) }
        runCatching { migrateAuth() }.onFailure { Timber.e(it) }
    }

    private suspend fun recordAttempt(attempt: Int) {
        runCatching {
            context.dataStore.edit { it[LegacyMigrationAttemptsKey] = attempt }
        }.onFailure { Timber.e(it) }
    }

    private suspend fun markDone() {
        context.dataStore.edit { it[LegacyMigratedKey] = true }
    }

    // ── reading ────────────────────────────────────────────────────────────

    /** A database that exists under our name but cannot be the old app's. */
    private class NotLegacyDatabase(message: String) : Exception(message)

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
                        throw NotLegacyDatabase("mediyo.db has none of the expected legacy tables")
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

    /** @return how many songs were written, so the caller can spot a total failure. */
    private fun writeAll(data: LegacyData): Int {
        val db = database.openHelper.writableDatabase
        var written = 0
        db.beginTransaction()
        try {
            val albumStats = HashMap<String, AlbumStats>()
            data.songs.values.forEach { song ->
                guard(db, "song ${song.id}") {
                    writeSong(db, song, albumStats)
                    written++
                }
            }
            writeAlbums(db, albumStats)
            data.playlists.forEach { playlist ->
                guard(db, "playlist ${playlist.id}") {
                    writePlaylist(db, playlist, data.playlistEntries[playlist.id].orEmpty())
                }
            }
            data.events.forEach { event ->
                guard(db, "play ${event.songId} at ${event.timestamp}") { writeEvent(db, event) }
            }
            data.searchQueries.forEach { query ->
                guard(db, "search \"$query\"") { writeSearch(db, query) }
            }
            data.albumBookmarks.forEach { bookmark ->
                guard(db, "album bookmark ${bookmark.id}") { writeAlbumBookmark(db, bookmark) }
            }
            data.artistBookmarks.forEach { bookmark ->
                guard(db, "artist bookmark ${bookmark.id}") { writeArtistBookmark(db, bookmark) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return written
    }

    /**
     * Runs one row's writes, and keeps the import alive if that row is bad.
     *
     * A failed statement does not by itself end a SQLite transaction, so the
     * next row can still land. If the transaction is already gone — a full
     * disk, an I/O error — there is nothing left to write into and the caller
     * has to retry the whole thing, so the error is rethrown.
     */
    private inline fun guard(db: SupportSQLiteDatabase, label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (!db.inTransaction()) throw e
            Timber.w(e, "Skipped $label")
        }
    }

    private fun writeSong(
        db: SupportSQLiteDatabase,
        song: LegacySong,
        albumStats: MutableMap<String, AlbumStats>,
    ) {
        val libraryAt = (song.inLibraryAt ?: song.likedAt)?.let { localWallClock(it) }
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

        // The album row must exist before `album_artist_map` can point at it,
        // because both tables are foreign-key constrained.
        writeAlbum(db, song, albumStats)
        writeArtists(db, song)
    }

    private fun writeArtists(
        db: SupportSQLiteDatabase,
        song: LegacySong,
    ) {
        val ids = song.artistIds.orEmpty().split(",").map { it.trim() }
            .filter { it.isNotEmpty() }
        val raw = song.artist.trim()
        if (raw.isEmpty()) return

        // The joined display string is ambiguous — "Earth, Wind & Fire" is one
        // artist while "A, B" is two. The parallel id list settles it whenever
        // it is there; splitting is only safe when the number of ids proves
        // the separator was a list separator.
        val credits = when {
            ids.isEmpty() -> listOf(raw to localArtistId(raw))
            ids.size == 1 -> listOf(raw to ids.first())
            else -> {
                val names = raw.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
                if (names.size == ids.size) {
                    names.zip(ids)
                } else {
                    // The id list and the display string disagree (a name with
                    // a comma in it, most likely). Trusting the ids would mean
                    // inventing names, so keep the display string whole.
                    Timber.d("Artist list does not line up for ${song.id}; keeping \"$raw\" as one credit")
                    listOf(raw to ids.first())
                }
            }
        }

        credits.forEachIndexed { index, (name, id) ->
            db.insert(
                "artist",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", id)
                    put("name", name)
                    put("thumbnailUrl", song.artworkUrl)
                    put("lastUpdateTime", nowWallClock())
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
                // `order` is a reserved word, and `SQLiteDatabase.insert`
                // pastes ContentValues keys into the statement unquoted, so
                // going through it produces `... (albumId,artistId,order) ...`
                // and SQLite refuses to even parse it. Raw SQL lets it be
                // quoted, the same way `index` is below.
                db.execSQL(
                    "INSERT OR IGNORE INTO album_artist_map (albumId, artistId, `order`) VALUES (?, ?, ?)",
                    arrayOf<Any>(albumId, id, index)
                )
            }
        }
    }

    private fun writeAlbum(
        db: SupportSQLiteDatabase,
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
                put("lastUpdateTime", nowWallClock())
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
        db: SupportSQLiteDatabase,
        albumStats: Map<String, AlbumStats>,
    ) {
        albumStats.forEach { (id, stats) ->
            // The legacy count only covers the songs the old app knew about.
            // Taking the larger of the two keeps an album the new app has
            // already seen from shrinking on import, and makes a retry a
            // no-op instead of a downgrade.
            db.execSQL(
                "UPDATE album SET songCount = MAX(songCount, ?), duration = MAX(duration, ?) WHERE id = ?",
                arrayOf<Any>(stats.songs, stats.duration, id)
            )
        }
    }

    private fun writePlaylist(
        db: SupportSQLiteDatabase,
        playlist: LegacyPlaylist,
        entries: List<Pair<String, Int>>,
    ) {
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
        // an unchanged row): its entries are already correct, and replacing
        // them would overwrite a playlist we do not own.
        if (inserted == -1L) return
        entries.sortedBy { it.second }
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

    private fun writeEvent(
        db: SupportSQLiteDatabase,
        event: LegacyEvent,
    ) {
        // The old table has no natural key, so deduplicate on the exact row an
        // earlier attempt would have written.
        val at = localWallClock(event.timestamp)
        db.execSQL(
            "INSERT INTO event (songId, timestamp, playTime) " +
                "SELECT ?, ?, ? WHERE NOT EXISTS (" +
                "SELECT 1 FROM event WHERE songId = ? AND timestamp = ? AND playTime = ?)",
            arrayOf<Any>(
                event.songId, at, event.playTime,
                event.songId, at, event.playTime,
            )
        )
    }

    private fun writeSearch(db: SupportSQLiteDatabase, query: String) {
        db.insert(
            "search_history",
            SQLiteDatabase.CONFLICT_IGNORE,
            ContentValues().apply { put("query", query) },
        )
    }

    private fun writeAlbumBookmark(db: SupportSQLiteDatabase, bookmark: LegacyBookmark) {
        val at = bookmark.savedAt.takeIf { it > 0 }?.let { localWallClock(it) } ?: nowWallClock()
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

    private fun writeArtistBookmark(db: SupportSQLiteDatabase, bookmark: LegacyBookmark) {
        val at = bookmark.savedAt.takeIf { it > 0 }?.let { localWallClock(it) } ?: nowWallClock()
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

        if (!to.exists()) {
            return from.renameTo(to).also {
                if (!it) Timber.w("Could not move the legacy download cache")
            }
        }

        if (to.isDirectory && to.listFiles().isNullOrEmpty()) {
            // An empty target is the only one that can be replaced outright.
            if (!to.deleteRecursively()) {
                Timber.w("Could not clear the empty download cache target")
                return false
            }
            return from.renameTo(to).also {
                if (!it) Timber.w("Could not move the legacy download cache")
            }
        }

        // Merging is not possible: the two caches have different UIDs and the
        // index of one would point at the other's files. Deleting the user's
        // audio to make room is worse than keeping it, and blocking the import
        // forever — which is what this used to do — means the library never
        // arrives either. Move the old cache aside and let the import finish.
        val aside = context.filesDir.resolve("$LEGACY_DOWNLOAD_CACHE.kept-${System.currentTimeMillis()}")
        if (!from.renameTo(aside)) {
            Timber.w("Could not move the legacy download cache out of the way")
            return false
        }
        Timber.w("The new download cache was not empty; the old one was kept at ${aside.name}")
        return true
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
     * The new tables store a `LocalDateTime` as the wall clock re-read as if
     * it were UTC (see `Converters`). The legacy columns are true epoch
     * millis, so copying them raw would render every migrated timestamp
     * shifted by the device's UTC offset — history grouped under the wrong
     * day, bookmarks dated in the future or the past.
     */
    private fun localWallClock(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    /** `System.currentTimeMillis()` in the same wall-clock convention. */
    private fun nowWallClock(): Long = localWallClock(System.currentTimeMillis())

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
