package com.teamshryne.mediyo.domain.repository

import com.teamshryne.mediyo.data.local.UserEventEntity
import kotlinx.coroutines.flow.Flow

/** Event types for [UserEventRepository]. Keep stable — stored in DB. */
object UserEventTypes {
    const val SEARCH = "search"
    const val SEARCH_TAP = "search_tap"
    const val HOME_TAP = "home_tap"
    const val VIEW_ALBUM = "view_album"
    const val VIEW_PLAYLIST = "view_playlist"
    const val VIEW_ARTIST = "view_artist"
    const val VIEW_SECTION = "view_section"
    const val VIEW_PODCAST = "view_podcast"
    const val VIEW_EPISODES = "view_episodes"
    const val QUEUE_ADD_NEXT = "queue_add_next"
    const val QUEUE_ADD_LAST = "queue_add_last"
    const val QUEUE_ADD_NEXT_LIST = "queue_add_next_list"
    const val QUEUE_ADD_LAST_LIST = "queue_add_last_list"
    const val QUEUE_REMOVE = "queue_remove"
    const val QUEUE_MOVE = "queue_move"
    const val LIKE = "like"
    const val UNLIKE = "unlike"
    const val FOLLOW = "follow"
    const val UNFOLLOW = "unfollow"
    const val SAVE = "save"
    const val UNSAVE = "unsave"
    const val PLAYLIST_CREATE = "playlist_create"
    const val PLAYLIST_DELETE = "playlist_delete"
    const val PLAYLIST_ADD = "playlist_add"
    const val PLAYLIST_REMOVE = "playlist_remove"
    const val PLAYLIST_REORDER = "playlist_reorder"
    const val PLAYLIST_CLEAR = "playlist_clear"
    const val SHUFFLE = "shuffle"
    const val REPEAT = "repeat"
    const val SEEK = "seek"
    const val SLEEP_SET = "sleep_set"
    const val SLEEP_END_OF_TRACK = "sleep_end_of_track"
    const val SLEEP_END_OF_QUEUE = "sleep_end_of_queue"
    const val SLEEP_CANCEL = "sleep_cancel"
    const val LYRICS_OPEN = "lyrics_open"
    const val COMMENTS_OPEN = "comments_open"
    const val WIDGET_TOGGLE = "widget_toggle"
    const val WIDGET_NEXT = "widget_next"
    const val WIDGET_PREV = "widget_prev"
    const val WIDGET_LIKE = "widget_like"
    const val WIDGET_PLAYLIST = "widget_playlist"
    const val WIDGET_LIKED = "widget_liked"
}

interface UserEventRepository {
    suspend fun log(
        type: String,
        videoId: String? = null,
        browseId: String? = null,
        label: String? = null,
        meta: String? = null,
        originType: String? = null,
        originLabel: String? = null,
        at: Long = System.currentTimeMillis()
    )

    fun flowRecent(limit: Int = 200): Flow<List<UserEventEntity>>
    suspend fun byType(type: String, limit: Int = 200): List<UserEventEntity>
    suspend fun since(since: Long): List<UserEventEntity>
    suspend fun countByTypeSince(type: String, since: Long): Int
    suspend fun clearAll()
    suspend fun clearType(type: String)
}
