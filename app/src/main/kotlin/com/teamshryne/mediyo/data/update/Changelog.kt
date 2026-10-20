package com.teamshryne.mediyo.data.update

import kotlinx.serialization.Serializable

/**
 * The release history: every release and every nightly, with the notes it
 * shipped.
 *
 * This is the other half of the updater's contract. `update.json` says
 * there is a newer build and where to get it; this says what changed between
 * the build the user is running and the newest one, in words meant for the
 * person using the app.
 *
 * The notes are written when the change is made, in the commit that made it,
 * and the release publishes them. The app never has to guess what a version
 * did: it reads what was written at the time.
 */
@Serializable
data class Changelog(
    val entries: List<ChangelogEntry> = emptyList(),
)

/** One release or one nightly, with the notes it shipped. */
@Serializable
data class ChangelogEntry(
    val version: String = "",
    val versionCode: Int = 0,
    /** "stable" or "nightly". */
    val channel: String = "",
    val publishedAt: String = "",
    val notes: List<ChangelogNote> = emptyList(),
)

/**
 * One change a user can see.
 *
 * [commit] is what tells two notes apart. The same change can appear twice
 * in the history: a release rolls up the commits that were already
 * announced in a nightly, so a user who was on the nightly channel and then
 * moved to the stable one would otherwise read the same note twice.
 */
@Serializable
data class ChangelogNote(
    val commit: String = "",
    val text: String = "",
)

object ChangelogUrls {
    const val REPO = "TeamShryne/Mediyo"

    /**
     * The newest release's copy of the history. Every release carries the
     * whole thing -- all the releases before it as well -- so this one file
     * is the whole history up to the newest release.
     */
    const val LATEST = "https://github.com/$REPO/releases/latest/download/changelog.json"

    /**
     * The rolling nightly release's copy. It carries the releases and the
     * nightlies, because a nightly is built on top of a release.
     */
    const val NIGHTLY = "https://github.com/$REPO/releases/download/nightly/changelog.json"

    fun url(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> LATEST
        UpdateChannel.NIGHTLY -> NIGHTLY
    }
}

/**
 * The notes a user has not seen yet: the entries above the build they are
 * running, on the channel they follow.
 *
 * Two rules, and they are the whole of it:
 *
 * - a stable entry counts on either channel, because a nightly is built on
 *   top of the latest release, and a user moving from stable to nightly
 *   has not seen the release either;
 * - a nightly entry counts only for a user who follows the nightly channel.
 *
 * The newest entry comes first, and the notes inside an entry keep the order
 * the changes landed in.
 */
fun Changelog.notesSince(installedCode: Int, channel: UpdateChannel): List<ChangelogEntry> {
    val seen = HashSet<String>()
    return entries
        .filter { it.versionCode > installedCode }
        .filter { it.channel != "nightly" || channel == UpdateChannel.NIGHTLY }
        .sortedByDescending { it.versionCode }
        .mapNotNull { entry ->
            val notes = entry.notes.filter { note ->
                note.commit.isBlank() || seen.add(note.commit)
            }
            if (notes.isEmpty()) null else entry.copy(notes = notes)
        }
}
