#!/usr/bin/env bash
set -Eeuo pipefail

TARGET_IDENTITY_DATABASE="${1:-${ALICIA_IDENTITY_DATABASE_SPLIT_TARGET:-alicia_identity}}"
ENV_FILE="${ALICIA_IDENTITY_DATABASE_SPLIT_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_IDENTITY_DATABASE_SPLIT_OUTPUT_DIR:-deploy/generated/identity-database-split}"
VERIFY_SCRIPT="${ALICIA_IDENTITY_DATABASE_SPLIT_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
ROLLBACK_ON_FAILURE="${ALICIA_IDENTITY_DATABASE_SPLIT_ROLLBACK_ON_VERIFY_FAILURE:-true}"
ALLOW_EXISTING_TARGET="${ALICIA_IDENTITY_DATABASE_SPLIT_ALLOW_EXISTING_TARGET:-false}"

IDENTITY_TABLES=(
    identity_user
    email_verification_code
    identity_refresh_token
    identity_audit_log
    identity_flyway_schema_history
)

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

replace_or_append_env() {
    local input_file="$1"
    local output_file="$2"
    local key="$3"
    local value="$4"

    KEY="$key" VALUE="$value" awk '
        BEGIN { replaced = 0 }
        $0 ~ "^[[:space:]]*" ENVIRON["KEY"] "[[:space:]]*=" {
            print ENVIRON["KEY"] "=" ENVIRON["VALUE"]
            replaced = 1
            next
        }
        { print }
        END {
            if (replaced == 0) {
                print ENVIRON["KEY"] "=" ENVIRON["VALUE"]
            }
        }
    ' "$input_file" > "$output_file"
}

compose() {
    local command=(docker compose)

    if [[ "${ALICIA_DOCKER_SUDO:-auto}" == "true" ]]; then
        command=(sudo docker compose)
    elif [[ "${ALICIA_DOCKER_SUDO:-auto}" == "auto" && "${EUID:-$(id -u)}" -ne 0 ]]; then
        if ! docker compose ps >/dev/null 2>&1; then
            command=(sudo docker compose)
        fi
    fi

    local file
    for file in $COMPOSE_FILES; do
        command+=(-f "$file")
    done

    "${command[@]}" "$@"
}

mysql_root_exec() {
    local sql="$1"

    compose exec -T db sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "$1"' sh "$sql"
}

mysql_root_query() {
    local sql="$1"

    compose exec -T db sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "$1"' sh "$sql"
}

quote_identifier() {
    local value="$1"
    printf '`%s`' "${value//\`/\`\`}"
}

require_database_name() {
    local label="$1"
    local value="$2"

    [[ "$value" =~ ^[A-Za-z0-9_]+$ ]] \
        || fail "$label must contain only letters, numbers, and underscores: $value"
}

source_table_count() {
    local table_name="$1"
    mysql_root_query "
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = '$SOURCE_CLOUD_DATABASE'
  AND table_name = '$table_name';
"
}

target_table_count() {
    mysql_root_query "
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = '$TARGET_IDENTITY_DATABASE';
"
}

restart_identity() {
    compose up -d --no-build identity
}

run_verification() {
    ALICIA_VERIFY_ENV_FILE="$ENV_FILE" bash "$VERIFY_SCRIPT"
}

rollback_env() {
    printf '\nRolling back identity database env from backup...\n' >&2
    install -m 600 "$BACKUP_FILE" "$ENV_FILE"
    restart_identity
    run_verification
}

[[ -f "$ENV_FILE" ]] || fail "Env file not found: $ENV_FILE"
[[ -f "$VERIFY_SCRIPT" ]] || fail "Verify script not found: $VERIFY_SCRIPT"
[[ -n "$TARGET_IDENTITY_DATABASE" ]] || fail "Target identity database name is empty."

SOURCE_CLOUD_DATABASE="$(dotenv_value "$ENV_FILE" MYSQL_DATABASE)"
SOURCE_CLOUD_DATABASE="${SOURCE_CLOUD_DATABASE:-alicia_cloud_storage}"
CURRENT_IDENTITY_DATABASE="$(dotenv_value "$ENV_FILE" ALICIA_IDENTITY_MYSQL_DATABASE)"
CURRENT_IDENTITY_DATABASE="${CURRENT_IDENTITY_DATABASE:-$SOURCE_CLOUD_DATABASE}"

