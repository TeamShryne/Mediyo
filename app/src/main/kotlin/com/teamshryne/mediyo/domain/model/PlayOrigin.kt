package com.teamshryne.mediyo.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class PlayOrigin {
    @Serializable data class Playlist(val id: String, val title: String, val playlistId: String? = null) : PlayOrigin()
    @Serializable data class Album(val id: String, val title: String) : PlayOrigin()
    @Serializable data class ArtistTop(val id: String, val name: String) : PlayOrigin()
    @Serializable data class Search(val query: String, val filter: String? = null) : PlayOrigin()
    @Serializable data class HomeShelf(val title: String) : PlayOrigin()
    @Serializable data class Podcast(val id: String) : PlayOrigin()
    @Serializable data class GenericList(val id: String) : PlayOrigin()
    @Serializable data class Liked(val count: Int = 0) : PlayOrigin()
    @Serializable data class LocalPlaylist(val id: String, val title: String) : PlayOrigin()
    @Serializable data class History(val label: String = "History") : PlayOrigin()
    @Serializable data class Single(val videoId: String) : PlayOrigin()
    @Serializable data class Radio(val seedVideoId: String) : PlayOrigin()
    @Serializable data object Unknown : PlayOrigin()

    fun label(): String = when (this) {
        is Playlist -> title
        is Album -> title
        is ArtistTop -> name
        is Search -> query
        is HomeShelf -> title
        is Podcast -> "Podcast"
        is GenericList -> "List"
        is Liked -> "Liked songs"
        is LocalPlaylist -> title
        is History -> "History"
        is Single -> "Single"
        is Radio -> "Radio"
        Unknown -> "Mediyo"
    }

    fun playlistIdForRadio(): String? = when (this) {
        is Playlist -> playlistId
        else -> null
    }
}

@Serializable
data class PlayQueueState(
    val origin: PlayOrigin = PlayOrigin.Unknown,
    val entries: List<Track> = emptyList(),
    val index: Int = -1,
    val radioContinuation: String? = null,
    val isFetchingRadio: Boolean = false,
    val isRadioEnabled: Boolean = true
) {
    val current: Track? get() = entries.getOrNull(index)
    val size: Int get() = entries.size
    val hasQueue: Boolean get() = entries.isNotEmpty() && index in entries.indices
}
