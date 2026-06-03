const CONFIG_JSON: &str = include_str!("../../../config.json");

fn extract_json_str<'a>(json: &'a str, key: &str) -> Option<&'a str> {
    let needle = format!("\"{}\"", key);
    let after_key = json.split(&needle).nth(1)?;
    let after_colon = after_key.splitn(2, ':').nth(1)?.trim();
    let after_quote = after_colon.strip_prefix('"')?;
    let value = after_quote.split('"').next()?;
    Some(value)
}

pub fn base_dir() -> &'static str {
    extract_json_str(CONFIG_JSON, "baseDir").unwrap_or(".bazel-jdt")
}

pub fn config_file() -> &'static str {
    extract_json_str(CONFIG_JSON, "configFile").unwrap_or(".bazelproject")
}

pub fn aspects_dir() -> String {
    format!("{}/{}", base_dir(), extract_json_str(CONFIG_JSON, "aspectsDir").unwrap_or("aspects"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_config_reads_from_root_config_json() {
        assert_eq!(base_dir(), ".bazel-jdt");
        assert_eq!(config_file(), ".bazelproject");
        assert_eq!(aspects_dir(), ".bazel-jdt/aspects");
    }
}
