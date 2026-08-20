use crate::error::{Error, Result};
use sha1::{Digest, Sha1};

/// Default origin used when deriving the SAPISIDHASH (site the cookies were
/// minted for). The web client signs per-request; platforms that want stricter
/// fidelity can pass their own origin.
pub const DEFAULT_ORIGIN: &str = "https://music.youtube.com";

/// Compute the classic `Authorization: SAPISIDHASH <ts>_<hash>` header value
/// from the SAPISID cookie.
///
/// Algorithm (reverse-engineered from YouTube web):
///   hash = SHA1("<ts> <SAPISID> <origin>")
///   header = "SAPISIDHASH <ts>_<hash>"
///
/// TODO(verify): the 2026 web client sends a multi-cookie format
/// (`SAPISIDHASH ... SAPISID1PHASH ... SAPISID3PHASH ...`). Confirm whether
/// the classic single hash is still accepted before relying on it.
pub fn sapisid_hash(sapisid: &str) -> Result<String> {
    sapisid_hash_with_origin(sapisid, DEFAULT_ORIGIN)
}

pub fn sapisid_hash_with_origin(sapisid: &str, origin: &str) -> Result<String> {
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| Error::Missing("system clock before unix epoch"))?
        .as_secs();

    let msg = format!("{ts} {sapisid} {origin}");
    let mut hasher = Sha1::new();
    hasher.update(msg.as_bytes());
    let digest = hasher.finalize();
    let hex: String = digest.iter().map(|b| format!("{b:02x}")).collect();

    Ok(format!("SAPISIDHASH {ts}_{hex}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sapisid_hash_format() {
        let h = sapisid_hash_with_origin("dummy_sapisid", "https://www.youtube.com").unwrap();
        assert!(h.starts_with("SAPISIDHASH "));
        let rest = h.trim_start_matches("SAPISIDHASH ").to_string();
        let (ts, hash) = rest.split_once('_').unwrap();
        assert!(ts.parse::<u64>().is_ok());
        assert_eq!(hash.len(), 40, "sha1 hex is 40 chars");
    }
}
