use mediyo_core::api::search::parse_search_response;
use mediyo_core::model::Category;

const FIXTURE: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/search_response.json"
);

#[test]
fn parses_captured_search_response() {
    let raw = std::fs::read_to_string(FIXTURE).expect("fixture missing");
    let resp: serde_json::Value = serde_json::from_str(&raw).expect("fixture is not valid json");
    let parsed = parse_search_response(&resp).expect("parse failed");

    assert!(
        parsed.results.len() >= 29,
        "expected >=29 results, got {}",
        parsed.results.len()
    );
    assert!(!parsed.filters.is_empty(), "expected filter chips");

    // filter chips
    let labels: Vec<&str> = parsed.filters.iter().map(|f| f.label.as_str()).collect();
    for want in ["Artists", "Albums", "Songs", "Videos"] {
        assert!(labels.contains(&want), "missing chip {want:?}: {labels:?}");
    }
    let songs_chip = parsed.filters.iter().find(|f| f.label == "Songs").unwrap();
    assert!(
        songs_chip.params.is_some(),
        "songs chip should carry filter params"
    );

    // album item
    let scorpion = parsed
        .results
        .iter()
        .find(|r| r.title == "Scorpion")
        .expect("Scorpion album missing");
    assert_eq!(scorpion.category, Category::Album);
    assert_eq!(scorpion.browse_id.as_deref(), Some("MPREb_ZBw3snXoAxN"));
    assert_eq!(scorpion.artists[0].name, "Drake");
    assert_eq!(scorpion.year.as_deref(), Some("2018"));
    assert!(scorpion.album.is_none());

    // song item with videoId
    let song = parsed
        .results
        .iter()
        .find(|r| r.title.starts_with("Life Is Good"))
        .expect("song missing");
    assert_eq!(song.category, Category::Song);
    assert_eq!(song.video_id.as_deref(), Some("6f8gDL-wPN8"));
    assert_eq!(song.artists[0].name, "Future");

    // artist item
    let artist = parsed
        .results
        .iter()
        .find(|r| r.title == "Drake D1")
        .expect("artist missing");
    assert_eq!(artist.category, Category::Artist);
    assert!(artist.browse_id.as_deref().unwrap_or("").starts_with("UC"));

    // playlist item
    let playlist = parsed
        .results
        .iter()
        .find(|r| r.title == "Presenting Drake")
        .expect("playlist missing");
    assert_eq!(playlist.category, Category::Playlist);
    assert!(playlist
        .browse_id
        .as_deref()
        .unwrap_or("")
        .starts_with("VL"));

    // thumbnails present on items
    assert!(parsed.results.iter().all(|r| !r.thumbnails.is_empty()));
}
