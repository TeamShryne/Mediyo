use mediyo_core::api::comments::{comments_page, comment_continuation, reply_page};
use mediyo_core::model::comments::comments_token;
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    session.fetch_visitor_data()?;

    // Get the comments continuation token from the watch response.
    let next_resp = mediyo_core::api::watch::next(&session, "58dyibIUscg", None)?;
    let token = comments_token(&next_resp).ok_or("no comments token")?;

    // Fetch the first page of comments (default: "Top" sort).
    let mut page = comments_page(&session, &token)?;
    let total = page.count.as_deref().unwrap_or("?");
    println!("=== COMMENTS ({total}) ===\n");

    // Show available sort filters.
    println!("Sort filters:");
    for f in &page.sort_filters {
        let marker = if f.selected { " *" } else { "" };
        println!(
            "  {}{} — {}",
            f.title,
            marker,
            f.subtitle.as_deref().unwrap_or("")
        );
    }
    println!();

    // Switch to "Newest" sort if available.
    let newest_token = page
        .sort_filters
        .iter()
        .find(|f| f.title == "Newest")
        .map(|f| f.continuation_token.clone());

    if let Some(nt) = &newest_token {
        println!("=== SWITCHING TO NEWEST ===\n");
        page = comments_page(&session, nt)?;
    }

    for c in page.comments.iter().take(5) {
        let badge = if c.author.is_creator {
            " [creator]"
        } else if c.author.is_verified {
            " [verified]"
        } else {
            ""
        };
        println!("@{}{}  ({})", c.author.name, badge, c.published_time);
        println!("  {}", c.content);
        println!(
            "  likes={}  replies={}",
            c.like_count.as_deref().unwrap_or("0"),
            c.reply_count.as_deref().unwrap_or("0"),
        );
        // Fetch replies for the first comment that has them.
        if let Some(rt) = &c.replies_continuation {
            match reply_page(&session, rt) {
                Ok(replies) => {
                    println!("  └─ {} replies:", replies.comments.len());
                    for r in replies.comments.iter().take(3) {
                        println!(
                            "     @{}: {}",
                            r.author.name,
                            r.content.chars().take(60).collect::<String>()
                        );
                    }
                }
                Err(e) => println!("  └─ replies unavailable: {e}"),
            }
            break; // just demo one thread
        }
        println!();
    }

    // Pagination demo with Newest sort.
    let mut pages = 1;
    while let Some(ct) = page.continuation.take() {
        if pages >= 3 {
            break;
        }
        page = comment_continuation(&session, &ct)?;
        pages += 1;
        println!("  page {pages}: {} comments", page.comments.len());
    }

    Ok(())
}
