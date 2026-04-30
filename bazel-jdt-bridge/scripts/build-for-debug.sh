#!/usr/bin/env bash
# Build and deploy for local debugging.
#
# Builds Rust native lib → Java OSGi bundle → copies JAR to vscode-extension/server/
# Optionally also builds the TS extension bundle.
#
# Usage:
#   ./scripts/build-for-debug.sh           # Full build (Rust + Java + TS)
#   ./scripts/build-for-debug.sh --skip-ts # Only Rust + Java (JAR to server/)
#   ./scripts/build-for-debug.sh --skip-rust # Skip Rust rebuild (Java + TS only)
#   ./scripts/build-for-debug.sh --help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SKIP_TS=false
SKIP_RUST=false
CLEAN=false

# --- Parse args ---
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-ts)     SKIP_TS=true;   shift ;;
        --skip-rust)   SKIP_RUST=true; shift ;;
        --clean)       CLEAN=true;     shift ;;
        --help|-h)
            cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --skip-ts     Skip TypeScript build (only Rust + Java)
  --skip-rust   Skip Rust rebuild (only Java + TS)
  --clean       Clear redb cache and extracted aspect dirs before building
  --help        Show this help

Output:
  vscode-extension/server/com.bazel.jdt.jar  (OSGi bundle with embedded native lib)
EOF
            exit 0
            ;;
        *)
            echo "ERROR: Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=== Bazel JDT Bridge — Debug Build ==="
echo ""

# --- Step 0: Clean caches (optional) ---
if [[ "$CLEAN" == true ]]; then
    echo "--- [clean] Clearing caches ---"
    cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/bazel-jdt"
    rm -f "$cache_dir/bazel-jdt-cache.redb"
    echo "  Cleared $cache_dir/bazel-jdt-cache.redb"
    # Remove extracted aspect dirs from known workspace examples
    for ws in "$PROJECT_ROOT"/../examples/*/; do
        aspect_dir="$ws/.bazel-jdt/aspects"
        if [[ -d "$aspect_dir" ]]; then
            rm -rf "$aspect_dir"
            echo "  Cleared $aspect_dir"
        fi
    done
    echo ""
fi

# --- Step 1: Rust native library ---
if [[ "$SKIP_RUST" == false ]]; then
    echo "--- [1/3] Building Rust native library ---"
    cd "$PROJECT_ROOT"

    cargo build --release -p bazel-jdt-core

    # Copy to Java resources so Maven bundles it into the JAR
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$os" in
        linux)
            platform_dir="linux-$arch"
            lib_name="libbazel_jdt_core.so"
            ;;
        darwin)
            platform_dir="darwin-$arch"
            lib_name="libbazel_jdt_core.dylib"
            ;;
        msys*|mingw*|cygwin*)
            platform_dir="windows-$arch"
            lib_name="bazel_jdt_core.dll"
            ;;
        *)
            echo "ERROR: Unsupported OS: $os"
            exit 1
            ;;
    esac

    native_dir="$PROJECT_ROOT/java-bridge/src/main/resources/native/$platform_dir"
    mkdir -p "$native_dir"
    cp "$PROJECT_ROOT/target/release/$lib_name" "$native_dir/"
    echo "  Copied $lib_name -> $native_dir/"
    echo ""
fi

# --- Step 2: Java OSGi bundle ---
echo "--- [$([ "$SKIP_RUST" == false ] && echo "2/3" || echo "1/2")] Building Java OSGi bundle ---"
cd "$PROJECT_ROOT/java-bridge"
mvn clean package -DskipTests -q

# Find and copy the JAR
jar_source=(target/bazel-jdt-bridge-*.jar)
if [[ ${#jar_source[@]} -eq 0 ]]; then
    echo "ERROR: JAR not found in target/"
    exit 1
fi
jar_source="${jar_source[0]}"

server_dir="$PROJECT_ROOT/vscode-extension/server"
mkdir -p "$server_dir"
cp "$jar_source" "$server_dir/com.bazel.jdt.jar"
echo "  Copied $(basename "$jar_source") -> $server_dir/com.bazel.jdt.jar"

# Verify native libs are bundled
native_count=$(jar tf "$server_dir/com.bazel.jdt.jar" | grep -c '^native/.*\.\(so\|dylib\|dll\)$' || true)
echo "  Native libraries in JAR: $native_count"
echo ""

# --- Step 3: TypeScript extension ---
if [[ "$SKIP_TS" == false ]]; then
    step=$([ "$SKIP_RUST" == false ] && echo "3/3" || echo "2/2")
    echo "--- [$step] Building VS Code extension bundle ---"
    cd "$PROJECT_ROOT/vscode-extension"

    if [[ ! -d node_modules ]]; then
        echo "  Installing npm dependencies..."
        npm install --silent
    fi

    npm run build
    echo "  Built dist/extension.js"
    echo ""
fi

echo "=== Done! ==="
echo ""
echo "To start debugging:"
echo "  1. Open bazel-jdt-bridge/vscode-extension/ in VS Code"
echo "  2. Press F5 (Launch Extension Development Host)"
echo "  3. Open a Bazel workspace with Java targets"
echo ""
echo "Server JAR: $server_dir/com.bazel.jdt.jar"
