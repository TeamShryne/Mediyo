//! Thumbnail extraction from `musicThumbnailRenderer` nodes.

use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Thumbnail {
    pub url: String,
    pub width: u32,
    pub height: u32,
}

/// Extract thumbnails from a thumbnail renderer payload.
/// Path: `thumbnail.musicThumbnailRenderer.thumbnail.thumbnails[]`.
pub fn thumbnails(v: &Value) -> Vec<Thumbnail> {
    let mut out = Vec::new();
    let arr = v
        .pointer("/thumbnail/musicThumbnailRenderer/thumbnail/thumbnails")
        .and_then(Value::as_array);
    let Some(arr) = arr else {
        return out;
    };
    for t in arr {
        if let (Some(url), Some(width), Some(height)) = (
            t.get("url").and_then(Value::as_str),
            t.get("width").and_then(Value::as_u64),
            t.get("height").and_then(Value::as_u64),
        ) {
            out.push(Thumbnail {
                url: url.to_string(),
                width: width as u32,
                height: height as u32,
            });
        }
    }
    out
}

/// Highest-resolution thumbnail URL, if any.
pub fn best_thumbnail_url(v: &Value) -> Option<String> {
    thumbnails(v)
        .into_iter()
        .max_by_key(|t| t.width)
        .map(|t| t.url)
}

/// Extract thumbnails from a `musicTwoRowItemRenderer` payload.
/// Path: `thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails[]`.
pub fn thumbnails_two_row(v: &Value) -> Vec<Thumbnail> {
    let mut out = Vec::new();
    let arr = v
        .pointer("/thumbnailRenderer/musicThumbnailRenderer/thumbnail/thumbnails")
        .and_then(Value::as_array);
    let Some(arr) = arr else {
        return out;
    };
    for t in arr {
        if let (Some(url), Some(width), Some(height)) = (
            t.get("url").and_then(Value::as_str),
            t.get("width").and_then(Value::as_u64),
            t.get("height").and_then(Value::as_u64),
        ) {
            out.push(Thumbnail {
                url: url.to_string(),
                width: width as u32,
                height: height as u32,
            });
        }
    }
    out
}
