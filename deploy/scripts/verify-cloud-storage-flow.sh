#!/usr/bin/env bash
set -Eeuo pipefail

CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
CURL_TIMEOUT="${ALICIA_STORAGE_VERIFY_CURL_TIMEOUT_SECONDS:-20}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
KEEP_TEST_DATA="${ALICIA_STORAGE_VERIFY_KEEP_TEST_DATA:-false}"

CLOUD_BASE_URL="${CLOUD_BASE_URL%/}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_COMMON=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_COMMON+=(-k)
fi

WORK_DIR="$(mktemp -d)"
TOKEN=""
SOURCE_FOLDER_ID=""
TARGET_FOLDER_ID=""
FILE_ID=""

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

require_json_contains_id() {
    local label="$1"
    local json="$2"
    local node_id="$3"

    printf '%s' "$json" | grep -Eq "\"id\"[[:space:]]*:[[:space:]]*$node_id([^0-9]|$)" \
        || fail "$label did not contain node id $node_id"
}

require_json_not_contains_id() {
    local label="$1"
    local json="$2"
    local node_id="$3"

    if printf '%s' "$json" | grep -Eq "\"id\"[[:space:]]*:[[:space:]]*$node_id([^0-9]|$)"; then
        fail "$label unexpectedly contained node id $node_id"
    fi
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
        cleanup_node "$TARGET_FOLDER_ID"
        cleanup_node "$SOURCE_FOLDER_ID"
    elif [[ "$KEEP_TEST_DATA" == "true" ]]; then
        printf 'Keeping verification data: sourceFolderId=%s targetFolderId=%s fileId=%s\n' \
            "${SOURCE_FOLDER_ID:-}" "${TARGET_FOLDER_ID:-}" "${FILE_ID:-}"
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
        ok "archive is readable and contains uploaded file"
        return 0
    fi

    local magic
    magic="$(dd if="$archive_file" bs=2 count=1 2>/dev/null || true)"
    [[ "$magic" == "PK" ]] || fail "archive is not ZIP-shaped"
    ok "archive is ZIP-shaped"
}

read_identity_credentials() {
    ACCOUNT="${ALICIA_IDENTITY_ACCOUNT:-}"
    PASSWORD="${ALICIA_IDENTITY_PASSWORD:-}"

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
SOURCE_FOLDER_NAME="alicia-storage-verify-src-$TIMESTAMP-$RANDOM_SUFFIX"
TARGET_FOLDER_NAME="alicia-storage-verify-target-$TIMESTAMP-$RANDOM_SUFFIX"
VERIFY_FILE_NAME="storage-flow-$TIMESTAMP.txt"
RENAMED_FILE_NAME="storage-flow-renamed-$TIMESTAMP.txt"
VERIFY_FILE="$WORK_DIR/$VERIFY_FILE_NAME"
DOWNLOAD_FILE="$WORK_DIR/downloaded-$VERIFY_FILE_NAME"
ARCHIVE_FILE="$WORK_DIR/storage-archive.zip"

printf 'Verifying Alicia cloud storage flow...\n'
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"

read_identity_credentials

printf 'Alicia storage verification %s\n' "$TIMESTAMP" > "$VERIFY_FILE"

LOGIN_RESPONSE="$(curl_body "identity login" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$(json_escape "$ACCOUNT")\",\"password\":\"$(json_escape "$PASSWORD")\"}")"
TOKEN="$(require_json_string "identity login" "$LOGIN_RESPONSE" "token")"
ok "identity login issued token"

OVERVIEW_RESPONSE="$(curl_body "storage overview" \
    "$CLOUD_BASE_URL/api/storage/overview" \
    -H "Authorization: Bearer $TOKEN")"
require_json_number "storage overview" "$OVERVIEW_RESPONSE" "usedBytes" >/dev/null
ok "storage overview accepts identity token"

