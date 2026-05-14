#!/usr/bin/env bash
# Create release branches for bazel-jdt-bridge
# Automates version bump, branch creation, and tag management
#
# Usage:
#   ./create-release-branch.sh pre                    # Next pre-release (auto version)
#   ./create-release-branch.sh stable                 # Stable release (graduate from pre)
#   ./create-release-branch.sh pre --bump minor       # Force minor version bump
#   ./create-release-branch.sh pre --dry-run          # Preview without executing
#   ./create-release-branch.sh pre --push             # Auto push after creation
#   ./create-release-branch.sh --help                 # Show help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# --- Globals ---
MODE=""
BUMP_LEVEL="patch"
DRY_RUN=false
FORCE=false
DO_PUSH=false
NO_FETCH=false

# Calculated values (set by calc_*_version)
NEW_VERSION=""
BRANCH_NAME=""
TAG_NAME=""
COMMIT_MSG=""

# Tag storage
declare -a STABLE_VERSIONS=()
declare -a PRE_VERSIONS=()       # format: "major.minor.patch pre_num"
declare -a STABLE_BASES=()       # "major.minor.patch" for each stable tag

# --- Functions ---

usage() {
    cat >&2 <<EOF
Usage: $0 <mode> [options]

Modes:
  pre       Pre-release: create pre-release/tag-vX.Y.Z-pre.N branch
  stable    Stable release: create release/tag-vX.Y.Z branch

Options:
  --bump <level>   Version bump level: patch (default), minor, major
  --dry-run        Print planned actions without executing
  --force          Skip interactive confirmation
  --push           Auto push branch and tag after creation
  --no-fetch       Skip git fetch --tags (use local tags only)
  --help           Show this help

Examples:
  $0 pre                    # v0.1.0-pre.1 → v0.1.0-pre.2
  $0 stable                 # v0.1.0-pre.N → v0.1.0
  $0 pre --bump minor       # Jump to v0.2.0-pre.1
  $0 pre --dry-run          # Preview next pre-release
EOF
    exit 1
}

parse_args() {
    if [[ $# -lt 1 ]]; then
        usage
    fi

    # First positional arg is mode
    case "$1" in
        pre|stable) MODE="$1"; shift ;;
        --help|-h)  usage ;;
        *)          echo "ERROR: Invalid mode '$1'. Use 'pre' or 'stable'." >&2; usage ;;
    esac

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --bump)
                if [[ $# -lt 2 ]]; then
                    echo "ERROR: --bump requires an argument (patch|minor|major)" >&2
                    exit 1
                fi
                BUMP_LEVEL="$2"
                if [[ "$BUMP_LEVEL" != "patch" && "$BUMP_LEVEL" != "minor" && "$BUMP_LEVEL" != "major" ]]; then
                    echo "ERROR: Invalid bump level '$BUMP_LEVEL'. Use patch, minor, or major." >&2
                    exit 1
                fi
                shift 2
                ;;
            --dry-run)   DRY_RUN=true;  shift ;;
            --force)     FORCE=true;    shift ;;
            --push)      DO_PUSH=true;  shift ;;
            --no-fetch)  NO_FETCH=true; shift ;;
            --help|-h)   usage ;;
            *)           echo "ERROR: Unknown option: $1" >&2; exit 1 ;;
        esac
    done
}

fetch_tags() {
    if [[ "$NO_FETCH" == "false" ]]; then
        echo "--- Fetching remote tags ---"
        git fetch --tags --all 2>/dev/null || true
    fi
}

# Parse a version string like "v0.1.0" or "v0.1.0-pre.3"
# Sets: PARSED_MAJOR, PARSED_MINOR, PARSED_PATCH, PARSED_PRE (empty if stable)
# Returns 0 if valid, 1 if not
parse_version() {
    local version="$1"
    PARSED_MAJOR=""
    PARSED_MINOR=""
    PARSED_PATCH=""
    PARSED_PRE=""

    if [[ "$version" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)(-pre\.([0-9]+))?$ ]]; then
        PARSED_MAJOR="${BASH_REMATCH[1]}"
        PARSED_MINOR="${BASH_REMATCH[2]}"
        PARSED_PATCH="${BASH_REMATCH[3]}"
        PARSED_PRE="${BASH_REMATCH[5]}"  # may be empty
        return 0
    fi
    return 1
}

