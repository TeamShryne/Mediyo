use serde_json::{json, Value};

use crate::error::Result;
use crate::model::watch::{
    parse_lyrics, parse_queue, parse_queue_continuation, parse_song, Lyrics, Queue, Song,
};
use crate::session::Session;

/// The `params` value the web client sends for a song watch (protobuf-encoded).
const SONG_PARAMS: &str = "wAEB";

/// POST a raw `next` request for a video (and optional playlist). Returns the
/// raw response JSON.
pub fn next(session: &Session, video_id: &str, playlist_id: Option<&str>) -> Result<Value> {
    let mut body = json!({
        "videoId": video_id,
        "params": SONG_PARAMS,
        "isAudioOnly": true,
        "enablePersistentPlaylistPanel": true,
    });
    if let Some(pid) = playlist_id {
        body["playlistId"] = Value::String(pid.to_string());
    }
    session.request("next", body)
}

/// POST a raw `next` continuation request (extends an infinite radio queue).
/// Returns the raw response JSON.
pub fn next_continuation(session: &Session, token: &str) -> Result<Value> {
    let body = json!({ "continuation": token });
    session.request("next", body)
}

/// Fetch the currently playing song plus the full watch queue.
/// `playlist_id` (e.g. a radio mix `RDAMVM...` or the album `OLAK5uy_...`)
/// controls the queue; omit it for a bare single-song queue.
pub fn get_song(session: &Session, video_id: &str, playlist_id: Option<&str>) -> Result<Song> {
    let resp = next(session, video_id, playlist_id)?;
    parse_song(&resp)
}

/// Fetch just the watch queue for a video.
pub fn get_queue(session: &Session, video_id: &str, playlist_id: Option<&str>) -> Result<Queue> {
    let resp = next(session, video_id, playlist_id)?;
    parse_queue(&resp)
}

/// Extend a queue (e.g. an infinite radio mix) with its next batch of items
/// using the `Queue::continuation` token.
pub fn extend_queue(session: &Session, token: &str) -> Result<Queue> {
    let resp = next_continuation(session, token)?;
    parse_queue_continuation(&resp)
}

/// Fetch the lyrics for a song using the `lyrics_browse_id` from
/// [`Song::lyrics_browse_id`].
pub fn get_lyrics(session: &Session, lyrics_browse_id: &str) -> Result<Lyrics> {
    let body = serde_json::json!({ "browseId": lyrics_browse_id });
    let resp = session.request("browse", body)?;
    parse_lyrics(&resp)
}
