use thiserror::Error;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, Error)]
pub enum Error {
    #[error("transport error: {0}")]
    Transport(#[from] ureq::Error),

    #[error("io error: {0}")]
    Io(#[from] std::io::Error),

    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),

    #[error("missing field `{0}`")]
    MissingField(&'static str),

    #[error("unexpected renderer `{0}` (wanted `{1}`)")]
    UnexpectedRenderer(String, &'static str),

    #[error("missing `{0}`")]
    Missing(&'static str),

    #[error("youtube api error: {0}")]
    Api(String),
}
