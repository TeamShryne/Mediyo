package com.teamshryne.mediyo.core.design

import androidx.navigation.NavController

/**
 * Navigate to an artist page, ignoring ids that cannot open one.
 *
 * Artist browse ids reach us straight out of parsed InnerTube runs, and a run
 * can carry a name with no browse endpoint behind it — the core hands back an
 * empty string for that slot. A null check alone is not enough, because
 * `"".let { navigate(...) }` still runs, and `navigate("artist/")` throws
 * IllegalArgumentException ("Navigation destination that matches request
 * .../artist/ cannot be found"), which takes the app down.
 *
 * So: trim, and require something that actually looks like a browse id. A
 * plain name never reaches the graph either — callers with only a name should
 * resolve it first (see `MediaMenuVm.resolveArtistIdByName`).
 */
fun NavController?.navigateArtist(browseId: String?) {
    val id = browseId?.trim().orEmpty()
    if (id.isEmpty() || id.any { it.isWhitespace() || it == '/' }) return
    this?.navigate("artist/$id")
}

/** True when [browseId] can actually open an artist page. */
fun isOpenableArtistId(browseId: String?): Boolean {
    val id = browseId?.trim().orEmpty()
    return id.isNotEmpty() && id.none { it.isWhitespace() || it == '/' }
}

private fun NavController?.navigateRoute(route: String, id: String?) {
    val clean = id?.trim().orEmpty()
    if (clean.isEmpty() || clean.any { it.isWhitespace() || it == '/' }) return
    this?.navigate("$route/$clean")
}

/** Open an album, ignoring ids that cannot open one. See [navigateArtist]. */
fun NavController?.navigateAlbum(browseId: String?) = navigateRoute("album", browseId)

/** Open a playlist, ignoring ids that cannot open one. See [navigateArtist]. */
fun NavController?.navigatePlaylist(browseId: String?) = navigateRoute("playlist", browseId)

/** Open a channel, ignoring ids that cannot open one. See [navigateArtist]. */
fun NavController?.navigateChannel(browseId: String?) = navigateRoute("channel", browseId)

/** Open a podcast, ignoring ids that cannot open one. See [navigateArtist]. */
fun NavController?.navigatePodcast(browseId: String?) = navigateRoute("podcast", browseId)