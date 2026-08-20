use serde_json::{json, Value};

use crate::context::{Context, CLIENT_NAME_HEADER};
use crate::error::{Error, Result};

const BASE_URL: &str = "https://music.youtube.com/youtubei/v1";
const USER_AGENT: &str = "Mozilla/5.0 (X11; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0";

/// Cookie-based authentication (platforms supply the cookie header value).
#[derive(Debug, Clone)]
pub struct Auth {
    /// Raw `Cookie` header value, e.g. `SAPISID=...; SID=...; ...`.
    pub cookie_header: String,
    /// SAPISID cookie value, used to derive the SAPISIDHASH Authorization header.
    pub sapisid: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Session {
    context: Context,
    agent: ureq::Agent,
    auth: Option<Auth>,
}

impl Default for Session {
    fn default() -> Self {
        Self::new()
    }
}

impl Session {
    /// Anonymous session (no login). Requires visitor data before most
    /// endpoints work; see [`Session::fetch_visitor_data`].
    pub fn new() -> Self {
        Self {
            context: Context::new(),
            agent: ureq::Agent::config_builder()
                .http_status_as_error(false)
                .build()
                .new_agent(),
            auth: None,
        }
    }

    pub fn with_context(mut self, context: Context) -> Self {
        self.context = context;
        self
    }

    /// Authenticated session built from a raw cookie header.
    /// Pass the `SAPISID` cookie value separately so the client can compute
    /// the SAPISIDHASH `Authorization` header.
    pub fn with_cookies(
        mut self,
        cookie_header: impl Into<String>,
        sapisid: Option<String>,
    ) -> Self {
        self.auth = Some(Auth {
            cookie_header: cookie_header.into(),
            sapisid,
        });
        self
    }

    pub fn context(&self) -> &Context {
        &self.context
    }

    /// Plain HTTP GET returning the response body as text
    /// (used for pages and player JS assets).
    pub fn get(&self, url: &str) -> Result<String> {
        let resp = self
            .agent
            .get(url)
            .header("User-Agent", USER_AGENT)
            .call()?;
        Ok(resp.into_body().read_to_string()?)
    }

    /// POST an innertube request. `body` is merged with the session context.
    /// The response body is decoded as JSON.
    pub fn request(&self, endpoint: &str, body: Value) -> Result<Value> {
        let mut body = body;
        let context = self.context.to_json();
        body["context"] = context["context"].clone();

        let url = format!("{BASE_URL}/{endpoint}?prettyPrint=false");
        let mut req = self
            .agent
            .post(&url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Youtube-Client-Name", CLIENT_NAME_HEADER)
            .header("X-Youtube-Client-Version", &self.context.client.version);

        if let Some(vd) = &self.context.client.visitor_data {
            req = req.header("X-Goog-Visitor-Id", vd);
        }
        if let Some(pid) = &self.context.client.page_id {
            req = req.header("X-Goog-PageId", pid);
        }
        if let Some(auth) = &self.auth {
            req = req.header("Cookie", &auth.cookie_header);
            req = req.header("X-Youtube-Bootstrap-Logged-In", "true");
            req = req.header("X-Goog-AuthUser", "2");
            if let Some(sapisid) = &auth.sapisid {
                let hash = crate::auth::sapisid_hash(sapisid)?;
                req = req.header("Authorization", &hash);
            }
        }

        let resp = req.send_json(body)?;
        let status = resp.status().as_u16();
        let text = resp.into_body().read_to_string()?;

        if status >= 400 {
            let message = extract_api_message(&text).unwrap_or_else(|| format!("HTTP {status}"));
            return Err(Error::Api(message));
        }

        Ok(serde_json::from_str(&text)?)
    }

    /// Fetch a fresh `visitorData` from YouTube. The value is returned and
    /// stored in the session context so subsequent requests carry it.
    ///
    /// The endpoint is a POST returning the raw visitor data string.
    pub fn fetch_visitor_data(&mut self) -> Result<String> {
        let url = format!("{BASE_URL}/visitor_id");
        let body = json!({
            "context": {
                "client": {
                    "clientName": "WEB",
                    "clientVersion": "2.20240101.00.00",
                }
            }
        });
        let resp = self
            .agent
            .post(&url)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .send_json(body)?;
        let text = resp.into_body().read_to_string()?;
        // Current format: innertube JSON with `responseContext.visitorData`.
        // Older format: the raw base64 string as the whole body.
        let visitor_data = match serde_json::from_str::<Value>(&text) {
            Ok(v) => v
                .pointer("/responseContext/visitorData")
                .and_then(Value::as_str)
                .map(str::to_string)
                .unwrap_or_else(|| text.trim().to_string()),
            Err(_) => text.trim().to_string(),
        };
        self.context.client.visitor_data = Some(visitor_data.clone());
        Ok(visitor_data)
    }
}

fn extract_api_message(body: &str) -> Option<String> {
    let v: Value = serde_json::from_str(body).ok()?;
    v.pointer("/error/message")
        .and_then(Value::as_str)
        .map(String::from)
}
