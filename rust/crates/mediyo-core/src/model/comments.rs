use serde_json::Value;

use crate::error::Result;
use crate::parser::runs;

/// Author of a comment.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommentAuthor {
    pub channel_id: String,
    pub name: String,
    pub avatar: String,
    pub is_verified: bool,
    pub is_creator: bool,
    pub is_artist: bool,
}

/// A single comment (top-level or reply).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Comment {
    pub comment_id: String,
    pub content: String,
    pub published_time: String,
    pub author: CommentAuthor,
    pub like_count: Option<String>,
    pub reply_count: Option<String>,
    /// Depth: 0 = top-level, 1+ = reply.
    pub reply_level: u32,
    /// Continuation token to fetch replies (top-level comments only).
    pub replies_continuation: Option<String>,
}

/// A sort filter option from the comments header (e.g. "Top", "Newest").
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommentSortFilter {
    pub title: String,
    pub selected: bool,
    /// Continuation token to fetch comments in this sort order.
    pub continuation_token: String,
    pub subtitle: Option<String>,
}

/// A page of comments.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommentsPage {
    /// Total comment count as shown in the header (e.g. "7,164").
    pub count: Option<String>,
    pub comments: Vec<Comment>,
    /// Continuation token for the next page of comments.
    pub continuation: Option<String>,
    /// Available sort filters (e.g. Top, Newest).
    pub sort_filters: Vec<CommentSortFilter>,
}

/// Extract the comments continuation token from a `next` watch response.
///
/// Returns the `reloadContinuationData` token from the "Comments" tab.
/// This is the default "Top" sort order. Use [`CommentsPage::sort_filters`]
/// to get tokens for other sort orders (e.g. "Newest").
pub fn comments_token(next_resp: &Value) -> Option<String> {
    let tabs = next_resp
        .pointer(
            "/contents/singleColumnMusicWatchNextResultsRenderer/tabbedRenderer/watchNextTabbedResultsRenderer/tabs",
        )
        .and_then(Value::as_array)?;
    for tab in tabs {
        let tr = tab.get("tabRenderer")?;
        if tr.get("title").and_then(Value::as_str) == Some("Comments") {
            return tr
                .pointer("/content/sectionListRenderer/continuations/0/reloadContinuationData/continuation")
                .and_then(Value::as_str)
                .map(String::from);
        }
    }
    None
}

/// Parse a comments page response (from a `next` continuation call).
///
/// The response contains `onResponseReceivedEndpoints` with:
/// - `reloadContinuationItemsCommand` (initial / reload): header in slot HEADER, body in slot BODY
/// - `appendContinuationItemsAction` (pagination / replies): continuation items appended
///
/// Comment data is in `frameworkUpdates.entityBatchUpdate.mutations[].payload.commentEntityPayload`.
pub fn parse_comments_page(resp: &Value) -> Result<CommentsPage> {
    let mut count = None;
    let mut comments = Vec::new();
    let mut continuation = None;
    let mut sort_filters = Vec::new();

    // Extract comments from frameworkUpdates.entityBatchUpdate.mutations
    let comment_entities = comment_entities(resp);

    if let Some(endpoints) = resp.get("onResponseReceivedEndpoints").and_then(Value::as_array) {
        for ep in endpoints {
            // reloadContinuationItemsCommand (initial page)
            if let Some(cmd) = ep.get("reloadContinuationItemsCommand") {
                // Header
                if let Some(items) = cmd
                    .get("continuationItems")
                    .and_then(Value::as_array)
                {
                    for item in items {
                        if let Some(hdr) = item.get("commentsHeaderRenderer") {
                            count = hdr.get("countText").and_then(runs::text);

                            // Parse sort filters
                            sort_filters = parse_sort_filters(hdr);
                        }
                    }
                }
            }

            // appendContinuationItemsAction (pagination)
            if let Some(act) = ep.get("appendContinuationItemsAction") {
                if let Some(items) = act
                    .get("continuationItems")
                    .and_then(Value::as_array)
                {
                    // Last item may carry a continuation token
                    if let Some(last) = items.last() {
                        continuation = extract_continuation_token(last);
                    }
                }
            }

            // Also check reloadContinuationItemsCommand body slot for continuation
            if let Some(cmd) = ep.get("reloadContinuationItemsCommand") {
                if let Some(items) = cmd
                    .get("continuationItems")
                    .and_then(Value::as_array)
                {
                    if let Some(last) = items.last() {
                        if continuation.is_none() {
                            continuation = extract_continuation_token(last);
                        }
                    }
                }
            }
        }
    }

    // Match comment entities to commentThreadRenderer items to get reply continuations
    let thread_map = build_thread_reply_map(resp);

    // Parse comments from the comment entities
    for entity in &comment_entities {
        let cep = &entity.comment_entity;
        let comment_id = cep
            .pointer("/properties/commentId")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let content = cep
            .pointer("/properties/content/content")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let published_time = cep
            .pointer("/properties/publishedTime")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let reply_level = cep
            .pointer("/properties/replyLevel")
            .and_then(Value::as_u64)
            .unwrap_or(0) as u32;

        let author = CommentAuthor {
            channel_id: cep
                .pointer("/author/channelId")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            name: cep
                .pointer("/author/displayName")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            avatar: cep
                .pointer("/author/avatarThumbnailUrl")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            is_verified: cep
                .pointer("/author/isVerified")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            is_creator: cep
                .pointer("/author/isCreator")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            is_artist: cep
                .pointer("/author/isArtist")
                .and_then(Value::as_bool)
                .unwrap_or(false),
        };

        let like_count = cep
            .pointer("/toolbar/likeCountNotliked")
            .and_then(Value::as_str)
            .map(String::from);

        let reply_count = cep
            .pointer("/toolbar/replyCount")
            .and_then(Value::as_str)
            .map(String::from);

        // Only top-level comments have reply continuations in the thread map
        let replies_continuation = if reply_level == 0 {
            thread_map.get(&entity.entity_key).cloned()
        } else {
            None
        };

        comments.push(Comment {
            comment_id,
            content,
            published_time,
            author,
            like_count,
            reply_count,
            reply_level,
            replies_continuation,
        });
    }

    Ok(CommentsPage {
        count,
        comments,
        continuation,
        sort_filters,
    })
}

