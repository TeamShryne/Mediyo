use std::sync::{Arc, Mutex};

use mediyo_core::Session;

uniffi::setup_scaffolding!("mediyo_ffi");

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MediyoError {
    #[error("API error: {msg}")]
    Api { msg: String },
}

impl From<mediyo_core::Error> for MediyoError {
    fn from(e: mediyo_core::Error) -> Self { MediyoError::Api { msg: e.to_string() } }
}

#[derive(uniffi::Object)]
pub struct MediyoSession { inner: Mutex<Session> }

#[uniffi::export]
impl MediyoSession {
    #[uniffi::constructor] pub fn new() -> Arc<Self> { Arc::new(Self { inner: Mutex::new(Session::new()) }) }
    #[uniffi::constructor] pub fn with_cookies(cookie: String, sapisid: Option<String>) -> Arc<Self> {
        Arc::new(Self { inner: Mutex::new(Session::new().with_cookies(cookie, sapisid)) })
    }
    #[uniffi::constructor] pub fn with_all(cookie: String, sapisid: Option<String>, visitor_data: String, page_id: Option<String>) -> Arc<Self> {
        let mut client = mediyo_core::context::Client::new();
        if !visitor_data.is_empty() {
            client = client.with_visitor_data(visitor_data);
        }
        if let Some(pid) = page_id { client = client.with_page_id(pid); }
        let ctx = mediyo_core::context::Context::new().with_client(client);
        Arc::new(Self { inner: Mutex::new(Session::new().with_context(ctx).with_cookies(cookie, sapisid)) })
    }
    pub fn fetch_visitor_data(&self) -> Result<String, MediyoError> { Ok(self.inner.lock().unwrap().fetch_visitor_data()?) }
}

