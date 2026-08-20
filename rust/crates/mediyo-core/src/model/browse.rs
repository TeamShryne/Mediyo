use serde_json::Value;

use crate::error::{Error, Result};
use crate::model::search::{
    parse_multi_row_item, parse_search_result, parse_two_row_item, ArtistRef, Category, SearchResult,
};
use crate::parser::{self, runs, thumbnails};

/// A "View All" navigation from a carousel header (`moreContentButton`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ViewAll {
    /// Target page browseId (discography, videos-playlist, search-like, ...).
    pub browse_id: String,
    /// Optional filter params carried by the endpoint.
    pub params: Option<String>,
    /// Music page type (e.g. `MUSIC_PAGE_TYPE_ARTIST_DISCOGRAPHY`).
    pub page_type: Option<String>,
}

/// A carousel section (e.g. "Albums", "Singles & EPs", "Releases for you").
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Carousel {
    pub title: String,
    pub items: Vec<SearchResult>,
    /// "View All" navigation to the full section, if the carousel has one.
    pub view_all: Option<ViewAll>,
    /// Continuation token to load more items in this carousel, if any.
    pub continuation: Option<String>,
}

/// The homepage: its carousels plus a token to load more sections.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HomePage {
    pub carousels: Vec<Carousel>,
    /// Continuation token for the next batch of carousels, if any.
    pub continuation: Option<String>,
}

/// A quick-navigation button from the explore page grid.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NavButton {
    pub label: String,
    pub browse_id: String,
    pub params: Option<String>,
    pub icon: Option<String>,
}

/// The explore page: navigation buttons plus carousels.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExplorePage {
    pub nav_buttons: Vec<NavButton>,
    pub carousels: Vec<Carousel>,
}

/// A generic paginated list of items (tracks, albums, videos, ...) from a
/// shelf, grid, or carousel "view all" page.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListPage {
    pub items: Vec<SearchResult>,
    /// Continuation token for the next page, if any.
    pub continuation: Option<String>,
}

/// Shared header payload from `musicResponsiveHeaderRenderer`
/// (used by album and playlist pages).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeaderInfo {
    pub title: String,
    pub year: Option<String>,
    /// Artist (album) / owner (playlist), from straplineTextOne.
    pub strapline: Option<ArtistRef>,
    /// e.g. "25 songs" / "81 songs"
    pub track_count: Option<String>,
    /// e.g. "1 hour, 30 minutes"
    pub total_duration: Option<String>,
    pub description: Option<String>,
    pub thumbnails: Vec<thumbnails::Thumbnail>,
    /// playlistId from the header play button (for watch/queue calls).
    pub playlist_id: Option<String>,
}

/// Parsed album detail page.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AlbumPage {
    pub title: String,
    pub artist: Option<ArtistRef>,
    pub year: Option<String>,
    pub track_count: Option<String>,
    pub total_duration: Option<String>,
    pub description: Option<String>,
    pub thumbnails: Vec<thumbnails::Thumbnail>,
    pub playlist_id: Option<String>,
    pub tracks: Vec<SearchResult>,
    pub carousels: Vec<Carousel>,
    /// Continuation token for more tracks (if the album exceeds one page).
    pub continuation: Option<String>,
}

/// Parsed playlist detail page.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlaylistPage {
    pub title: String,
    pub owner: Option<ArtistRef>,
    pub year: Option<String>,
    pub track_count: Option<String>,
    pub total_duration: Option<String>,
    pub description: Option<String>,
    pub thumbnails: Vec<thumbnails::Thumbnail>,
    pub playlist_id: Option<String>,
    pub tracks: Vec<SearchResult>,
    /// Continuation token for more tracks (if the playlist exceeds one page).
    pub continuation: Option<String>,
}

/// A watch endpoint for playing artist radio/mix.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WatchEndpoint {
    pub video_id: Option<String>,
    pub playlist_id: String,
    pub params: Option<String>,
}

