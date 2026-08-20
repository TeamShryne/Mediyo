use mediyo_core::api::watch::{extend_queue, get_lyrics, get_queue};
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    session.fetch_visitor_data()?;

    let mut queue = get_queue(&session, "58dyibIUscg", Some("RDAMVM58dyibIUscg"))?;

    // Fetch lyrics via the song's lyrics_browse_id.
    let song =
        mediyo_core::api::watch::get_song(&session, "58dyibIUscg", Some("RDAMVM58dyibIUscg"))?;
    if let Some(bid) = &song.lyrics_browse_id {
        match get_lyrics(&session, bid) {
            Ok(lyrics) => {
                println!("LYRICS ({} lines):", lyrics.lines.len());
                for line in lyrics.lines.iter().take(4) {
                    println!("  {line}");
                }
                println!("  ...");
            }
            Err(e) => println!("(lyrics unavailable: {e})"),
        }
    } else {
        println!("(no lyrics browseId)");
    }

    let initial = queue.items.len();
    let mut pages = 1;
    // An infinite radio queue never runs out — cap the demo walk.
    const MAX_PAGES: usize = 5;
    while let Some(token) = queue.continuation.take() {
        if pages >= MAX_PAGES {
            break;
        }
        let next = extend_queue(&session, &token)?;
        let n = next.items.len();
        queue.items.extend(next.items);
        queue.continuation = next.continuation;
        pages += 1;
        println!(
            "  queue page {pages}: +{n} items (infinite: {})",
            next.is_infinite
        );
    }
    println!(
        "NOW PLAYING: {} (videoId {})\n  queue: {initial} -> {} tracks across {pages} pages (playlist {}, still infinite: {})",
        queue.items.first().map(|q| q.title.as_str()).unwrap_or("-"),
        queue.items.first().map(|q| q.video_id.as_str()).unwrap_or(""),
        queue.items.len(),
        queue.playlist_id,
        queue.continuation.is_some() || queue.is_infinite,
    );
    for q in queue.items.iter().take(5) {
        let a: Vec<&str> = q.artists.iter().map(|a| a.name.as_str()).collect();
        println!(
            "    {} [{:?}] {:<30} {:>5} by {}",
            if q.selected { ">" } else { " " },
            q.index,
            q.title,
            q.duration.as_deref().unwrap_or("?:"),
            a.join(", "),
        );
    }
    Ok(())
}
