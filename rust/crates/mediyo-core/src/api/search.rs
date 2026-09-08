use serde_json::{json, Value};

use crate::error::{Error, Result};
use crate::model::search::parse_search_result;
use crate::model::{ArtistRef, Category, SearchFilter, SearchResponse, SearchResult};
use crate::parser;
use crate::session::Session;

/// Run a search. `params` is an optional base64 filter from a [`SearchFilter`]
/// (e.g. songs-only). Use [`search`] for an unscoped search.
pub fn search_with_params(
    session: &Session,
    query: &str,
    params: Option<&str>,
) -> Result<SearchResponse> {
    let mut body = json!({ "query": query });
    if let Some(p) = params {
        body["params"] = Value::String(p.to_string());
    }
    let resp = session.request("search", body)?;
    parse_search_response(&resp)
}

/// Unscoped search returning results plus the available filter chips.
pub fn search(session: &Session, query: &str) -> Result<SearchResponse> {
    search_with_params(session, query, None)
}

/// Fetch next page of a filtered search via its continuation token.
pub fn search_continuation(session: &Session, token: &str) -> Result<SearchResponse> {
    let resp = session.request("search", serde_json::json!({"continuation": token}))?;
    parse_search_continuation(&resp)
}

/// Parse a `search` endpoint response into results + filter chips.
pub fn parse_search_response(resp: &Value) -> Result<SearchResponse> {
    let tsr = resp
        .pointer("/contents/tabbedSearchResultsRenderer")
        .ok_or(Error::MissingField("contents.tabbedSearchResultsRenderer"))?;

    let tabs = tsr
        .get("tabs")
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("tabs"))?;

    let tab = tabs
        .iter()
        .find(|t| t.pointer("/tabRenderer/selected").and_then(Value::as_bool) == Some(true))
        .or_else(|| tabs.first())
        .and_then(|t| t.get("tabRenderer"))
        .ok_or(Error::MissingField("tabRenderer"))?;

    let content = tab.get("content").ok_or(Error::MissingField("content"))?;
    let (_, section_list) = parser::renderer(content).ok_or(Error::Missing("content renderer"))?;

    let mut results: Vec<SearchResult> = Vec::new();
    let mut filters: Vec<SearchFilter> = Vec::new();

    if let Some(header) = section_list.get("header") {
        filters = parse_chips(header);
    }

    if let Some(sections) = section_list.get("contents").and_then(Value::as_array) {
        for section in sections {
            let Some((name, payload)) = parser::renderer(section) else {
                continue;
            };
            match name {
                "itemSectionRenderer" => {
                    if let Some(items) = payload.get("contents").and_then(Value::as_array) {
                        for item in items {
                            let Some((rname, _)) = parser::renderer(item) else {
                                continue;
                            };
                            if rname == "musicResponsiveListItemRenderer" {
                                results.push(parse_search_result(item)?);
                            }
                        }
                    }
                }
                "musicShelfRenderer" => {
                    // Filtered searches (Songs/Artists/Albums/...) return a bare
                    // musicShelfRenderer section instead of itemSectionRenderer.
                    if let Some(items) = payload.get("contents").and_then(Value::as_array) {
                        for item in items {
                            let Some((rname, _)) = parser::renderer(item) else {
                                continue;
                            };
                            match rname {
                                "musicResponsiveListItemRenderer" => {
                                    results.push(parse_search_result(item)?)
                                }
                                "musicTwoRowItemRenderer" => {
                                    results.push(crate::model::search::parse_two_row_item(item)?)
                                }
                                _ => {}
                            }
                        }
                    }
                }
                "musicCardShelfRenderer" => {
                    // Top-result shelf: the shelf chrome itself carries the top
                    // entity (artist title/subtitle/avatar, or the top song /
                    // album / playlist tile) while `contents` holds the "More
                    // from …" rows, plus sometimes a leading messageRenderer.
                    // Emit the chrome first, then every non-duplicate result
                    // row, all flagged for featuring.
                    let hero = parse_card_hero(payload);
                    let hero_key: Option<String> = hero.as_ref().and_then(|h| {
                        h.video_id.clone().or_else(|| h.browse_id.clone())
                    });
                    if let Some(hero) = hero {
                        results.push(hero);
                    }
                    let topic_artist = topic_artist(payload);
                    if let Some(inner) = payload.get("contents").and_then(Value::as_array) {
                        for item in inner {
                            let Some((rname, _)) = parser::renderer(item) else {
                                continue;
                            };
                            match rname {
                                "musicResponsiveListItemRenderer" => {
                                    let mut r = parse_search_result(item)?;
                                    // Skip the chrome duplicate: some shelves
                                    // repeat the top entity as the first row.
                                    if let Some(key) = hero_key.as_deref() {
                                        let row_key = r.video_id.as_deref()
                                            .or(r.browse_id.as_deref());
                                        if row_key == Some(key) {
                                            continue;
                                        }
                                    }
                                    // Card rows often omit the artist (the shelf
                                    // title carries the topic) — backfill it.
                                    if r.artists.is_empty() && r.video_id.is_some() {
                                        if let Some(name) = topic_artist.clone() {
                                            r.artists.push(ArtistRef { name, id: None });
                                        }
                                    }
                                    r.top_result = true;
                                    results.push(r);
                                }
                                "musicTwoRowItemRenderer" => {
                                    let mut r = crate::model::search::parse_two_row_item(item)?;
                                    if let Some(key) = hero_key.as_deref() {
                                        let row_key = r.video_id.as_deref()
                                            .or(r.browse_id.as_deref());
                                        if row_key == Some(key) {
                                            continue;
                                        }
                                    }
                                    r.top_result = true;
                                    results.push(r);
                                }
                                _ => {}
                            }
                        }
                    }
                }
                _ => {}
            }
        }
    }

    let continuation = section_list
        .pointer("/continuations/0/nextContinuationData/continuation")
        .or_else(|| section_list.pointer("/continuations/0/musicShelfContinuation/continuation"))
        .and_then(|v| v.as_str())
        .map(String::from)
        .or_else(|| {
            // Filtered search: musicShelfRenderer holds the continuation
            section_list
                .get("contents")
                .and_then(|v| v.as_array())
                .and_then(|arr| arr.iter().find_map(|sec| sec.pointer("/musicShelfRenderer/continuations/0/nextContinuationData/continuation")))
                .and_then(|v| v.as_str())
                .map(String::from)
        })
        .or_else(|| {
            section_list
                .pointer("/continuations/0/gridContinuation/continuation")
                .and_then(|v| v.as_str())
                .map(String::from)
        });

    Ok(SearchResponse { filters, results, continuation })
}