# Read version from vscode-extension/package.json
get_pkg_json_version() {
    local version
    version=$(grep '"version"' "$PROJECT_ROOT/vscode-extension/package.json" | head -1 | sed 's/.*"version" *: *"\([^"]*\)".*/\1/')
    if [[ -z "$version" ]]; then
        echo "ERROR: Could not read version from vscode-extension/package.json" >&2
        exit 1
    fi
    echo "$version"
}

# Collect and classify all v* tags
collect_tags() {
    echo "--- Analyzing existing tags ---"
    STABLE_VERSIONS=()
    PRE_VERSIONS=()
    STABLE_BASES=()

    local tags
    tags=$(git tag -l "v*" 2>/dev/null || true)

    if [[ -z "$tags" ]]; then
        echo "  No existing tags found."
        return
    fi

    while IFS= read -r tag; do
        if parse_version "$tag"; then
            local base="${PARSED_MAJOR}.${PARSED_MINOR}.${PARSED_PATCH}"
            if [[ -n "$PARSED_PRE" ]]; then
                # Store as "major.minor.patch pre_num" for easy parsing
                PRE_VERSIONS+=("${PARSED_MAJOR}.${PARSED_MINOR}.${PARSED_PATCH} ${PARSED_PRE}")
                echo "  Pre-release: $tag"
            else
                STABLE_VERSIONS+=("${PARSED_MAJOR}.${PARSED_MINOR}.${PARSED_PATCH}")
                STABLE_BASES+=("${PARSED_MAJOR}.${PARSED_MINOR}.${PARSED_PATCH}")
                echo "  Stable: $tag"
            fi
        fi
    done <<< "$tags"

    echo "  Found ${#STABLE_VERSIONS[@]} stable tag(s), ${#PRE_VERSIONS[@]} pre-release tag(s)."
}

# Sort helper: compare two "M m p" triples, return highest
# Usage: highest_of "0 1 0\n0 2 0" → "0 2 0"
highest_version() {
    local versions="$1"
    echo "$versions" | sort -t' ' -k1,1n -k2,2n -k3,3n | tail -1
}

