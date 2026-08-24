#!/usr/bin/env bash
set -Eeuo pipefail

ENV_FILE="${ALICIA_DROP_CLOUD_IDENTITY_RESIDUE_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_DROP_CLOUD_IDENTITY_RESIDUE_OUTPUT_DIR:-deploy/generated/identity-database-split}"
VERIFY_SCRIPT="${ALICIA_DROP_CLOUD_IDENTITY_RESIDUE_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"

IDENTITY_TABLES=(
    identity_user
    email_verification_code
    identity_refresh_token
    identity_audit_log
    identity_flyway_schema_history
)

CLOUD_RESIDUE_TABLES=(
    identity_refresh_token
    email_verification_code
    identity_audit_log
    identity_flyway_schema_history
    identity_user
    sys_user
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

mysql_dump_tables() {
    local database="$1"
    shift

    compose exec -T db sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"' sh "$database" "$@"
}

quote_identifier() {
    local value="$1"
    printf '`%s`' "${value//\`/\`\`}"
}

quote_sql_string() {
    local value="$1"
    printf "'%s'" "$value"
}

require_database_name() {
    local label="$1"
    local value="$2"

    [[ "$value" =~ ^[A-Za-z0-9_]+$ ]] \
        || fail "$label must contain only letters, numbers, and underscores: $value"
}

table_exists() {
    local database="$1"
    local table_name="$2"

    mysql_root_query "
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = $(quote_sql_string "$database")
  AND table_name = $(quote_sql_string "$table_name");
"
}

existing_cloud_residue_tables() {
    local table_name
    local count

    for table_name in "${CLOUD_RESIDUE_TABLES[@]}"; do
        count="$(table_exists "$CLOUD_DATABASE" "$table_name" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"
        if [[ "${count:-0}" == "1" ]]; then
            printf '%s\n' "$table_name"
        fi
    done
}

foreign_key_reference_count() {
    mysql_root_query "
SELECT COUNT(*)
FROM information_schema.referential_constraints
WHERE constraint_schema = $(quote_sql_string "$CLOUD_DATABASE")
  AND referenced_table_name IN ('identity_user', 'sys_user');
"
}

run_verification() {
    ALICIA_VERIFY_ENV_FILE="$ENV_FILE" \
    ALICIA_VERIFY_REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED=true \
    bash "$VERIFY_SCRIPT"
}

[[ -f "$ENV_FILE" ]] || fail "Env file not found: $ENV_FILE"
[[ -f "$VERIFY_SCRIPT" ]] || fail "Verify script not found: $VERIFY_SCRIPT"

CLOUD_DATABASE="$(dotenv_value "$ENV_FILE" MYSQL_DATABASE)"
CLOUD_DATABASE="${CLOUD_DATABASE:-alicia_cloud_storage}"
IDENTITY_DATABASE="$(dotenv_value "$ENV_FILE" ALICIA_IDENTITY_MYSQL_DATABASE)"

[[ -n "$IDENTITY_DATABASE" ]] \
    || fail "ALICIA_IDENTITY_MYSQL_DATABASE is not set in $ENV_FILE; split identity first."

require_database_name "Cloud database name" "$CLOUD_DATABASE"
require_database_name "Identity database name" "$IDENTITY_DATABASE"

[[ "$IDENTITY_DATABASE" != "$CLOUD_DATABASE" ]] \
    || fail "Identity database must differ from cloud database before dropping cloud identity residue."

for table_name in "${IDENTITY_TABLES[@]}"; do
    count="$(table_exists "$IDENTITY_DATABASE" "$table_name" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"
    [[ "$count" == "1" ]] || fail "Identity database is missing required table: $IDENTITY_DATABASE.$table_name"
done

fk_count="$(foreign_key_reference_count | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"
[[ "${fk_count:-0}" == "0" ]] \
    || fail "Cloud database still has $fk_count foreign key(s) referencing identity tables."

mapfile -t existing_tables < <(existing_cloud_residue_tables)
if [[ "${#existing_tables[@]}" -eq 0 ]]; then
    ok "cloud database already has no identity-owned residue tables"
    run_verification
    exit 0
fi

umask 077
mkdir -p "$OUTPUT_DIR"
TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
BACKUP_FILE="$OUTPUT_DIR/cloud-identity-residue.$TIMESTAMP.sql"

printf 'Dropping identity-owned residue from cloud database:\n'
printf '  cloud database:    %s\n' "$CLOUD_DATABASE"
printf '  identity database: %s\n' "$IDENTITY_DATABASE"
printf '  backup file:       %s\n' "$BACKUP_FILE"
printf '  tables:            %s\n' "${existing_tables[*]}"

mysql_dump_tables "$CLOUD_DATABASE" "${existing_tables[@]}" > "$BACKUP_FILE"
chmod 600 "$BACKUP_FILE"
ok "cloud identity residue backup written"

for table_name in "${existing_tables[@]}"; do
    mysql_root_exec "DROP TABLE IF EXISTS $(quote_identifier "$CLOUD_DATABASE").$(quote_identifier "$table_name");"
done
ok "cloud identity residue tables dropped"

run_verification

ok "cloud database identity residue cleanup verified"
printf '\nBackup retained at:\n'
printf '  %s\n' "$BACKUP_FILE"
