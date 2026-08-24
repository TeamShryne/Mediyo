use mediyo_core::api::browse::{home_continue, home_page, next_page, playlist};
use mediyo_core::api::search::{search, search_continuation, search_with_params};
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut s = Session::new();
    s.fetch_visitor_data()?;
    println!("== visitor: {:?}\n", s.context().client.visitor_data);

    // ── 1) plain search + filters ──
    let resp = search(&s, "drake")?;
    println!(
        "plain search: {} results, continuation={}",
        resp.results.len(),
        resp.continuation.is_some()
    );

    // ── 2) filtered search (root cause check for empty results) ──
    if let Some(f) = resp.filters.iter().find(|f| f.params.is_some()) {
        let fr = search_with_params(&s, "drake", Some(f.params.as_deref().unwrap()))?;
        println!(
            "filtered [{:>20}]: {} results, continuation={}",
            f.label,
            fr.results.len(),
            fr.continuation.is_some()
        );
    }

    // ── 3) home pagination ──
    let hp = home_page(&s)?;
    let items1: usize = hp.carousels.iter().map(|c| c.items.len()).sum();
    println!(
        "\nhome p1: {} carousels / {} items, continuation={}",
        hp.carousels.len(),
        items1,
        hp.continuation.is_some()
    );
    if let Some(t) = &hp.continuation {
        let hc = home_continue(&s, t)?;
        let items2: usize = hc.carousels.iter().map(|c| c.items.len()).sum();
        println!(
            "home p2: {} carousels / {} items, continuation={}",
            hc.carousels.len(),
            items2,
            hc.continuation.is_some()
        );
    }

    // ── 4) playlist pagination on a LARGE community playlist ──
    let cp = resp
        .filters
        .iter()
        .find(|f| f.label.contains("Community") && f.params.is_some())
        .cloned();
    let mut candidates: Vec<String> = Vec::new();
    if let Some(f) = cp {
        let fr = search_with_params(&s, "top hits", Some(f.params.as_deref().unwrap()))?;
        if let Some(tok) = &fr.continuation {
            let more = search_continuation(&s, tok)?;
            for r in &more.results {
                if let Some(pid) = r.playlist_id.clone().or_else(|| r.browse_id.clone()) {
                    candidates.push(pid);
                }
            }
        }
    }
    println!("\nplaylist candidates: {}", candidates.len());
    'outer: for pid in candidates.iter().take(5) {
        let page = match playlist(&s, pid) {
            Ok(p) => p,
            Err(e) => { println!("playlist {pid} err: {e}"); continue; }
        };
        println!("playlist {pid}: {} tracks, continuation={}", page.tracks.len(), page.continuation.is_some());
        let Some(t) = page.continuation.clone() else { continue };
        let np = next_page(&s, &t)?;
        println!("  p2 same-session: {} items, continuation={}", np.items.len(), np.continuation.is_some());
        if !np.items.is_empty() {
            println!("    p1[0]={:?} p2[0]={:?} dup={}", page.tracks[0].video_id, np.items[0].video_id, page.tracks[0].video_id == np.items[0].video_id);
            let mut s2 = Session::new();
            s2.fetch_visitor_data()?;
            let np2 = next_page(&s2, &t)?;
            println!("  p2 fresh-session: {} items", np2.items.len());
            if !np2.items.is_empty() {
                println!("    fresh p2[0]={:?}", np2.items[0].video_id);
            }
            break 'outer;
        }
    }

    // ── 5) the REAL app flow: open playlists straight from HOME shelves ──
    println!("\n== home-shelf playlists ==");
    let mut checked = 0;
    let mut all_home_carousels = hp.carousels.clone();
    if let Some(t) = &hp.continuation {
        if let Ok(hc) = home_continue(&s, t) {
            all_home_carousels.extend(hc.carousels);
        }
    }
    'outer5: for c in &all_home_carousels {
        for it in &c.items {
            if checked >= 8 { break 'outer5; }
            let Some(pid) = it.playlist_id.clone().or_else(|| it.browse_id.clone().filter(|b| b.starts_with("VL"))) else { continue };
            let page = match playlist(&s, &pid) {
                Ok(p) => p,
                Err(e) => { println!("{pid}: open err {e}"); continue },
            };
            match page.continuation.clone() {
                None => println!("{} [{}] tracks={} no-continuation", pid, c.title, page.tracks.len()),
                Some(t) => {
                    let np = match next_page(&s, &t) { Ok(n) => n, Err(e) => { println!("{pid}: cont err {e}"); continue } };
                    let p1_ids: Vec<_> = page.tracks.iter().filter_map(|r| r.video_id.clone()).collect();
                    let p2_ids: Vec<_> = np.items.iter().filter_map(|r| r.video_id.clone()).collect();
                    let inter = p1_ids.iter().filter(|id| p2_ids.contains(id)).count();
                    println!(
                        "{} [{}] p1={} p2={} overlap={} p2cont={} first_dup={}",
                        pid, c.title, p1_ids.len(), p2_ids.len(), inter, np.continuation.is_some(),
                        match (p1_ids.first(), p2_ids.first()) { (Some(a), Some(b)) => a == b, _ => false }
                    );
                }
            }
            checked += 1;
        }
    }

    Ok(())
}
