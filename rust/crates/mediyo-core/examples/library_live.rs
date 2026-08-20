use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Load cookies from env or file
    let cookie_header = std::env::var("YTM_COOKIES").ok().or_else(|| {
        std::fs::read_to_string("/tmp/opencode/cookies.txt")
            .ok()
            .map(|s| s.trim().to_string())
    });
    let Some(cookie_header) = cookie_header else {
        eprintln!("Set YTM_COOKIES env var or save cookies to /tmp/opencode/cookies.txt");
        std::process::exit(1);
    };
    // SAPISID extraction for auth hash
    let sapisid = cookie_header.split(';').find_map(|p| {
        let p = p.trim();
        p.strip_prefix("SAPISID=").map(|v| v.to_string())
    });

    let visitor_data = std::env::var("YTM_VISITOR_ID")
        .ok()
        .or_else(|| std::fs::read_to_string("/tmp/opencode/visitor.txt").ok().map(|s| s.trim().to_string()));
    let page_id = std::env::var("YTM_PAGE_ID")
        .ok()
        .or_else(|| std::fs::read_to_string("/tmp/opencode/pageid.txt").ok().map(|s| s.trim().to_string()));

    let mut client = mediyo_core::context::Client::new();
    if let Some(vd) = visitor_data {
        client = client.with_visitor_data(vd);
    } else {
        // fallback: fetch fresh visitorData
        let mut tmp = Session::new();
        let vd = tmp.fetch_visitor_data()?;
        client = client.with_visitor_data(vd.clone());
        println!("visitorData (fresh): {}...", &vd[..20.min(vd.len())]);
    }
    if let Some(pid) = page_id {
        client = client.with_page_id(pid);
    }
    let session = Session::new()
        .with_context(mediyo_core::context::Context::new().with_client(client))
        .with_cookies(cookie_header, sapisid);

    let landing = mediyo_core::api::library::landing(&session)?;
    println!("\nLIBRARY LANDING: {} tiles", landing.items.len());
    for it in &landing.items {
        println!("  - {} -> {}", it.title, it.browse_id.as_deref().unwrap_or("-"));
    }

    let pls = mediyo_core::api::library::playlists(&session)?;
    println!("\nPLAYLISTS: {} items", pls.items.len());
    for p in pls.items.iter().take(5) {
        println!("  - {} -> {}", p.title, p.browse_id.as_deref().unwrap_or("-"));
    }

    let songs = mediyo_core::api::library::songs(&session)?;
    println!("\nLIKED SONGS: {} items", songs.items.len());
    for s in songs.items.iter().take(5) {
        println!("  - {}", s.title);
    }

    let hist = mediyo_core::api::library::history(&session)?;
    println!("\nHISTORY: {} items", hist.items.len());
    for h in hist.items.iter().take(3) {
        println!("  - {}", h.title);
    }

    let acct = mediyo_core::api::library::account_info(&session)?;
    println!("\nACCOUNT: {} ({})", acct.name, acct.handle.as_deref().unwrap_or("-"));
    if let Some(url) = acct.photo_url {
        println!("  photo: {}...", &url[..60.min(url.len())]);
    }

    Ok(())
}
