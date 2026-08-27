#!/usr/bin/env bash
set -Eeuo pipefail

DEFAULT_PROJECT_DIR="$HOME/aliciaCloudStorage"
if [[ -f compose.yaml ]]; then
    DEFAULT_PROJECT_DIR="$PWD"
fi

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$DEFAULT_PROJECT_DIR}"
if [[ $# -gt 0 && -d "$1" && -f "$1/compose.yaml" ]]; then
    PROJECT_DIR="$1"
    shift
fi

CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
PACKAGE_DIR="${ALICIA_ANDROID_GIT_PACKAGE_DIR:-deploy/android-app-package}"
APK_PATH="${ALICIA_ANDROID_GIT_APK_PATH:-$PACKAGE_DIR/current.apk}"
VERSION_FILE="${ALICIA_ANDROID_GIT_VERSION_FILE:-$PACKAGE_DIR/version-name.txt}"
RELEASE_NOTES_FILE="${ALICIA_ANDROID_GIT_RELEASE_NOTES_FILE:-$PACKAGE_DIR/release-notes.txt}"
SHA256_FILE="${ALICIA_ANDROID_GIT_SHA256_FILE:-$APK_PATH.sha256}"
CURL_TIMEOUT="${ALICIA_ANDROID_APP_PACKAGE_CURL_TIMEOUT_SECONDS:-60}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
FORCE_PUBLISH="${ALICIA_ANDROID_APP_PACKAGE_FORCE:-false}"
SKIP_DOWNLOAD_VERIFY="${ALICIA_ANDROID_APP_PACKAGE_SKIP_DOWNLOAD_VERIFY:-false}"

CLOUD_BASE_URL="${CLOUD_BASE_URL%/}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_COMMON=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_COMMON+=(-k)
fi

WORK_DIR="$(mktemp -d)"
ACCESS_TOKEN="${ALICIA_ADMIN_TOKEN:-}"
REFRESH_TOKEN=""
SHOULD_LOGOUT="false"

cleanup() {
    local exit_code=$?
    set +e

    if [[ "$SHOULD_LOGOUT" == "true" && -n "$ACCESS_TOKEN" ]]; then
        local logout_payload
        if [[ -n "$REFRESH_TOKEN" ]]; then
            logout_payload="$(printf '{"refreshToken":"%s","allDevices":false}' "$(json_escape "$REFRESH_TOKEN")")"
        else
            logout_payload='{"allDevices":false}'
        fi

        curl -fsS "${CURL_COMMON[@]}" \
            -X POST "$IDENTITY_BASE_URL/api/identity/auth/logout" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: application/json" \
            --data-binary "$logout_payload" >/dev/null 2>&1 || true
    fi

    rm -rf "$WORK_DIR"
    exit "$exit_code"
}
trap cleanup EXIT

ok() {
    printf '[OK] %s\n' "$1"
}

warn() {
    printf '[WARN] %s\n' "$1" >&2
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

resolve_project_path() {
    local path="$1"
    if [[ "$path" == /* ]]; then
        printf '%s' "$path"
    else
        printf '%s/%s' "$PROJECT_DIR" "$path"
    fi
}

trim() {
    local value="$1"
    value="${value%$'\r'}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

json_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

extract_json_string() {
    local key="$1"
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

extract_json_boolean() {
    local key="$1"
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p" | head -n 1
}

curl_json_or_fail() {
    local label="$1"
    shift

    local output
    local error_file="$WORK_DIR/curl-error.txt"
    if ! output="$(curl -fsS "${CURL_COMMON[@]}" "$@" 2>"$error_file")"; then
        printf '[FAIL] %s\n' "$label" >&2
        cat "$error_file" >&2 || true
        exit 1
    fi

    printf '%s' "$output"
}

read_first_line() {
    local path="$1"
    sed '/^[[:space:]]*$/d' "$path" | head -n 1 | tr -d '\r'
}

read_release_notes() {
    local path="$1"
    local notes
    notes="$(cat "$path")"
    notes="${notes%$'\n'}"
    notes="${notes%$'\r'}"
    printf '%s' "$notes"
}

read_identity_credentials() {
    local account="${ALICIA_VERIFY_ACCOUNT:-${ALICIA_IDENTITY_ACCOUNT:-}}"
    local password="${ALICIA_VERIFY_PASSWORD:-${ALICIA_IDENTITY_PASSWORD:-}}"

    if [[ -z "$account" ]]; then
        read -r -p "Identity admin account/email/phone: " account
    fi

    if [[ -z "$password" ]]; then
        read -r -s -p "Identity admin password: " password
        printf '\n'
    fi

    [[ -n "$account" ]] || fail "Identity admin account is required."
    [[ -n "$password" ]] || fail "Identity admin password is required."

    local login_payload
    local login_response
    login_payload="$(printf '{"identifier":"%s","password":"%s"}' "$(json_escape "$account")" "$(json_escape "$password")")"
    login_response="$(curl_json_or_fail "identity admin login" \
        -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
        -H "Content-Type: application/json" \
        --data-binary "$login_payload")"
    unset password login_payload

    ACCESS_TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string token)"
    REFRESH_TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string refreshToken)"
    [[ -n "$ACCESS_TOKEN" ]] || fail "Identity admin login did not return an access token."
    SHOULD_LOGOUT="true"
    ok "identity admin login issued token"
}

verify_sha256_if_present() {
    local apk_path="$1"
    local sha_file="$2"

    if [[ ! -f "$sha_file" ]]; then
        warn "APK sha256 file is missing; upload will continue without local checksum verification."
        return 0
    fi

    command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required to verify $sha_file."

    local expected
    local actual
    expected="$(awk 'NF { print tolower($1); exit }' "$sha_file")"
    actual="$(sha256sum "$apk_path" | awk '{ print tolower($1) }')"
    [[ -n "$expected" ]] || fail "APK sha256 file is empty: $sha_file"
    [[ "$actual" == "$expected" ]] || fail "APK SHA-256 mismatch. Expected $expected, got $actual."
    ok "APK SHA-256 matches Git artifact checksum"
}

server_already_has_version() {
    local version_name="$1"
    local response
    response="$(curl_json_or_fail "current app-package version preflight" "$CLOUD_BASE_URL/api/app-package/version")"

    local available
    local current_version
    available="$(printf '%s' "$response" | tr -d '\n' | extract_json_boolean available)"
    current_version="$(printf '%s' "$response" | tr -d '\n' | extract_json_string versionName)"

    [[ "$available" == "true" && "$current_version" == "$version_name" ]]
}

verify_public_endpoints() {
    local expected_version="$1"
    local version_response
    version_response="$(curl_json_or_fail "public app-package version" "$PUBLIC_BASE_URL/api/app-package/version")"

    local available
    local actual_version
    available="$(printf '%s' "$version_response" | tr -d '\n' | extract_json_boolean available)"
    actual_version="$(printf '%s' "$version_response" | tr -d '\n' | extract_json_string versionName)"

    [[ "$available" == "true" ]] || fail "Public app-package version endpoint does not report an available package."
    [[ "$actual_version" == "$expected_version" ]] || fail "Public app-package version mismatch. Expected $expected_version, got ${actual_version:-<empty>}."
    ok "public app-package version matches Git artifact"

    if [[ "$SKIP_DOWNLOAD_VERIFY" == "true" ]]; then
        return 0
    fi

    local header_file="$WORK_DIR/download-head.txt"
    local error_file="$WORK_DIR/download-head-error.txt"
    local status
    status="$(curl "${CURL_COMMON[@]}" -I -o "$header_file" -w '%{http_code}' "$PUBLIC_BASE_URL/api/app-package/download/current" 2>"$error_file" || true)"
    if [[ "$status" != "200" && "$status" != "302" ]]; then
        printf '[FAIL] public app-package download endpoint returned HTTP %s\n' "${status:-<none>}" >&2
        cat "$error_file" >&2 || true
        exit 1
    fi
    ok "public app-package download endpoint is reachable"
}

cd "$PROJECT_DIR"

APK_PATH="$(resolve_project_path "$APK_PATH")"
VERSION_FILE="$(resolve_project_path "$VERSION_FILE")"
RELEASE_NOTES_FILE="$(resolve_project_path "$RELEASE_NOTES_FILE")"
SHA256_FILE="$(resolve_project_path "$SHA256_FILE")"

[[ -f "$APK_PATH" ]] || fail "Missing Git Android APK artifact: $APK_PATH"
[[ -f "$VERSION_FILE" ]] || fail "Missing Android version file: $VERSION_FILE"
[[ -f "$RELEASE_NOTES_FILE" ]] || fail "Missing Android release notes file: $RELEASE_NOTES_FILE"

VERSION_NAME="${ALICIA_ANDROID_GIT_VERSION_NAME:-$(read_first_line "$VERSION_FILE")}"
VERSION_NAME="$(trim "$VERSION_NAME")"
RELEASE_NOTES="${ALICIA_ANDROID_GIT_RELEASE_NOTES:-$(read_release_notes "$RELEASE_NOTES_FILE")}"
RELEASE_NOTES="$(trim "$RELEASE_NOTES")"

[[ -n "$VERSION_NAME" ]] || fail "Android versionName is empty."
[[ -n "$RELEASE_NOTES" ]] || fail "Android release notes are empty."
[[ ! "$RELEASE_NOTES" =~ ^TODO: ]] || fail "Android release notes still contain TODO text."

printf 'Publishing Git Android APK artifact...\n'
printf 'Project:      %s\n' "$PROJECT_DIR"
printf 'APK:          %s\n' "$APK_PATH"
printf 'Version:      %s\n' "$VERSION_NAME"
printf 'Cloud API:    %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base:  %s\n' "$PUBLIC_BASE_URL"

verify_sha256_if_present "$APK_PATH" "$SHA256_FILE"

if [[ "$FORCE_PUBLISH" != "true" ]] && server_already_has_version "$VERSION_NAME"; then
    ok "server already exposes Android version $VERSION_NAME; upload skipped"
    verify_public_endpoints "$VERSION_NAME"
    exit 0
fi

if [[ -z "$ACCESS_TOKEN" ]]; then
    read_identity_credentials
else
    ok "using ALICIA_ADMIN_TOKEN for app package upload"
fi

upload_response="$(curl_json_or_fail "android app-package upload" \
    -X POST "$CLOUD_BASE_URL/api/admin/app-package" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -F "file=@${APK_PATH};type=application/vnd.android.package-archive" \
    --form-string "versionName=$VERSION_NAME" \
    --form-string "releaseNotes=$RELEASE_NOTES")"

uploaded_version="$(printf '%s' "$upload_response" | tr -d '\n' | extract_json_string versionName)"
[[ "$uploaded_version" == "$VERSION_NAME" ]] || fail "Uploaded app-package version mismatch. Expected $VERSION_NAME, got ${uploaded_version:-<empty>}."
ok "Git Android APK uploaded to server"

verify_public_endpoints "$VERSION_NAME"

printf 'Alicia Git Android APK publish completed.\n'
