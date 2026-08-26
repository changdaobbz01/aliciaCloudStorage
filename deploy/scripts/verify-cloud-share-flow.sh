#!/usr/bin/env bash
set -Eeuo pipefail

CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
CURL_TIMEOUT="${ALICIA_SHARE_VERIFY_CURL_TIMEOUT_SECONDS:-20}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
KEEP_TEST_DATA="${ALICIA_SHARE_VERIFY_KEEP_TEST_DATA:-false}"

CLOUD_BASE_URL="${CLOUD_BASE_URL%/}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_COMMON=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_COMMON+=(-k)
fi

WORK_DIR="$(mktemp -d)"
TOKEN=""
SHARE_ID=""
SOURCE_FOLDER_ID=""
SAVE_FOLDER_ID=""

ok() {
    printf '[OK] %s\n' "$1"
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
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

json_number_array() {
    local count="$1"
    local start="${2:-1}"
    local output="["
    local i

    for ((i = 0; i < count; i += 1)); do
        if ((i > 0)); then
            output+=","
        fi
        output+="$((start + i))"
    done

    output+="]"
    printf '%s' "$output"
}

extract_json_string() {
    local json="$1"
    local key="$2"
    printf '%s' "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

extract_json_number() {
    local json="$1"
    local key="$2"
    printf '%s' "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" | head -n 1
}

extract_json_bool() {
    local json="$1"
    local key="$2"
    printf '%s' "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p" | head -n 1
}

curl_body() {
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

curl_file() {
    local label="$1"
    local output_file="$2"
    shift 2

    local error_file="$WORK_DIR/curl-error.txt"
    if ! curl -fsS "${CURL_COMMON[@]}" -o "$output_file" "$@" 2>"$error_file"; then
        printf '[FAIL] %s\n' "$label" >&2
        cat "$error_file" >&2 || true
        exit 1
    fi

    ok "$label"
}

expect_http_status() {
    local label="$1"
    local expected_status="$2"
    shift 2

    local body_file="$WORK_DIR/http-status-body.txt"
    local status
    status="$(curl "${CURL_COMMON[@]}" -o "$body_file" -w '%{http_code}' "$@" 2>/dev/null || true)"

    if [[ "$status" != "$expected_status" ]]; then
        printf '[FAIL] %s: expected HTTP %s, got %s\n' "$label" "$expected_status" "${status:-<none>}" >&2
        if [[ -s "$body_file" ]]; then
            cat "$body_file" >&2
            printf '\n' >&2
        fi
        exit 1
    fi

    ok "$label"
}

require_json_string() {
    local label="$1"
    local json="$2"
    local key="$3"
    local value
    value="$(extract_json_string "$json" "$key")"

    [[ -n "$value" ]] || fail "$label missing JSON string field: $key"
    printf '%s' "$value"
}

require_json_number() {
    local label="$1"
    local json="$2"
    local key="$3"
    local value
    value="$(extract_json_number "$json" "$key")"

    [[ -n "$value" ]] || fail "$label missing JSON number field: $key"
    printf '%s' "$value"
}

require_json_bool() {
    local label="$1"
    local json="$2"
    local key="$3"
    local expected="$4"
    local value
    value="$(extract_json_bool "$json" "$key")"

    [[ "$value" == "$expected" ]] || fail "$label expected $key=$expected, got ${value:-<missing>}"
}

require_json_contains_id() {
    local label="$1"
    local json="$2"
    local node_id="$3"

    printf '%s' "$json" | grep -Eq "\"id\"[[:space:]]*:[[:space:]]*$node_id([^0-9]|$)" \
        || fail "$label did not contain node id $node_id"
}

cleanup_node() {
    local node_id="$1"
    [[ -n "$node_id" ]] || return 0

    curl -fsS "${CURL_COMMON[@]}" \
        -X DELETE "$CLOUD_BASE_URL/api/storage/nodes/$node_id" \
        -H "Authorization: Bearer $TOKEN" >/dev/null 2>&1 || true
    curl -fsS "${CURL_COMMON[@]}" \
        -X DELETE "$CLOUD_BASE_URL/api/storage/trash/$node_id" \
        -H "Authorization: Bearer $TOKEN" >/dev/null 2>&1 || true
}

cleanup() {
    local exit_code=$?
    set +e

    if [[ "$KEEP_TEST_DATA" != "true" && -n "$TOKEN" ]]; then
        if [[ -n "$SHARE_ID" ]]; then
            curl -fsS "${CURL_COMMON[@]}" \
                -X DELETE "$CLOUD_BASE_URL/api/share-links/$SHARE_ID" \
                -H "Authorization: Bearer $TOKEN" >/dev/null 2>&1 || true
        fi

        cleanup_node "$SAVE_FOLDER_ID"
        cleanup_node "$SOURCE_FOLDER_ID"
    elif [[ "$KEEP_TEST_DATA" == "true" ]]; then
        printf 'Keeping verification data: shareId=%s sourceFolderId=%s saveFolderId=%s\n' \
            "${SHARE_ID:-}" "${SOURCE_FOLDER_ID:-}" "${SAVE_FOLDER_ID:-}"
    fi

    rm -rf "$WORK_DIR"
    exit "$exit_code"
}
trap cleanup EXIT

validate_zip_contains_file() {
    local archive_file="$1"
    local expected_file_name="$2"
    local expected_file_path="$3"

    if command -v python3 >/dev/null 2>&1; then
        python3 - "$archive_file" "$expected_file_name" "$expected_file_path" <<'PY'
import pathlib
import sys
import zipfile

archive = pathlib.Path(sys.argv[1])
expected_name = sys.argv[2]
expected_content = pathlib.Path(sys.argv[3]).read_bytes()

with zipfile.ZipFile(archive) as zf:
    matches = [name for name in zf.namelist() if name == expected_name or name.endswith("/" + expected_name)]
    if not matches:
        raise SystemExit(f"zip does not contain {expected_name}")
    actual = zf.read(matches[0])
    if actual != expected_content:
        raise SystemExit(f"zip entry {matches[0]} content mismatch")
PY
        ok "share archive is readable and contains uploaded file"
        return 0
    fi

    local magic
    magic="$(dd if="$archive_file" bs=2 count=1 2>/dev/null || true)"
    [[ "$magic" == "PK" ]] || fail "share archive is not ZIP-shaped"
    ok "share archive is ZIP-shaped"
}

read_identity_credentials() {
    ACCOUNT="${ALICIA_IDENTITY_ACCOUNT:-${ALICIA_VERIFY_ACCOUNT:-}}"
    PASSWORD="${ALICIA_IDENTITY_PASSWORD:-${ALICIA_VERIFY_PASSWORD:-}}"

    if [[ -z "$ACCOUNT" ]]; then
        read -r -p "Identity account/email/phone: " ACCOUNT
    fi

    if [[ -z "$PASSWORD" ]]; then
        read -r -s -p "Identity password: " PASSWORD
        printf '\n'
    fi
}

TIMESTAMP="$(date +%Y%m%d%H%M%S)"
RANDOM_SUFFIX="${RANDOM}${RANDOM}"
SOURCE_FOLDER_NAME="alicia-share-verify-src-$TIMESTAMP-$RANDOM_SUFFIX"
SAVE_FOLDER_NAME="alicia-share-verify-save-$TIMESTAMP-$RANDOM_SUFFIX"
VERIFY_FILE_NAME="share-flow-$TIMESTAMP.txt"
VERIFY_FILE="$WORK_DIR/$VERIFY_FILE_NAME"
DOWNLOAD_FILE="$WORK_DIR/downloaded-$VERIFY_FILE_NAME"
ARCHIVE_FILE="$WORK_DIR/share-archive.zip"
SHARE_TITLE="Alicia share verify $TIMESTAMP"
SHARE_PASSWORD="verify-$RANDOM_SUFFIX"

printf 'Verifying Alicia cloud share flow...\n'
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"

read_identity_credentials

printf 'Alicia share verification %s\n' "$TIMESTAMP" > "$VERIFY_FILE"

LOGIN_RESPONSE="$(curl_body "identity login" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$(json_escape "$ACCOUNT")\",\"password\":\"$(json_escape "$PASSWORD")\"}")"
TOKEN="$(require_json_string "identity login" "$LOGIN_RESPONSE" "token")"
ok "identity login issued token"

TOO_MANY_SHARE_NODE_IDS="$(json_number_array 21)"

expect_http_status "share create rejects too many items" 400 \
    -X POST "$CLOUD_BASE_URL/api/share-links" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":$TOO_MANY_SHARE_NODE_IDS,\"title\":\"too-many-items\",\"password\":\"\",\"expiresInDays\":1,\"allowDownload\":true,\"allowSave\":true}"

SOURCE_FOLDER_RESPONSE="$(curl_body "create source folder" \
    -X POST "$CLOUD_BASE_URL/api/storage/folders" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":null,\"folderName\":\"$(json_escape "$SOURCE_FOLDER_NAME")\"}")"
SOURCE_FOLDER_ID="$(require_json_number "create source folder" "$SOURCE_FOLDER_RESPONSE" "id")"
ok "created source folder $SOURCE_FOLDER_ID"

SAVE_FOLDER_RESPONSE="$(curl_body "create save target folder" \
    -X POST "$CLOUD_BASE_URL/api/storage/folders" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":null,\"folderName\":\"$(json_escape "$SAVE_FOLDER_NAME")\"}")"
