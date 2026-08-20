use mediyo_core::api::library::{LikeStatus, add_to_playlist, create_playlist, rate_playlist, rate_song, remove_from_playlist};
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cookie_header = std::env::var("YTM_COOKIES")
        .ok()
        .or_else(|| std::fs::read_to_string("/tmp/opencode/cookies.txt").ok().map(|s| s.trim().to_string()))
        .expect("cookies");
    let sapisid = cookie_header.split(';').find_map(|p| p.trim().strip_prefix("SAPISID=").map(|v| v.to_string()));
    let visitor_data = std::fs::read_to_string("/tmp/opencode/visitor.txt").ok().map(|s| s.trim().to_string());
    let page_id = std::fs::read_to_string("/tmp/opencode/pageid.txt").ok().map(|s| s.trim().to_string());
    let mut client = mediyo_core::context::Client::new();
    if let Some(vd) = visitor_data { client = client.with_visitor_data(vd); }
    if let Some(pid) = page_id { client = client.with_page_id(pid); }
    let session = Session::new().with_context(mediyo_core::context::Context::new().with_client(client)).with_cookies(cookie_header, sapisid);

    // 1) Like a song, then remove like
    let vid = "0zmIgxfZz0M"; // Boom Shaka
    let r = rate_song(&session, vid, LikeStatus::Like)?;
    println!("like_song LIKE: {}", r.get("status").and_then(|v| v.as_str()).unwrap_or("no status"));
    let r = rate_song(&session, vid, LikeStatus::Indifferent)?;
    println!("like_song INDIFFERENT: {}", r.get("status").and_then(|v| v.as_str()).unwrap_or("no status"));

    // 2) Save a playlist/album/podcast to library (LIKE) then remove
    // Use a public playlist: 1000 HITS... PL id from earlier? Use browseId VLPL's playlistId without VL
    let public_playlist = "PLdshE9Fnm-qyACgmWRd9pRE3EzKDK1u0X"; // from research browse_playlist
    let r = rate_playlist(&session, public_playlist, LikeStatus::Like)?;
    println!("rate_playlist LIKE: {}", r.get("status").and_then(|v| v.as_str()).unwrap_or("ok"));
    let r = rate_playlist(&session, public_playlist, LikeStatus::Indifferent)?;
    println!("rate_playlist INDIFFERENT: {}", r.get("status").and_then(|v| v.as_str()).unwrap_or("ok"));

    // 3) Single add to playlist (create temp playlist, add, remove, delete)
    let title = format!(
        "mediyo-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis()
            % 100000
    );
    let pid = create_playlist(&session, &title, "mediyo test", "PRIVATE")?;
    println!("created playlist: {pid}");

    let add_resp = add_to_playlist(&session, &pid, vid)?;
    println!("add_to_playlist: {}", add_resp.get("status").and_then(|v| v.as_str()).unwrap_or("ok"));
    // Extract setVideoId for remove
    let set_vid = add_resp
        .get("playlistEditResults")
        .and_then(|v| v.as_array())
        .and_then(|arr| arr.first())
        .and_then(|v| v.get("playlistEditVideoAddedResultData"))
        .and_then(|v| v.get("newSetVideoId"))
        .and_then(|v| v.as_str())
        .unwrap_or("");
    if !set_vid.is_empty() {
        let r = remove_from_playlist(&session, &pid, set_vid, vid)?;
        println!("remove_from_playlist: {}", r.get("status").and_then(|v| v.as_str()).unwrap_or("ok"));
    }

    // Cleanup: delete playlist
    let del = session.request("playlist/delete", serde_json::json!({"playlistId": pid}))?;
    println!("delete_playlist: {}", del.get("status").and_then(|v| v.as_str()).unwrap_or("ok"));

    println!("\nAll mutations succeeded");
    Ok(())
}
