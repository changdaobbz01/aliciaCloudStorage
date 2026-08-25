#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
if [[ $# -gt 0 && -d "$1" && -f "$1/compose.yaml" ]]; then
    PROJECT_DIR="$1"
    shift
fi

OUTPUT_DIR="${ALICIA_BACKUP_OUTPUT_DIR:-deploy/generated/production-backups}"
BACKUP_DIR="${1:-${ALICIA_BACKUP_VALIDATE_DIR:-}}"
REQUIRE_SENSITIVE_ARCHIVE="${ALICIA_BACKUP_VALIDATE_REQUIRE_SENSITIVE_ARCHIVE:-false}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

latest_backup_dir() {
    [[ -d "$OUTPUT_DIR" ]] || return 0
    find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\t%p\n' 2>/dev/null \
        | sort -r \
        | awk 'NR == 1 { print $2 }'
}

require_manifest_key() {
    local key="$1"

    grep -Eq "^${key}=.+" "$MANIFEST_FILE" \
        || fail "Manifest is missing required key: $key"
}

validate_sha256sums() {
    [[ -f SHA256SUMS ]] || fail "Missing SHA256SUMS."
    command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required."
    sha256sum -c SHA256SUMS
    ok "backup checksums match"
}

validate_gzip_dump() {
    local dump_file="$1"
    local create_count

    gzip -t "$dump_file" || fail "Invalid gzip dump: $dump_file"
    create_count="$(gzip -cd "$dump_file" | grep -Eic '^(CREATE TABLE|CREATE DATABASE|DROP TABLE|INSERT INTO)' || true)"
    [[ "${create_count:-0}" -gt 0 ]] || fail "Dump does not look like a MySQL dump: $dump_file"
    ok "database dump is readable: $(basename "$dump_file")"
}

validate_sensitive_archive() {
    local archive_file="$1"
    local listing

    listing="$(tar -tzf "$archive_file")" || fail "Sensitive config archive is not readable."
    [[ -n "$listing" ]] || fail "Sensitive config archive is empty."
    if printf '%s\n' "$listing" | grep -q '^sensitive-config/\.env$'; then
        ok "sensitive archive contains env snapshot"
    else
        printf '[WARN] sensitive archive does not contain .env snapshot\n' >&2
    fi
    ok "sensitive config archive is readable"
}

cd "$PROJECT_DIR"

if [[ -z "$BACKUP_DIR" ]]; then
    BACKUP_DIR="$(latest_backup_dir)"
fi

[[ -n "$BACKUP_DIR" ]] || fail "No backup directory found under $OUTPUT_DIR."
[[ -d "$BACKUP_DIR" ]] || fail "Backup directory does not exist: $BACKUP_DIR"

MANIFEST_FILE="$BACKUP_DIR/manifest.txt"
[[ -f "$MANIFEST_FILE" ]] || fail "Missing manifest: $MANIFEST_FILE"

printf 'Validating Alicia production backup:\n'
printf '  backup dir: %s\n' "$BACKUP_DIR"
printf '\n'

(
    cd "$BACKUP_DIR"
    validate_sha256sums
)

require_manifest_key created_at
require_manifest_key project_dir
require_manifest_key git_head
require_manifest_key git_branch
require_manifest_key cloud_database
require_manifest_key identity_database
require_manifest_key same_database

mapfile -t dump_files < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.sql.gz' | sort)
[[ "${#dump_files[@]}" -gt 0 ]] || fail "No database dumps found in backup directory."

for dump_file in "${dump_files[@]}"; do
    validate_gzip_dump "$dump_file"
done

SENSITIVE_ARCHIVE="$BACKUP_DIR/sensitive-config.tar.gz"
if [[ -f "$SENSITIVE_ARCHIVE" ]]; then
    validate_sensitive_archive "$SENSITIVE_ARCHIVE"
elif [[ "$REQUIRE_SENSITIVE_ARCHIVE" == "true" ]]; then
    fail "Missing required sensitive config archive."
else
    printf 'Sensitive config archive is absent; skipped.\n'
fi

ok "manifest contains required keys"
printf '\nAlicia production backup validation passed.\n'