/// Parsed artist page.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArtistPage {
    pub name: String,
    pub subscriber_count: Option<String>,
    pub monthly_audience: Option<String>,
    pub description: Option<String>,
    pub thumbnails: Vec<thumbnails::Thumbnail>,
    pub top_songs: Vec<SearchResult>,
    pub carousels: Vec<Carousel>,
    /// Continuation token for more "top songs", if any.
    pub continuation: Option<String>,
    /// "Play" button endpoint (artist mix/radio).
    pub play_button: Option<WatchEndpoint>,
    /// "Start Radio" button endpoint.
    pub radio_button: Option<WatchEndpoint>,
    /// Share entity serialized token.
    pub share_entity: Option<String>,
}

/// Parse an album `browse` response (twoColumn layout, musicResponsiveHeaderRenderer).
pub fn parse_album_page(resp: &Value) -> Result<AlbumPage> {
    let header = responsive_header(resp).ok_or(Error::MissingField("album header"))?;
    let info = parse_responsive_header(header)?;

    let mut tracks = Vec::new();
    let mut carousels = Vec::new();
    let mut continuation = None;
    for section in secondary_sections(resp) {
        let Some((name, payload)) = parser::renderer(section) else {
            continue;
        };
        match name {
            "musicShelfRenderer" | "musicPlaylistShelfRenderer" => {
                continuation = append_list_items(payload, &mut tracks)?;
            }
            "musicCarouselShelfRenderer" => {
                carousels.push(parse_carousel(payload)?);
            }
            _ => {}
        }
    }

    Ok(AlbumPage {
        title: info.title,
        artist: info.strapline,
        year: info.year,
        track_count: info.track_count,
        total_duration: info.total_duration,
        description: info.description,
        thumbnails: info.thumbnails,
        playlist_id: info.playlist_id,
        tracks,
        carousels,
        continuation,
    })
}

/// Parse a playlist `browse` response (twoColumn layout, musicResponsiveHeaderRenderer).
pub fn parse_playlist_page(resp: &Value) -> Result<PlaylistPage> {
    let header = responsive_header(resp).ok_or(Error::MissingField("playlist header"))?;
    let info = parse_responsive_header(header)?;

    let mut tracks = Vec::new();
    let mut continuation = None;
    for section in secondary_sections(resp) {
        let Some((name, payload)) = parser::renderer(section) else {
            continue;
        };
        if name == "musicPlaylistShelfRenderer" || name == "musicShelfRenderer" {
            continuation = append_list_items(payload, &mut tracks)?;
        }
    }

    Ok(PlaylistPage {
        title: info.title,
        owner: info.strapline,
        year: info.year,
        track_count: info.track_count,
        total_duration: info.total_duration,
        description: info.description,
        thumbnails: info.thumbnails,
        playlist_id: info.playlist_id,
        tracks,
        continuation,
    })
}

/// Parse a `browse` continuation response (playlist/album shelves) into the
/// next page of items. Handles the current
/// `onResponseReceivedActions[].appendContinuationItemsAction` shape plus the
/// legacy `continuationContents.musicPlaylistShelfContinuation` /
/// `sectionListContinuation` shapes.
pub fn parse_list_continuation(resp: &Value) -> Result<ListPage> {
    let mut items = Vec::new();
    let mut continuation = None;

    if let Some(actions) = resp
        .get("onResponseReceivedActions")
        .and_then(Value::as_array)
    {
        for action in actions {
            if let Some(items_arr) =
                action.pointer("/appendContinuationItemsAction/continuationItems")
            {
                if let Some(tok) = append_continuation_items(items_arr, &mut items)? {
                    continuation = Some(tok);
                }
            }
        }
    }

    if items.is_empty() && continuation.is_none() {
        if let Some(shelf) = resp.pointer("/continuationContents/musicPlaylistShelfContinuation") {
            let items_arr = shelf.get("contents").unwrap_or(&Value::Null);
            if let Some(tok) = append_continuation_items(items_arr, &mut items)? {
                continuation = Some(tok);
            }
        } else if let Some(sections) = resp
            .pointer("/continuationContents/sectionListContinuation/contents")
            .and_then(Value::as_array)
        {
            let (s, tok) = parse_sections(sections)?;
            items.extend(s);
            continuation = tok;
        }
    }

    Ok(ListPage {
        items,
        continuation,
    })
}