SOURCE_FOLDER_RESPONSE="$(curl_body "create source folder" \
    -X POST "$CLOUD_BASE_URL/api/storage/folders" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":null,\"folderName\":\"$(json_escape "$SOURCE_FOLDER_NAME")\"}")"
SOURCE_FOLDER_ID="$(require_json_number "create source folder" "$SOURCE_FOLDER_RESPONSE" "id")"
ok "created source folder $SOURCE_FOLDER_ID"

TARGET_FOLDER_RESPONSE="$(curl_body "create target folder" \
    -X POST "$CLOUD_BASE_URL/api/storage/folders" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":null,\"folderName\":\"$(json_escape "$TARGET_FOLDER_NAME")\"}")"
TARGET_FOLDER_ID="$(require_json_number "create target folder" "$TARGET_FOLDER_RESPONSE" "id")"
ok "created target folder $TARGET_FOLDER_ID"

UPLOAD_RESPONSE="$(curl_body "upload file" \
    -X POST "$CLOUD_BASE_URL/api/storage/files?parentId=$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$VERIFY_FILE;filename=$VERIFY_FILE_NAME;type=text/plain")"
FILE_ID="$(require_json_number "upload file" "$UPLOAD_RESPONSE" "id")"
UPLOADED_FILE_NAME="$(require_json_string "upload file" "$UPLOAD_RESPONSE" "name")"
[[ "$UPLOADED_FILE_NAME" == "$VERIFY_FILE_NAME" ]] || fail "upload returned unexpected file name $UPLOADED_FILE_NAME"
ok "uploaded file $FILE_ID"

SOURCE_LIST_RESPONSE="$(curl_body "list source folder" \
    "$CLOUD_BASE_URL/api/storage/nodes?parentId=$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN")"
require_json_contains_id "list source folder" "$SOURCE_LIST_RESPONSE" "$FILE_ID"
ok "source folder lists uploaded file"

ACCESS_URL_RESPONSE="$(curl_body "file access-url" \
    "$PUBLIC_BASE_URL/api/storage/files/$FILE_ID/access-url?disposition=attachment" \
    -H "Authorization: Bearer $TOKEN")"
require_json_string "file access-url" "$ACCESS_URL_RESPONSE" "url" >/dev/null
ACCESS_URL_FILE_NAME="$(require_json_string "file access-url" "$ACCESS_URL_RESPONSE" "fileName")"
[[ "$ACCESS_URL_FILE_NAME" == "$VERIFY_FILE_NAME" ]] || fail "file access-url returned unexpected fileName $ACCESS_URL_FILE_NAME"
ok "file access-url exposes uploaded file"

curl_file "file direct download" "$DOWNLOAD_FILE" \
    "$PUBLIC_BASE_URL/api/storage/files/$FILE_ID/download" \
    -H "Authorization: Bearer $TOKEN"
cmp -s "$VERIFY_FILE" "$DOWNLOAD_FILE" || fail "file download content mismatch"
ok "file direct download content matches upload"

curl_file "folder archive download" "$ARCHIVE_FILE" \
    -X POST "$PUBLIC_BASE_URL/api/storage/nodes/archive" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":[$SOURCE_FOLDER_ID,$FILE_ID]}"
validate_zip_contains_file "$ARCHIVE_FILE" "$VERIFY_FILE_NAME" "$VERIFY_FILE"

RENAME_RESPONSE="$(curl_body "rename file" \
    -X PUT "$CLOUD_BASE_URL/api/storage/nodes/$FILE_ID/rename" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"newName\":\"$(json_escape "$RENAMED_FILE_NAME")\"}")"
RENAMED_RESPONSE_NAME="$(require_json_string "rename file" "$RENAME_RESPONSE" "name")"
[[ "$RENAMED_RESPONSE_NAME" == "$RENAMED_FILE_NAME" ]] || fail "rename returned unexpected file name $RENAMED_RESPONSE_NAME"
ok "file rename succeeds"

MOVE_RESPONSE="$(curl_body "move file" \
    -X PUT "$CLOUD_BASE_URL/api/storage/nodes/$FILE_ID/move" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"parentId\":$TARGET_FOLDER_ID}")"
