#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
if [[ $# -gt 0 && -d "$1" && -f "$1/compose.yaml" ]]; then
    PROJECT_DIR="$1"
    shift
fi

ENV_FILE="${ALICIA_BACKUP_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_BACKUP_OUTPUT_DIR:-deploy/generated/production-backups}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
INCLUDE_ENV="${ALICIA_BACKUP_INCLUDE_ENV:-true}"
INCLUDE_CERTS="${ALICIA_BACKUP_INCLUDE_CERTS:-true}"
INCLUDE_GENERATED_KEYS="${ALICIA_BACKUP_INCLUDE_GENERATED_KEYS:-true}"
VALIDATE_AFTER_BACKUP="${ALICIA_VALIDATE_BACKUP_AFTER_CREATE:-true}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

dotenv_value() {
    local file="$1"
    local key="$2"
    local line
    if [[ ! -f "$file" ]]; then
        return 0
    fi

    line="$(sed -n "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*//p" "$file" | tail -n 1)"
    line="${line%$'\r'}"
    line="${line%%#*}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"

    if [[ "${line:0:1}" == "\"" && "${line: -1}" == "\"" ]]; then
        line="${line:1:${#line}-2}"
    elif [[ "${line:0:1}" == "'" && "${line: -1}" == "'" ]]; then
        line="${line:1:${#line}-2}"
    fi

    printf '%s' "$line"
}

compose() {
    local command=(docker compose)
    local file

    if [[ "${ALICIA_DOCKER_SUDO:-auto}" == "true" ]]; then
        command=(sudo docker compose)
    elif [[ "${ALICIA_DOCKER_SUDO:-auto}" == "auto" && "${EUID:-$(id -u)}" -ne 0 ]]; then
        if ! docker compose ps >/dev/null 2>&1; then
            command=(sudo docker compose)
        fi
    fi

    for file in $COMPOSE_FILES; do
        command+=(-f "$file")
    done

    "${command[@]}" "$@"
}

require_database_name() {
    local label="$1"
    local value="$2"

    [[ "$value" =~ ^[A-Za-z0-9_]+$ ]] \
        || fail "$label must contain only letters, numbers, and underscores: $value"
}

database_exists() {
    local database="$1"
    compose exec -T db sh -lc '
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -uroot -e "
SELECT COUNT(*)
FROM information_schema.schemata
WHERE schema_name = '\''$1'\'';
"
    ' sh "$database" | tr -d '\r' | tail -n 1 | tr -d '[:space:]'
}

dump_database() {
    local label="$1"
    local database="$2"
    local output_file="$3"

    printf 'Backing up %s database: %s\n' "$label" "$database"
    compose exec -T db sh -lc '
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump \
            --single-transaction \
            --routines \
            --triggers \
            --events \
            --set-gtid-purged=OFF \
            -uroot "$1"
    ' sh "$database" | gzip -c > "$output_file"
    chmod 600 "$output_file"
    ok "$label database dump written"
}

copy_if_file() {
    local source_path="$1"
    local target_path="$2"

    if [[ -f "$source_path" ]]; then
        mkdir -p "$(dirname "$target_path")"
        install -m 600 "$source_path" "$target_path"
        return 0
    fi

    return 1
}

archive_sensitive_config() {
    local archive_file="$1"
    local stage_dir="$BACKUP_DIR/sensitive-config"
    local copied=0

    rm -rf "$stage_dir"
    mkdir -p "$stage_dir"

    if [[ "$INCLUDE_ENV" == "true" ]] && copy_if_file "$ENV_FILE" "$stage_dir/.env"; then
        copied=1
    fi

    if [[ "$INCLUDE_CERTS" == "true" && -d deploy/certs ]]; then
        mkdir -p "$stage_dir/deploy/certs"
        while IFS= read -r -d '' cert_file; do
            copy_if_file "$cert_file" "$stage_dir/$cert_file" >/dev/null || true
            copied=1
        done < <(find deploy/certs -maxdepth 1 -type f -name '*.pem' -print0)
    fi

    if [[ "$INCLUDE_GENERATED_KEYS" == "true" && -d deploy/generated/identity-rs256 ]]; then
        mkdir -p "$stage_dir/deploy/generated"
        cp -a deploy/generated/identity-rs256 "$stage_dir/deploy/generated/"
        copied=1
    fi

    if [[ "$copied" == "1" ]]; then
        tar -C "$BACKUP_DIR" -czf "$archive_file" sensitive-config
        chmod 600 "$archive_file"
        ok "sensitive config archive written"
    else
        printf 'No sensitive config files found for archive.\n'
    fi

    rm -rf "$stage_dir"
}

write_manifest() {
    local manifest_file="$1"
    local tracked_status

    tracked_status="$(git status --porcelain --untracked-files=no || true)"

    {
        printf 'Alicia production backup manifest\n'
        printf 'created_at=%s\n' "$(date -Is 2>/dev/null || date)"
        printf 'project_dir=%s\n' "$PROJECT_DIR"
        printf 'git_head=%s\n' "$(git rev-parse --short HEAD)"
        printf 'git_branch=%s\n' "$(git rev-parse --abbrev-ref HEAD)"
        printf 'cloud_database=%s\n' "$CLOUD_DATABASE"
        printf 'identity_database=%s\n' "$IDENTITY_DATABASE"
        printf 'same_database=%s\n' "$SAME_DATABASE"
        printf 'include_env=%s\n' "$INCLUDE_ENV"
        printf 'include_certs=%s\n' "$INCLUDE_CERTS"
        printf 'include_generated_keys=%s\n' "$INCLUDE_GENERATED_KEYS"
        if [[ -n "$tracked_status" ]]; then
            printf 'tracked_changes=true\n'
            printf '\nTracked changes at backup time:\n'
            printf '%s\n' "$tracked_status"
        else
            printf 'tracked_changes=false\n'
        fi
    } > "$manifest_file"
    chmod 600 "$manifest_file"
}

write_checksums() {
    if command -v sha256sum >/dev/null 2>&1; then
        (
            cd "$BACKUP_DIR"
            find . -maxdepth 1 -type f ! -name SHA256SUMS -print0 \
                | sort -z \
                | xargs -0 sha256sum > SHA256SUMS
        )
        chmod 600 "$BACKUP_DIR/SHA256SUMS"
        ok "sha256 checksums written"
    else
        printf 'sha256sum is not available; checksum file skipped.\n'
    fi
}

cd "$PROJECT_DIR"

[[ -f "$ENV_FILE" ]] || fail "Env file not found: $ENV_FILE"
for file in $COMPOSE_FILES; do
    [[ -f "$file" ]] || fail "Missing compose file: $file"
done
command -v gzip >/dev/null 2>&1 || fail "gzip is required."
command -v tar >/dev/null 2>&1 || fail "tar is required."

CLOUD_DATABASE="$(dotenv_value "$ENV_FILE" MYSQL_DATABASE)"
CLOUD_DATABASE="${CLOUD_DATABASE:-alicia_cloud_storage}"
IDENTITY_DATABASE="$(dotenv_value "$ENV_FILE" ALICIA_IDENTITY_MYSQL_DATABASE)"
IDENTITY_DATABASE="${IDENTITY_DATABASE:-$CLOUD_DATABASE}"

require_database_name "Cloud database name" "$CLOUD_DATABASE"
require_database_name "Identity database name" "$IDENTITY_DATABASE"

[[ "$(database_exists "$CLOUD_DATABASE")" == "1" ]] || fail "Cloud database does not exist: $CLOUD_DATABASE"
[[ "$(database_exists "$IDENTITY_DATABASE")" == "1" ]] || fail "Identity database does not exist: $IDENTITY_DATABASE"

SAME_DATABASE=false
if [[ "$CLOUD_DATABASE" == "$IDENTITY_DATABASE" ]]; then
    SAME_DATABASE=true
fi

umask 077
TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
BACKUP_DIR="$OUTPUT_DIR/$TIMESTAMP"
mkdir -p "$BACKUP_DIR"

printf 'Preparing Alicia production backup:\n'
printf '  output dir:        %s\n' "$BACKUP_DIR"
printf '  cloud database:    %s\n' "$CLOUD_DATABASE"
printf '  identity database: %s\n' "$IDENTITY_DATABASE"
printf '  sensitive config:  env=%s certs=%s generatedKeys=%s\n' \
    "$INCLUDE_ENV" "$INCLUDE_CERTS" "$INCLUDE_GENERATED_KEYS"
printf '\n'

dump_database "cloud" "$CLOUD_DATABASE" "$BACKUP_DIR/cloud-$CLOUD_DATABASE.sql.gz"
if [[ "$SAME_DATABASE" == "true" ]]; then
    printf 'Identity database is the same as cloud database; second dump skipped.\n'
else
    dump_database "identity" "$IDENTITY_DATABASE" "$BACKUP_DIR/identity-$IDENTITY_DATABASE.sql.gz"
fi

archive_sensitive_config "$BACKUP_DIR/sensitive-config.tar.gz"
write_manifest "$BACKUP_DIR/manifest.txt"
write_checksums

if [[ "$VALIDATE_AFTER_BACKUP" == "true" ]]; then
    bash deploy/scripts/validate-production-backup.sh "$BACKUP_DIR"
fi

printf '\nAlicia production backup completed.\n'
printf 'Backup directory:\n'
printf '  %s\n' "$BACKUP_DIR"
printf '\nKeep this directory private. It can contain database dumps, .env, TLS certs, and signing key material.\n'
