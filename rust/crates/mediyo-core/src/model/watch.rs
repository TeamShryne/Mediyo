use serde_json::Value;

use crate::error::{Error, Result};
use crate::model::search::ArtistRef;
use crate::parser::{self, runs};

/// A single track in the watch queue (`playlistPanelVideoRenderer`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct QueueItem {
    pub video_id: String,
    pub playlist_set_video_id: String,
    pub title: String,
    pub artists: Vec<ArtistRef>,
    pub album: Option<String>,
    /// Duration as text (e.g. "3:30").
    pub duration: Option<String>,
    /// True for the currently playing track.
    pub selected: bool,
    /// Position within the queue.
    pub index: Option<i32>,
    pub thumbnail: Option<String>,
}

/// The watch queue (`musicQueueRenderer.content.playlistPanelRenderer`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Queue {
    pub playlist_id: String,
    pub is_infinite: bool,
    /// Continuation token to load more queue entries.
    pub continuation: Option<String>,
    pub items: Vec<QueueItem>,
}

/// The currently playing song plus its watch queue.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Song {
    pub video_id: String,
    pub playlist_set_video_id: String,
    pub title: String,
    pub artists: Vec<ArtistRef>,
    pub album: Option<String>,
    pub duration: Option<String>,
    pub thumbnail: Option<String>,
    /// playlistId of the current queue (e.g. a radio mix `RDAMVM...`).
    pub playlist_id: String,
    /// browseId for the lyrics page (fetch with `api::watch::get_lyrics`).
    pub lyrics_browse_id: Option<String>,
    pub queue: Vec<QueueItem>,
}

/// Lyrics text for a song (lines of text).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Lyrics {
    /// Lyrics lines (splits on `\r\n` / `\n` boundaries).
    pub lines: Vec<String>,
}

/// Parse the watch queue from a `next` response.
pub fn parse_queue(resp: &Value) -> Result<Queue> {
    let panel = queue_panel(resp).ok_or(Error::MissingField("queue panel"))?;
    parse_panel(panel)
}

/// Parse a queue continuation response (`continuationContents.playlistPanelContinuation`)
/// into the next batch of queue items.
pub fn parse_queue_continuation(resp: &Value) -> Result<Queue> {
    let panel = resp
        .pointer("/continuationContents/playlistPanelContinuation")
        .ok_or(Error::MissingField("queue continuation panel"))?;
    parse_panel(panel)
}