MOVED_PARENT_ID="$(require_json_number "move file" "$MOVE_RESPONSE" "parentId")"
[[ "$MOVED_PARENT_ID" == "$TARGET_FOLDER_ID" ]] || fail "move returned unexpected parentId $MOVED_PARENT_ID"
ok "file move succeeds"

SOURCE_AFTER_MOVE_RESPONSE="$(curl_body "list source folder after move" \
    "$CLOUD_BASE_URL/api/storage/nodes?parentId=$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN")"
require_json_not_contains_id "source folder after move" "$SOURCE_AFTER_MOVE_RESPONSE" "$FILE_ID"
ok "source folder no longer lists moved file"

TARGET_AFTER_MOVE_RESPONSE="$(curl_body "list target folder after move" \
    "$CLOUD_BASE_URL/api/storage/nodes?parentId=$TARGET_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN")"
require_json_contains_id "target folder after move" "$TARGET_AFTER_MOVE_RESPONSE" "$FILE_ID"
ok "target folder lists moved file"

curl_body "batch trash verification folders" \
    -X POST "$CLOUD_BASE_URL/api/storage/nodes/batch/trash" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":[$SOURCE_FOLDER_ID,$TARGET_FOLDER_ID]}" >/dev/null
ok "batch trash succeeds"

TRASH_RESPONSE="$(curl_body "list trash" \
    "$CLOUD_BASE_URL/api/storage/trash" \
    -H "Authorization: Bearer $TOKEN")"
require_json_contains_id "trash list" "$TRASH_RESPONSE" "$SOURCE_FOLDER_ID"
require_json_contains_id "trash list" "$TRASH_RESPONSE" "$TARGET_FOLDER_ID"
ok "trash list contains trashed folders"

RESTORE_RESPONSE="$(curl_body "batch restore verification folders" \
    -X POST "$CLOUD_BASE_URL/api/storage/trash/batch/restore" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"nodeIds\":[$SOURCE_FOLDER_ID,$TARGET_FOLDER_ID]}")"
require_json_contains_id "restore response" "$RESTORE_RESPONSE" "$SOURCE_FOLDER_ID"
require_json_contains_id "restore response" "$RESTORE_RESPONSE" "$TARGET_FOLDER_ID"
ok "batch restore succeeds"

ROOT_AFTER_RESTORE_RESPONSE="$(curl_body "list root after restore" \
    "$CLOUD_BASE_URL/api/storage/nodes" \
    -H "Authorization: Bearer $TOKEN")"
require_json_contains_id "root after restore" "$ROOT_AFTER_RESTORE_RESPONSE" "$SOURCE_FOLDER_ID"
require_json_contains_id "root after restore" "$ROOT_AFTER_RESTORE_RESPONSE" "$TARGET_FOLDER_ID"
ok "restored folders are visible again"

curl_body "move target folder to trash" \
    -X DELETE "$CLOUD_BASE_URL/api/storage/nodes/$TARGET_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
ok "single delete moves folder to trash"

expect_http_status "download under trashed folder is unavailable" 400 \
    "$PUBLIC_BASE_URL/api/storage/files/$FILE_ID/download" \
    -H "Authorization: Bearer $TOKEN"

curl_body "permanently delete target folder" \
    -X DELETE "$CLOUD_BASE_URL/api/storage/trash/$TARGET_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
TARGET_FOLDER_ID=""
FILE_ID=""
ok "permanent delete removes target folder subtree"

curl_body "move source folder to trash" \
    -X DELETE "$CLOUD_BASE_URL/api/storage/nodes/$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
curl_body "permanently delete source folder" \
    -X DELETE "$CLOUD_BASE_URL/api/storage/trash/$SOURCE_FOLDER_ID" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
SOURCE_FOLDER_ID=""
ok "temporary source folder cleaned"

printf 'Alicia cloud storage flow verification passed.\n'