// ── common ───────────────────────────────────────────────────────────────
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiThumbnail { pub url: String, pub width: u32, pub height: u32 }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiSearchResult {
    pub title: String, pub video_id: Option<String>, pub browse_id: Option<String>, pub playlist_id: Option<String>,
    pub category: String, pub year: Option<String>, pub duration: Option<String>, pub explicit: bool,
    pub thumbnails: Vec<FfiThumbnail>,
    pub artists: Vec<String>,
    pub album: Option<String>,
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiSearchFilter { pub label: String, pub query: String, pub params: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiSearchResponse { pub results: Vec<FfiSearchResult>, pub filters: Vec<FfiSearchFilter>, pub continuation: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiCarousel { pub title: String, pub items: Vec<FfiSearchResult>, pub continuation: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiViewAll { pub browse_id: String, pub params: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiWatchEndpoint { pub video_id: Option<String>, pub playlist_id: String, pub params: Option<String> }
fn to_ffi_search(r: mediyo_core::model::SearchResult) -> FfiSearchResult {
    FfiSearchResult {
        title: r.title.clone(), video_id: r.video_id.clone(), browse_id: r.browse_id.clone(), playlist_id: r.playlist_id.clone(),
        category: format!("{:?}", r.category), year: r.year.clone(), duration: r.duration.clone(), explicit: r.explicit,
        thumbnails: r.thumbnails.into_iter().map(to_ffi_thumb).collect(),
        artists: r.artists.into_iter().map(|a| a.name).collect(),
        album: r.album.map(|a| a.name),
    }
}
fn to_ffi_thumb(t: mediyo_core::parser::thumbnails::Thumbnail) -> FfiThumbnail { FfiThumbnail { url: t.url, width: t.width, height: t.height } }

// ── search ───────────────────────────────────────────────────────────────
#[uniffi::export] pub fn search(session: Arc<MediyoSession>, query: String) -> Result<FfiSearchResponse, MediyoError> {
    let g = session.inner.lock().unwrap();
    let r = mediyo_core::api::search::search(&g, &query)?;
    Ok(FfiSearchResponse { results: r.results.into_iter().map(to_ffi_search).collect(), filters: r.filters.into_iter().map(|f| FfiSearchFilter{label:f.label, query:f.query, params:f.params}).collect(), continuation: r.continuation })
}
#[uniffi::export] pub fn search_with_params(session: Arc<MediyoSession>, query: String, params: String) -> Result<FfiSearchResponse, MediyoError> {
    let g = session.inner.lock().unwrap();
    let r = mediyo_core::api::search::search_with_params(&g, &query, Some(&params))?;
    Ok(FfiSearchResponse { results: r.results.into_iter().map(to_ffi_search).collect(), filters: r.filters.into_iter().map(|f| FfiSearchFilter{label:f.label, query:f.query, params:f.params}).collect(), continuation: r.continuation })
}
#[uniffi::export] pub fn search_continuation(session: Arc<MediyoSession>, token: String) -> Result<FfiSearchResponse, MediyoError> {
    let g = session.inner.lock().unwrap();
    let r = mediyo_core::api::search::search_continuation(&g, &token)?;
    Ok(FfiSearchResponse { results: r.results.into_iter().map(to_ffi_search).collect(), filters: Vec::new(), continuation: r.continuation })
}

// ── browse ───────────────────────────────────────────────────────────────
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiHomePage { pub carousels: Vec<FfiCarousel>, pub continuation: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiExplorePage { pub nav_buttons: Vec<FfiNavButton>, pub carousels: Vec<FfiCarousel> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiNavButton { pub label: String, pub browse_id: String, pub params: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiArtistPage {
    pub name: String, pub subscriber_count: Option<String>, pub monthly_audience: Option<String>, pub description: Option<String>,
    pub thumbnails: Vec<FfiThumbnail>,
    pub top_songs: Vec<FfiSearchResult>, pub carousels: Vec<FfiCarousel>, pub continuation: Option<String>,
    pub play_button: Option<FfiWatchEndpoint>, pub radio_button: Option<FfiWatchEndpoint>, pub share_entity: Option<String>,
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiAlbumPage {
    pub title: String, pub artist: Option<String>, pub year: Option<String>,
    pub thumbnails: Vec<FfiThumbnail>,
    pub tracks: Vec<FfiSearchResult>, pub carousels: Vec<FfiCarousel>, pub continuation: Option<String>
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiPlaylistPage {
    pub title: String, pub track_count: Option<String>,
    pub thumbnails: Vec<FfiThumbnail>,
    pub tracks: Vec<FfiSearchResult>, pub continuation: Option<String>
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiListPage { pub items: Vec<FfiSearchResult>, pub continuation: Option<String> }
fn to_ffi_carousel(c: mediyo_core::model::Carousel) -> FfiCarousel { FfiCarousel { title: c.title, items: c.items.into_iter().map(to_ffi_search).collect(), continuation: c.continuation } }
fn to_ffi_watch_ep(e: mediyo_core::model::WatchEndpoint) -> FfiWatchEndpoint { FfiWatchEndpoint { video_id: e.video_id, playlist_id: e.playlist_id, params: e.params } }

#[uniffi::export] pub fn browse_home(session: Arc<MediyoSession>) -> Result<FfiHomePage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::home_page(&g)?;
    Ok(FfiHomePage { carousels: p.carousels.into_iter().map(to_ffi_carousel).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_home_continue(session: Arc<MediyoSession>, token: String) -> Result<FfiHomePage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::home_continue(&g, &token)?;
    Ok(FfiHomePage { carousels: p.carousels.into_iter().map(to_ffi_carousel).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_explore(session: Arc<MediyoSession>) -> Result<FfiExplorePage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::explore(&g)?;
    Ok(FfiExplorePage { nav_buttons: p.nav_buttons.into_iter().map(|b| FfiNavButton{label:b.label, browse_id:b.browse_id, params:b.params}).collect(), carousels: p.carousels.into_iter().map(to_ffi_carousel).collect() })
}
#[uniffi::export] pub fn browse_artist(session: Arc<MediyoSession>, browse_id: String) -> Result<FfiArtistPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::artist(&g, &browse_id)?;
    Ok(FfiArtistPage { name: p.name, subscriber_count: p.subscriber_count, monthly_audience: p.monthly_audience, description: p.description, thumbnails: p.thumbnails.into_iter().map(to_ffi_thumb).collect(), top_songs: p.top_songs.into_iter().map(to_ffi_search).collect(), carousels: p.carousels.into_iter().map(to_ffi_carousel).collect(), continuation: p.continuation, play_button: p.play_button.map(to_ffi_watch_ep), radio_button: p.radio_button.map(to_ffi_watch_ep), share_entity: p.share_entity })
}
#[uniffi::export] pub fn browse_album(session: Arc<MediyoSession>, browse_id: String) -> Result<FfiAlbumPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::album(&g, &browse_id)?;
    Ok(FfiAlbumPage { title: p.title, artist: p.artist.map(|a| a.name), year: p.year, thumbnails: p.thumbnails.into_iter().map(to_ffi_thumb).collect(), tracks: p.tracks.into_iter().map(to_ffi_search).collect(), carousels: p.carousels.into_iter().map(to_ffi_carousel).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_playlist(session: Arc<MediyoSession>, browse_id: String) -> Result<FfiPlaylistPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::playlist(&g, &browse_id)?;
    Ok(FfiPlaylistPage { title: p.title, track_count: p.track_count, thumbnails: p.thumbnails.into_iter().map(to_ffi_thumb).collect(), tracks: p.tracks.into_iter().map(to_ffi_search).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_list_page(session: Arc<MediyoSession>, browse_id: String, params: Option<String>) -> Result<FfiListPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::list_page(&g, &browse_id, params.as_deref())?;
    Ok(FfiListPage { items: p.items.into_iter().map(to_ffi_search).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_next_page(session: Arc<MediyoSession>, token: String) -> Result<FfiListPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::browse::next_page(&g, &token)?;
    Ok(FfiListPage { items: p.items.into_iter().map(to_ffi_search).collect(), continuation: p.continuation })
}
#[uniffi::export] pub fn browse_podcast(session: Arc<MediyoSession>, browse_id: String) -> Result<FfiListPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let resp = g.request("browse", serde_json::json!({"browseId": browse_id}))?;
    let p = mediyo_core::model::browse::parse_list_page(&resp)?;
    Ok(FfiListPage { items: p.items.into_iter().map(to_ffi_search).collect(), continuation: p.continuation })
}

// ── watch ──────────────────────────────────────────────────────────────────
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiQueueItem {
    pub title: String, pub video_id: String, pub artists: Vec<String>,
    pub album: Option<String>, pub duration: Option<String>, pub thumbnails: Vec<FfiThumbnail>
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiQueue { pub playlist_id: String, pub is_infinite: bool, pub items: Vec<FfiQueueItem>, pub continuation: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiSong {
    pub title: String, pub video_id: String, pub artists: Vec<String>, pub album: Option<String>,
    pub duration: Option<String>, pub thumbnails: Vec<FfiThumbnail>, pub lyrics_browse_id: Option<String>
}
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiLyrics { pub lines: Vec<String> }
#[uniffi::export] pub fn watch_get_song(session: Arc<MediyoSession>, video_id: String, playlist_id: Option<String>) -> Result<FfiSong, MediyoError> {
    let g = session.inner.lock().unwrap();
    let s = mediyo_core::api::watch::get_song(&g, &video_id, playlist_id.as_deref())?;
    Ok(FfiSong {
        title: s.title, video_id: s.video_id, artists: s.artists.into_iter().map(|a| a.name).collect(), album: s.album,
        duration: s.duration, thumbnails: s.thumbnail.into_iter().map(|u| FfiThumbnail{url:u, width:0, height:0}).collect(), lyrics_browse_id: s.lyrics_browse_id
    })
}
#[uniffi::export] pub fn watch_get_queue(session: Arc<MediyoSession>, video_id: String, playlist_id: Option<String>) -> Result<FfiQueue, MediyoError> {
    let g = session.inner.lock().unwrap();
    let q = mediyo_core::api::watch::get_queue(&g, &video_id, playlist_id.as_deref())?;
    Ok(FfiQueue { playlist_id: q.playlist_id, is_infinite: q.is_infinite, items: q.items.into_iter().map(|i| FfiQueueItem{
        title:i.title, video_id:i.video_id, artists:i.artists.into_iter().map(|a|a.name).collect(), album:i.album, duration:i.duration,
        thumbnails: i.thumbnail.into_iter().map(|u| FfiThumbnail{url:u, width:0, height:0}).collect()
    }).collect(), continuation: q.continuation })
}
#[uniffi::export] pub fn watch_extend_queue(session: Arc<MediyoSession>, token: String) -> Result<FfiQueue, MediyoError> {
    let g = session.inner.lock().unwrap();
    let q = mediyo_core::api::watch::extend_queue(&g, &token)?;
    Ok(FfiQueue { playlist_id: q.playlist_id, is_infinite: q.is_infinite, items: q.items.into_iter().map(|i| FfiQueueItem{
        title:i.title, video_id:i.video_id, artists:i.artists.into_iter().map(|a|a.name).collect(), album:i.album, duration:i.duration,
        thumbnails: i.thumbnail.into_iter().map(|u| FfiThumbnail{url:u, width:0, height:0}).collect()
    }).collect(), continuation: q.continuation })
}
#[uniffi::export] pub fn watch_get_lyrics(session: Arc<MediyoSession>, browse_id: String) -> Result<FfiLyrics, MediyoError> {
    let g = session.inner.lock().unwrap();
    let l = mediyo_core::api::watch::get_lyrics(&g, &browse_id)?;
    Ok(FfiLyrics { lines: l.lines })
}

// ── comments ───────────────────────────────────────────────────────────────
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiComment { pub content: String, pub author: String, pub published_time: String, pub like_count: Option<String>, pub reply_count: Option<String>, pub replies_continuation: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiCommentSortFilter { pub title: String, pub selected: bool, pub continuation_token: String }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiCommentsPage { pub count: Option<String>, pub comments: Vec<FfiComment>, pub continuation: Option<String>, pub sort_filters: Vec<FfiCommentSortFilter> }
#[uniffi::export] pub fn comments_token(session: Arc<MediyoSession>, video_id: String) -> Result<Option<String>, MediyoError> {
    let g = session.inner.lock().unwrap();
    let next = mediyo_core::api::watch::next(&g, &video_id, None)?;
    Ok(mediyo_core::model::comments_token(&next))
}
#[uniffi::export] pub fn comments_page(session: Arc<MediyoSession>, token: String) -> Result<FfiCommentsPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::comments::comments_page(&g, &token)?;
    Ok(FfiCommentsPage { count: p.count, comments: p.comments.into_iter().map(|c| FfiComment{content:c.content, author:c.author.name, published_time:c.published_time, like_count:c.like_count, reply_count:c.reply_count, replies_continuation:c.replies_continuation}).collect(), continuation: p.continuation, sort_filters: p.sort_filters.into_iter().map(|f| FfiCommentSortFilter{title:f.title, selected:f.selected, continuation_token:f.continuation_token}).collect() })
}
#[uniffi::export] pub fn comments_next_page(session: Arc<MediyoSession>, token: String) -> Result<FfiCommentsPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::comments::comment_continuation(&g, &token)?;
    Ok(FfiCommentsPage { count: p.count, comments: p.comments.into_iter().map(|c| FfiComment{content:c.content, author:c.author.name, published_time:c.published_time, like_count:c.like_count, reply_count:c.reply_count, replies_continuation:c.replies_continuation}).collect(), continuation: p.continuation, sort_filters: p.sort_filters.into_iter().map(|f| FfiCommentSortFilter{title:f.title, selected:f.selected, continuation_token:f.continuation_token}).collect() })
}
#[uniffi::export] pub fn comments_replies(session: Arc<MediyoSession>, token: String) -> Result<FfiCommentsPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let p = mediyo_core::api::comments::reply_page(&g, &token)?;
    Ok(FfiCommentsPage { count: p.count, comments: p.comments.into_iter().map(|c| FfiComment{content:c.content, author:c.author.name, published_time:c.published_time, like_count:c.like_count, reply_count:c.reply_count, replies_continuation:c.replies_continuation}).collect(), continuation: p.continuation, sort_filters: Vec::new() })
}

// ── library ────────────────────────────────────────────────────────────────
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiAccountInfo { pub name: String, pub handle: Option<String>, pub photo_url: Option<String> }
#[derive(Debug, Clone, uniffi::Record)] pub struct FfiLibraryPage { pub titles: Vec<String>, pub browse_ids: Vec<Option<String>>, pub continuation: Option<String> }
fn to_ffi_lib_page(p: mediyo_core::model::LibraryPage) -> FfiLibraryPage {
    FfiLibraryPage { titles: p.items.iter().map(|i| i.title.clone()).collect(), browse_ids: p.items.iter().map(|i| i.browse_id.clone()).collect(), continuation: p.continuation }
}
#[uniffi::export] pub fn library_landing(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::landing(&g)?))
}
#[uniffi::export] pub fn library_playlists(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::playlists(&g)?))
}
#[uniffi::export] pub fn library_songs(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::songs(&g)?))
}
#[uniffi::export] pub fn library_albums(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::albums(&g)?))
}
#[uniffi::export] pub fn library_artists(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::artists(&g)?))
}
#[uniffi::export] pub fn library_subscriptions(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::subscriptions(&g)?))
}
#[uniffi::export] pub fn library_history(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(to_ffi_lib_page(mediyo_core::api::library::history(&g)?))
}
#[uniffi::export] pub fn library_podcasts(session: Arc<MediyoSession>) -> Result<FfiLibraryPage, MediyoError> {
    let g = session.inner.lock().unwrap();
    let resp = g.request("browse", serde_json::json!({"browseId": "FEmusic_library_non_music_audio_list"}))?;
    let p = mediyo_core::model::library::parse_library_songs(&resp)?;
    Ok(to_ffi_lib_page(p))
}
#[uniffi::export] pub fn account_info(session: Arc<MediyoSession>) -> Result<FfiAccountInfo, MediyoError> {
    let g = session.inner.lock().unwrap();
    let a = mediyo_core::api::library::account_info(&g)?;
    Ok(FfiAccountInfo { name: a.name, handle: a.handle, photo_url: a.photo_url })
}
#[derive(Debug, Clone, uniffi::Enum)] pub enum FfiLikeStatus { Like, Dislike, Indifferent }
#[uniffi::export] pub fn rate_song(session: Arc<MediyoSession>, video_id: String, status: FfiLikeStatus) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap();
    let s = match status { FfiLikeStatus::Like => mediyo_core::api::library::LikeStatus::Like, FfiLikeStatus::Dislike => mediyo_core::api::library::LikeStatus::Dislike, FfiLikeStatus::Indifferent => mediyo_core::api::library::LikeStatus::Indifferent };
    mediyo_core::api::library::rate_song(&g, &video_id, s)?; Ok(())
}
#[uniffi::export] pub fn rate_playlist(session: Arc<MediyoSession>, playlist_id: String, status: FfiLikeStatus) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap();
    let s = match status { FfiLikeStatus::Like => mediyo_core::api::library::LikeStatus::Like, FfiLikeStatus::Dislike => mediyo_core::api::library::LikeStatus::Dislike, FfiLikeStatus::Indifferent => mediyo_core::api::library::LikeStatus::Indifferent };
    mediyo_core::api::library::rate_playlist(&g, &playlist_id, s)?; Ok(())
}
#[uniffi::export] pub fn add_to_playlist(session: Arc<MediyoSession>, playlist_id: String, video_id: String) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap(); mediyo_core::api::library::add_to_playlist(&g, &playlist_id, &video_id)?; Ok(())
}
#[uniffi::export] pub fn add_many_to_playlist(session: Arc<MediyoSession>, playlist_id: String, video_ids: Vec<String>) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap();
    let refs: Vec<&str> = video_ids.iter().map(|s| s.as_str()).collect();
    mediyo_core::api::library::add_many_to_playlist(&g, &playlist_id, &refs)?; Ok(())
}
#[uniffi::export] pub fn remove_from_playlist(session: Arc<MediyoSession>, playlist_id: String, set_video_id: String, video_id: String) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap(); mediyo_core::api::library::remove_from_playlist(&g, &playlist_id, &set_video_id, &video_id)?; Ok(())
}
#[uniffi::export] pub fn create_playlist(session: Arc<MediyoSession>, title: String, description: String) -> Result<String, MediyoError> {
    let g = session.inner.lock().unwrap(); Ok(mediyo_core::api::library::create_playlist(&g, &title, &description, "PRIVATE")?)
}
#[uniffi::export] pub fn delete_playlist(session: Arc<MediyoSession>, playlist_id: String) -> Result<(), MediyoError> {
    let g = session.inner.lock().unwrap(); g.request("playlist/delete", serde_json::json!({"playlistId": playlist_id}))?; Ok(())
}
