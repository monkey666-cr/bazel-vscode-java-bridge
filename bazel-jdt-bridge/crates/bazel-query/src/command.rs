use std::path::{Path, PathBuf};
use tokio::process::Command;

use bazel_aspect::TargetIdeInfo;

/// Returns the platform-specific Bazel binary name.
/// On Windows, returns "bazel.exe"; on Unix, returns "bazel".
fn bazel_binary_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "bazel.exe"
    } else {
        "bazel"
    }
}

/// Normalizes path separators to forward slashes for cross-platform compatibility.
#[cfg(target_os = "windows")]
fn normalize_path_separators(path: &str) -> String {
    path.replace('\\', "/")
}

#[cfg(not(target_os = "windows"))]
fn normalize_path_separators(path: &str) -> String {
    path.to_string()
}

/// Error type for Bazel command execution
#[derive(Debug, thiserror::Error)]
pub enum BazelError {
    #[error("Bazel command failed: {message}")]
    CommandFailed { message: String },

    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),

    #[error("UTF-8 error: {0}")]
    Utf8Error(#[from] std::string::FromUtf8Error),
}

/// Async Bazel command invoker
pub struct BazelInvoker {
    bazel_path: String,
    workspace_root: PathBuf,
    aspect_label: String,
}

impl BazelInvoker {
    pub fn new(bazel_path: &str, workspace_root: &Path, aspect_label: &str) -> Self {
        Self {
            bazel_path: bazel_path.to_string(),
            workspace_root: workspace_root.to_path_buf(),
            aspect_label: aspect_label.to_string(),
        }
    }

    pub fn with_default_bazel(workspace_root: &Path, aspect_label: &str) -> Self {
        Self::new(bazel_binary_name(), workspace_root, aspect_label)
    }

    /// Discover all Java targets in the workspace
    pub async fn discover_java_targets(&self) -> Result<Vec<String>, BazelError> {
        let output = Command::new(&self.bazel_path)
            .current_dir(&self.workspace_root)
            .args([
                "query",
                "--output=label",
                "kind(java_library, //...:*) union kind(java_binary, //...:*) union kind(java_test, //...:*) union kind(java_import, //...:*)",
            ])
            .output()
            .await?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(BazelError::CommandFailed {
                message: format!("bazel query failed: {}", stderr),
            });
        }

        let stdout = String::from_utf8(output.stdout)?;
        let targets = stdout
            .lines()
            .map(|l| l.trim().to_string())
            .filter(|l| !l.is_empty())
            .collect();

        Ok(targets)
    }

    /// Build targets with IntelliJ aspects to get dependency info.
    /// Bazel writes aspect output file paths to stderr, not stdout.
    pub async fn build_with_aspects(
        &self,
        targets: &[String],
        aspect_file: &str,
    ) -> Result<String, BazelError> {
        let targets_arg = targets.join(" ");

        let output = Command::new(&self.bazel_path)
            .current_dir(&self.workspace_root)
            .args([
                "build",
                &format!("--aspects={}", aspect_file),
                "--output_groups=intellij-info-java,intellij-info-generic",
                "--show_result=100",
                &targets_arg,
            ])
            .output()
            .await?;

        let stderr = String::from_utf8(output.stderr)?;

        if !output.status.success() {
            return Err(BazelError::CommandFailed {
                message: format!("bazel build with aspects failed: {}", stderr),
            });
        }

        Ok(stderr)
    }

    /// Get the execution root path for this workspace (e.g., for locating bazel-out artifacts)
    pub fn get_execution_root(&self) -> PathBuf {
        self.workspace_root.join("bazel-out")
    }

    /// Resolve full classpath information for targets using IntelliJ aspects.
    /// This is the "Slow Path" — invokes Bazel build with aspects.
    pub async fn resolve_full_classpath(
        &self,
        targets: &[String],
    ) -> Result<Vec<TargetIdeInfo>, BazelError> {
        if targets.is_empty() {
            return Ok(Vec::new());
        }

        let aspect_output = self.build_with_aspects(targets, &self.aspect_label).await?;

        let info_files = crate::output::parse_aspect_output_locations(&aspect_output);

        let mut results = Vec::new();
        for info_path in &info_files {
            let normalized_path = normalize_path_separators(info_path);
            let absolute_path = self.workspace_root.join(&normalized_path);
            match tokio::fs::read_to_string(&absolute_path).await {
                Ok(content) => {
                    let mut target_info =
                        bazel_aspect::text_proto::parse_text_proto_quiet(&content);
                    resolve_artifact_paths(&mut target_info, &self.workspace_root);
                    results.push(target_info);
                }
                Err(e) => {
                    log::warn!(
                        "Failed to read aspect output file {}: {}",
                        normalized_path,
                        e
                    );
                    continue;
                }
            }
        }

        Ok(results)
    }
}

fn resolve_artifact_paths(info: &mut bazel_aspect::TargetIdeInfo, workspace_root: &Path) {
    let resolve_loc = |loc: &mut bazel_aspect::ArtifactLocation| {
        if loc.absolute_path.is_none() {
            if let Some(combined) = loc.best_path() {
                let absolute = workspace_root.join(&combined);
                loc.absolute_path = Some(absolute.to_string_lossy().into_owned());
            }
        }
    };

    if let Some(ref mut java) = info.java_info {
        for jar in &mut java.jars {
            resolve_loc(&mut jar.jar);
            if let Some(ref mut src) = jar.source_jar {
                resolve_loc(src);
            }
            if let Some(ref mut iface) = jar.interface_jar {
                resolve_loc(iface);
            }
        }
        for loc in &mut java.sources {
            resolve_loc(loc);
        }
        for loc in &mut java.compile_jars {
            resolve_loc(loc);
        }
        for loc in &mut java.runtime_jars {
            resolve_loc(loc);
        }
        for loc in &mut java.source_jars {
            resolve_loc(loc);
        }
        for jar in &mut java.generated_jars {
            resolve_loc(&mut jar.jar);
            if let Some(ref mut src) = jar.source_jar {
                resolve_loc(src);
            }
        }
    }
}
