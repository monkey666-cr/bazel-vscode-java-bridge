//! Data model types for Bazel BUILD file parsing.
//!
//! These structs represent the parsed contents of Bazel BUILD files.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

/// Type of Java rule in a BUILD file
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum RuleType {
    JavaLibrary,
    JavaBinary,
    JavaTest,
    JavaImport,
    Other(String),
}

impl RuleType {
    pub fn from_rule_name(name: &str) -> Self {
        match name {
            "java_library" => RuleType::JavaLibrary,
            "java_binary" => RuleType::JavaBinary,
            "java_test" => RuleType::JavaTest,
            "java_import" => RuleType::JavaImport,
            other => RuleType::Other(other.to_string()),
        }
    }

    pub fn is_java_rule(name: &str) -> bool {
        matches!(
            name,
            "java_library" | "java_binary" | "java_test" | "java_import"
        )
    }
}

/// Represents a Java rule extracted from a BUILD file
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaRule {
    pub rule_type: RuleType,
    pub name: String,
    pub srcs: Vec<String>,
    pub deps: Vec<String>,
    pub runtime_deps: Vec<String>,
    pub resources: Vec<String>,
    pub plugins: Vec<String>,
    pub exports: Vec<String>,
    pub test_only: bool,
    pub visibility: Vec<String>,
}

/// A load statement in a BUILD file
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoadStatement {
    pub path: String,
    pub symbols: Vec<String>,
}

/// Represents a parsed BUILD file with content hash and extracted rules
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ParsedBuildFile {
    pub path: PathBuf,
    pub content_hash: String,
    pub rules: Vec<JavaRule>,
    pub loads: Vec<LoadStatement>,
}

/// Indicates whether a parsed value contains glob/variable references
/// (which means the Fast Path is insufficient)
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FastPathStatus {
    /// Fast Path can proceed — all values are concrete
    Sufficient,
    /// Fast Path cannot proceed — contains glob() or variable references
    Insufficient { reason: String },
}
