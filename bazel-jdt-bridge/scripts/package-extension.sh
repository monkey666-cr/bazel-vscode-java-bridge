#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build"

validate_native_libs_in_jar() {
    local jar_path="$1"
    local native_count
    native_count=$(jar tf "$jar_path" | grep -c '^native/.*\.\(so\|dylib\|dll\)$' || true)
    if [[ "$native_count" -lt 1 ]]; then
        echo "WARNING: No native libraries found in JAR."
        return 1
    fi
    echo "Validated $native_count native libraries in JAR."
    return 0
}

build_native_current_platform() {
    if ! command -v cargo &>/dev/null; then
        echo "ERROR: cargo not found. Cannot build native libraries."
        return 1
    fi
    echo "--- Building native library for current platform ---"
    cd "$PROJECT_ROOT"
    cargo build --release -p bazel-jdt-core

    local os arch lib_name platform_dir
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$os" in
        linux)  platform_dir="linux-$arch";  lib_name="libbazel_jdt_core.so" ;;
        darwin) platform_dir="darwin-$arch";  lib_name="libbazel_jdt_core.dylib" ;;
        *)      echo "ERROR: Unsupported OS: $os"; return 1 ;;
    esac

    local native_dir="$PROJECT_ROOT/java-bridge/src/main/resources/native/$platform_dir"
    mkdir -p "$native_dir"
    cp "$PROJECT_ROOT/target/release/$lib_name" "$native_dir/"
    echo "Copied $lib_name to $native_dir/"
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo "--- Building Java bridge JAR ---"
cd "$PROJECT_ROOT/java-bridge"
mvn clean package -DskipTests
cp target/bazel-jdt-bridge-*.jar "$BUILD_DIR/com.bazel.jdt.jar"

if ! validate_native_libs_in_jar "$BUILD_DIR/com.bazel.jdt.jar"; then
    echo "Attempting to build native libraries for current platform..."
    if build_native_current_platform; then
        echo "--- Rebuilding JAR with native libraries ---"
        mvn clean package -DskipTests
        cp target/bazel-jdt-bridge-*.jar "$BUILD_DIR/com.bazel.jdt.jar"
        if ! validate_native_libs_in_jar "$BUILD_DIR/com.bazel.jdt.jar"; then
            echo "ERROR: Native libraries still missing after rebuild."
            exit 1
        fi
    else
        echo "ERROR: Could not build native libraries. Package may not work."
        exit 1
    fi
fi

echo "--- Building VSCode extension ---"
cd "$PROJECT_ROOT/vscode-extension"
npm install
npm run build

mkdir -p "$BUILD_DIR/vscode-extension/server"
cp "$BUILD_DIR/com.bazel.jdt.jar" "$BUILD_DIR/vscode-extension/server/"
cp package.json "$BUILD_DIR/vscode-extension/"
cp -r dist "$BUILD_DIR/vscode-extension/"

cd "$BUILD_DIR/vscode-extension"
npx @vscode/vsce package --no-dependencies
mv *.vsix "$BUILD_DIR/"

echo "Done: $BUILD_DIR/"
ls -la "$BUILD_DIR/"