pub fn parse_search_continuation(resp: &Value) -> Result<SearchResponse> {
    let cont = resp
        .pointer("/continuationContents/musicShelfContinuation")
        .or_else(|| resp.pointer("/continuationContents/itemSectionContinuation"))
        .ok_or(Error::MissingField("continuationContents"))?;

    let mut results = Vec::new();
    if let Some(contents) = cont.get("contents").and_then(Value::as_array) {
        for item in contents {
            if let Some((rname, _)) = parser::renderer(item) {
                if rname == "musicResponsiveListItemRenderer" {
                    results.push(parse_search_result(item)?);
                } else if rname == "musicTwoRowItemRenderer" {
                    results.push(crate::model::search::parse_two_row_item(item)?);
                }
            }
        }
    }
    let continuation = cont
        .pointer("/continuations/0/nextContinuationData/continuation")
        .or_else(|| cont.pointer("/continuations/0/musicShelfContinuation/continuation"))
        .and_then(|v| v.as_str())
        .map(String::from);

    Ok(SearchResponse { filters: Vec::new(), results, continuation })
}

/// Topic of a `musicCardShelfRenderer` when it links to an artist page
/// (e.g. title "Drake" → UCU6cE7pdJPc6DU2jSrKEsdQ). Song-titled shelves
/// (watchEndpoint titles) return None so we never mislabel a track title
/// as an artist.
fn topic_artist(card: &Value) -> Option<String> {
    topic_artist_entity(card).map(|(name, _)| name)
}

/// Top result carried by the card shelf chrome itself: YTM renders the shelf
/// title block as the big "Top result" tile (artist, song, album, playlist,
/// podcast, …) while `contents` holds the "More from …" rows, so we emit the
/// chrome as a result ahead of the shelf rows.
fn parse_card_hero(card: &Value) -> Option<SearchResult> {
    // Artist-titled shelves first: the chrome carries the artist entity
    // (title/subtitle/avatar) reported as the "Top result" artist tile.
    if let Some((name, browse_id)) = topic_artist_entity(card) {
        let info = card.get("subtitle").and_then(parser::runs::text);
        return Some(SearchResult {
            category: Category::Artist,
            title: name,
            artists: Vec::new(),
            album: None,
            video_id: None,
            browse_id: Some(browse_id),
            browse_params: None,
            playlist_id: None,
            year: None,
            info,
            track_number: None,
            duration: None,
            thumbnails: parser::thumbnails::thumbnails(card),
            explicit: false,
            top_result: true,
        });
    }
    parse_card_chrome_top(card)
}