/// Parse the next batch of homepage carousels from a `browse` continuation.
pub fn parse_home_continuation(resp: &Value) -> Result<HomePage> {
    let sections = resp
        .pointer("/continuationContents/sectionListContinuation/contents")
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("home continuation sections"))?;

    let mut carousels = Vec::new();
    for section in sections {
        let Some((name, payload)) = parser::renderer(section) else {
            continue;
        };
        if name == "musicCarouselShelfRenderer" {
            carousels.push(parse_carousel(payload)?);
        }
    }

    let continuation = resp
        .pointer("/continuationContents/sectionListContinuation/continuations")
        .and_then(Value::as_array)
        .and_then(|c| c.first())
        .and_then(next_cont_token)
        .map(String::from);

    Ok(HomePage {
        carousels,
        continuation,
    })
}

/// Parse a "view all" / discovery page (shelf, grid, or carousel of items).
pub fn parse_list_page(resp: &Value) -> Result<ListPage> {
    let sections = list_page_sections(resp).ok_or(Error::MissingField("list page sections"))?;
    let (items, continuation) = parse_sections(sections)?;
    Ok(ListPage {
        items,
        continuation,
    })
}

fn list_page_sections(resp: &Value) -> Option<&[Value]> {
    let single = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array);
    if let Some(s) = single {
        return Some(s);
    }
    resp.pointer(
        "/contents/twoColumnBrowseResultsRenderer/secondaryContents/sectionListRenderer/contents",
    )
    .and_then(Value::as_array)
    .map(|s| s.as_slice())
}

/// Extract all items (+ trailing continuation token) from a list of
/// `sectionListRenderer` section objects (shelves, grids, carousels).
fn parse_sections(sections: &[Value]) -> Result<(Vec<SearchResult>, Option<String>)> {
    let mut items = Vec::new();
    let mut continuation = None;
    for section in sections {
        let Some((name, payload)) = parser::renderer(section) else {
            continue;
        };
        match name {
            "musicShelfRenderer" => {
                let contents = payload.get("contents").unwrap_or(&Value::Null);
                if let Some(tok) = append_continuation_items(contents, &mut items)? {
                    continuation = Some(tok);
                }
            }
            "gridRenderer" => {
                let contents = payload.get("items").unwrap_or(&Value::Null);
                if let Some(tok) = append_continuation_items(contents, &mut items)? {
                    continuation = Some(tok);
                }
            }
            "musicCarouselShelfRenderer" => {
                let contents = payload.get("contents").unwrap_or(&Value::Null);
                if let Some(tok) = append_continuation_items(contents, &mut items)? {
                    continuation = Some(tok);
                }
            }
            _ => {}
        }
    }
    Ok((items, continuation))
}

/// Parse an artist `browse` response (singleColumn layout, musicImmersiveHeaderRenderer).
pub fn parse_artist_page(resp: &Value) -> Result<ArtistPage> {
    let header = resp
        .pointer("/header/musicImmersiveHeaderRenderer")
        .ok_or(Error::MissingField("artist header"))?;

    let name = header.get("title").and_then(runs::text).unwrap_or_default();
    let subscriber_count = header
        .pointer("/subscriptionButton/subscribeButtonRenderer/subscriberCountText")
        .and_then(runs::text);
    let monthly_audience = header.get("monthlyListenerCount").and_then(runs::text);
    let description = header.get("description").and_then(runs::text);
    let thumbnails = thumbnails::thumbnails(header);

    let mut top_songs = Vec::new();
    let mut carousels = Vec::new();
    let mut continuation = None;
    let mut fallback_description = None;

    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array);
    if let Some(sections) = sections {
        for section in sections {
            let Some((name, payload)) = parser::renderer(section) else {
                continue;
            };
            match name {
                "musicShelfRenderer" => {
                    continuation = append_list_items(payload, &mut top_songs)?;
                }
                "musicCarouselShelfRenderer" => carousels.push(parse_carousel(payload)?),
                "musicDescriptionShelfRenderer" => {
                    fallback_description = payload.get("description").and_then(runs::text);
                }
                _ => {}
            }
        }
    }

    let play_button = parse_watch_endpoint(
        header.pointer("/playButton/buttonRenderer/navigationEndpoint"),
    );
    let radio_button = parse_watch_endpoint(
        header.pointer("/startRadioButton/buttonRenderer/navigationEndpoint"),
    );
    let share_entity = header
        .pointer("/shareEndpoint/shareEntityEndpoint/serializedShareEntity")
        .and_then(Value::as_str)
        .map(String::from);

    Ok(ArtistPage {
        name,
        subscriber_count,
        monthly_audience,
        description: description.or(fallback_description),
        thumbnails,
        top_songs,
        carousels,
        continuation,
        play_button,
        radio_button,
        share_entity,
    })
}