require_database_name "Cloud database name" "$SOURCE_CLOUD_DATABASE"
require_database_name "Current identity database name" "$CURRENT_IDENTITY_DATABASE"
require_database_name "Target identity database name" "$TARGET_IDENTITY_DATABASE"

[[ "$TARGET_IDENTITY_DATABASE" != "$SOURCE_CLOUD_DATABASE" ]] \
    || fail "Target identity database must differ from cloud database: $SOURCE_CLOUD_DATABASE"
[[ "$CURRENT_IDENTITY_DATABASE" == "$SOURCE_CLOUD_DATABASE" ]] \
    || fail "Identity already appears split to $CURRENT_IDENTITY_DATABASE; refusing to overwrite."

for table_name in "${IDENTITY_TABLES[@]}"; do
    count="$(source_table_count "$table_name" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"
    [[ "$count" == "1" ]] || fail "Source identity table is missing: $SOURCE_CLOUD_DATABASE.$table_name"
done

existing_target_tables="$(target_table_count | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"
if [[ "${existing_target_tables:-0}" != "0" && "$ALLOW_EXISTING_TARGET" != "true" ]]; then
    fail "Target database $TARGET_IDENTITY_DATABASE already has $existing_target_tables table(s). Set ALICIA_IDENTITY_DATABASE_SPLIT_ALLOW_EXISTING_TARGET=true only after manual review."
fi

umask 077
mkdir -p "$OUTPUT_DIR"
TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
CANDIDATE_FILE="$OUTPUT_DIR/$TARGET_IDENTITY_DATABASE.candidate.$TIMESTAMP.env"
BACKUP_FILE="$OUTPUT_DIR/$TARGET_IDENTITY_DATABASE.backup.$TIMESTAMP.env"
TEMP_FILE="$CANDIDATE_FILE.tmp"

cp "$ENV_FILE" "$TEMP_FILE"
replace_or_append_env "$TEMP_FILE" "$CANDIDATE_FILE" ALICIA_IDENTITY_MYSQL_DATABASE "$TARGET_IDENTITY_DATABASE"
rm -f "$TEMP_FILE"
chmod 600 "$CANDIDATE_FILE"

source_db="$(quote_identifier "$SOURCE_CLOUD_DATABASE")"
target_db="$(quote_identifier "$TARGET_IDENTITY_DATABASE")"

printf 'Preparing identity database split:\n'
printf '  cloud database:    %s\n' "$SOURCE_CLOUD_DATABASE"
printf '  identity database: %s\n' "$TARGET_IDENTITY_DATABASE"
printf '  candidate env:     %s\n' "$CANDIDATE_FILE"
printf '  backup path:       %s\n' "$BACKUP_FILE"

mysql_root_exec "CREATE DATABASE IF NOT EXISTS $target_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

for table_name in "${IDENTITY_TABLES[@]}"; do
    table="$(quote_identifier "$table_name")"
    if [[ "$ALLOW_EXISTING_TARGET" == "true" ]]; then
        mysql_root_exec "DROP TABLE IF EXISTS $target_db.$table;"
    fi
    mysql_root_exec "CREATE TABLE $target_db.$table LIKE $source_db.$table;"
    mysql_root_exec "INSERT INTO $target_db.$table SELECT * FROM $source_db.$table;"
done
ok "identity tables copied to $TARGET_IDENTITY_DATABASE"

install -m 600 "$ENV_FILE" "$BACKUP_FILE"
install -m 600 "$CANDIDATE_FILE" "$ENV_FILE"

if ! restart_identity; then
    printf '[FAIL] identity restart failed after database split env replacement.\n' >&2
    if [[ "$ROLLBACK_ON_FAILURE" == "true" ]]; then
        rollback_env
    fi
    exit 1
fi

if ! run_verification; then
    printf '[FAIL] verification failed after identity database split.\n' >&2
    if [[ "$ROLLBACK_ON_FAILURE" == "true" ]]; then
        rollback_env
    fi
    exit 1
fi

ok "identity database split to $TARGET_IDENTITY_DATABASE verified"
printf '\nRollback command if needed:\n'
printf '  install -m 600 %s %s\n' "$BACKUP_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