# Get highest pre-release base and pre number
get_highest_pre() {
    if [[ ${#PRE_VERSIONS[@]} -eq 0 ]]; then
        return 1
    fi
    local sorted
    sorted=$(printf '%s\n' "${PRE_VERSIONS[@]}" | sort -t' ' -k1,1V -k2,2n | tail -1)
    HIGHEST_PRE_BASE=$(echo "$sorted" | awk '{print $1}')
    HIGHEST_PRE_NUM=$(echo "$sorted" | awk '{print $2}')
    return 0
}

# Get highest stable version
get_highest_stable() {
    if [[ ${#STABLE_VERSIONS[@]} -eq 0 ]]; then
        return 1
    fi
    local sorted
    sorted=$(printf '%s\n' "${STABLE_VERSIONS[@]}" | sort -t'.' -k1,1n -k2,2n -k3,3n | tail -1)
    HIGHEST_STABLE="$sorted"
    return 0
}

# Check if a base version exists in stable tags
is_stable_released() {
    local base="$1"
    for sb in "${STABLE_BASES[@]}"; do
        if [[ "$sb" == "$base" ]]; then
            return 0
        fi
    done
    return 1
}

# Bump a version string (major.minor.patch) by given level
bump_version() {
    local base="$1"
    local level="$2"
    local major minor patch
    IFS='.' read -r major minor patch <<< "$base"
    case "$level" in
        major) echo "$((major + 1)).0.0" ;;
        minor) echo "${major}.$((minor + 1)).0" ;;
        patch) echo "${major}.${minor}.$((patch + 1))" ;;
        *)     echo "$base" ;;
    esac
}

calc_pre_version() {
    echo "--- Calculating pre-release version ---"

    local base=""
    local pre_num=1

    # If --bump is explicitly minor or major, we need a base to bump from
    if [[ "$BUMP_LEVEL" == "minor" || "$BUMP_LEVEL" == "major" ]]; then
        if get_highest_pre; then
            base=$(bump_version "$HIGHEST_PRE_BASE" "$BUMP_LEVEL")
        elif get_highest_stable; then
            base=$(bump_version "$HIGHEST_STABLE" "$BUMP_LEVEL")
        else
            local pkg_ver
            pkg_ver=$(get_pkg_json_version)
            # Strip any -pre suffix from package.json
            base="${pkg_ver%%-pre*}"
            base=$(bump_version "$base" "$BUMP_LEVEL")
        fi
        pre_num=1
    elif get_highest_pre; then
        # Have pre tags — check if base was already released as stable
        if is_stable_released "$HIGHEST_PRE_BASE"; then
            # Base already has a stable release → bump minor
            base=$(bump_version "$HIGHEST_PRE_BASE" "minor")
            pre_num=1
            echo "  Base $HIGHEST_PRE_BASE already released as stable, bumping to $base"
        else
            base="$HIGHEST_PRE_BASE"
            pre_num=$((HIGHEST_PRE_NUM + 1))
            echo "  Continuing $base pre-release series"
        fi
    else
        # No pre tags — start from package.json or stable
        if get_highest_stable; then
            base=$(bump_version "$HIGHEST_STABLE" "minor")
        else
            local pkg_ver
            pkg_ver=$(get_pkg_json_version)
            base="${pkg_ver%%-pre*}"
        fi
        pre_num=1
    fi

    NEW_VERSION="${base}-pre.${pre_num}"
    TAG_NAME="v${NEW_VERSION}"
    BRANCH_NAME="pre-release/tag-${TAG_NAME}"
    COMMIT_MSG="chore: bump version to ${NEW_VERSION}"
}

calc_stable_version() {
    echo "--- Calculating stable version ---"

    local base=""

    if [[ "$BUMP_LEVEL" == "minor" || "$BUMP_LEVEL" == "major" ]]; then
        if get_highest_pre; then
            base=$(bump_version "$HIGHEST_PRE_BASE" "$BUMP_LEVEL")
        elif get_highest_stable; then
            base=$(bump_version "$HIGHEST_STABLE" "$BUMP_LEVEL")
        else
            local pkg_ver
            pkg_ver=$(get_pkg_json_version)
            base="${pkg_ver%%-pre*}"
            base=$(bump_version "$base" "$BUMP_LEVEL")
        fi
    elif get_highest_pre; then
        # Graduate from pre-release: strip -pre.N suffix
        base="$HIGHEST_PRE_BASE"
        echo "  Graduating pre-release $base series to stable"
    elif get_highest_stable; then
        # No pre tags, bump from latest stable
        base=$(bump_version "$HIGHEST_STABLE" "minor")
        echo "  No pre-release tags, bumping from stable $HIGHEST_STABLE"
    else
        # No tags at all
        local pkg_ver
        pkg_ver=$(get_pkg_json_version)
        base="${pkg_ver%%-pre*}"
        echo "  No tags found, using package.json version"
    fi

    NEW_VERSION="$base"
    TAG_NAME="v${NEW_VERSION}"
    BRANCH_NAME="release/tag-${TAG_NAME}"
    COMMIT_MSG="chore: release ${TAG_NAME}"
}

check_conflicts() {
    echo "--- Checking for conflicts ---"

    # Check tag exists locally
    if git tag -l "$TAG_NAME" | grep -q .; then
        echo "ERROR: Tag $TAG_NAME already exists!" >&2
        exit 1
    fi

    # Check tag exists remotely
    if git ls-remote --tags origin "refs/tags/$TAG_NAME" 2>/dev/null | grep -q .; then
        echo "ERROR: Tag $TAG_NAME already exists on remote!" >&2
        exit 1
    fi

    # Check branch exists locally
    if git branch --list "$BRANCH_NAME" | grep -q .; then
        echo "ERROR: Branch $BRANCH_NAME already exists!" >&2
        exit 1
    fi

    # Check branch exists remotely
    if git branch --list -r "origin/$BRANCH_NAME" | grep -q .; then
        echo "ERROR: Branch $BRANCH_NAME already exists on remote!" >&2
        exit 1
    fi

    echo "  No conflicts detected."
}

check_working_tree() {
    echo "--- Checking working tree ---"
    if ! git diff --quiet HEAD 2>/dev/null; then
        echo "ERROR: Working tree has uncommitted changes. Please commit or stash first." >&2
        git status --short >&2
        exit 1
    fi
    echo "  Working tree is clean."
}

show_plan() {
    echo ""
    echo "========================================="
    echo "  Release Plan"
    echo "========================================="
    echo "  Mode:       $MODE"
    echo "  Version:    $NEW_VERSION"
    echo "  Branch:     $BRANCH_NAME"
    echo "  Tag:        $TAG_NAME"
    echo "  Commit msg: $COMMIT_MSG"
    echo "========================================="
    echo ""
}

confirm() {
    if [[ "$FORCE" == "true" ]]; then
        return
    fi
    if [[ ! -t 0 ]]; then
        echo "  (Non-interactive terminal, auto-confirming)"
        return
    fi
    read -r -p "Proceed? [Y/n] " response
    case "$response" in
        ""|Y|y) ;;
        *)     echo "Aborted."; exit 0 ;;
    esac
}