/// Chrome-based top result for song/video/album/playlist/podcast shelves:
/// the shelf title links at the top entity (watch or browse endpoint) and
/// the subtitle carries its artists / album / year / counts.
fn parse_card_chrome_top(card: &Value) -> Option<SearchResult> {
    let title_node = card.get("title")?;
    let title = parser::runs::text(title_node)?;
    if title.trim().is_empty() {
        return None;
    }
    let mut video_id: Option<String> = None;
    let mut browse_id: Option<String> = None;
    let mut page_type: Option<&str> = None;
    let mut mvt: Option<&str> = None;
    if let Some((_, ep)) = parser::runs::run_items(title_node).into_iter().next() {
        if let Some(ep) = ep {
            page_type = parser::page_type(ep);
            mvt = parser::music_video_type(ep);
            match parser::endpoint(ep) {
                Some(parser::Endpoint::Browse { id }) => browse_id = Some(id.to_string()),
                Some(parser::Endpoint::Watch { video_id: vid }) => {
                    video_id = Some(vid.to_string())
                }
                Some(parser::Endpoint::WatchPlaylist { playlist_id: pid }) => {
                    browse_id = Some(pid.to_string())
                }
                _ => {}
            }
        }
    }
    // Without an id the chrome is not actionable — fall back to the rows.
    if video_id.is_none() && browse_id.is_none() {
        return None;
    }

    let subtitle_node = card.get("subtitle");
    let mut category = match (&video_id, page_type) {
        (Some(_), _) => mvt
            .map(Category::from_music_video_type)
            .filter(|c| *c != Category::Unknown)
            .unwrap_or(Category::Unknown),
        (None, Some(pt)) => {
            let c = Category::from_page_type(pt);
            if c != Category::Unknown {
                c
            } else if browse_id.as_deref().is_some_and(|id| id.starts_with("MPREb_")) {
                Category::Album
            } else {
                Category::Unknown
            }
        }
        (None, None) => Category::Unknown,
    };
    // The subtitle leads with the kind label ("Song", "Album", "Single", …)
    // when the endpoint carries no usable page type.
    if category == Category::Unknown {
        if let Some(sub) = subtitle_node {
            if let Some(first) = parser::runs::run_items(sub).first() {
                category = Category::from_label(first.0);
            }
        }
    }
    if category == Category::Unknown {
        category = if video_id.is_some() {
            Category::Song
        } else {
            Category::Playlist
        };
    }

    let mut artists = Vec::new();
    let mut album = None;
    let mut year = None;
    let mut info = None;
    if let Some(sub) = subtitle_node {
        crate::model::search::parse_subtitle(sub, &mut artists, &mut album, &mut year, &mut info);
    }
    // Card subtitles sometimes carry the duration ("4:09") as a trailing
    // segment — surface it as duration instead of loose info text.
    let mut duration: Option<String> = None;
    if let Some(sub) = subtitle_node {
        for (text, _) in parser::runs::run_items(sub) {
            let t = text.trim();
            if t.len() <= 8 && t.contains(':') && t.chars().all(|c| c.is_ascii_digit() || c == ':')
            {
                duration = Some(t.to_string());
                break;
            }
        }
    }
    if duration.is_some() && info.as_deref() == duration.as_deref() {
        info = None;
    }

    Some(SearchResult {
        category,
        title: title.trim().to_string(),
        artists,
        album,
        video_id,
        browse_id,
        browse_params: None,
        playlist_id: None,
        year,
        info,
        track_number: None,
        duration,
        thumbnails: parser::thumbnails::thumbnails(card),
        explicit: crate::model::search::is_explicit(card),
        top_result: true,
    })
}

fn topic_artist_entity(card: &Value) -> Option<(String, String)> {
    let title = card.get("title")?;
    let (text, ep) = parser::runs::run_items(title).into_iter().next()?;
    let ep = ep?;
    let parser::Endpoint::Browse { id } = parser::endpoint(ep)? else {
        return None;
    };
    if !id.starts_with("UC") {
        return None;
    }
    match parser::page_type(ep) {
        None | Some("MUSIC_PAGE_TYPE_ARTIST") => {
            let t = text.trim();
            if t.is_empty() {
                None
            } else {
                Some((t.to_string(), id.to_string()))
            }
        }
        _ => None,
    }
}

