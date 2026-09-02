use serde_json::Value;

use crate::error::{Error, Result};
use crate::parser::{self, runs, thumbnails};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Category {
    Song,
    Video,
    Album,
    Artist,
    Playlist,
    Episode,
    Podcast,
    Profile,
    Unknown,
}

impl Category {
    pub fn from_label(label: &str) -> Self {
        match label {
            "Song" => Category::Song,
            "Video" => Category::Video,
            "Album" | "Single" | "EP" => Category::Album,
            "Artist" => Category::Artist,
            "Playlist" => Category::Playlist,
            "Episode" => Category::Episode,
            "Podcast" => Category::Podcast,
            "Profile" => Category::Profile,
            _ => Category::Unknown,
        }
    }

    pub fn from_page_type(page_type: &str) -> Self {
        match page_type {
            "MUSIC_PAGE_TYPE_ALBUM" => Category::Album,
            "MUSIC_PAGE_TYPE_ARTIST" => Category::Artist,
            "MUSIC_PAGE_TYPE_PLAYLIST" => Category::Playlist,
            "MUSIC_PAGE_TYPE_USER_CHANNEL" => Category::Profile,
            "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" => Category::Podcast,
            _ => Category::Unknown,
        }
    }

    pub fn from_music_video_type(video_type: &str) -> Self {
        match video_type {
            "MUSIC_VIDEO_TYPE_ATV" => Category::Song,
            "MUSIC_VIDEO_TYPE_OMV" => Category::Video,
            "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" => Category::Episode,
            _ => Category::Unknown,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArtistRef {
    pub name: String,
    pub id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AlbumRef {
    pub name: String,
    pub id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SearchResult {
    pub category: Category,
    pub title: String,
    pub artists: Vec<ArtistRef>,
    pub album: Option<AlbumRef>,
    /// videoId (songs, videos, episodes).
    pub video_id: Option<String>,
    /// browseId (albums, artists, playlists, profiles, podcasts).
    pub browse_id: Option<String>,
    /// Optional browse params (e.g. moods category `ggMPOg...`).
    pub browse_params: Option<String>,
    /// playlistId when the item is a playable playlist (watch context).
    pub playlist_id: Option<String>,
    /// Year, when present in the subtitle.
    pub year: Option<String>,
    /// Loose trailing info: view/subscriber/song counts, upload recency, ...
    pub info: Option<String>,
    /// Track number (album/playlist track lists, from `index`).
    pub track_number: Option<String>,
    /// Duration as text (e.g. "2:17"), from `fixedColumns`.
    pub duration: Option<String>,
    pub thumbnails: Vec<thumbnails::Thumbnail>,
    pub explicit: bool,
}

/// A search scope chip (Artists / Albums / Songs / ...) with its filter params.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SearchFilter {
    pub label: String,
    pub query: String,
    pub params: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SearchResponse {
    pub filters: Vec<SearchFilter>,
    pub results: Vec<SearchResult>,
    pub continuation: Option<String>,
}

/// Parse a `musicResponsiveListItemRenderer` frame into a [`SearchResult`].
pub fn parse_search_result(v: &Value) -> Result<SearchResult> {
    let (_, payload) = parser::renderer(v).ok_or(Error::Missing("renderer"))?;

    let flex_cols = payload
        .get("flexColumns")
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("flexColumns"))?;

    let col_text = |i: usize| -> Option<&Value> {
        flex_cols
            .get(i)
            .and_then(|c| c.get("musicResponsiveListItemFlexColumnRenderer"))
            .and_then(|c| c.get("text"))
    };

    let title = col_text(0).and_then(runs::text).unwrap_or_default();
    let subtitle_node = col_text(1);

    let thumbnails = thumbnails::thumbnails(payload);
    let explicit = is_explicit(payload);

    let mut video_id: Option<&str> = None;
    let mut browse_id: Option<&str> = None;
    let mut playlist_id: Option<&str> = None;
    let mut page_type: Option<&str> = None;
    let mut music_video_type: Option<&str> = None;

    if let Some(ne) = payload.get("navigationEndpoint") {
        page_type = parser::page_type(ne);
        match parser::endpoint(ne) {
            Some(parser::Endpoint::Browse { id }) => browse_id = Some(id),
            Some(parser::Endpoint::WatchPlaylist { playlist_id: pid }) => playlist_id = Some(pid),
            _ => {}
        }
    }

    if let Some(overlay_ep) = payload
        .pointer("/overlay/musicItemThumbnailOverlayRenderer/content/musicPlayButtonRenderer/playNavigationEndpoint")
    {
        music_video_type = parser::music_video_type(overlay_ep);
        if let Some(parser::Endpoint::Watch { video_id: vid }) = parser::endpoint(overlay_ep) {
            video_id = Some(vid);
        }
    }
    if video_id.is_none() {
        video_id = payload
            .pointer("/playlistItemData/videoId")
            .and_then(Value::as_str);
    }

    let category = resolve_category(video_id, music_video_type, page_type, subtitle_node);

    let mut artists = Vec::new();
    let mut album = None;
    let mut year = None;
    let mut info = None;
    if let Some(sub) = subtitle_node {
        parse_subtitle(sub, &mut artists, &mut album, &mut year, &mut info);
    }

    let track_number = payload
        .pointer("/index/runs/0/text")
        .and_then(Value::as_str)
        .map(String::from);
    let duration = payload
        .pointer("/fixedColumns/0/musicResponsiveListItemFixedColumnRenderer/text")
        .and_then(runs::text);

    Ok(SearchResult {
        category,
        title,
        artists,
        album,
        video_id: video_id.map(String::from),
        browse_id: browse_id.map(String::from),
        browse_params: None,
        playlist_id: playlist_id.map(String::from),
        year,
        info,
        track_number,
        duration,
        thumbnails,
        explicit,
    })
}

/// Parse a `musicTwoRowItemRenderer` frame (carousel / home cards) into a
/// [`SearchResult`].
pub fn parse_two_row_item(v: &Value) -> Result<SearchResult> {
    let (_, payload) = parser::renderer(v).ok_or(Error::Missing("renderer"))?;

    let title = payload
        .get("title")
        .and_then(runs::text)
        .unwrap_or_default();
    let subtitle_node = payload.get("subtitle");
    let thumbnails = thumbnails::thumbnails_two_row(payload);
    let explicit = is_explicit(payload);

    let mut video_id: Option<&str> = None;
    let mut browse_id: Option<&str> = None;
    let mut page_type: Option<&str> = None;
    let mut music_video_type: Option<&str> = None;

    if let Some(ne) = payload.get("navigationEndpoint") {
        page_type = parser::page_type(ne);
        music_video_type = parser::music_video_type(ne);
        match parser::endpoint(ne) {
            Some(parser::Endpoint::Browse { id }) => browse_id = Some(id),
            Some(parser::Endpoint::Watch { video_id: vid }) => video_id = Some(vid),
            Some(parser::Endpoint::WatchPlaylist { playlist_id: pid }) => {
                browse_id = Some(pid);
            }
            _ => {}
        }
    }

    let category = resolve_category(video_id, music_video_type, page_type, subtitle_node);

    let mut artists = Vec::new();
    let mut album = None;
    let mut year = None;
    let mut info = None;
    if let Some(sub) = subtitle_node {
        parse_subtitle(sub, &mut artists, &mut album, &mut year, &mut info);
    }

    Ok(SearchResult {
        category,
        title,
        artists,
        album,
        video_id: video_id.map(String::from),
        browse_id: browse_id.map(String::from),
        browse_params: None,
        playlist_id: None,
        year,
        info,
        track_number: None,
        duration: None,
        thumbnails,
        explicit,
    })
}

/// Parse a `musicMultiRowListItemRenderer` (podcast episodes, etc.)
/// into a [`SearchResult`].
pub fn parse_multi_row_item(v: &Value) -> Result<SearchResult> {
    let (_, payload) = parser::renderer(v).ok_or(Error::Missing("renderer"))?;

    // Title + browseId from title.runs[0].navigationEndpoint.browseEndpoint
    let title = payload.get("title").and_then(runs::text).unwrap_or_default();
    let browse_id = payload
        .pointer("/title/runs/0/navigationEndpoint/browseEndpoint/browseId")
        .and_then(Value::as_str)
        .map(String::from);

    // VideoId from onTap.watchEndpoint or overlay play button
    let video_id = payload
        .pointer("/onTap/watchEndpoint/videoId")
        .or_else(|| {
            payload.pointer(
                "/overlay/musicItemThumbnailOverlayRenderer/content/musicPlayButtonRenderer/playNavigationEndpoint/watchEndpoint/videoId",
            )
        })
        .and_then(Value::as_str)
        .map(String::from);

    // Subtitle → info text (views, date)
    let info = payload
        .get("subtitle")
        .and_then(runs::text);

    // secondTitle → album/artist (e.g. podcast show name)
    let album_name = payload.get("secondTitle").and_then(runs::text);
    let album = album_name.map(|name| crate::model::search::AlbumRef {
        name,
        id: payload
            .pointer("/secondTitle/runs/0/navigationEndpoint/browseEndpoint/browseId")
            .and_then(Value::as_str)
            .map(String::from),
    });

    // Thumbnails
    let thumbnails = payload
        .pointer("/thumbnail/musicThumbnailRenderer/thumbnail/thumbnails")
        .and_then(Value::as_array)
        .map(|arr| {
            arr.iter()
                .filter_map(|t| {
                    Some(crate::parser::thumbnails::Thumbnail {
                        url: t.get("url")?.as_str()?.to_string(),
                        width: t.get("width")?.as_u64()? as u32,
                        height: t.get("height")?.as_u64()? as u32,
                    })
                })
                .collect()
        })
        .unwrap_or_default();

    Ok(SearchResult {
        category: Category::Episode,
        title,
        artists: Vec::new(),
        album,
        video_id,
        browse_id,
        browse_params: None,
        playlist_id: None,
        year: None,
        info,
        track_number: None,
        duration: None,
        thumbnails,
        explicit: false,
    })
}

fn resolve_category(
    video_id: Option<&str>,
    music_video_type: Option<&str>,
    page_type: Option<&str>,
    subtitle: Option<&Value>,
) -> Category {
    if video_id.is_some() {
        if let Some(vt) = music_video_type {
            let c = Category::from_music_video_type(vt);
            if c != Category::Unknown {
                return c;
            }
        }
    } else if let Some(pt) = page_type {
        let c = Category::from_page_type(pt);
        if c != Category::Unknown {
            return c;
        }
    }
    if let Some(sub) = subtitle {
        if let Some(first) = runs::run_items(sub).first() {
            return Category::from_label(first.0);
        }
    }
    Category::Unknown
}

/// Parse the subtitle `runs` into artists / album / year / trailing info.
fn parse_subtitle(
    node: &Value,
    artists: &mut Vec<ArtistRef>,
    album: &mut Option<AlbumRef>,
    year: &mut Option<String>,
    info: &mut Option<String>,
) {
    let items = runs::run_items(node);
    if items.is_empty() {
        return;
    }

    // A leading category label ("Song", "Album", ...) is metadata, not a
    // segment. Only skip it when it's a known label — playlist/album tracks
    // often start directly with the artist name.
    let mut current = String::new();
    let mut current_ep: Option<&Value> = None;

    for (i, (text, ep)) in items.iter().enumerate() {
        let text = *text;
        let ep = *ep;
        if i == 0 && is_category_label(text) {
            continue;
        }
        if text == " • " || text == " •" || text == "• " {
            classify_segment(&current, current_ep, artists, album, year, info);
            current.clear();
            current_ep = None;
            continue;
        }
        if current.is_empty() {
            current_ep = ep;
        }
        current.push_str(text);
    }
    classify_segment(&current, current_ep, artists, album, year, info);
}

fn is_category_label(text: &str) -> bool {
    matches!(
        text,
        "Song"
            | "Video"
            | "Album"
            | "Artist"
            | "Playlist"
            | "Episode"
            | "Podcast"
            | "Profile"
            | "Single"
            | "EP"
            | "Upload"
    )
}

fn classify_segment(
    segment: &str,
    ep: Option<&Value>,
    artists: &mut Vec<ArtistRef>,
    album: &mut Option<AlbumRef>,
    year: &mut Option<String>,
    info: &mut Option<String>,
) {
    let text = segment.trim();
    if text.is_empty() {
        return;
    }
    if let Some(ep) = ep {
        if let Some(parser::Endpoint::Browse { id }) = parser::endpoint(ep) {
            if id.starts_with("UC") {
                artists.push(ArtistRef {
                    name: text.to_string(),
                    id: Some(id.to_string()),
                });
                return;
            }
            if parser::page_type(ep) == Some("MUSIC_PAGE_TYPE_ALBUM") || id.starts_with("MPREb_") {
                *album = Some(AlbumRef {
                    name: text.to_string(),
                    id: Some(id.to_string()),
                });
                return;
            }
            return;
        }
    }
    // No navigation endpoint: year or trailing info.
    if is_year(text) {
        *year = Some(text.to_string());
    } else if info.is_none() {
        *info = Some(text.to_string());
    }
}

fn is_year(text: &str) -> bool {
    text.len() == 4 && text.chars().all(|c| c.is_ascii_digit())
}

fn is_explicit(payload: &Value) -> bool {
    let Some(badges) = payload.get("badges").and_then(Value::as_array) else {
        return false;
    };
    badges.iter().any(|b| {
        let label = b
            .pointer("/musicInlineBadgeRenderer/accessibilityData/accessibilityData/label")
            .and_then(Value::as_str)
            .unwrap_or("");
        let icon = b
            .pointer("/musicInlineBadgeRenderer/icon/iconType")
            .and_then(Value::as_str)
            .unwrap_or("");
        label.contains("Explicit") || icon == "MUSIC_EXPLICIT_BADGE"
    })
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn parse_album_item() {
        let v = json!({
            "musicResponsiveListItemRenderer": {
                "thumbnail": {
                    "musicThumbnailRenderer": {
                        "thumbnail": {
                            "thumbnails": [
                                { "url": "https://x/a.jpg", "width": 60, "height": 60 }
                            ]
                        }
                    }
                },
                "flexColumns": [
                    {
                        "musicResponsiveListItemFlexColumnRenderer": {
                            "text": { "runs": [{ "text": "Scorpion" }] }
                        }
                    },
                    {
                        "musicResponsiveListItemFlexColumnRenderer": {
                            "text": { "runs": [
                                { "text": "Album" },
                                { "text": " • " },
                                { "text": "Drake", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCb1CvGR6F7eCcUPRGtyy0axA" } } },
                                { "text": " • " },
                                { "text": "2018" }
                            ]}
                        }
                    }
                ],
                "badges": [
                    { "musicInlineBadgeRenderer": {
                        "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" },
                        "accessibilityData": { "accessibilityData": { "label": "Explicit" } }
                    }}
                ],
                "navigationEndpoint": {
                    "browseEndpoint": {
                        "browseId": "MPREb_ZBw3snXoAxN",
                        "browseEndpointContextSupportedConfigs": {
                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ALBUM" }
                        }
                    }
                }
            }
        });
        let r = parse_search_result(&v).unwrap();
        assert_eq!(r.category, Category::Album);
        assert_eq!(r.title, "Scorpion");
        assert_eq!(r.browse_id.as_deref(), Some("MPREb_ZBw3snXoAxN"));
        assert_eq!(r.artists.len(), 1);
        assert_eq!(r.artists[0].name, "Drake");
        // Album items carry their name as the title; subtitle has no album segment.
        assert!(r.album.is_none());
        assert_eq!(r.year.as_deref(), Some("2018"));
        assert!(r.explicit);
        assert_eq!(r.thumbnails.len(), 1);
    }

    #[test]
    fn parse_song_item() {
        let v = json!({
            "musicResponsiveListItemRenderer": {
                "flexColumns": [
                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Life Is Good (feat. Drake)" }] } } },
                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                        { "text": "Song" },
                        { "text": " • " },
                        { "text": "Future", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCrzUq3jB4Y1NgrC4Y4DqR9A" } } }
                    ] } } }
                ],
                "playlistItemData": { "videoId": "6f8gDL-wPN8" },
                "overlay": {
                    "musicItemThumbnailOverlayRenderer": {
                        "content": {
                            "musicPlayButtonRenderer": {
                                "playNavigationEndpoint": {
                                    "watchEndpoint": {
                                        "videoId": "6f8gDL-wPN8",
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
        });
        let r = parse_search_result(&v).unwrap();
        assert_eq!(r.category, Category::Song);
        assert_eq!(r.video_id.as_deref(), Some("6f8gDL-wPN8"));
        assert_eq!(r.artists.len(), 1);
        assert_eq!(r.artists[0].name, "Future");
        assert!(r.browse_id.is_none());
    }
}