/// Parse a reply continuation response (from `next` with a reply token).
///
/// Uses `appendContinuationItemsAction` to extract replies, plus mutations
/// for comment data.
pub fn parse_reply_continuation(resp: &Value) -> Result<CommentsPage> {
    let mut continuation = None;

    if let Some(endpoints) = resp.get("onResponseReceivedEndpoints").and_then(Value::as_array) {
        for ep in endpoints {
            if let Some(act) = ep.get("appendContinuationItemsAction") {
                if let Some(items) = act
                    .get("continuationItems")
                    .and_then(Value::as_array)
                {
                    if let Some(last) = items.last() {
                        continuation = extract_continuation_token(last);
                    }
                }
            }
        }
    }

    let mut comments = Vec::new();
    let comment_entities = comment_entities(resp);

    for entity in &comment_entities {
        let cep = &entity.comment_entity;
        let comment_id = cep
            .pointer("/properties/commentId")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let content = cep
            .pointer("/properties/content/content")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let published_time = cep
            .pointer("/properties/publishedTime")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let reply_level = cep
            .pointer("/properties/replyLevel")
            .and_then(Value::as_u64)
            .unwrap_or(0) as u32;

        let author = CommentAuthor {
            channel_id: cep
                .pointer("/author/channelId")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            name: cep
                .pointer("/author/displayName")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            avatar: cep
                .pointer("/author/avatarThumbnailUrl")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            is_verified: cep
                .pointer("/author/isVerified")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            is_creator: cep
                .pointer("/author/isCreator")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            is_artist: cep
                .pointer("/author/isArtist")
                .and_then(Value::as_bool)
                .unwrap_or(false),
        };

        let like_count = cep
            .pointer("/toolbar/likeCountNotliked")
            .and_then(Value::as_str)
            .map(String::from);

        let reply_count = cep
            .pointer("/toolbar/replyCount")
            .and_then(Value::as_str)
            .map(String::from);

        comments.push(Comment {
            comment_id,
            content,
            published_time,
            author,
            like_count,
            reply_count,
            reply_level,
            replies_continuation: None,
        });
    }

    Ok(CommentsPage {
        count: None,
        comments,
        continuation,
        sort_filters: Vec::new(),
    })
}

// ── helpers ──────────────────────────────────────────────────────────────────

struct CommentEntity {
    entity_key: String,
    comment_entity: Value,
}

