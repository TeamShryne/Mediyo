use serde_json::json;

/// Innertube `client` descriptor. `WEB_REMIX` is the YouTube Music web client.
///
/// Captured from live traffic (see `research/REQUESTS.md`):
/// - clientName `WEB_REMIX`, header `X-Youtube-Client-Name: 67`
/// - clientVersion `1.20260811.15.00`
pub const DEFAULT_CLIENT_NAME: &str = "WEB_REMIX";
pub const DEFAULT_CLIENT_VERSION: &str = "1.20260818.08.00";
pub const DEFAULT_HL: &str = "en";
pub const DEFAULT_GL: &str = "US";
pub const CLIENT_NAME_HEADER: &str = "67";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Client {
    pub name: String,
    pub version: String,
    pub hl: String,
    pub gl: String,
    pub visitor_data: Option<String>,
    pub page_id: Option<String>,
}

impl Default for Client {
    fn default() -> Self {
        Self {
            name: DEFAULT_CLIENT_NAME.into(),
            version: DEFAULT_CLIENT_VERSION.into(),
            hl: DEFAULT_HL.into(),
            gl: DEFAULT_GL.into(),
            visitor_data: None,
            page_id: None,
        }
    }
}

impl Client {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_version(mut self, version: impl Into<String>) -> Self {
        self.version = version.into();
        self
    }

    pub fn with_hl(mut self, hl: impl Into<String>) -> Self {
        self.hl = hl.into();
        self
    }

    pub fn with_gl(mut self, gl: impl Into<String>) -> Self {
        self.gl = gl.into();
        self
    }

    pub fn with_visitor_data(mut self, visitor_data: impl Into<String>) -> Self {
        self.visitor_data = Some(visitor_data.into());
        self
    }

    pub fn with_page_id(mut self, page_id: impl Into<String>) -> Self {
        self.page_id = Some(page_id.into());
        self
    }
}

/// The innertube `context` object sent with every request.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Context {
    pub client: Client,
}

impl Context {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_client(mut self, client: Client) -> Self {
        self.client = client;
        self
    }

    /// Serialize the minimal innertube context. Matches the shape the live web
    /// client sends, minus the per-device noise (`configInfo`, `adSignalsInfo`,
    /// screen metrics, ...). Verified against `research/search_request_body.json`.
    pub fn to_json(&self) -> serde_json::Value {
        let mut client = json!({
            "clientName": self.client.name,
            "clientVersion": self.client.version,
            "hl": self.client.hl,
            "gl": self.client.gl,
        });
        if let Some(visitor_data) = &self.client.visitor_data {
            client["visitorData"] = serde_json::Value::String(visitor_data.clone());
        }
        json!({
            "context": {
                "client": client,
            }
        })
    }
}
