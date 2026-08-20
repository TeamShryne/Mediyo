//! Text extraction: innertube text nodes are `{"runs": [...]}` or `{"simpleText": "..."}`.

use serde_json::Value;

/// Returns the `runs` array of a text node.
pub fn runs(v: &Value) -> Option<&Vec<Value>> {
    v.as_object()?.get("runs")?.as_array()
}

/// Concatenated text of all runs (or `simpleText`). Returns `None` when the
/// node holds no text.
pub fn text(v: &Value) -> Option<String> {
    if let Some(tt) = v.get("simpleText").and_then(Value::as_str) {
        return Some(tt.to_string());
    }
    let runs = runs(v)?;
    let mut out = String::new();
    for run in runs {
        if let Some(t) = run.get("text").and_then(Value::as_str) {
            out.push_str(t);
        }
    }
    Some(out)
}

/// Iterate runs of a text node. Each run carries `text` and optionally a
/// `navigationEndpoint` (used to link artist/album names).
pub fn run_items(v: &Value) -> Vec<(&str, Option<&Value>)> {
    let Some(runs) = runs(v) else {
        return vec![];
    };
    runs.iter()
        .filter_map(|r| {
            let t = r.get("text")?.as_str()?;
            Some((t, r.get("navigationEndpoint")))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn simple_text() {
        assert_eq!(
            text(&json!({ "simpleText": "hello" })),
            Some("hello".to_string())
        );
    }

    #[test]
    fn runs_concat() {
        let v = json!({ "runs": [
            { "text": "Album" },
            { "text": " • " },
            { "text": "Drake" }
        ]});
        assert_eq!(text(&v), Some("Album • Drake".to_string()));
    }
}