SAVE_FOLDER_ID="$(require_json_number "create save target folder" "$SAVE_FOLDER_RESPONSE" "id")"
ok "created save target folder $SAVE_FOLDER_ID"

UPLOAD_RESPONSE="$(curl_body "upload share verification file" \
    -X POST "$CLOUD_BASE_URL/api/storage/files?parentId=$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$VERIFY_FILE;filename=$VERIFY_FILE_NAME;type=text/plain")"
FILE_ID="$(require_json_number "upload share verification file" "$UPLOAD_RESPONSE" "id")"
ok "uploaded share verification file $FILE_ID"

CREATE_SHARE_RESPONSE="$(curl_body "create share link" \
    -X POST "$CLOUD_BASE_URL/api/share-links" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":[$SOURCE_FOLDER_ID],\"title\":\"$(json_escape "$SHARE_TITLE")\",\"password\":\"$(json_escape "$SHARE_PASSWORD")\",\"expiresInDays\":1,\"allowDownload\":true,\"allowSave\":true}")"
SHARE_ID="$(require_json_number "create share link" "$CREATE_SHARE_RESPONSE" "id")"
SHARE_CODE="$(require_json_string "create share link" "$CREATE_SHARE_RESPONSE" "shareCode")"
ok "created share link $SHARE_CODE"

