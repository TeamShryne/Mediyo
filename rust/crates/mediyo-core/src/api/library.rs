use crate::error::Result;
use crate::model::library::{
    parse_account_info, parse_library_albums, parse_library_artists, parse_library_history,
    parse_library_playlists, parse_library_songs, AccountInfo, LibraryPage,
};
use crate::session::Session;

/// Like status for library items.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LikeStatus {
    Like,
    Dislike,
    Indifferent,
}

fn like_endpoint(status: LikeStatus) -> &'static str {
    match status {
        LikeStatus::Like => "like/like",
        LikeStatus::Dislike => "like/dislike",
        LikeStatus::Indifferent => "like/removelike",
    }
}

/// Fetch library playlists (`FEmusic_liked_playlists`).
pub fn playlists(session: &Session) -> Result<LibraryPage> {
    let resp = session.request("browse", serde_json::json!({"browseId": "FEmusic_liked_playlists"}))?;
    parse_library_playlists(&resp)
}

/// Fetch library songs (liked videos) (`FEmusic_liked_videos`).
pub fn songs(session: &Session) -> Result<LibraryPage> {
    let resp = session.request("browse", serde_json::json!({"browseId": "FEmusic_liked_videos"}))?;
    parse_library_songs(&resp)
}

/// Fetch liked albums (`FEmusic_liked_albums`).
pub fn albums(session: &Session) -> Result<LibraryPage> {
    let resp = session.request("browse", serde_json::json!({"browseId": "FEmusic_liked_albums"}))?;
    parse_library_albums(&resp)
}

/// Fetch library artists (`FEmusic_library_corpus_track_artists`).
pub fn artists(session: &Session) -> Result<LibraryPage> {
    let resp = session.request(
        "browse",
        serde_json::json!({"browseId": "FEmusic_library_corpus_track_artists"}),
    )?;
    parse_library_artists(&resp)
}

/// Fetch subscriptions (`FEmusic_library_corpus_artists`).
pub fn subscriptions(session: &Session) -> Result<LibraryPage> {
    let resp = session.request(
        "browse",
        serde_json::json!({"browseId": "FEmusic_library_corpus_artists"}),
    )?;
    parse_library_artists(&resp)
}

/// Fetch library landing (grid of 3 tiles).
pub fn landing(session: &Session) -> Result<LibraryPage> {
    let resp = session.request(
        "browse",
        serde_json::json!({"browseId": "FEmusic_library_landing"}),
    )?;
    parse_library_playlists(&resp)
}

/// Fetch history (`FEmusic_history`).
pub fn history(session: &Session) -> Result<LibraryPage> {
    let resp = session.request("browse", serde_json::json!({"browseId": "FEmusic_history"}))?;
    parse_library_history(&resp)
}

/// Fetch account info (`account/account_menu`).
pub fn account_info(session: &Session) -> Result<AccountInfo> {
    let resp = session.request("account/account_menu", serde_json::json!({}))?;
    parse_account_info(&resp)
}

// ── mutations ────────────────────────────────────────────────────────────────

/// Rate a song (LIKE / DISLIKE / INDIFFERENT).
pub fn rate_song(session: &Session, video_id: &str, status: LikeStatus) -> Result<serde_json::Value> {
    let body = serde_json::json!({"target": {"videoId": video_id}});
    session.request(like_endpoint(status), body)
}

/// Save or remove a playlist/album/podcast from the library.
/// For albums the `playlistId` is the `OLAK...` id, for podcasts the `MPSP...` id.
pub fn rate_playlist(session: &Session, playlist_id: &str, status: LikeStatus) -> Result<serde_json::Value> {
    let body = serde_json::json!({"target": {"playlistId": playlist_id}});
    session.request(like_endpoint(status), body)
}

/// Add a single video to a playlist (owned).
pub fn add_to_playlist(session: &Session, playlist_id: &str, video_id: &str) -> Result<serde_json::Value> {
    add_many_to_playlist(session, playlist_id, &[video_id])
}

/// Add multiple videos to a playlist at once.
pub fn add_many_to_playlist(
    session: &Session,
    playlist_id: &str,
    video_ids: &[&str],
) -> Result<serde_json::Value> {
    let actions: Vec<serde_json::Value> = video_ids
        .iter()
        .map(|vid| serde_json::json!({"action": "ACTION_ADD_VIDEO", "addedVideoId": vid}))
        .collect();
    let body = serde_json::json!({
        "playlistId": playlist_id,
        "actions": actions,
    });
    session.request("browse/edit_playlist", body)
}

/// Remove a video from a playlist (requires `setVideoId` from `get_playlist`).
pub fn remove_from_playlist(
    session: &Session,
    playlist_id: &str,
    set_video_id: &str,
    video_id: &str,
) -> Result<serde_json::Value> {
    let body = serde_json::json!({
        "playlistId": playlist_id,
        "actions": [{
            "action": "ACTION_REMOVE_VIDEO",
            "setVideoId": set_video_id,
            "removedVideoId": video_id
        }],
    });
    session.request("browse/edit_playlist", body)
}

/// Create a new playlist.
pub fn create_playlist(
    session: &Session,
    title: &str,
    description: &str,
    privacy: &str,
) -> Result<String> {
    let body = serde_json::json!({
        "title": title,
        "description": description,
        "privacyStatus": privacy,
    });
    let resp = session.request("playlist/create", body)?;
    resp.get("playlistId")
        .and_then(|v| v.as_str())
        .map(String::from)
        .ok_or(crate::error::Error::MissingField("playlistId"))
}
