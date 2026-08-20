use serde_json::{json, Value};

use crate::error::Result;
use crate::model::browse::{
    parse_album_page, parse_artist_page, parse_explore_page, parse_home, parse_home_continuation,
    parse_home_page, parse_list_continuation, parse_list_page, parse_playlist_page,
};
use crate::model::{AlbumPage, ArtistPage, Carousel, ExplorePage, HomePage, ListPage, PlaylistPage};
use crate::session::Session;

/// POST a raw `browse` request for a browseId. Returns the raw response JSON.
pub fn browse(session: &Session, browse_id: &str) -> Result<Value> {
    browse_with_params(session, browse_id, None)
}

/// POST a raw `browse` request for a browseId with optional filter `params`
/// (used by artist "View All" navigation, discography pages, etc.).
pub fn browse_with_params(
    session: &Session,
    browse_id: &str,
    params: Option<&str>,
) -> Result<Value> {
    let mut body = json!({ "browseId": browse_id });
    if let Some(p) = params {
        body["params"] = Value::String(p.to_string());
    }
    session.request("browse", body)
}

/// POST a raw `browse` continuation request. Returns the raw response JSON.
pub fn browse_continuation(session: &Session, token: &str) -> Result<Value> {
    let body = json!({ "continuation": token });
    session.request("browse", body)
}

/// Fetch the next page of items for a playlist/album/carousel (or any list
/// shelf) given the continuation token from a previous page.
pub fn next_page(session: &Session, token: &str) -> Result<ListPage> {
    let resp = browse_continuation(session, token)?;
    parse_list_continuation(&resp)
}

/// Fetch a generic "view all" / discovery page (shelf, grid, or carousel of
/// items) by browseId + params, e.g. from [`Carousel::view_all`].
pub fn list_page(session: &Session, browse_id: &str, params: Option<&str>) -> Result<ListPage> {
    let resp = browse_with_params(session, browse_id, params)?;
    parse_list_page(&resp)
}

/// Fetch and parse an album page by browseId (e.g. `MPREb_...`).
pub fn album(session: &Session, browse_id: &str) -> Result<AlbumPage> {
    let resp = browse(session, browse_id)?;
    parse_album_page(&resp)
}

/// Fetch and parse a playlist page by browseId (e.g. `VL...`).
pub fn playlist(session: &Session, browse_id: &str) -> Result<PlaylistPage> {
    let resp = browse(session, browse_id)?;
    parse_playlist_page(&resp)
}

/// Fetch and parse an artist page by browseId (e.g. `UC...`).
pub fn artist(session: &Session, browse_id: &str) -> Result<ArtistPage> {
    let resp = browse(session, browse_id)?;
    parse_artist_page(&resp)
}

/// Fetch and parse the homepage recommendations (first page of carousels).
pub fn home(session: &Session) -> Result<Vec<Carousel>> {
    let resp = browse(session, "FEmusic_home")?;
    parse_home(&resp)
}

/// Fetch the homepage recommendations including the continuation token.
pub fn home_page(session: &Session) -> Result<HomePage> {
    let resp = browse(session, "FEmusic_home")?;
    parse_home_page(&resp)
}

/// Fetch the explore page: navigation buttons + carousels.
pub fn explore(session: &Session) -> Result<ExplorePage> {
    let resp = browse(session, "FEmusic_explore")?;
    parse_explore_page(&resp)
}

/// Fetch the next batch of homepage carousels given a continuation token.
pub fn home_continue(session: &Session, token: &str) -> Result<HomePage> {
    let resp = browse_continuation(session, token)?;
    parse_home_continuation(&resp)
}
