use mediyo_core::api::browse::{
    artist, explore, home_continue, home_page, list_page, next_page, playlist,
};
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    session.fetch_visitor_data()?;

    let a = artist(&session, "UCeLHszkByNZtPKcaVXOCOQQ")?;
    println!(
        "ARTIST: {} — subscribers {} — {}",
        a.name,
        a.subscriber_count.unwrap_or_default(),
        a.monthly_audience.unwrap_or_default(),
    );
    println!("  top songs: {}", a.top_songs.len());
    for t in a.top_songs.iter().take(3) {
        println!(
            "    - {} / {}",
            t.title,
            t.video_id.as_deref().unwrap_or("")
        );
    }
    if let Some(ep) = &a.play_button {
        let vid = ep.video_id.as_deref().unwrap_or("-");
        println!("  play: {} ({})", vid, ep.playlist_id);
    }
    if let Some(ep) = &a.radio_button {
        let vid = ep.video_id.as_deref().unwrap_or("-");
        println!("  radio: {} ({})", vid, ep.playlist_id);
    }
    if let Some(s) = &a.share_entity {
        println!("  share: {s}");
    }
    for c in a.carousels.iter().take(4) {
        println!("  carousel '{}': {}", c.title, c.items.len());
    }

    let mut p = playlist(&session, "VLPLdshE9Fnm-qyACgmWRd9pRE3EzKDK1u0X")?;
    println!(
        "\nPLAYLIST: {} — {} ({})",
        p.title,
        p.track_count.unwrap_or_default(),
        p.total_duration.unwrap_or_default()
    );
    let mut total = p.tracks.len();
    for t in p.tracks.iter().take(3) {
        let artists: Vec<&str> = t.artists.iter().map(|a| a.name.as_str()).collect();
        println!(
            "    - {:<28} {:>5}  by {}",
            t.title,
            t.duration.as_deref().unwrap_or("?:"),
            artists.join(", ")
        );
    }

    // Walk every continuation page until the token runs out.
    let mut pages = 1;
    while let Some(token) = p.continuation.take() {
        let next = next_page(&session, &token)?;
        total += next.items.len();
        p.continuation = next.continuation;
        pages += 1;
    }
    println!("  total tracks (all pages): {total} across {pages} pages");

    // Artist carousel "View All": fetch the full discography grid.
    let singles = a
        .carousels
        .iter()
        .find(|c| c.title == "Singles & EPs")
        .expect("singles carousel");
    if let Some(view_all) = &singles.view_all {
        let disco = list_page(&session, &view_all.browse_id, view_all.params.as_deref())?;
        println!(
            "\nVIEW ALL '{}': {} items (first: {})",
            singles.title,
            disco.items.len(),
            disco.items.first().map(|i| i.title.as_str()).unwrap_or("-")
        );
    }

    let ep = explore(&session)?;
    println!(
        "\nEXPLORE: {} nav buttons, {} carousels",
        ep.nav_buttons.len(),
        ep.carousels.len()
    );
    for btn in &ep.nav_buttons {
        println!("  nav: {} -> {}", btn.label, btn.browse_id);
    }
    for c in ep.carousels.iter().take(3) {
        println!("  carousel '{}': {} items", c.title, c.items.len());
    }

    let home = home_page(&session)?;
    let mut carousels = home.carousels;
    let mut home_pages = 1;
    let mut token = home.continuation;
    while let Some(t) = token.take() {
        let next = home_continue(&session, &t)?;
        let n = next.carousels.len();
        carousels.extend(next.carousels);
        token = next.continuation;
        home_pages += 1;
        println!("  home page {home_pages}: +{n} carousels");
    }
    println!(
        "\nHOME: {} carousels across {home_pages} pages",
        carousels.len()
    );
    for c in carousels.iter().take(6) {
        let va = c
            .view_all
            .as_ref()
            .map(|v| format!(" [view all -> {}]", v.browse_id))
            .unwrap_or_default();
        println!("  - '{}': {} items{va}", c.title, c.items.len());
    }
    Ok(())
}