/// Parse the homepage (`FEmusic_home`) response into carousels.
pub fn parse_home(resp: &Value) -> Result<Vec<Carousel>> {
    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("home sections"))?;

    let mut carousels = Vec::new();
    for section in sections {
        if let Some((name, payload)) = parser::renderer(section) {
            if name == "musicCarouselShelfRenderer" {
                carousels.push(parse_carousel(payload)?);
            }
        }
    }
    Ok(carousels)
}

/// Parse the homepage (`FEmusic_home`) response into carousels plus a
/// continuation token for loading more sections.
pub fn parse_home_page(resp: &Value) -> Result<HomePage> {
    let carousels = parse_home(resp)?;
    let continuation = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/continuations",
        )
        .and_then(Value::as_array)
        .and_then(|c| c.first())
        .and_then(next_cont_token)
        .map(String::from);
    Ok(HomePage {
        carousels,
        continuation,
    })
}

/// Parse the explore page (`FEmusic_explore`) response.
///
/// Returns navigation buttons (New releases, Charts, Moods & genres, Podcasts)
/// plus carousels (New albums & singles, Trending, New music videos, etc.).
pub fn parse_explore_page(resp: &Value) -> Result<ExplorePage> {
    let sections = resp
        .pointer(
            "/contents/singleColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents",
        )
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("explore sections"))?;

    let mut nav_buttons = Vec::new();
    let mut carousels = Vec::new();

    for section in sections {
        // Grid of quick-nav buttons
        if let Some(grid) = section.get("gridRenderer") {
            if let Some(items) = grid.get("items").and_then(Value::as_array) {
                for item in items {
                    if let Some(btn) = item.get("musicNavigationButtonRenderer") {
                        let label = btn
                            .pointer("/buttonText/runs/0/text")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let browse_id = btn
                            .pointer("/clickCommand/browseEndpoint/browseId")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let params = btn
                            .pointer("/clickCommand/browseEndpoint/params")
                            .and_then(Value::as_str)
                            .map(String::from);
                        let icon = btn
                            .pointer("/iconStyle/icon/iconType")
                            .and_then(Value::as_str)
                            .map(String::from);
                        if !browse_id.is_empty() {
                            nav_buttons.push(NavButton {
                                label,
                                browse_id,
                                params,
                                icon,
                            });
                        }
                    }
                }
            }
        }

        // Carousels (same as home page)
        if let Some((name, payload)) = parser::renderer(section) {
            if name == "musicCarouselShelfRenderer" {
                carousels.push(parse_carousel(payload)?);
            }
        }
    }

    Ok(ExplorePage {
        nav_buttons,
        carousels,
    })
}

fn responsive_header(resp: &Value) -> Option<&Value> {
    resp.pointer(
        "/contents/twoColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents/0/musicResponsiveHeaderRenderer",
    )
}

fn secondary_sections(resp: &Value) -> Vec<&Value> {
    resp.pointer(
        "/contents/twoColumnBrowseResultsRenderer/secondaryContents/sectionListRenderer/contents",
    )
    .and_then(Value::as_array)
    .map(|a| a.iter().collect())
    .unwrap_or_default()
}

