use crate::error::Result;
use crate::model::comments::{parse_comments_page, parse_reply_continuation, CommentsPage};
use crate::session::Session;

/// Fetch a page of comments using a continuation token from
/// [`crate::model::comments::comments_token`].
pub fn comments_page(session: &Session, token: &str) -> Result<CommentsPage> {
    let body = serde_json::json!({ "continuation": token });
    let resp = session.request("next", body)?;
    parse_comments_page(&resp)
}

/// Fetch the next page of comments using the continuation token from a
/// previous [`CommentsPage`].
pub fn comment_continuation(session: &Session, token: &str) -> Result<CommentsPage> {
    comments_page(session, token)
}

/// Fetch replies for a comment using the `replies_continuation` from
/// a top-level [`Comment`](crate::model::comments::Comment).
pub fn reply_page(session: &Session, token: &str) -> Result<CommentsPage> {
    let body = serde_json::json!({ "continuation": token });
    let resp = session.request("next", body)?;
    parse_reply_continuation(&resp)
}
