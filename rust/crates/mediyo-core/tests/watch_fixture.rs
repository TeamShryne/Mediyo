use mediyo_core::model::watch::{
    parse_lyrics, parse_queue, parse_queue_continuation, parse_song, Lyrics, Queue, Song,
};

const FIXTURE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/next_song.json");
const FIXTURE_NEXT: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../research/next_continuation.json"
);
const LYRICS_FIXTURE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/lyrics.json");

fn fixture() -> serde_json::Value {
    let raw = std::fs::read_to_string(FIXTURE).expect("next_song.json is readable");
    serde_json::from_str(&raw).expect("next_song.json is valid JSON")
}

#[test]
fn parses_current_song_and_queue() {
    let resp = fixture();
    let song: Song = parse_song(&resp).unwrap();

    assert_eq!(song.video_id, "58dyibIUscg");
    assert_eq!(song.playlist_set_video_id, "56B44F6D10557CC6");
    assert_eq!(song.title, "Sunflower (Remix)");
    assert_eq!(song.playlist_id, "RDAMVM58dyibIUscg");
    assert_eq!(song.duration.as_deref(), Some("3:30"));

    assert_eq!(song.artists.len(), 4);
    assert_eq!(song.artists[0].name, "Post Malone");
    assert_eq!(song.artists[1].name, "Swae Lee");
    assert!(song.artists.iter().all(|a| a.id.is_some()));

    let album = song.album.as_deref().unwrap();
    assert!(album.contains("Spider-Verse"), "album: {album}");

    assert_eq!(
        song.lyrics_browse_id.as_deref(),
        Some("MPLYt_V8meDSsonVm-14"),
        "should carry lyrics browseId"
    );

    assert_eq!(song.queue.len(), 50);
    assert!(song.queue[0].selected);
    assert_eq!(song.queue[0].index, Some(0));
    assert_eq!(song.queue[0].video_id, "58dyibIUscg");
    assert!(!song.queue[1].selected);
    assert!(song.queue[1].thumbnail.is_some());
}

#[test]
fn parses_queue_metadata() {
    let resp = fixture();
    let queue: Queue = parse_queue(&resp).unwrap();

    assert_eq!(queue.playlist_id, "RDAMVM58dyibIUscg");
    assert!(queue.is_infinite);
    assert!(queue.continuation.is_some());
    assert!(queue.continuation.as_deref().unwrap().len() > 100);
    assert_eq!(queue.items.len(), 50);

    let non_selected = &queue.items[1];
    assert_eq!(non_selected.video_id, "jyOBRJ4_Zss");
    assert_eq!(non_selected.title, "Unforgettable X Show Me Love (French Montana, WizTheMc, bees & honey, Swae Lee) [Jr Stit Mashup]");
    assert_eq!(non_selected.duration.as_deref(), Some("3:35"));
    assert_eq!(non_selected.artists.len(), 1);
    assert_eq!(non_selected.artists[0].name, "Jr Stit");
    assert_eq!(non_selected.album, None);
}

#[test]
fn parses_queue_continuation() {
    let resp: serde_json::Value =
        serde_json::from_str(&std::fs::read_to_string(FIXTURE_NEXT).expect("fixture readable"))
            .expect("fixture json");
    let queue: Queue = parse_queue_continuation(&resp).unwrap();

    assert_eq!(queue.playlist_id, "RDAMVM58dyibIUscg");
    assert!(queue.is_infinite);
    assert!(queue.continuation.is_some(), "radio queue stays infinite");
    assert_eq!(queue.items.len(), 49, "expected 49 more queue items");

    let first = &queue.items[0];
    assert_eq!(first.video_id, "JtoVIo3kiJ8");
    assert!(!first.title.is_empty());
    assert!(!first.artists.is_empty());
}

#[test]
fn parses_lyrics_fixture() {
    let resp: serde_json::Value =
        serde_json::from_str(&std::fs::read_to_string(LYRICS_FIXTURE).expect("fixture readable"))
            .expect("fixture json");
    let lyrics: Lyrics = parse_lyrics(&resp).unwrap();

    assert!(!lyrics.lines.is_empty(), "should have lyrics lines");
    assert!(lyrics.lines.len() > 10, "expected a full lyrics sheet");
    assert!(
        lyrics.lines.iter().any(|l| l.contains("sunflower")),
        "lyrics should contain the word 'sunflower': {:?}",
        &lyrics.lines[..5]
    );
}