fn parse_chips(header: &Value) -> Vec<SearchFilter> {
    let mut filters = Vec::new();
    let Some(chips) = header
        .pointer("/chipCloudRenderer/chips")
        .and_then(Value::as_array)
    else {
        return filters;
    };
    for chip in chips {
        let label = chip
            .pointer("/chipCloudChipRenderer/text/runs/0/text")
            .and_then(Value::as_str)
            .unwrap_or("");
        let query = chip
            .pointer("/chipCloudChipRenderer/navigationEndpoint/searchEndpoint/query")
            .and_then(Value::as_str)
            .unwrap_or("");
        let params = chip
            .pointer("/chipCloudChipRenderer/navigationEndpoint/searchEndpoint/params")
            .and_then(Value::as_str);
        filters.push(SearchFilter {
            label: label.to_string(),
            query: query.to_string(),
            params: params.map(String::from),
        });
    }
    filters
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    /// A song-titled card shelf must yield the chrome song as the first top
    /// result, ahead of the "More from …" rows (which must not duplicate it).
    #[test]
    fn song_card_chrome_is_top_result() {
        let v = json!({
            "contents": {
                "tabbedSearchResultsRenderer": {
                    "tabs": [{
                        "tabRenderer": {
                            "selected": true,
                            "content": {
                                "sectionListRenderer": {
                                    "contents": [{
                                        "musicCardShelfRenderer": {
                                            "title": { "runs": [{
                                                "text": "Set Fire to the Rain",
                                                "navigationEndpoint": {
                                                    "watchEndpoint": {
                                                        "videoId": "Ri7-vnrJD3k",
                                                        "watchEndpointMusicSupportedConfigs": {
                                                            "watchEndpointMusicConfig": {
                                                                "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                                                            }
                                                        }
                                                    }
                                                }
                                            }]},
                                            "subtitle": { "runs": [
                                                { "text": "Song" },
                                                { "text": " • " },
                                                { "text": "Adele", "navigationEndpoint": { "browseEndpoint": {
                                                    "browseId": "UCsRM0GpXYTzNuuJzOsSNYtg",
                                                    "browseEndpointContextSupportedConfigs": {
                                                        "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                                    }
                                                } } },
                                                { "text": " • " },
                                                { "text": "2011" }
                                            ]},
                                            "thumbnail": {},
                                            "contents": [
                                                {
                                                    "musicResponsiveListItemRenderer": {
                                                        "flexColumns": [
                                                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Set Fire to the Rain" }] } } },
                                                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                                                                { "text": "Song" },
                                                                { "text": " • " },
                                                                { "text": "Adele", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCsRM0GpXYTzNuuJzOsSNYtg" } } }
                                                            ] } } }
                                                        ],
                                                        "playlistItemData": { "videoId": "Ri7-vnrJD3k" },
                                                        "overlay": {
                                                            "musicItemThumbnailOverlayRenderer": {
                                                                "content": {
                                                                    "musicPlayButtonRenderer": {
                                                                        "playNavigationEndpoint": {
                                                                            "watchEndpoint": {
                                                                                "videoId": "Ri7-vnrJD3k",
                                                                                "watchEndpointMusicSupportedConfigs": {
                                                                                    "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                {
                                                    "musicResponsiveListItemRenderer": {
                                                        "flexColumns": [
                                                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Someone Like You" }] } } },
                                                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                                                                { "text": "Song" },
                                                                { "text": " • " },
                                                                { "text": "Adele", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCsRM0GpXYTzNuuJzOsSNYtg" } } }
                                                            ] } } }
                                                        ],
                                                        "playlistItemData": { "videoId": "hLQl3WQQoQ0" },
                                                        "overlay": {
                                                            "musicItemThumbnailOverlayRenderer": {
                                                                "content": {
                                                                    "musicPlayButtonRenderer": {
                                                                        "playNavigationEndpoint": {
                                                                            "watchEndpoint": {
                                                                                "videoId": "hLQl3WQQoQ0",
                                                                                "watchEndpointMusicSupportedConfigs": {
                                                                                    "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            ]
                                        }
                                    }]
                                }
                            }
                        }
                    }]
                }
            }
        });
        let r = parse_search_response(&v).unwrap();
        // Chrome hero first: the top song itself, flagged for featuring.
        assert_eq!(r.results.len(), 2);
        let hero = &r.results[0];
        assert_eq!(hero.title, "Set Fire to the Rain");
        assert_eq!(hero.category, Category::Song);
        assert_eq!(hero.video_id.as_deref(), Some("Ri7-vnrJD3k"));
        assert!(hero.top_result);
        assert_eq!(hero.artists.len(), 1);
        assert_eq!(hero.artists[0].name, "Adele");
        assert_eq!(hero.year.as_deref(), Some("2011"));
        // The duplicate chrome row is skipped; the "more" row follows.
        let more = &r.results[1];
        assert_eq!(more.title, "Someone Like You");
        assert!(more.top_result);
    }
}
