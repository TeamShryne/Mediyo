use mediyo_core::model::library::{
    parse_account_info, parse_library_history, parse_library_playlists, parse_library_songs,
    AccountInfo, LibraryPage,
};

const PLAYLISTS: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/library_playlists.json");
const SONGS: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/library_songs.json");
const ACCOUNT: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/account_menu.json");
const HISTORY: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/library_history.json");

fn load(path: &str) -> serde_json::Value {
    let raw = std::fs::read_to_string(path).expect("fixture readable");
    serde_json::from_str(&raw).expect("valid json")
}

#[test]
fn parses_library_playlists() {
    let page: LibraryPage = parse_library_playlists(&load(PLAYLISTS)).unwrap();
    // Should have at least Liked Music + one custom playlist + Episodes
    assert!(page.items.len() >= 2, "expected at least 2 playlists, got {}", page.items.len());
    let titles: Vec<&str> = page.items.iter().map(|p| p.title.as_str()).collect();
    assert!(titles.contains(&"Liked Music"), "missing Liked Music: {titles:?}");
}

#[test]
fn parses_library_songs() {
    let page: LibraryPage = parse_library_songs(&load(SONGS)).unwrap();
    assert!(!page.items.is_empty(), "liked songs should not be empty");
    // First items are liked songs
    assert!(page.items[0].title.len() > 2);
}

#[test]
fn parses_account_info() {
    let info: AccountInfo = parse_account_info(&load(ACCOUNT)).unwrap();
    assert!(!info.name.is_empty(), "account name");
    // handle may be "@ShryneX" or similar
    assert!(info.handle.is_some());
}

#[test]
fn parses_history() {
    let page: LibraryPage = parse_library_history(&load(HISTORY)).unwrap();
    // History may be 1 item or more; just check parsing succeeds
    assert!(!page.items.is_empty(), "history should have at least 1");
}
