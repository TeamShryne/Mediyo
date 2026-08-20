//! Generic navigation helpers over innertube JSON responses.
//!
//! Innertube responses are deeply nested objects whose shape depends on which
//! renderer produced a node. Renderers appear as dict keys named `*Renderer`
//! (e.g. `{"musicResponsiveListItemRenderer": {...}}`). All helpers here work
//! directly on `serde_json::Value` and borrow rather than allocate.

use serde_json::Value;

pub mod runs;
pub mod thumbnails;

/// Find the renderer of a node: the first key ending with `Renderer`.
/// Returns `(name, payload)`.
pub fn renderer(v: &Value) -> Option<(&str, &Value)> {
    let obj = v.as_object()?;
    obj.iter()
        .find(|(k, _)| k.ends_with("Renderer"))
        .map(|(k, v)| (k.as_str(), v))
}

/// A navigation endpoint extracted from a node.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Endpoint<'a> {
    /// `browseEndpoint` → an artist/album/playlist/channel page.
    Browse { id: &'a str },
    /// `watchEndpoint` → a song/video/episode.
    Watch { video_id: &'a str },
    /// `watchPlaylistEndpoint` → a playlist (watch context).
    WatchPlaylist { playlist_id: &'a str },
    /// `searchEndpoint` → a search query (used by filter chips).
    Search { query: &'a str },
}

/// Extract the navigation endpoint from a node (`navigationEndpoint`,
/// `playNavigationEndpoint`, `defaultServiceEndpoint`, ...).
pub fn endpoint(v: &Value) -> Option<Endpoint<'_>> {
    let ne = v.as_object()?;
    if let Some(ep) = ne.get("browseEndpoint") {
        let id = ep.get("browseId")?.as_str()?;
        return Some(Endpoint::Browse { id });
    }
    if let Some(ep) = ne.get("watchEndpoint") {
        let video_id = ep.get("videoId")?.as_str()?;
        return Some(Endpoint::Watch { video_id });
    }
    if let Some(ep) = ne.get("watchPlaylistEndpoint") {
        let playlist_id = ep.get("playlistId")?.as_str()?;
        return Some(Endpoint::WatchPlaylist { playlist_id });
    }
    if let Some(ep) = ne.get("searchEndpoint") {
        let query = ep.get("query")?.as_str()?;
        return Some(Endpoint::Search { query });
    }
    None
}

/// `browseEndpointContextMusicConfig.pageType`, e.g. `MUSIC_PAGE_TYPE_ALBUM`.
pub fn page_type(v: &Value) -> Option<&str> {
    let obj = v.as_object()?;
    let ep = obj.get("browseEndpoint")?;
    ep.pointer("/browseEndpointContextSupportedConfigs/browseEndpointContextMusicConfig/pageType")
        .and_then(Value::as_str)
}

/// `watchEndpoint.watchEndpointMusicSupportedConfigs.watchEndpointMusicConfig.musicVideoType`,
/// e.g. `MUSIC_VIDEO_TYPE_ATV`, `MUSIC_VIDEO_TYPE_OMV`,
/// `MUSIC_VIDEO_TYPE_PODCAST_EPISODE`.
pub fn music_video_type(v: &Value) -> Option<&str> {
    let obj = v.as_object()?;
    obj.get("watchEndpoint")
        .and_then(|ep| {
            ep.pointer(
                "/watchEndpointMusicSupportedConfigs/watchEndpointMusicConfig/musicVideoType",
            )
        })
        .and_then(Value::as_str)
}

/// The `<renderer>List` or container object at `path` from a renderer payload.
/// Returns the payload of the child renderer whose name ends with `suffix`.
pub fn find_in_list<'a>(v: &'a Value, suffix: &str) -> Option<&'a Value> {
    let obj = v.as_object()?;
    for (k, val) in obj {
        if k.ends_with("List") || k.ends_with("Items") {
            if let Some(found) = child_renderer(val, suffix) {
                return Some(found);
            }
        }
    }
    None
}

/// If `v` is a list of renderer frames, return the payload of the first whose
/// renderer name ends with `suffix`.
pub fn child_renderer<'a>(v: &'a Value, suffix: &str) -> Option<&'a Value> {
    let arr = v.as_array()?;
    for item in arr {
        let (name, payload) = renderer(item)?;
        if name.ends_with(suffix) {
            return Some(payload);
        }
    }
    None
}

/// Get a field by exact key name from the object.
pub fn field<'a>(v: &'a Value, key: &str) -> Option<&'a Value> {
    v.as_object()?.get(key)
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn renderer_detection() {
        let v = json!({ "musicResponsiveListItemRenderer": { "title": "x" } });
        let (name, payload) = renderer(&v).unwrap();
        assert_eq!(name, "musicResponsiveListItemRenderer");
        assert_eq!(payload, &json!({ "title": "x" }));
    }

    #[test]
    fn endpoint_kinds() {
        assert_eq!(
            endpoint(&json!({ "browseEndpoint": { "browseId": "MPRE_xyz" } })),
            Some(Endpoint::Browse { id: "MPRE_xyz" })
        );
        assert_eq!(
            endpoint(&json!({ "watchEndpoint": { "videoId": "abc" } })),
            Some(Endpoint::Watch { video_id: "abc" })
        );
        assert_eq!(
            endpoint(&json!({ "watchPlaylistEndpoint": { "playlistId": "OLAK5uy_p" } })),
            Some(Endpoint::WatchPlaylist {
                playlist_id: "OLAK5uy_p"
            })
        );
        assert_eq!(
            endpoint(&json!({ "searchEndpoint": { "query": "drake" } })),
            Some(Endpoint::Search { query: "drake" })
        );
    }
}