fn parse_responsive_header(header: &Value) -> Result<HeaderInfo> {
    let title = header.get("title").and_then(runs::text).unwrap_or_default();

    let mut year = None;
    if let Some(runs_arr) = header
        .get("subtitle")
        .and_then(|s| s.get("runs"))
        .and_then(Value::as_array)
    {
        for run in runs_arr {
            if let Some(t) = run.get("text").and_then(Value::as_str) {
                if is_year(t) {
                    year = Some(t.to_string());
                }
            }
        }
    }

    let mut strapline = None;
    if let Some(st) = header.get("straplineTextOne") {
        let name = runs::text(st).unwrap_or_default();
        let id = st
            .pointer("/runs/0/navigationEndpoint/browseEndpoint/browseId")
            .and_then(Value::as_str);
        if !name.is_empty() {
            strapline = Some(ArtistRef {
                name,
                id: id.map(String::from),
            });
        }
    }

    let second_subtitle = header
        .get("secondSubtitle")
        .and_then(runs::text)
        .unwrap_or_default();
    let (track_count, total_duration) = split_track_stats(&second_subtitle);

    let description = header
        .pointer("/description/musicDescriptionShelfRenderer/description")
        .and_then(runs::text);

    let thumbnails = thumbnails::thumbnails(header);
    let playlist_id = header
        .pointer("/buttons")
        .and_then(Value::as_array)
        .and_then(|b| {
            b.iter().find_map(|b| {
                b.pointer(
                    "/musicPlayButtonRenderer/playNavigationEndpoint/watchPlaylistEndpoint/playlistId",
                )
                .and_then(Value::as_str)
                .or_else(|| {
                    b.pointer("/musicPlayButtonRenderer/playNavigationEndpoint/watchEndpoint/playlistId")
                        .and_then(Value::as_str)
                })
            })
        })
        .map(String::from);

    Ok(HeaderInfo {
        title,
        year,
        strapline,
        track_count,
        total_duration,
        description,
        thumbnails,
        playlist_id,
    })
}

/// Append `musicResponsiveListItemRenderer` items from a shelf, skipping the
/// trailing `continuationItemRenderer` sentinel and returning its token.
fn append_list_items(payload: &Value, out: &mut Vec<SearchResult>) -> Result<Option<String>> {
    append_continuation_items(payload.get("contents").unwrap_or(&Value::Null), out)
}

/// Append list items from a `continuationItems` array (shelf, grid, or
/// continuation response). Handles list and two-row items and returns the
/// `continuationCommand` token if a sentinel is present.
fn append_continuation_items(items: &Value, out: &mut Vec<SearchResult>) -> Result<Option<String>> {
    let mut continuation = None;
    if let Some(items) = items.as_array() {
        for item in items {
            let Some((rname, payload)) = parser::renderer(item) else {
                continue;
            };
            match rname {
                "musicResponsiveListItemRenderer" => out.push(parse_search_result(item)?),
                "musicTwoRowItemRenderer" => out.push(parse_two_row_item(item)?),
                "continuationItemRenderer" => {
                    continuation = payload
                        .pointer("/continuationEndpoint/continuationCommand/token")
                        .and_then(Value::as_str)
                        .map(String::from);
                }
                _ => {}
            }
        }
    }
    Ok(continuation)
}

