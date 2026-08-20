use serde_json::Value;

use crate::error::{Error, Result};
use crate::model::search::{parse_multi_row_item, parse_search_result, parse_two_row_item, SearchResult};

/// User account info from `account/account_menu`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AccountInfo {
    pub name: String,
    pub handle: Option<String>,
    pub photo_url: Option<String>,
}

/// A paginated library page (playlists, songs, albums, etc.).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryPage {
    pub items: Vec<SearchResult>,
    pub continuation: Option<String>,
}

/// Parse `account/account_menu` response.
pub fn parse_account_info(resp: &Value) -> Result<AccountInfo> {
    let header = resp
        .pointer("/actions/0/openPopupAction/popup/multiPageMenuRenderer/header/activeAccountHeaderRenderer")
        .ok_or(Error::MissingField("account header"))?;

    let name = header
        .pointer("/accountName/runs/0/text")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string();

    let handle = header
        .pointer("/channelHandle/runs/0/text")
        .and_then(Value::as_str)
        .map(String::from);

    let photo_url = header
        .pointer("/accountPhoto/thumbnails/0/url")
        .and_then(Value::as_str)
        .map(String::from);

    Ok(AccountInfo {
        name,
        handle,
        photo_url,
    })
}

/// Parse library playlists (`FEmusic_liked_playlists`) — gridRenderer.
pub fn parse_library_playlists(resp: &Value) -> Result<LibraryPage> {
    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("library playlists sections"))?;

    let mut items = Vec::new();
    let mut continuation = None;

    for sec in sections {
        if let Some(grid) = sec.get("gridRenderer") {
            if let Some(arr) = grid.get("items").and_then(Value::as_array) {
                for it in arr {
                    // Skip "New playlist" button (has createPlaylistEndpoint, not browseEndpoint)
                    if let Some(renderer) = it.get("musicTwoRowItemRenderer") {
                        let has_browse = renderer
                            .pointer("/navigationEndpoint/browseEndpoint/browseId")
                            .is_some();
                        if !has_browse {
                            continue;
                        }
                        if let Ok(sr) = parse_two_row_item(it) {
                            items.push(sr);
                        }
                    }
                }
            }
            // grid continuation
            if let Some(cont) = grid
                .pointer("/continuations/0/nextContinuationData/continuation")
                .or_else(|| grid.pointer("/continuations/0/nextContinuationData/continuation"))
                .and_then(Value::as_str)
            {
                continuation = Some(cont.to_string());
            }
        }
    }

    // sectionListRenderer-level continuation (gridContinuation)
    if continuation.is_none() {
        continuation = resp
            .pointer(
                "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/continuations/0/nextContinuationData/continuation",
            )
            .or_else(|| {
                resp.pointer(
                    "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/continuations/0/gridContinuation/continuation",
                )
            })
            .and_then(Value::as_str)
            .map(String::from);
    }

    Ok(LibraryPage { items, continuation })
}

/// Parse library songs (`FEmusic_liked_videos`) — musicShelfRenderer.
pub fn parse_library_songs(resp: &Value) -> Result<LibraryPage> {
    parse_library_shelf(resp)
}

/// Parse library history (`FEmusic_history`) — musicShelfRenderer.
pub fn parse_library_history(resp: &Value) -> Result<LibraryPage> {
    parse_library_shelf(resp)
}

/// Parse library artists (`FEmusic_library_corpus_track_artists` / `FEmusic_library_corpus_artists`) — musicShelfRenderer or grid.
pub fn parse_library_artists(resp: &Value) -> Result<LibraryPage> {
    // Try shelf first
    if let Ok(page) = parse_library_shelf(resp) {
        if !page.items.is_empty() {
            return Ok(page);
        }
    }
    // Fall back to grid (for subscriptions maybe)
    parse_library_playlists(resp)
}

/// Parse library albums (`FEmusic_liked_albums`) — handles empty messageRenderer.
pub fn parse_library_albums(resp: &Value) -> Result<LibraryPage> {
    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array);

    if let Some(secs) = sections {
        for sec in secs {
            if sec.get("itemSectionRenderer").is_some() {
                // Empty state with messageRenderer — return empty page
                return Ok(LibraryPage {
                    items: Vec::new(),
                    continuation: None,
                });
            }
        }
    }

    // Otherwise try grid/shelf
    if let Ok(page) = parse_library_playlists(resp) {
        if !page.items.is_empty() {
            return Ok(page);
        }
    }
    parse_library_shelf(resp)
}

fn parse_library_shelf(resp: &Value) -> Result<LibraryPage> {
    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("library shelf sections"))?;

    let mut items = Vec::new();
    let mut continuation = None;

    for sec in sections {
        if let Some(shelf) = sec.get("musicShelfRenderer") {
            if let Some(arr) = shelf.get("contents").and_then(Value::as_array) {
                for it in arr {
                    if it.get("musicResponsiveListItemRenderer").is_some() {
                        // Skip "Shuffle all" header (has musicIconBadgeRenderer thumbnail)
                        if it
                            .pointer("/musicResponsiveListItemRenderer/thumbnail/musicIconBadgeRenderer")
                            .is_some()
                        {
                            continue;
                        }
                        if let Ok(sr) = parse_search_result(it) {
                            items.push(sr);
                        } else if let Ok(sr) = parse_multi_row_item(it) {
                            items.push(sr);
                        }
                    }
                }
            }
            if let Some(cont) = shelf
                .pointer("/continuations/0/nextContinuationData/continuation")
                .or_else(|| shelf.pointer("/continuations/0/musicShelfContinuation/continuation"))
                .and_then(Value::as_str)
            {
                continuation = Some(cont.to_string());
            }
        }
        if let Some(grid) = sec.get("gridRenderer") {
            if let Some(arr) = grid.get("items").and_then(Value::as_array) {
                for it in arr {
                    if it.get("musicTwoRowItemRenderer").is_some() {
                        if let Ok(sr) = parse_two_row_item(it) {
                            items.push(sr);
                        }
                    }
                }
            }
        }
    }

    if continuation.is_none() {
        continuation = resp
            .pointer(
                "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/continuations/0/nextContinuationData/continuation",
            )
            .and_then(Value::as_str)
            .map(String::from);
    }

    Ok(LibraryPage { items, continuation })
}
