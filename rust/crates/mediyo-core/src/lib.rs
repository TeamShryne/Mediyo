//! mediyo-core — metadata-only YouTube Music core library.
//!
//! See the [guide](https://teamshryne.github.io/mediyo-core/) for auth, search/browse, library/mutations.

pub mod api;
pub mod auth;
pub mod context;
pub mod error;
pub mod model;
pub mod parser;
pub mod session;

pub use context::{Client, Context};
pub use error::{Error, Result};
pub use session::Session;
