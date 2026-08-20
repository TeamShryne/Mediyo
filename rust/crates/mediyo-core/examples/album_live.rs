use mediyo_core::api::browse::album;
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    session.fetch_visitor_data()?;

    let page = album(&session, "MPREb_ZBw3snXoAxN")?;
    println!(
        "{} — {} ({}) by {}",
        page.title,
        page.track_count.unwrap_or_default(),
        page.total_duration.unwrap_or_default(),
        page.artist.map(|a| a.name).unwrap_or_default(),
    );
    if let Some(d) = &page.description {
        println!("desc: {}...", &d[..d.len().min(90)]);
    }
    println!("playlist_id: {:?}", page.playlist_id);
    println!("tracks: {}", page.tracks.len());
    for t in page.tracks.iter().take(5) {
        println!(
            "  {:>2}. {:<32} {:>5}  {}",
            t.track_number.as_deref().unwrap_or("-"),
            t.title,
            t.duration.as_deref().unwrap_or("?:"),
            t.video_id.as_deref().unwrap_or(""),
        );
    }
    for c in &page.carousels {
        println!("carousel '{}': {} items", c.title, c.items.len());
        for i in c.items.iter().take(3) {
            println!("   - {:?} {:?}", i.category, i.title);
        }
    }
    Ok(())
}
