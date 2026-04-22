/// Parse bazel query label output (one label per line)
pub fn parse_label_output(output: &str) -> Vec<String> {
    output
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty() && l.starts_with("//"))
        .collect()
}

/// Parse aspect output file locations
/// Format: bazel-out/<config>/bin/<package>/<target>-<hash>.intellij-info.txt
pub fn parse_aspect_output_locations(output: &str) -> Vec<String> {
    output
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| l.ends_with(".intellij-info.txt"))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_label_output() {
        let output = "//java/com/example:lib\n//java/com/example:lib_test\n";
        let labels = parse_label_output(output);
        assert_eq!(
            labels,
            vec!["//java/com/example:lib", "//java/com/example:lib_test"]
        );
    }

    #[test]
    fn test_parse_label_output_empty() {
        let labels = parse_label_output("");
        assert!(labels.is_empty());
    }

    #[test]
    fn test_parse_aspect_output_locations() {
        let output = "bazel-out/k8-fastbuild/bin/java/com/example/lib-abc123.intellij-info.txt\n";
        let locations = parse_aspect_output_locations(output);
        assert_eq!(locations.len(), 1);
        assert!(locations[0].ends_with(".intellij-info.txt"));
    }
}