# Print a command that would be executed
dry_run_cmd() {
    echo "  + $*"
}

dry_run() {
    echo "--- Dry run: commands that would be executed ---"
    dry_run_cmd git checkout main
    dry_run_cmd git pull origin main
    dry_run_cmd git checkout -b "$BRANCH_NAME"
    dry_run_cmd "cd vscode-extension && npm version $NEW_VERSION --no-git-tag-version && cd .."
    dry_run_cmd git add vscode-extension/package.json
    dry_run_cmd git commit -m "\"$COMMIT_MSG\""
    dry_run_cmd git tag "$TAG_NAME"
    if [[ "$DO_PUSH" == "true" ]]; then
        dry_run_cmd git push origin "$BRANCH_NAME"
        dry_run_cmd git push origin "$TAG_NAME"
    fi
    echo ""
}

execute() {
    echo "--- Creating release branch ---"

    echo "  Checking out main..."
    git checkout main
    git pull origin main

    echo "  Creating branch $BRANCH_NAME..."
    git checkout -b "$BRANCH_NAME"

    echo "  Bumping version to $NEW_VERSION..."
    cd "$PROJECT_ROOT/vscode-extension"
    if ! command -v npm &>/dev/null; then
        echo "ERROR: npm not found. Please install Node.js." >&2
        exit 1
    fi
    npm version "$NEW_VERSION" --no-git-tag-version
    cd "$PROJECT_ROOT"

    echo "  Committing version change..."
    git add vscode-extension/package.json
    git commit -m "$COMMIT_MSG"

    echo "  Creating tag $TAG_NAME..."
    git tag "$TAG_NAME"

    if [[ "$DO_PUSH" == "true" ]]; then
        echo "  Pushing to origin..."
        git push origin "$BRANCH_NAME"
        git push origin "$TAG_NAME"
    fi
}

show_summary() {
    echo ""
    echo "========================================="
    echo "  Done!"
    echo "========================================="
    echo "  Branch: $BRANCH_NAME"
    echo "  Tag:    $TAG_NAME"
    echo ""
    if [[ "$DO_PUSH" == "true" ]]; then
        echo "  Branch and tag pushed to origin."
        echo "  CI release pipeline should be running."
        echo "  Check: https://github.com/$(git remote get-url origin | sed 's/.*github.com[:/]\(.*\)\.git/\1/' | sed 's/.*github.com[:/]\(.*\)/\1/')/actions"
    else
        echo "  To push:"
        echo "    git push origin $BRANCH_NAME"
        echo "    git push origin $TAG_NAME"
    fi
    echo "========================================="
}

# --- Main ---

parse_args "$@"
fetch_tags
collect_tags

if [[ "$MODE" == "pre" ]]; then
    calc_pre_version
else
    calc_stable_version
fi

check_conflicts
check_working_tree
show_plan

if [[ "$DRY_RUN" == "true" ]]; then
    dry_run
    echo "Dry run complete. No changes were made."
    exit 0
fi

confirm
execute
show_summary