fn parse_panel(panel: &Value) -> Result<Queue> {
    let playlist_id = panel
        .get("playlistId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string();
    let is_infinite = panel
        .get("isInfinite")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    let continuation = panel
        .get("continuations")
        .and_then(Value::as_array)
        .and_then(|c| c.first())
        .and_then(continuation_token)
        .map(String::from);

    let mut items = Vec::new();
    if let Some(contents) = panel.get("contents").and_then(Value::as_array) {
        for content in contents {
            if let Some((rname, _)) = parser::renderer(content) {
                if rname == "playlistPanelVideoRenderer" {
                    items.push(parse_queue_item(content)?);
                }
            }
        }
    }

    Ok(Queue {
        playlist_id,
        is_infinite,
        continuation,
        items,
    })
}

/// Parse the currently playing song plus its queue from a `next` response.
pub fn parse_song(resp: &Value) -> Result<Song> {
    let queue = parse_queue(resp)?;
    let current = queue
        .items
        .iter()
        .find(|i| i.selected)
        .or_else(|| queue.items.first())
        .ok_or(Error::MissingField("current queue item"))?;

    let album = current
        .album
        .clone()
        .or_else(|| {
            resp.pointer(
                "/playerOverlays/playerOverlayRenderer/browserMediaSession/browserMediaSessionRenderer/album",
            )
            .and_then(runs::text)
        });

    let lyrics_browse_id = lyrics_tab_browse_id(resp).map(String::from);

    Ok(Song {
        video_id: current.video_id.clone(),
        playlist_set_video_id: current.playlist_set_video_id.clone(),
        title: current.title.clone(),
        artists: current.artists.clone(),
        album,
        duration: current.duration.clone(),
        thumbnail: current.thumbnail.clone(),
        playlist_id: queue.playlist_id.clone(),
        lyrics_browse_id,
        queue: queue.items,
    })
}

/// Parse a lyrics `browse` response into a [`Lyrics`] struct.
pub fn parse_lyrics(resp: &Value) -> Result<Lyrics> {
    let desc = resp
        .pointer(
            "/contents/sectionListRenderer/contents/0/musicDescriptionShelfRenderer/description",
        )
        .ok_or(Error::MissingField("lyrics description"))?;

    let text = runs::text(desc).unwrap_or_default();
    let lines: Vec<String> = text
        .split("\r\n")
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();

    if lines.is_empty() {
        return Err(Error::Missing("lyrics lines"));
    }

    Ok(Lyrics { lines })
}

/// Extract the lyrics browseId from the "Lyrics" tab in a `next` response.
fn lyrics_tab_browse_id(resp: &Value) -> Option<&str> {
    let tabs = resp
        .pointer(
            "/contents/singleColumnMusicWatchNextResultsRenderer/tabbedRenderer/watchNextTabbedResultsRenderer/tabs",
        )
        .and_then(Value::as_array)?;
    for tab in tabs {
        let tr = tab.get("tabRenderer")?;
        if tr.get("title").and_then(Value::as_str) == Some("Lyrics") {
            return tr
                .pointer("/endpoint/browseEndpoint/browseId")
                .and_then(Value::as_str);
        }
    }
    None
}

fn queue_panel(resp: &Value) -> Option<&Value> {
    resp.pointer(
        "/contents/singleColumnMusicWatchNextResultsRenderer/tabbedRenderer/watchNextTabbedResultsRenderer/tabs/0/tabRenderer/content/musicQueueRenderer/content/playlistPanelRenderer",
    )
}

/// Extract the continuation token regardless of its data wrapper
/// (`nextContinuationData`, `reloadContinuationData`, `nextRadioContinuationData`).
fn continuation_token(v: &Value) -> Option<&str> {
    let obj = v.as_object()?;
    for key in [
        "nextContinuationData",
        "reloadContinuationData",
        "nextRadioContinuationData",
    ] {
        if let Some(tok) = obj
            .get(key)
            .and_then(|d| d.get("continuation"))
            .and_then(Value::as_str)
        {
            return Some(tok);
        }
    }
    None
}

fn parse_queue_item(v: &Value) -> Result<QueueItem> {
    let (_, payload) = parser::renderer(v).ok_or(Error::Missing("renderer"))?;

    let video_id = payload
        .get("videoId")
        .and_then(Value::as_str)
        .ok_or(Error::MissingField("videoId"))?
        .to_string();
    let playlist_set_video_id = payload
        .get("playlistSetVideoId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string();
    let title = payload
        .get("title")
        .and_then(runs::text)
        .unwrap_or_default();
    let (artists, album) = parse_byline(payload.get("longBylineText"));
    let duration = payload.get("lengthText").and_then(runs::text);
    let selected = payload
        .get("selected")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    let index = payload
        .pointer("/navigationEndpoint/watchEndpoint/index")
        .and_then(Value::as_i64)
        .map(|i| i as i32);
    let thumbnail = largest_thumbnail(payload.get("thumbnail"));

    Ok(QueueItem {
        video_id,
        playlist_set_video_id,
        title,
        artists,
        album,
        duration,
        selected,
        index,
        thumbnail,
    })
}

/// Split a `longBylineText` node into linked artists and the album name.
/// Runs without a navigation endpoint (separators, view/like counts) are
/// skipped.
fn parse_byline(node: Option<&Value>) -> (Vec<ArtistRef>, Option<String>) {
    let Some(node) = node else {
        return (Vec::new(), None);
    };
    let mut artists = Vec::new();
    let mut album = None;
    for (text, ep) in runs::run_items(node) {
        let Some(ep) = ep else { continue };
        let Some(parser::Endpoint::Browse { id }) = parser::endpoint(ep) else {
            continue;
        };
        if id.starts_with("UC") {
            artists.push(ArtistRef {
                name: text.to_string(),
                id: Some(id.to_string()),
            });
        } else if parser::page_type(ep) == Some("MUSIC_PAGE_TYPE_ALBUM") || id.starts_with("MPREb_")
        {
            album = Some(text.to_string());
        }
    }
    (artists, album)
}

fn largest_thumbnail(node: Option<&Value>) -> Option<String> {
    node?
        .get("thumbnails")?
        .as_array()?
        .iter()
        .filter_map(|t| t.get("url").and_then(Value::as_str))
        .next_back()
        .map(String::from)
}