fn parse_carousel(payload: &Value) -> Result<Carousel> {
    let title = payload
        .pointer("/header/musicCarouselShelfBasicHeaderRenderer/title")
        .and_then(runs::text)
        .unwrap_or_default();

    let mut items = Vec::new();
    let mut continuation = None;
    if let Some(contents) = payload.get("contents").and_then(Value::as_array) {
        for item in contents {
            let Some((rname, _)) = parser::renderer(item) else {
                continue;
            };
            match rname {
                "musicTwoRowItemRenderer" => items.push(parse_two_row_item(item)?),
                "musicNavigationButtonRenderer" => {
                    if let Some(btn) = item.get("musicNavigationButtonRenderer") {
                        let title = btn
                            .get("buttonText")
                            .and_then(runs::text)
                            .unwrap_or_default();
                        let browse_id = btn
                            .pointer("/clickCommand/browseEndpoint/browseId")
                            .and_then(Value::as_str)
                            .map(String::from);
                        items.push(SearchResult {
                            category: Category::Unknown,
                            title,
                            artists: Vec::new(),
                            album: None,
                            video_id: None,
                            browse_id,
                            playlist_id: None,
                            year: None,
                            info: None,
                            track_number: None,
                            duration: None,
                            thumbnails: Vec::new(),
                            explicit: false,
                        });
                    }
                }
                "musicResponsiveListItemRenderer" => items.push(parse_search_result(item)?),
                "musicMultiRowListItemRenderer" => {
                    items.push(parse_multi_row_item(item)?);
                }
                "continuationItemRenderer" => {
                    continuation = item
                        .pointer("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")
                        .and_then(Value::as_str)
                        .map(String::from);
                }
                _ => {}
            }
        }
    }

    let view_all = payload
        .pointer(
            "/header/musicCarouselShelfBasicHeaderRenderer/moreContentButton/buttonRenderer/navigationEndpoint/browseEndpoint",
        )
        .map(|ep| {
            let browse_id = ep
                .get("browseId")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string();
            let params = ep
                .get("params")
                .and_then(Value::as_str)
                .map(String::from);
            let page_type = ep
                .pointer(
                    "/browseEndpointContextSupportedConfigs/browseEndpointContextMusicConfig/pageType",
                )
                .and_then(Value::as_str)
                .map(String::from);
            ViewAll {
                browse_id,
                params,
                page_type,
            }
        })
        .filter(|v| !v.browse_id.is_empty());

    Ok(Carousel {
        title,
        items,
        view_all,
        continuation,
    })
}

/// Extract a continuation token from any of the known wrapper objects
/// (`nextContinuationData`, `reloadContinuationData`, `nextRadioContinuationData`).
/// Parse a `watchEndpoint` or `watchPlaylistEndpoint` into a [`WatchEndpoint`].
fn parse_watch_endpoint(ep: Option<&Value>) -> Option<WatchEndpoint> {
    let ep = ep?;
    // Try watchEndpoint first (has videoId + playlistId)
    if let Some(we) = ep.get("watchEndpoint") {
        let video_id = we.get("videoId").and_then(Value::as_str).map(String::from);
        let playlist_id = we.get("playlistId")?.as_str()?.to_string();
        let params = we.get("params").and_then(Value::as_str).map(String::from);
        return Some(WatchEndpoint {
            video_id,
            playlist_id,
            params,
        });
    }
    // Fall back to watchPlaylistEndpoint (playlistId + params, no videoId)
    if let Some(wpe) = ep.get("watchPlaylistEndpoint") {
        let playlist_id = wpe.get("playlistId")?.as_str()?.to_string();
        let params = wpe.get("params").and_then(Value::as_str).map(String::from);
        return Some(WatchEndpoint {
            video_id: None,
            playlist_id,
            params,
        });
    }
    None
}

fn next_cont_token(v: &Value) -> Option<&str> {
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

/// Split "1.1M views • 831 tracks • 51+ hours" into (count, duration).
/// Skips leading metadata (views) by matching on the relevant units.
fn split_track_stats(text: &str) -> (Option<String>, Option<String>) {
    let mut track_count = None;
    let mut total_duration = None;
    for part in text.split('•').map(str::trim).filter(|s| !s.is_empty()) {
        if part.contains("song") || part.contains("track") {
            track_count = Some(part.to_string());
        } else if part.contains("hour") || part.contains("minute") {
            total_duration = Some(part.to_string());
        }
    }
    (track_count, total_duration)
}

fn is_year(text: &str) -> bool {
    text.len() == 4 && text.chars().all(|c| c.is_ascii_digit())
}