curl_file "cloudPan share page serves SPA shell" "$WORK_DIR/share-page.html" \
    "$PUBLIC_BASE_URL/cloudPan/share/$SHARE_CODE"

STATUS_RESPONSE="$(curl_body "public share status" \
    "$PUBLIC_BASE_URL/api/public/share-links/$SHARE_CODE/status")"
require_json_bool "public share status" "$STATUS_RESPONSE" "available" "true"
require_json_bool "public share status" "$STATUS_RESPONSE" "requiresPassword" "true"
ok "public share status exposes available password-protected share"

expect_http_status "share detail requires password access token" 400 \
    "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/detail" \
    -H "Authorization: Bearer $TOKEN"

VERIFY_PASSWORD_RESPONSE="$(curl_body "verify share password" \
    -X POST "$PUBLIC_BASE_URL/api/public/share-links/$SHARE_CODE/verify-password" \
    -H "Content-Type: application/json" \
    -d "{\"password\":\"$(json_escape "$SHARE_PASSWORD")\"}")"
SHARE_ACCESS_TOKEN="$(require_json_string "verify share password" "$VERIFY_PASSWORD_RESPONSE" "accessToken")"
ok "share password verification issued access token"

DETAIL_RESPONSE="$(curl_body "share detail with access token" \
    "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/detail" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN")"
require_json_bool "share detail" "$DETAIL_RESPONSE" "allowDownload" "true"
require_json_bool "share detail" "$DETAIL_RESPONSE" "allowSave" "true"
require_json_contains_id "share detail" "$DETAIL_RESPONSE" "$SOURCE_FOLDER_ID"
require_json_contains_id "share detail" "$DETAIL_RESPONSE" "$FILE_ID"
ok "share detail contains shared folder and file"

