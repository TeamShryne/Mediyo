use serde_json::{json, Value};

use crate::error::{Error, Result};
use crate::model::search::parse_search_result;
use crate::model::{SearchFilter, SearchResponse, SearchResult};
use crate::parser;
use crate::session::Session;

/// Run a search. `params` is an optional base64 filter from a [`SearchFilter`]
/// (e.g. songs-only). Use [`search`] for an unscoped search.
pub fn search_with_params(
    session: &Session,
    query: &str,
    params: Option<&str>,
) -> Result<SearchResponse> {
    let mut body = json!({ "query": query });
    if let Some(p) = params {
        body["params"] = Value::String(p.to_string());
    }
    let resp = session.request("search", body)?;
    parse_search_response(&resp)
}

/// Unscoped search returning results plus the available filter chips.
pub fn search(session: &Session, query: &str) -> Result<SearchResponse> {
    search_with_params(session, query, None)
}

/// Fetch next page of a filtered search via its continuation token.
pub fn search_continuation(session: &Session, token: &str) -> Result<SearchResponse> {
    let resp = session.request("search", serde_json::json!({"continuation": token}))?;
    parse_search_continuation(&resp)
}

/// Parse a `search` endpoint response into results + filter chips.
pub fn parse_search_response(resp: &Value) -> Result<SearchResponse> {
    let tsr = resp
        .pointer("/contents/tabbedSearchResultsRenderer")
        .ok_or(Error::MissingField("contents.tabbedSearchResultsRenderer"))?;

    let tabs = tsr
        .get("tabs")
        .and_then(Value::as_array)
        .ok_or(Error::MissingField("tabs"))?;

    let tab = tabs
        .iter()
        .find(|t| t.pointer("/tabRenderer/selected").and_then(Value::as_bool) == Some(true))
        .or_else(|| tabs.first())
        .and_then(|t| t.get("tabRenderer"))
        .ok_or(Error::MissingField("tabRenderer"))?;

    let content = tab.get("content").ok_or(Error::MissingField("content"))?;
    let (_, section_list) = parser::renderer(content).ok_or(Error::Missing("content renderer"))?;

    let mut results: Vec<SearchResult> = Vec::new();
    let mut filters: Vec<SearchFilter> = Vec::new();

    if let Some(header) = section_list.get("header") {
        filters = parse_chips(header);
    }

    if let Some(sections) = section_list.get("contents").and_then(Value::as_array) {
        for section in sections {
            let Some((name, payload)) = parser::renderer(section) else {
                continue;
            };
            match name {
                "itemSectionRenderer" => {
                    if let Some(items) = payload.get("contents").and_then(Value::as_array) {
                        for item in items {
                            let Some((rname, _)) = parser::renderer(item) else {
                                continue;
                            };
                            if rname == "musicResponsiveListItemRenderer" {
                                results.push(parse_search_result(item)?);
                            }
                        }
                    }
                }
                "musicShelfRenderer" => {
                    // Filtered searches (Songs/Artists/Albums/...) return a bare
                    // musicShelfRenderer section instead of itemSectionRenderer.
                    if let Some(items) = payload.get("contents").and_then(Value::as_array) {
                        for item in items {
                            let Some((rname, _)) = parser::renderer(item) else {
                                continue;
                            };
                            match rname {
                                "musicResponsiveListItemRenderer" => {
                                    results.push(parse_search_result(item)?)
                                }
                                "musicTwoRowItemRenderer" => {
                                    results.push(crate::model::search::parse_two_row_item(item)?)
                                }
                                _ => {}
                            }
                        }
                    }
                }
                "musicCardShelfRenderer" => {
                    // Top-result card: contents[0] is a result renderer.
                    if let Some(inner) = payload.get("contents").and_then(Value::as_array) {
                        if let Some(first) = inner.first() {
                            if let Some((rname, _)) = parser::renderer(first) {
                                if rname == "musicResponsiveListItemRenderer" {
                                    results.push(parse_search_result(first)?);
                                }
                            }
                        }
                    }
                }
                _ => {}
            }
        }
    }

    let continuation = section_list
        .pointer("/continuations/0/nextContinuationData/continuation")
        .or_else(|| section_list.pointer("/continuations/0/musicShelfContinuation/continuation"))
        .and_then(|v| v.as_str())
        .map(String::from)
        .or_else(|| {
            // Filtered search: musicShelfRenderer holds the continuation
            section_list
                .get("contents")
                .and_then(|v| v.as_array())
                .and_then(|arr| arr.iter().find_map(|sec| sec.pointer("/musicShelfRenderer/continuations/0/nextContinuationData/continuation")))
                .and_then(|v| v.as_str())
                .map(String::from)
        })
        .or_else(|| {
            section_list
                .pointer("/continuations/0/gridContinuation/continuation")
                .and_then(|v| v.as_str())
                .map(String::from)
        });

    Ok(SearchResponse { filters, results, continuation })
}

pub fn parse_search_continuation(resp: &Value) -> Result<SearchResponse> {
    let cont = resp
        .pointer("/continuationContents/musicShelfContinuation")
        .or_else(|| resp.pointer("/continuationContents/itemSectionContinuation"))
        .ok_or(Error::MissingField("continuationContents"))?;

    let mut results = Vec::new();
    if let Some(contents) = cont.get("contents").and_then(Value::as_array) {
        for item in contents {
            if let Some((rname, _)) = parser::renderer(item) {
                if rname == "musicResponsiveListItemRenderer" {
                    results.push(parse_search_result(item)?);
                } else if rname == "musicTwoRowItemRenderer" {
                    results.push(crate::model::search::parse_two_row_item(item)?);
                }
            }
        }
    }
    let continuation = cont
        .pointer("/continuations/0/nextContinuationData/continuation")
        .or_else(|| cont.pointer("/continuations/0/musicShelfContinuation/continuation"))
        .and_then(|v| v.as_str())
        .map(String::from);

    Ok(SearchResponse { filters: Vec::new(), results, continuation })
}

fn parse_chips(header: &Value) -> Vec<SearchFilter> {
    let mut filters = Vec::new();
    let Some(chips) = header
        .pointer("/chipCloudRenderer/chips")
        .and_then(Value::as_array)
    else {
        return filters;
    };
    for chip in chips {
        let label = chip
            .pointer("/chipCloudChipRenderer/text/runs/0/text")
            .and_then(Value::as_str)
            .unwrap_or("");
        let query = chip
            .pointer("/chipCloudChipRenderer/navigationEndpoint/searchEndpoint/query")
            .and_then(Value::as_str)
            .unwrap_or("");
        let params = chip
            .pointer("/chipCloudChipRenderer/navigationEndpoint/searchEndpoint/params")
            .and_then(Value::as_str);
        filters.push(SearchFilter {
            label: label.to_string(),
            query: query.to_string(),
            params: params.map(String::from),
        });
    }
    filters
}
