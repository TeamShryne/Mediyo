use mediyo_core::model::browse::{
    parse_artist_page, parse_list_continuation, parse_list_page, parse_playlist_page,
};

const ARTIST: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/browse_artist.json"
);
const PLAYLIST: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/browse_playlist.json"
);
const PLAYLIST_NEXT: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/browse_playlist_continuation.json"
);
const DISCOGRAPHY: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/browse_discography.json"
);

fn load(path: &str) -> serde_json::Value {
    let raw = std::fs::read_to_string(path).expect("fixture missing");
    serde_json::from_str(&raw).expect("fixture not json")
}

#[test]
fn parses_captured_artist_page() {
    let page = parse_artist_page(&load(ARTIST)).expect("parse failed");

    assert_eq!(page.name, "Post Malone");
    assert_eq!(page.subscriber_count.as_deref(), Some("28.9M"));
    assert!(
        page.monthly_audience
            .as_deref()
            .unwrap_or("")
            .contains("monthly"),
        "monthly audience expected, got {:?}",
        page.monthly_audience
    );
    assert!(page
        .description
        .as_deref()
        .unwrap_or("")
        .contains("Post Malone"));
    assert!(!page.thumbnails.is_empty());

    // top songs shelf
    assert!(!page.top_songs.is_empty(), "expected top songs");
    assert!(page.top_songs.iter().all(|t| t.video_id.is_some()));

    // carousels
    assert!(page.carousels.len() >= 5);
    let titles: Vec<&str> = page.carousels.iter().map(|c| c.title.as_str()).collect();
    for want in ["Albums", "Singles & EPs", "Videos", "Fans might also like"] {
        assert!(
            titles.contains(&want),
            "missing carousel {want:?}: {titles:?}"
        );
    }

    // play / radio buttons
    let play = page.play_button.as_ref().expect("play_button");
    assert!(play.video_id.is_some(), "play video_id");
    assert!(play.playlist_id.starts_with("RDA"), "play playlist_id: {}", play.playlist_id);
    let radio = page.radio_button.as_ref().expect("radio_button");
    assert!(!radio.playlist_id.is_empty(), "radio playlist_id");
    assert!(radio.playlist_id.starts_with("RDE"), "radio playlist_id: {}", radio.playlist_id);

    // share entity
    let share = page.share_entity.as_ref().expect("share_entity");
    assert!(!share.is_empty());
    assert!(page.carousels.iter().all(|c| !c.items.is_empty()));
}

#[test]
fn parses_captured_playlist_page() {
    let page = parse_playlist_page(&load(PLAYLIST)).expect("parse failed");

    assert_eq!(
        page.title,
        "1000 HITS of 1970-1979 - Billboard year-end top 100 singles"
    );
    assert_eq!(page.track_count.as_deref(), Some("831 tracks"));
    assert_eq!(page.total_duration.as_deref(), Some("51+ hours"));
    // 100 tracks + a continuation sentinel item.
    assert_eq!(page.tracks.len(), 100, "expected 100 tracks on page 1");
    assert!(
        page.continuation.is_some(),
        "a >100-track playlist must expose a continuation token"
    );
    // Legacy mega-playlists render text-only rows (no videoId/artists), but
    // the row title still comes through.
    assert!(page.tracks.iter().all(|t| !t.title.is_empty()));
}

#[test]
fn parses_captured_playlist_continuation() {
    let page = parse_list_continuation(&load(PLAYLIST_NEXT)).expect("parse failed");

    assert_eq!(page.items.len(), 100, "expected 100 tracks on page 2");
    assert!(
        page.continuation.is_some(),
        "expected another continuation token after page 2"
    );
    assert!(page.items.iter().all(|t| !t.title.is_empty()));
}

#[test]
fn parses_captured_discography_view_all() {
    let page = parse_list_page(&load(DISCOGRAPHY)).expect("parse failed");

    // Post Malone discography: a grid of album/single cards (two-row items).
    assert_eq!(page.items.len(), 26, "expected 26 album cards");
    assert!(
        page.items
            .iter()
            .all(|i| !i.title.is_empty() && i.browse_id.is_some()),
        "album cards should carry a title and a browseId"
    );
    assert!(page.continuation.is_none(), "26 items fit on one page");
}

#[test]
fn parses_artist_carousel_view_all_buttons() {
    let page = parse_artist_page(&load(ARTIST)).expect("parse failed");

    // "Singles & EPs" and "Videos" carousels carry View-All navigations.
    let singles = page
        .carousels
        .iter()
        .find(|c| c.title == "Singles & EPs")
        .expect("singles carousel");
    let va = singles.view_all.as_ref().expect("view all endpoint");
    assert_eq!(va.browse_id, "MPADUCyD3XWRK9ko-izf2nBSFitw");
    assert_eq!(
        va.page_type.as_deref(),
        Some("MUSIC_PAGE_TYPE_ARTIST_DISCOGRAPHY")
    );

    let videos = page
        .carousels
        .iter()
        .find(|c| c.title == "Videos")
        .expect("videos carousel");
    let va = videos.view_all.as_ref().expect("view all endpoint");
    assert_eq!(va.browse_id, "VLOLAK5uy_ln7tZIaGLodzJ77XY7dS50pUvG8Fdx6NU");

    // Carousels without a view-all must leave the field None.
    let albums = page
        .carousels
        .iter()
        .find(|c| c.title == "Albums")
        .expect("albums carousel");
    assert!(albums.view_all.is_none());
}
