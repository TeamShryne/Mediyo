use mediyo_core::model::browse::{parse_explore_page, ExplorePage};

const EXPLORE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/explore.json");

fn fixture() -> serde_json::Value {
    let raw = std::fs::read_to_string(EXPLORE).expect("explore.json readable");
    serde_json::from_str(&raw).expect("explore.json valid JSON")
}

#[test]
fn parses_explore_page() {
    let resp = fixture();
    let page: ExplorePage = parse_explore_page(&resp).unwrap();

    // 4 quick-nav buttons
    assert_eq!(page.nav_buttons.len(), 4, "expected 4 nav buttons");
    let labels: Vec<&str> = page.nav_buttons.iter().map(|b| b.label.as_str()).collect();
    for want in ["New releases", "Charts", "Moods & genres", "Podcasts"] {
        assert!(labels.contains(&want), "missing nav button {want:?}: {labels:?}");
    }
    for btn in &page.nav_buttons {
        assert!(!btn.browse_id.is_empty(), "browse_id should not be empty");
    }

    // 5 carousels
    assert_eq!(page.carousels.len(), 5, "expected 5 carousels");
    let titles: Vec<&str> = page.carousels.iter().map(|c| c.title.as_str()).collect();
    for want in [
        "New albums & singles",
        "Moods & genres",
        "Popular episodes",
        "Trending",
        "New music videos",
    ] {
        assert!(
            titles.contains(&want),
            "missing carousel {want:?}: {titles:?}"
        );
    }

    // Each carousel has a title
    for c in &page.carousels {
        assert!(!c.title.is_empty(), "carousel should have a title");
    }

    // "Moods & genres" carousel has nav button items
    let moods = page.carousels.iter().find(|c| c.title == "Moods & genres").unwrap();
    assert!(!moods.items.is_empty(), "Moods & genres should have nav button items");
    let moods_labels: Vec<&str> = moods.items.iter().map(|i| i.title.as_str()).collect();
    assert!(moods_labels.contains(&"Chill"), "should have Chill button: {moods_labels:?}");

    // "Popular episodes" carousel has multi-row items (episodes)
    let episodes = page.carousels.iter().find(|c| c.title == "Popular episodes").unwrap();
    assert!(!episodes.items.is_empty(), "Popular episodes should have items");
    let first_ep = &episodes.items[0];
    assert!(!first_ep.title.is_empty(), "episode should have title");
    assert!(first_ep.video_id.is_some(), "episode should have videoId");
    assert!(first_ep.browse_id.is_some(), "episode should have browseId");
}
