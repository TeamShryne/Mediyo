package com.teamshryne.mediyo.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.teamshryne.mediyo.data.auth.AuthRepository
import com.teamshryne.mediyo.music.constants.LegacyMigratedKey
import com.teamshryne.mediyo.music.db.Converters
import com.teamshryne.mediyo.music.db.InternalDatabase
import com.teamshryne.mediyo.music.db.MusicDatabase
import com.teamshryne.mediyo.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Replays a real 0.9.0 database through the import.
 *
 * This test exists because the import shipped broken: a column named `order`
 * went into an insert unquoted, SQLite refused to parse it, and every user
 * with a liked song got an empty library instead — silently, because the
 * failure was caught and the legacy file was left for a retry that could
 * never succeed. The assertions therefore cover the whole path, not just the
 * tables that happened to be easy to check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LegacyMediyoMigrationTest {

    private lateinit var context: Context
    private lateinit var database: MusicDatabase
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() = runBlocking {
        // A device that is not on UTC is the case that used to shift every
        // imported timestamp, so the test runs in one.
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }

        database = MusicDatabase(
            Room.databaseBuilder(context, InternalDatabase::class.java, TEST_DB)
                .allowMainThreadQueries()
                .build()
        )

        // An album this install already knows about, with more songs on it
        // than the legacy library has. Importing must not shrink it.
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO album (id, title, year, thumbnailUrl, themeColor, songCount, duration, lastUpdateTime, bookmarkedAt) " +
                "VALUES ('album-one', 'Album One', NULL, NULL, NULL, 10, 999, 0, NULL)"
        )

        writeLegacyDatabase()
    }

    @After
    fun tearDown() = runBlocking {
        database.close()
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase("mediyo.db")
        File(context.cacheDir, "legacy-mediyo-copy.db").delete()
        context.filesDir.resolve("download").deleteRecursively()
        context.filesDir.resolve("mediyo_downloads").deleteRecursively()
        context.dataStore.edit { it.clear() }
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun importsTheLegacyLibrary() = runBlocking {
        LegacyMediyoMigration(context, database, AuthRepository(context)).run()

        val db = database.openHelper.readableDatabase

        // Every track from likes, downloads, playlists and history survived.
        assertEquals(5, count(db, "SELECT COUNT(*) FROM song"))
        assertEquals(3, count(db, "SELECT COUNT(*) FROM song WHERE liked = 1"))
        assertEquals(4, count(db, "SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL"))

        // The regression: `order` is a keyword, so this table used to be
        // impossible to write at all.
        assertEquals(4, count(db, "SELECT COUNT(*) FROM album_artist_map"))
        assertEquals(
            listOf(0, 1),
            ints(db, "SELECT `order` FROM album_artist_map WHERE albumId = 'album-two' ORDER BY `order`")
        )
        assertFalse(db.query("PRAGMA foreign_key_check").use { it.moveToFirst() })

        // "One, Two" with two ids is two artists; "Earth, Wind & Fire" with no
        // ids is one, not two.
        assertEquals(6, count(db, "SELECT COUNT(*) FROM artist"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM artist WHERE name = 'Earth, Wind & Fire'"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM artist WHERE name = 'Earth'"))

        // History: two raw plays for one song, one aggregate-only play for
        // another.
        assertEquals(3, count(db, "SELECT COUNT(*) FROM event"))
        // A play that only existed in the aggregate table still carries its
        // listening time.
        assertEquals(120_000L, long(db, "SELECT totalPlayTime FROM song WHERE id = 'v3'"))

        // Playlists keep their name, order and the entry that is not in the
        // library.
        assertEquals(1, count(db, "SELECT COUNT(*) FROM playlist"))
        assertEquals(
            listOf("v1", "v4"),
            strings(db, "SELECT songId FROM playlist_song_map ORDER BY position")
        )

        assertEquals(2, count(db, "SELECT COUNT(*) FROM search_history"))

        // Album bookmark migrated; the saved public playlist did not (it has
        // no library entry in this UI).
        assertEquals(1, count(db, "SELECT COUNT(*) FROM album WHERE bookmarkedAt IS NOT NULL"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM album WHERE id = 'album-bookmark'"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM album WHERE id = 'playlist-bookmark'"))
        // The pre-existing album kept its larger count.
        assertEquals(10, count(db, "SELECT songCount FROM album WHERE id = 'album-one'"))

        assertEquals(1, count(db, "SELECT COUNT(*) FROM artist WHERE bookmarkedAt IS NOT NULL"))

        // Timestamps are stored in the wall-clock convention the converters
        // use, so history renders on the day the user actually played it.
        val playedAt = long(db, "SELECT timestamp FROM event WHERE songId = 'v3'")
        assertEquals(
            Instant.ofEpochMilli(LEGACY_AGGREGATE_PLAYED_AT)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime(),
            Converters().fromTimestamp(playedAt),
        )

        // The import finished: the flag is set and the legacy file is gone.
        assertTrue(context.dataStore.data.first()[LegacyMigratedKey] == true)
        assertFalse(context.getDatabasePath("mediyo.db").exists())
    }

    @Test
    fun aSecondRunDoesNotDuplicateAnything() = runBlocking {
        LegacyMediyoMigration(context, database, AuthRepository(context)).run()
        val tables = listOf(
            "song", "artist", "album", "playlist", "playlist_song_map",
            "song_artist_map", "album_artist_map", "song_album_map",
            "event", "search_history",
        )
        val counts = tables.associateWith { count(database.openHelper.readableDatabase, "SELECT COUNT(*) FROM $it") }

        // A retry that starts from a clean flag re-reads the same data. The
        // file is gone after a successful import, so put a copy back: this is
        // the crash-between-commit-and-cleanup case.
        writeLegacyDatabase()
        context.dataStore.edit { it.clear() }
        LegacyMediyoMigration(context, database, AuthRepository(context)).run()

        val db = database.openHelper.readableDatabase
        counts.forEach { (table, expected) ->
            assertEquals("$table grew on the second import", expected, count(db, "SELECT COUNT(*) FROM $table"))
        }
        assertEquals(3, count(db, "SELECT COUNT(*) FROM event"))
    }

    // ── legacy fixture ─────────────────────────────────────────────────────

    private fun writeLegacyDatabase() {
        val file = context.getDatabasePath("mediyo.db")
        file.parentFile?.mkdirs()
        file.delete()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                "CREATE TABLE liked_tracks (videoId TEXT PRIMARY KEY, title TEXT, artist TEXT, " +
                    "artistIds TEXT, artworkUrl TEXT, album TEXT, albumId TEXT, duration TEXT, likedAt INTEGER)"
            )
            legacy.execSQL(
                "CREATE TABLE downloaded_tracks (videoId TEXT PRIMARY KEY, title TEXT, artist TEXT, " +
                    "artworkUrl TEXT, downloadedAt INTEGER)"
            )
            legacy.execSQL("CREATE TABLE local_playlists (id TEXT PRIMARY KEY, title TEXT)")
            legacy.execSQL(
                "CREATE TABLE local_playlist_entries (id TEXT, playlistId TEXT, trackVideoId TEXT, " +
                    "title TEXT, artist TEXT, artistIds TEXT, artworkUrl TEXT, album TEXT, albumId TEXT, " +
                    "duration TEXT, position INTEGER)"
            )
            legacy.execSQL(
                "CREATE TABLE history_entries (videoId TEXT PRIMARY KEY, title TEXT, artist TEXT, " +
                    "artworkUrl TEXT, albumId TEXT, lastPlayedAt INTEGER, totalPlayMs INTEGER, playCount INTEGER)"
            )
            legacy.execSQL("CREATE TABLE history_play_events (videoId TEXT, playedAt INTEGER, playDurationMs INTEGER)")
            legacy.execSQL("CREATE TABLE user_events (type TEXT, label TEXT, createdAt INTEGER)")
            legacy.execSQL(
                "CREATE TABLE saved_collections (browseId TEXT PRIMARY KEY, kind TEXT, title TEXT, " +
                    "artworkUrl TEXT, savedAt INTEGER)"
            )
            legacy.execSQL(
                "CREATE TABLE followed_artists (browseId TEXT PRIMARY KEY, name TEXT, artworkUrl TEXT, followedAt INTEGER)"
            )

            // A plain single-artist like with no artist ids.
            legacy.execSQL(
                "INSERT INTO liked_tracks (videoId, title, artist, artistIds, album, albumId, duration, likedAt) " +
                    "VALUES ('v1', 'Song One', 'Artist One', NULL, 'Album One', 'album-one', '3:45', $LEGACY_LIKED_AT)"
            )
            // Two artists, settled by the parallel id list.
            legacy.execSQL(
                "INSERT INTO liked_tracks (videoId, title, artist, artistIds, album, albumId, duration, likedAt) " +
                    "VALUES ('v2', 'Song Two', 'One, Two', 'UCa,UCb', 'Album Two', 'album-two', '245', $LEGACY_LIKED_AT)"
            )
            // A name with a comma in it and no ids: one artist, not two.
            legacy.execSQL(
                "INSERT INTO liked_tracks (videoId, title, artist, artistIds, album, albumId, duration, likedAt) " +
                    "VALUES ('v5', 'Song Five', 'Earth, Wind & Fire', NULL, 'Album Three', 'album-three', '200', $LEGACY_LIKED_AT)"
            )
            legacy.execSQL(
                "INSERT INTO downloaded_tracks (videoId, title, artist, artworkUrl, downloadedAt) " +
                    "VALUES ('v3', 'Downloaded', 'Solo Artist', 'art3', $LEGACY_DOWNLOAD_AT)"
            )

            legacy.execSQL("INSERT INTO local_playlists (id, title) VALUES ('p1', 'Road trip')")
            legacy.execSQL(
                "INSERT INTO local_playlist_entries (id, playlistId, trackVideoId, title, artist, albumId, " +
                    "duration, position) VALUES ('e1', 'p1', 'v1', 'Song One', 'Artist One', 'album-one', '3:45', 0)"
            )
            legacy.execSQL(
                "INSERT INTO local_playlist_entries (id, playlistId, trackVideoId, title, artist, albumId, " +
                    "duration, position) VALUES ('e2', 'p1', 'v4', 'Hidden', 'Band', NULL, '1:00', 1)"
            )

            // Aggregate-only history for the downloaded song.
            legacy.execSQL(
                "INSERT INTO history_entries (videoId, title, artist, albumId, lastPlayedAt, totalPlayMs, playCount) " +
                    "VALUES ('v3', 'Downloaded', 'Solo Artist', NULL, $LEGACY_AGGREGATE_PLAYED_AT, 120000, 2)"
            )
            legacy.execSQL(
                "INSERT INTO history_play_events (videoId, playedAt, playDurationMs) VALUES " +
                    "('v2', $LEGACY_PLAY_AT, 90000), ('v2', ${LEGACY_PLAY_AT + 1000}, 30000)"
            )

            legacy.execSQL(
                "INSERT INTO user_events (type, label, createdAt) VALUES " +
                    "('search', 'hello', 1), ('search', 'world', 2), ('view', 'ignored', 3)"
            )
            legacy.execSQL(
                "INSERT INTO saved_collections (browseId, kind, title, savedAt) VALUES " +
                    "('album-bookmark', 'ALBUM', 'Saved Album', $LEGACY_SAVED_AT), " +
                    "('playlist-bookmark', 'PLAYLIST', 'Saved Playlist', $LEGACY_SAVED_AT)"
            )
            legacy.execSQL(
                "INSERT INTO followed_artists (browseId, name, followedAt) VALUES " +
                    "('UCartist', 'Followed Artist', $LEGACY_SAVED_AT)"
            )
        } finally {
            legacy.close()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun long(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun ints(db: SupportSQLiteDatabase, sql: String): List<Int> =
        db.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }

    private fun strings(db: SupportSQLiteDatabase, sql: String): List<String> =
        db.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private companion object {
        const val TEST_DB = "test-song.db"

        const val LEGACY_LIKED_AT = 1_700_000_000_000L
        const val LEGACY_DOWNLOAD_AT = 1_700_000_100_000L
        const val LEGACY_AGGREGATE_PLAYED_AT = 1_700_000_200_000L
        const val LEGACY_PLAY_AT = 1_700_000_300_000L
        const val LEGACY_SAVED_AT = 1_700_000_400_000L
    }
}
