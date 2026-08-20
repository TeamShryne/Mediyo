use mediyo_core::model::comments::{
    comments_token, parse_comments_page, parse_reply_continuation, CommentsPage,
};

const NEXT_FIXTURE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/next_song.json");
const COMMENTS_FIXTURE: &str =
    concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/comments.json");
const REPLIES_FIXTURE: &str =
    concat!(env!("CARGO_MANIFEST_DIR"), "/../../research/comments_replies.json");

fn fixture() -> serde_json::Value {
    let raw = std::fs::read_to_string(COMMENTS_FIXTURE).expect("comments.json readable");
    serde_json::from_str(&raw).expect("comments.json valid JSON")
}

fn replies_fixture() -> serde_json::Value {
    let raw = std::fs::read_to_string(REPLIES_FIXTURE).expect("replies.json readable");
    serde_json::from_str(&raw).expect("replies.json valid JSON")
}

#[test]
fn extracts_comments_token_from_watch_response() {
    let raw = std::fs::read_to_string(NEXT_FIXTURE).expect("next_song.json readable");
    let next_resp: serde_json::Value = serde_json::from_str(&raw).expect("valid JSON");
    let token = comments_token(&next_resp);
    assert!(token.is_some(), "should find comments continuation token");
    let t = token.unwrap();
    assert!(t.starts_with("Eg0SC"), "token should start with Eg0SC: {t}");
}

#[test]
fn parses_comments_page() {
    let resp = fixture();
    let page: CommentsPage = parse_comments_page(&resp).unwrap();

    // header count
    let count = page.count.as_deref().unwrap();
    assert!(count.contains("7"), "count should be like 7,164: {count}");

    // sort filters
    assert_eq!(page.sort_filters.len(), 2, "should have Top and Newest");
    let top = page.sort_filters.iter().find(|f| f.title == "Top").unwrap();
    assert!(top.selected, "Top should be selected");
    assert!(!top.continuation_token.is_empty());
    let newest = page
        .sort_filters
        .iter()
        .find(|f| f.title == "Newest")
        .unwrap();
    assert!(!newest.selected);
    assert!(!newest.continuation_token.is_empty());

    // 20 top-level comments
    assert_eq!(page.comments.len(), 20, "expected 20 comments per page");

    // first comment
    let c = &page.comments[0];
    assert!(!c.comment_id.is_empty());
    assert!(!c.content.is_empty(), "comment should have content");
    assert!(!c.author.name.is_empty(), "author should have name");
    assert_eq!(c.reply_level, 0, "top-level should be level 0");
    assert!(
        c.replies_continuation.is_some(),
        "first comment should have replies continuation"
    );
}

#[test]
fn parses_reply_continuation() {
    let resp = replies_fixture();
    let page: CommentsPage = parse_reply_continuation(&resp).unwrap();

    assert!(
        !page.comments.is_empty(),
        "reply page should have reply comments"
    );

    let reply = &page.comments[0];
    assert_eq!(reply.reply_level, 1, "replies should be level 1");
    assert!(!reply.content.is_empty());
    assert!(!reply.author.name.is_empty());

    // replies may have their own continuation for "Show more replies"
    // (depends on the thread)
}