/// Extract all `commentEntityPayload` entries from `frameworkUpdates.entityBatchUpdate.mutations`.
fn comment_entities(resp: &Value) -> Vec<CommentEntity> {
    let mutations = resp
        .pointer("/frameworkUpdates/entityBatchUpdate/mutations")
        .and_then(Value::as_array);
    let mut out = Vec::new();
    if let Some(arr) = mutations {
        for m in arr {
            let entity_key = m
                .get("entityKey")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string();
            if let Some(cep) = m
                .get("payload")
                .and_then(|p| p.get("commentEntityPayload"))
            {
                out.push(CommentEntity {
                    entity_key,
                    comment_entity: cep.clone(),
                });
            }
        }
    }
    out
}

/// Map entity_key → replies_continuation from commentThreadRenderer items.
fn build_thread_reply_map(resp: &Value) -> std::collections::HashMap<String, String> {
    let mut map = std::collections::HashMap::new();
    let endpoints = match resp.get("onResponseReceivedEndpoints").and_then(Value::as_array) {
        Some(e) => e,
        None => return map,
    };
    for ep in endpoints {
        // reloadContinuationItemsCommand body slot (initial) + appendContinuationItemsAction (pagination)
        let items_opt = ep
            .get("reloadContinuationItemsCommand")
            .and_then(|cmd| cmd.get("continuationItems"))
            .or_else(|| {
                ep.get("appendContinuationItemsAction")
                    .and_then(|act| act.get("continuationItems"))
            });
        if let Some(items) = items_opt.and_then(Value::as_array) {
            for item in items {
                if let Some(ctr) = item.get("commentThreadRenderer") {
                    let entity_key = ctr
                        .get("commentViewModel")
                        .and_then(|vm| vm.get("commentViewModel"))
                        .and_then(|vm| vm.get("commentKey"))
                        .and_then(Value::as_str)
                        .unwrap_or_default()
                        .to_string();
                    let replies_token = extract_replies_token(ctr);
                    if !entity_key.is_empty() {
                        if let Some(token) = replies_token {
                            map.insert(entity_key, token);
                        }
                    }
                }
            }
        }
    }
    map
}

/// Extract the reply continuation token from a commentThreadRenderer's replies.
fn extract_replies_token(ctr: &Value) -> Option<String> {
    let sub_threads = ctr
        .get("replies")?
        .get("commentRepliesRenderer")?
        .get("subThreads")
        .and_then(Value::as_array)?;
    for st in sub_threads {
        if let Some(tok) = st
            .pointer("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")
            .and_then(Value::as_str)
        {
            return Some(tok.to_string());
        }
    }
    None
}

/// Extract a continuation token from a continuationItemRenderer.
/// Tries `continuationEndpoint.continuationCommand.token` first (pagination),
/// then `button.buttonRenderer.command.continuationCommand.token` (reply "Show more").
fn extract_continuation_token(item: &Value) -> Option<String> {
    let cir = item.get("continuationItemRenderer")?;
    // pagination continuation
    if let Some(tok) = cir
        .pointer("/continuationEndpoint/continuationCommand/token")
        .and_then(Value::as_str)
    {
        return Some(tok.to_string());
    }
    // "Show more replies" button continuation
    if let Some(tok) = cir
        .pointer("/button/buttonRenderer/command/continuationCommand/token")
        .and_then(Value::as_str)
    {
        return Some(tok.to_string());
    }
    None
}

/// Parse sort filter options from the `commentsHeaderRenderer`.
fn parse_sort_filters(hdr: &Value) -> Vec<CommentSortFilter> {
    let items = match hdr
        .pointer("/sortMenu/sortFilterSubMenuRenderer/subMenuItems")
        .and_then(Value::as_array)
    {
        Some(a) => a,
        None => return Vec::new(),
    };
    items
        .iter()
        .filter_map(|item| {
            let title = item.get("title")?.as_str()?.to_string();
            let token = item
                .pointer("/continuation/reloadContinuationData/continuation")
                .and_then(Value::as_str)?
                .to_string();
            let selected = item.get("selected").and_then(Value::as_bool).unwrap_or(false);
            let subtitle = item
                .get("subtitle")
                .and_then(Value::as_str)
                .map(String::from);
            Some(CommentSortFilter {
                title,
                selected,
                continuation_token: token,
                subtitle,
            })
        })
        .collect()
}