expect_http_status "share save rejects empty selected set" 400 \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/save" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"parentId":null,"selectedNodeIds":[]}'

expect_http_status "share archive rejects empty selection" 400 \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/nodes/archive" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"nodeIds":[]}'

TOO_MANY_SHARE_SAVE_NODE_IDS="$(json_number_array 501)"
TOO_MANY_SHARE_ARCHIVE_NODE_IDS="$(json_number_array 101)"

expect_http_status "share save rejects too many selected items" 400 \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/save" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":null,\"selectedNodeIds\":$TOO_MANY_SHARE_SAVE_NODE_IDS}"

expect_http_status "share archive rejects too many selections" 400 \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/nodes/archive" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":$TOO_MANY_SHARE_ARCHIVE_NODE_IDS}"

ACCESS_URL_RESPONSE="$(curl_body "share file access-url" \
    "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/files/$FILE_ID/access-url?disposition=attachment" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN")"
require_json_string "share file access-url" "$ACCESS_URL_RESPONSE" "url" >/dev/null
FILE_NAME_FROM_URL="$(require_json_string "share file access-url" "$ACCESS_URL_RESPONSE" "fileName")"
[[ "$FILE_NAME_FROM_URL" == "$VERIFY_FILE_NAME" ]] || fail "share file access-url returned unexpected fileName $FILE_NAME_FROM_URL"
ok "share file access-url exposes uploaded file"

curl_file "share file direct download" "$DOWNLOAD_FILE" \
    "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/files/$FILE_ID/download" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN"
cmp -s "$VERIFY_FILE" "$DOWNLOAD_FILE" || fail "share file download content mismatch"
ok "share file download content matches upload"

curl_file "share folder archive download" "$ARCHIVE_FILE" \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/nodes/archive" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":[$SOURCE_FOLDER_ID,$FILE_ID]}"
validate_zip_contains_file "$ARCHIVE_FILE" "$VERIFY_FILE_NAME" "$VERIFY_FILE"

SAVE_SHARE_RESPONSE="$(curl_body "save share to drive" \
    -X POST "$PUBLIC_BASE_URL/api/share-links/$SHARE_CODE/save" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Share-Access-Token: $SHARE_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":$SAVE_FOLDER_ID,\"selectedNodeIds\":[$SOURCE_FOLDER_ID]}")"
printf '%s' "$SAVE_SHARE_RESPONSE" | grep -Fq "$SOURCE_FOLDER_NAME" \
    || fail "save share response did not contain saved source folder name"
ok "share save flow copied selected folder into target folder"

REVOKE_RESPONSE="$(curl_body "revoke share link" \
    -X DELETE "$CLOUD_BASE_URL/api/share-links/$SHARE_ID" \
    -H "Authorization: Bearer $TOKEN")"
REVOKED_STATUS="$(require_json_string "revoke share link" "$REVOKE_RESPONSE" "status")"
[[ "$REVOKED_STATUS" == "REVOKED" ]] || fail "share revoke status expected REVOKED, got $REVOKED_STATUS"
SHARE_ID=""
ok "share revoke succeeds"

STATUS_AFTER_REVOKE="$(curl_body "public share status after revoke" \
    "$PUBLIC_BASE_URL/api/public/share-links/$SHARE_CODE/status")"
require_json_bool "public share status after revoke" "$STATUS_AFTER_REVOKE" "available" "false"
REVOKED_REASON="$(extract_json_string "$STATUS_AFTER_REVOKE" "reason")"
[[ "$REVOKED_REASON" == "REVOKED" ]] || fail "public share status after revoke expected reason REVOKED, got ${REVOKED_REASON:-<missing>}"
ok "revoked share is no longer publicly available"

cleanup_node "$SAVE_FOLDER_ID"
cleanup_node "$SOURCE_FOLDER_ID"
SAVE_FOLDER_ID=""
SOURCE_FOLDER_ID=""
ok "temporary verification folders cleaned"

printf 'Alicia cloud share flow verification passed.\n'
