#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_CLOUD_PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

CLOUD_PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$DEFAULT_CLOUD_PROJECT_DIR}"
MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-}"
SKIP_BUILD=false

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

usage() {
    cat <<'USAGE'
Usage: verify-platform-frontend-split-local.sh [options]

Options:
  --cloud DIR       AliciaCloudStorage repository path.
  --main-site DIR   mainSite repository path.
  --skip-build      Skip npm builds and run static/API contract checks only.
  -h, --help        Show this help.

Environment:
  ALICIA_CLOUD_PROJECT_DIR
  ALICIA_MAIN_SITE_PROJECT_DIR
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --cloud)
            shift
            [[ $# -gt 0 ]] || fail "--cloud requires a directory"
            CLOUD_PROJECT_DIR="$1"
            ;;
        --main-site)
            shift
            [[ $# -gt 0 ]] || fail "--main-site requires a directory"
            MAIN_SITE_PROJECT_DIR="$1"
            ;;
        --skip-build)
            SKIP_BUILD=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
    shift
done

resolve_dir() {
    local label="$1"
    local target="$2"

    [[ -n "$target" ]] || fail "$label path is empty"
    [[ -d "$target" ]] || fail "$label path is not a directory: $target"
    (cd "$target" && pwd)
}

require_command() {
    local command_name="$1"
    local message="$2"

    command -v "$command_name" >/dev/null 2>&1 || fail "$message"
}

require_file() {
    local label="$1"
    local path="$2"

    [[ -f "$path" ]] || fail "Missing $label: $path"
}

run_step() {
    local name="$1"
    shift

    printf '[RUN] %s\n' "$name"
    "$@"
    ok "$name"
}

run_npm_script() {
    local package_dir="$1"
    local script_name="$2"

    [[ -d "$package_dir" ]] || fail "Missing npm package directory: $package_dir"
    (cd "$package_dir" && npm run "$script_name")
}

run_bash_script() {
    local project_dir="$1"
    local relative_script="$2"
    local script_path="$project_dir/$relative_script"

    require_file "$relative_script" "$script_path"
    (cd "$project_dir" && bash "$relative_script")
}

require_contains() {
    local label="$1"
    local project_dir="$2"
    local relative_path="$3"
    local needle="$4"
    local message="$5"
    local path="$project_dir/$relative_path"

    require_file "platform contract file" "$path"
    grep -Fq -- "$needle" "$path" || fail "$label: $message"
}

verify_profile_source() {
    local label="$1"
    local project_dir="$2"
    local relative_path="$3"
    local title_needle="$4"
    shift 4
    local shared_source_needles=(
        "account-profile-modal"
        "account-profile-form"
        "profile-avatar-row account-profile-hero"
        "account-profile-copy"
        "account-profile-actions"
        "account-profile-fields"
    )
    local needle

    require_contains "$label" "$project_dir" "$relative_path" "$title_needle" "must use the shared Account profile title chrome"
    for needle in "${shared_source_needles[@]}"; do
        require_contains "$label" "$project_dir" "$relative_path" "$needle" "must keep the shared profile source contract"
    done
    for needle in "$@"; do
        require_contains "$label" "$project_dir" "$relative_path" "$needle" "must expose the shared profile fields in the profile editor"
    done
}

verify_profile_style() {
    local label="$1"
    local project_dir="$2"
    local relative_path="$3"
    local action_needle="$4"
    local shared_style_needles=(
        ".account-profile-form"
        ".account-profile-hero"
        "grid-template-columns: 64px minmax(0, 1fr);"
        "gap: 14px;"
        "margin-bottom: 18px;"
        "padding: 12px;"
        "border-radius: 8px;"
        "background: #f8fbff;"
        ".account-profile-copy"
        ".account-profile-actions"
        ".account-profile-fields"
    )
    local needle

    for needle in "${shared_style_needles[@]}"; do
        require_contains "$label" "$project_dir" "$relative_path" "$needle" "must keep the shared profile style contract"
    done
    require_contains "$label" "$project_dir" "$relative_path" "$action_needle" "must style the upload action consistently for its UI framework"
}

verify_shared_account_profile() {
    verify_profile_source \
        "main site profile dialog" \
        "$MAIN_SITE_PROJECT_DIR" \
        "webApp/src/App.tsx" \
        '<DialogHeader kicker="Account" title=' \
        "form.nickname" \
        "form.phoneNumber" \
        "form.avatarUrl"

    verify_profile_source \
        "identity console profile modal" \
        "$MAIN_SITE_PROJECT_DIR" \
        "userSite/src/pages/IdentityConsolePage.tsx" \
        'title={<AliciaModalTitle eyebrow="Account">' \
        'name="nickname"' \
        'name="phoneNumber"' \
        'name="avatarUrl"'

    verify_profile_source \
        "cloud web profile modal" \
        "$CLOUD_PROJECT_DIR" \
        "webApp/src/features/drive/DriveProfileModals.tsx" \
        'title={<AliciaModalTitle eyebrow="Account">' \
        'name="nickname"' \
        'name="phoneNumber"' \
        'name="avatarUrl"'

    verify_profile_source \
        "cloud console profile modal" \
        "$CLOUD_PROJECT_DIR" \
        "sysManage/src/pages/CloudConsolePage.tsx" \
        'title={<AliciaModalTitle eyebrow="Account">' \
        'name="nickname"' \
        'name="phoneNumber"' \
        'name="avatarUrl"'

    verify_profile_style \
        "main site profile styles" \
        "$MAIN_SITE_PROJECT_DIR" \
        "webApp/src/styles.css" \
        ".avatar-upload-action"

    verify_profile_style \
        "identity console profile styles" \
        "$MAIN_SITE_PROJECT_DIR" \
        "userSite/src/index.css" \
        ".account-profile-actions .ant-btn"

    verify_profile_style \
        "cloud web profile styles" \
        "$CLOUD_PROJECT_DIR" \
        "webApp/src/index.css" \
        ".account-profile-actions .ant-btn"

    verify_profile_style \
        "cloud console profile styles" \
        "$CLOUD_PROJECT_DIR" \
        "sysManage/src/index.css" \
        ".account-profile-actions .ant-btn"
}

verify_identity_console_api_contract() {
    local script_path="$CLOUD_PROJECT_DIR/deploy/scripts/verify-identity-console-api-contracts.mjs"

    require_command node "Node.js is required for identity console API contract verification."
    require_file "identity console API contract verifier" "$script_path"
    node "$script_path" --main-site "$MAIN_SITE_PROJECT_DIR" --cloud "$CLOUD_PROJECT_DIR"
}

CLOUD_PROJECT_DIR="$(resolve_dir "Cloud project" "$CLOUD_PROJECT_DIR")"

if [[ -z "$MAIN_SITE_PROJECT_DIR" ]]; then
    MAIN_SITE_PROJECT_DIR="$(cd "$CLOUD_PROJECT_DIR/.." && pwd)/mainSite"
fi

MAIN_SITE_PROJECT_DIR="$(resolve_dir "Main site project" "$MAIN_SITE_PROJECT_DIR")"

printf '[RUN] Alicia platform frontend split local verification\n'
printf 'Main site project: %s\n' "$MAIN_SITE_PROJECT_DIR"
printf 'Cloud project: %s\n' "$CLOUD_PROJECT_DIR"

if [[ "$SKIP_BUILD" != "true" ]]; then
    require_command npm "npm is required unless --skip-build is used."
    run_step "build main site webApp" run_npm_script "$MAIN_SITE_PROJECT_DIR/webApp" build
    run_step "build identity userSite" run_npm_script "$MAIN_SITE_PROJECT_DIR/userSite" build
    run_step "build cloud webApp" run_npm_script "$CLOUD_PROJECT_DIR/webApp" build
    run_step "build cloud sysManage" run_npm_script "$CLOUD_PROJECT_DIR/sysManage" build
else
    printf '[SKIP] frontend builds\n'
fi

run_step "main site frontend boundary check" run_bash_script "$MAIN_SITE_PROJECT_DIR" "deploy/scripts/check-main-site-frontend-boundaries.sh"
run_step "cloud frontend boundary check" run_bash_script "$CLOUD_PROJECT_DIR" "deploy/scripts/check-frontend-console-boundaries.sh"
run_step "cloud identity route boundary check" run_bash_script "$CLOUD_PROJECT_DIR" "deploy/scripts/check-identity-route-boundary.sh"
run_step "shared account profile contract" verify_shared_account_profile
run_step "identity console IdentityApi contract" verify_identity_console_api_contract

ok "Alicia platform frontend split local verification complete"
