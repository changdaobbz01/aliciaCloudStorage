#!/usr/bin/env bash
set -Eeuo pipefail

CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
RAG_HEALTH_URL="${ALICIA_RAG_HEALTH_URL:-${PUBLIC_BASE_URL%/}/rag/api/health}"
RAG_DEPENDENCY_HEALTH_URL="${ALICIA_RAG_DEPENDENCY_HEALTH_URL:-${PUBLIC_BASE_URL%/}/rag/api/health/dependencies}"
RAG_ACTION_PLAN_CONTRACT_URL="${ALICIA_RAG_ACTION_PLAN_CONTRACT_URL:-${PUBLIC_BASE_URL%/}/rag/api/assistant/contracts/action-plan}"
CURL_TIMEOUT="${ALICIA_VERIFY_CURL_TIMEOUT_SECONDS:-12}"
STARTUP_WAIT_SECONDS="${ALICIA_VERIFY_STARTUP_WAIT_SECONDS:-90}"
STARTUP_WAIT_INTERVAL_SECONDS="${ALICIA_VERIFY_STARTUP_WAIT_INTERVAL_SECONDS:-2}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
SKIP_ADMIN_CHECK="${ALICIA_VERIFY_SKIP_ADMIN_CHECK:-false}"
SKIP_AUDIT_CHECK="${ALICIA_VERIFY_SKIP_AUDIT_CHECK:-false}"
SKIP_IDENTITY_FLYWAY_CHECK="${ALICIA_VERIFY_SKIP_IDENTITY_FLYWAY_CHECK:-false}"
SKIP_CACHE_CHECKS="${ALICIA_VERIFY_SKIP_CACHE_CHECKS:-false}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
ENV_FILE="${ALICIA_VERIFY_ENV_FILE:-.env}"
FORBID_PREVIOUS_KEY_ID="${ALICIA_VERIFY_FORBID_PREVIOUS_KEY_ID:-}"
FORBID_PREVIOUS_RSA_KEY_ID="${ALICIA_VERIFY_FORBID_PREVIOUS_RSA_KEY_ID:-}"
REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED="${ALICIA_VERIFY_REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED:-}"

CLOUD_BASE_URL="${CLOUD_BASE_URL%/}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_ARGS=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_ARGS+=(-k)
fi

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
    local key="$1"
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

extract_json_object_string() {
    local object_key="$1"
    local key="$2"
    sed -n "s/.*\"$object_key\"[[:space:]]*:[[:space:]]*{[^}]*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

extract_json_number() {
    local key="$1"
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" | head -n 1
}

dotenv_value() {
    local key="$1"
    local line
    if [[ ! -f "$ENV_FILE" ]]; then
        return 0
    fi

    line="$(sed -n "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*//p" "$ENV_FILE" | tail -n 1)"
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

previous_key_id_present() {
    local previous_keys="$1"
    local target_key_id="$2"
    local entry
    local entry_key_id
    local -a entries=()

    IFS=';' read -r -a entries <<< "$previous_keys"
    for entry in "${entries[@]}"; do
        entry="${entry#"${entry%%[![:space:]]*}"}"
        entry="${entry%"${entry##*[![:space:]]}"}"
        [[ "$entry" == *"="* ]] || continue
        entry_key_id="${entry%%=*}"
        entry_key_id="${entry_key_id#"${entry_key_id%%[![:space:]]*}"}"
        entry_key_id="${entry_key_id%"${entry_key_id##*[![:space:]]}"}"
        if [[ "$entry_key_id" == "$target_key_id" ]]; then
            return 0
        fi
    done

    return 1
}

base64url_decode() {
    local label="$1"
    local value="$2"
    local normalized="${value//-/+}"
    normalized="${normalized//_/\/}"

    case $((${#normalized} % 4)) in
        0) ;;
        2) normalized="${normalized}==" ;;
        3) normalized="${normalized}=" ;;
        *) fail "$label has invalid base64url padding" ;;
    esac

    printf '%s' "$normalized" | base64 -d 2>/dev/null || fail "$label is not valid base64url"
}

require_jwt_token() {
    local label="$1"
    local token="$2"
    local without_dots="${token//./}"
    local dot_count=$((${#token} - ${#without_dots}))

    if [[ "$dot_count" -ne 2 ]]; then
        fail "$label is not a JWT access token"
    fi

    ok "$label is JWT-shaped"
}

require_jwt_metadata() {
    local label="$1"
    local token="$2"
    local encoded_header
    local encoded_payload
    local signature
    local header_json
    local payload_json
    local algorithm
    local token_type
    local token_key_id
    local issuer
    local audience
    local subject
    local expires_at
    local token_version

    IFS='.' read -r encoded_header encoded_payload signature <<< "$token"
    header_json="$(base64url_decode "$label header" "$encoded_header")"
    payload_json="$(base64url_decode "$label payload" "$encoded_payload")"

    algorithm="$(printf '%s' "$header_json" | extract_json_string alg)"
    token_type="$(printf '%s' "$header_json" | extract_json_string typ)"
    token_key_id="$(printf '%s' "$header_json" | extract_json_string kid)"
    issuer="$(printf '%s' "$payload_json" | extract_json_string iss)"
    audience="$(printf '%s' "$payload_json" | extract_json_string aud)"
    subject="$(printf '%s' "$payload_json" | extract_json_string sub)"
    expires_at="$(printf '%s' "$payload_json" | extract_json_number exp)"
    token_version="$(printf '%s' "$payload_json" | extract_json_number ver)"

    [[ "$algorithm" == "$EXPECTED_TOKEN_ALGORITHM" ]] || fail "$label alg expected $EXPECTED_TOKEN_ALGORITHM, got ${algorithm:-<missing>}"
    [[ "$token_type" == "JWT" ]] || fail "$label typ expected JWT, got ${token_type:-<missing>}"
    [[ "$token_key_id" == "$EXPECTED_TOKEN_KEY_ID" ]] || fail "$label kid expected $EXPECTED_TOKEN_KEY_ID, got ${token_key_id:-<missing>}"
    [[ "$issuer" == "$EXPECTED_TOKEN_ISSUER" ]] || fail "$label iss expected $EXPECTED_TOKEN_ISSUER, got ${issuer:-<missing>}"
    [[ "$audience" == "$EXPECTED_TOKEN_AUDIENCE" ]] || fail "$label aud expected $EXPECTED_TOKEN_AUDIENCE, got ${audience:-<missing>}"
    [[ -n "$subject" ]] || fail "$label sub is missing"
    [[ -n "$expires_at" ]] || fail "$label exp is missing"
    [[ -n "$token_version" ]] || fail "$label ver is missing"

    ok "$label metadata matches expected iss/aud/kid"
}

jwt_number_claim() {
    local label="$1"
    local token="$2"
    local claim="$3"
    local encoded_header
    local encoded_payload
    local signature
    local payload_json

    IFS='.' read -r encoded_header encoded_payload signature <<< "$token"
    payload_json="$(base64url_decode "$label payload" "$encoded_payload")"
    printf '%s' "$payload_json" | extract_json_number "$claim"
}

curl_ok() {
    local label="$1"
    shift

    curl -fsS "${CURL_ARGS[@]}" "$@" >/dev/null
    ok "$label"
}

curl_ok_with_wait() {
    local label="$1"
    shift

    local wait_seconds="$STARTUP_WAIT_SECONDS"
    local interval_seconds="$STARTUP_WAIT_INTERVAL_SECONDS"
    local deadline
    local output=""
    local announced=false

    [[ "$wait_seconds" =~ ^[0-9]+$ ]] || fail "ALICIA_VERIFY_STARTUP_WAIT_SECONDS must be a non-negative integer."
    [[ "$interval_seconds" =~ ^[0-9]+$ ]] || fail "ALICIA_VERIFY_STARTUP_WAIT_INTERVAL_SECONDS must be a non-negative integer."
    [[ "$interval_seconds" -gt 0 ]] || interval_seconds=1

    deadline=$((SECONDS + wait_seconds))

    while true; do
        if output="$(curl -fsS "${CURL_ARGS[@]}" "$@" 2>&1 >/dev/null)"; then
            ok "$label"
            return 0
        fi

        if [[ "$SECONDS" -ge "$deadline" ]]; then
            printf '[FAIL] %s did not become available within %s seconds\n' "$label" "$wait_seconds" >&2
            [[ -z "$output" ]] || printf '%s\n' "$output" >&2
            exit 1
        fi

        if [[ "$announced" == "false" ]]; then
            printf 'Waiting for %s...\n' "$label" >&2
            announced=true
        fi
        sleep "$interval_seconds"
    done
}

curl_body() {
    local label="$1"
    shift

    curl -fsS "${CURL_ARGS[@]}" "$@" || fail "$label body request failed"
}

curl_headers() {
    local label="$1"
    shift

    curl -fsS "${CURL_ARGS[@]}" -I "$@" || fail "$label headers request failed"
}

expect_no_store_index() {
    local label="$1"
    local url="$2"
    local headers

    headers="$(curl_headers "$label" "$url")"
    printf '%s' "$headers" | grep -qi '^Cache-Control: .*no-store' \
        || fail "$label did not expose no-store cache control"

    ok "$label no-store cache"
}

expect_spa_shell() {
    local label="$1"
    local url="$2"
    local expected_title="$3"
    local expected_asset_prefix="$4"
    local body

    body="$(curl_body "$label" "$url")"
    printf '%s' "$body" | grep -q '<div id="root">' \
        || fail "$label did not return an Alicia SPA shell"
    printf '%s' "$body" | grep -Fq "<title>$expected_title</title>" \
        || fail "$label did not return the expected $expected_title shell"
    printf '%s' "$body" | grep -Fq "$expected_asset_prefix" \
        || fail "$label did not reference built frontend assets under $expected_asset_prefix"

    ok "$label SPA shell"
}

expect_spa_asset_cache() {
    local label="$1"
    local origin_url="$2"
    local index_url="$3"
    local body
    local asset_path
    local headers

    body="$(curl_body "$label index" "$index_url")"
    asset_path="$(printf '%s' "$body" | sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' | head -n 1)"
    [[ -n "$asset_path" ]] || fail "$label could not find built JS asset"
    [[ "$asset_path" == /* ]] || fail "$label built JS asset path must be absolute"

    headers="$(curl_headers "$label asset" "$origin_url$asset_path")"
    printf '%s' "$headers" | grep -qi '^Cache-Control: .*immutable' \
        || fail "$label built asset did not expose immutable cache control"

    ok "$label built asset cache"
}

expect_redirect_location() {
    local label="$1"
    local expected_status="$2"
    local expected_location="$3"
    local url="$4"

    local header_file
    local status
    local location
    local absolute_expected_location
    header_file="$(mktemp)"
    status="$(curl "${CURL_ARGS[@]}" -I -o /dev/null -D "$header_file" -w '%{http_code}' "$url" || true)"
    location="$(awk 'BEGIN { IGNORECASE = 1 } /^location:/ { sub(/^[^:]+:[[:space:]]*/, ""); sub(/\r$/, ""); print; exit }' "$header_file")"
    absolute_expected_location="$expected_location"
    if [[ "$expected_location" == /* ]]; then
        absolute_expected_location="${PUBLIC_BASE_URL}${expected_location}"
    fi

    if [[ "$status" != "$expected_status" ]]; then
        printf '[FAIL] %s: expected HTTP %s, got %s\n' "$label" "$expected_status" "$status" >&2
        sed -n '1,20p' "$header_file" >&2 || true
        rm -f "$header_file"
        exit 1
    fi

    if [[ "$location" != "$expected_location" && "$location" != "$absolute_expected_location" ]]; then
        printf '[FAIL] %s: expected Location %s, got %s\n' "$label" "$expected_location" "${location:-<missing>}" >&2
        sed -n '1,20p' "$header_file" >&2 || true
        rm -f "$header_file"
        exit 1
    fi

    rm -f "$header_file"
    ok "$label"
}

expect_status() {
    local label="$1"
    local expected="$2"
    shift 2

    local body_file
    local status
    body_file="$(mktemp)"
    status="$(curl "${CURL_ARGS[@]}" -o "$body_file" -w '%{http_code}' "$@" || true)"

    if [[ "$status" != "$expected" ]]; then
        printf '[FAIL] %s: expected HTTP %s, got %s\n' "$label" "$expected" "$status" >&2
        sed -n '1,20p' "$body_file" >&2 || true
        rm -f "$body_file"
        exit 1
    fi

    rm -f "$body_file"
    ok "$label"
}

expect_recent_audit_event() {
    local label="$1"
    local event_type="$2"
    local outcome="$3"
    local user_id="$4"
    local detail="$5"
    local count
    local audit_sql

    audit_sql="
SELECT COUNT(*)
FROM identity_audit_log
WHERE event_type = '$event_type'
  AND outcome = '$outcome'
  AND actor_user_id = $user_id
  AND target_user_id = $user_id
  AND detail = '$detail'
  AND created_at >= NOW() - INTERVAL 10 MINUTE;
"
    count="$(mysql_query "$IDENTITY_MYSQL_DATABASE" "$audit_sql")" || fail "$label query failed"
    count="$(printf '%s' "$count" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"

    if [[ -z "$count" || "$count" -lt 1 ]]; then
        fail "$label was not found in recent identity audit rows"
    fi

    ok "$label"
}

verify_identity_user_table_boundary() {
    local counts
    local identity_user_count
    local sys_user_count
    local table_boundary_sql

    table_boundary_sql="$(cat <<'SQL'
SELECT
  SUM(table_name = 'identity_user') AS identity_user_count,
  SUM(table_name = 'sys_user') AS sys_user_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('identity_user', 'sys_user');
SQL
)"

    counts="$(mysql_query "$IDENTITY_MYSQL_DATABASE" "$table_boundary_sql")" \
        || fail "identity user table boundary query failed"
    counts="$(printf '%s' "$counts" | tr -d '\r' | tail -n 1)"
    identity_user_count="$(printf '%s' "$counts" | awk '{print $1}')"
    sys_user_count="$(printf '%s' "$counts" | awk '{print $2}')"
    identity_user_count="${identity_user_count:-0}"
    sys_user_count="${sys_user_count:-0}"

    [[ "$identity_user_count" == "1" ]] || fail "identity_user table is missing"
    [[ "$sys_user_count" == "0" ]] || fail "legacy sys_user table is still present"

    ok "identity user table boundary finalized"
}

verify_no_identity_table_foreign_keys() {
    local count
    local foreign_key_boundary_sql

    foreign_key_boundary_sql="$(cat <<'SQL'
SELECT COUNT(*)
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IN ('identity_user', 'sys_user');
SQL
)"

    count="$(mysql_query "$CLOUD_MYSQL_DATABASE" "$foreign_key_boundary_sql")" \
        || fail "identity foreign key boundary query failed"
    count="$(printf '%s' "$count" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"

    [[ "${count:-0}" == "0" ]] || fail "identity table is still referenced by $count database foreign key(s)"

    ok "identity table foreign key boundary finalized"
}

verify_cloud_identity_tables_removed() {
    local residue
    local table_residue_sql

    if [[ "$CLOUD_MYSQL_DATABASE" == "$IDENTITY_MYSQL_DATABASE" ]]; then
        printf '[SKIP] cloud identity residue table check; identity database is not split\n'
        return 0
    fi

    table_residue_sql="$(cat <<'SQL'
SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ','), '')
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'sys_user',
      'identity_user',
      'email_verification_code',
      'identity_refresh_token',
      'identity_audit_log',
      'identity_flyway_schema_history'
  );
SQL
)"

    residue="$(mysql_query "$CLOUD_MYSQL_DATABASE" "$table_residue_sql")" \
        || fail "cloud identity residue table query failed"
    residue="$(printf '%s' "$residue" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"

    [[ -z "$residue" ]] || fail "cloud database still contains identity-owned table(s): $residue"

    ok "cloud database identity residue removed"
}

verify_cloud_object_cleanup_table() {
    local count
    local cleanup_table_sql

    cleanup_table_sql="$(cat <<'SQL'
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'cloud_object_cleanup_task';
SQL
)"

    count="$(mysql_query "$CLOUD_MYSQL_DATABASE" "$cleanup_table_sql")" \
        || fail "cloud object cleanup table query failed"
    count="$(printf '%s' "$count" | tr -d '\r' | tail -n 1 | tr -d '[:space:]')"

    [[ "${count:-0}" == "1" ]] || fail "cloud object cleanup task table is missing"

    ok "cloud object cleanup task table migrated"
}

curl_json_or_fail() {
    local label="$1"
    shift

    local body_file
    local status
    body_file="$(mktemp)"
    status="$(curl "${CURL_ARGS[@]}" -o "$body_file" -w '%{http_code}' "$@" || true)"

    if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
        printf '[FAIL] %s: expected HTTP 2xx, got %s\n' "$label" "$status" >&2
        sed -n '1,20p' "$body_file" >&2 || true
        rm -f "$body_file"
        exit 1
    fi

    cat "$body_file"
    rm -f "$body_file"
}

expect_audit_log_filter() {
    local label="$1"
    local event_type="$2"
    local outcome="$3"
    local target_user_id="$4"
    local response
    local total_items
    local returned_event_type
    local returned_outcome
    local returned_target_user_id

    response="$(curl_json_or_fail "$label" \
        "$PUBLIC_BASE_URL/api/identity/admin/audit-logs?eventType=$event_type&outcome=$outcome&targetUserId=$target_user_id&size=5" \
        -H "Authorization: Bearer $TOKEN")"
    total_items="$(printf '%s' "$response" | tr -d '\n' | extract_json_number totalItems)"
    returned_event_type="$(printf '%s' "$response" | tr -d '\n' | extract_json_string eventType)"
    returned_outcome="$(printf '%s' "$response" | tr -d '\n' | extract_json_string outcome)"
    returned_target_user_id="$(printf '%s' "$response" | tr -d '\n' | extract_json_number targetUserId)"

    if [[ -z "$total_items" || "$total_items" -lt 1 ]]; then
        fail "$label returned no matching audit rows"
    fi
    [[ "$returned_event_type" == "$event_type" ]] || fail "$label eventType expected $event_type, got ${returned_event_type:-<missing>}"
    [[ "$returned_outcome" == "$outcome" ]] || fail "$label outcome expected $outcome, got ${returned_outcome:-<missing>}"
    [[ "$returned_target_user_id" == "$target_user_id" ]] || fail "$label targetUserId expected $target_user_id, got ${returned_target_user_id:-<missing>}"

    ok "$label"
}

expect_cloud_identity_gateway_telemetry() {
    local response
    local compact

    response="$(curl_json_or_fail "cloud dependency health identity gateway telemetry" \
        "$CLOUD_BASE_URL/api/health/dependencies")"
    compact="$(printf '%s' "$response" | tr -d '\n')"

    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"successCount"[[:space:]]*:[[:space:]]*[1-9][0-9]*' \
        || fail "cloud dependency health did not expose auth.me identity gateway success count"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"totalCount"[[:space:]]*:[[:space:]]*[1-9][0-9]*' \
        || fail "cloud dependency health did not expose auth.me identity gateway total count"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"consecutiveFailureCount"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose auth.me identity gateway consecutive failure count"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"averageDurationMs"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose auth.me identity gateway average duration"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"maxDurationMs"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose auth.me identity gateway max duration"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"lastObservedAt"[[:space:]]*:[[:space:]]*"' \
        || fail "cloud dependency health did not expose auth.me identity gateway last observed timestamp"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"auth.me"[^}]*"lastSuccessAt"[[:space:]]*:[[:space:]]*"' \
        || fail "cloud dependency health did not expose auth.me identity gateway last success timestamp"
    printf '%s' "$compact" | grep -q '"currentUserCache"[[:space:]]*:[[:space:]]*{[^}]*"enabled"[[:space:]]*:[[:space:]]*\(true\|false\)' \
        || fail "cloud dependency health did not expose identity current-user cache enabled flag"
    printf '%s' "$compact" | grep -q '"currentUserCache"[[:space:]]*:[[:space:]]*{[^}]*"ttlMillis"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose identity current-user cache ttl"
    printf '%s' "$compact" | grep -q '"currentUserCache"[[:space:]]*:[[:space:]]*{[^}]*"maxEntries"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose identity current-user cache max entries"
    printf '%s' "$compact" | grep -q '"currentUserCache"[[:space:]]*:[[:space:]]*{[^}]*"size"[[:space:]]*:[[:space:]]*[0-9][0-9]*' \
        || fail "cloud dependency health did not expose identity current-user cache size"

    ok "cloud dependency health exposes identity gateway telemetry"
}

expect_rag_dependency_health_telemetry() {
    local response
    local compact

    response="$(curl_json_or_fail "rag dependency health telemetry" \
        "$RAG_DEPENDENCY_HEALTH_URL")"
    compact="$(printf '%s' "$response" | tr -d '\n')"

    printf '%s' "$compact" | grep -q '"service"[[:space:]]*:[[:space:]]*"rag-service"' \
        || fail "rag dependency health did not expose rag-service"
    printf '%s' "$compact" | grep -q '"identity"[[:space:]]*:[[:space:]]*{[^}]*"available"[[:space:]]*:[[:space:]]*true' \
        || fail "rag dependency health did not report identity as available"
    printf '%s' "$compact" | grep -q '"storage"[[:space:]]*:[[:space:]]*{[^}]*"available"[[:space:]]*:[[:space:]]*true' \
        || fail "rag dependency health did not report storage as available"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"identity.health"[^}]*"successCount"[[:space:]]*:[[:space:]]*[1-9][0-9]*' \
        || fail "rag dependency health did not expose identity.health success count"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"identity.auth.me"[^}]*"successCount"[[:space:]]*:[[:space:]]*[1-9][0-9]*' \
        || fail "rag dependency health did not expose identity.auth.me success count"
    printf '%s' "$compact" | grep -q '"operation"[[:space:]]*:[[:space:]]*"storage.health"[^}]*"successCount"[[:space:]]*:[[:space:]]*[1-9][0-9]*' \
        || fail "rag dependency health did not expose storage.health success count"

    ok "rag dependency health exposes identity and storage telemetry"
}

expect_cloud_application_role() {
    local label="$1"
    local response="$2"
    local expected_role="${3:-}"

    expect_identity_response_application_role "$label" "$response" "cloud" "$expected_role"
}

expect_rag_application_role() {
    local label="$1"
    local response="$2"
    local expected_role="${3:-}"

    expect_identity_response_application_role "$label" "$response" "rag" "$expected_role"
}

expect_identity_response_application_role() {
    local label="$1"
    local response="$2"
    local app_code="$3"
    local expected_role="${4:-}"
    local role

    role="$(printf '%s' "$response" | tr -d '\n' | extract_json_object_string appRoles "$app_code")"
    if [[ -z "$role" ]]; then
        fail "$label did not expose appRoles.$app_code"
    fi

    if [[ -n "$expected_role" && "$role" != "$expected_role" ]]; then
        fail "$label appRoles.$app_code expected $expected_role, got $role"
    fi

    ok "$label exposes $app_code application role $role"
}

expect_application_role_entry() {
    local label="$1"
    local response="$2"
    local app_code="$3"
    local expected_role="$4"
    local compact_response

    compact_response="$(printf '%s' "$response" | tr -d '\n')"
    printf '%s' "$compact_response" \
        | grep -q "\"appCode\"[[:space:]]*:[[:space:]]*\"$app_code\"[^}]*\"roleCode\"[[:space:]]*:[[:space:]]*\"$expected_role\"" \
        || fail "$label did not return $app_code application role $expected_role"

    ok "$label returns $app_code application role $expected_role"
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

mysql_query() {
    local database="$1"
    local sql="$2"

    compose exec -T db sh -lc 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "$2"' sh "$database" "$sql"
}

mysql_exec() {
    local database="$1"
    local sql="$2"

    compose exec -T db sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "$2"' sh "$database" "$sql"
}

ACCOUNT="${ALICIA_VERIFY_ACCOUNT:-}"
PASSWORD="${ALICIA_VERIFY_PASSWORD:-}"
DOTENV_MYSQL_DATABASE="$(dotenv_value MYSQL_DATABASE)"
DOTENV_IDENTITY_MYSQL_DATABASE="$(dotenv_value ALICIA_IDENTITY_MYSQL_DATABASE)"
DOTENV_TOKEN_ISSUER="$(dotenv_value ALICIA_AUTH_TOKEN_ISSUER)"
DOTENV_TOKEN_AUDIENCE="$(dotenv_value ALICIA_AUTH_TOKEN_AUDIENCE)"
DOTENV_TOKEN_KEY_ID="$(dotenv_value ALICIA_AUTH_TOKEN_KEY_ID)"
DOTENV_TOKEN_ALGORITHM="$(dotenv_value ALICIA_AUTH_TOKEN_ALGORITHM)"
EXPECTED_TOKEN_ISSUER="${ALICIA_VERIFY_TOKEN_ISSUER:-${DOTENV_TOKEN_ISSUER:-https://windwindwind-alicia.cn}}"
EXPECTED_TOKEN_AUDIENCE="${ALICIA_VERIFY_TOKEN_AUDIENCE:-${DOTENV_TOKEN_AUDIENCE:-alicia-tools}}"
EXPECTED_TOKEN_KEY_ID="${ALICIA_VERIFY_TOKEN_KEY_ID:-${DOTENV_TOKEN_KEY_ID:-alicia-hs256-v1}}"
EXPECTED_TOKEN_ALGORITHM="${ALICIA_VERIFY_TOKEN_ALGORITHM:-${DOTENV_TOKEN_ALGORITHM:-HS256}}"
EXPECTED_TOKEN_ALGORITHM="$(printf '%s' "$EXPECTED_TOKEN_ALGORITHM" | tr '[:lower:]' '[:upper:]')"
DOTENV_PREVIOUS_KEYS="$(dotenv_value ALICIA_AUTH_TOKEN_PREVIOUS_KEYS)"
DOTENV_PREVIOUS_RSA_PUBLIC_KEYS="$(dotenv_value ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS)"
CLOUD_MYSQL_DATABASE="${ALICIA_VERIFY_CLOUD_MYSQL_DATABASE:-${DOTENV_MYSQL_DATABASE:-alicia_cloud_storage}}"
IDENTITY_MYSQL_DATABASE="${ALICIA_VERIFY_IDENTITY_MYSQL_DATABASE:-${DOTENV_IDENTITY_MYSQL_DATABASE:-$CLOUD_MYSQL_DATABASE}}"
EXPECTED_CLOUD_APP_ROLE="${ALICIA_VERIFY_CLOUD_APP_ROLE:-}"
if [[ -z "$EXPECTED_CLOUD_APP_ROLE" && "$SKIP_ADMIN_CHECK" != "true" ]]; then
    EXPECTED_CLOUD_APP_ROLE="CLOUD_ADMIN"
fi
EXPECTED_RAG_APP_ROLE="${ALICIA_VERIFY_RAG_APP_ROLE:-}"
if [[ -z "$EXPECTED_RAG_APP_ROLE" && "$SKIP_ADMIN_CHECK" != "true" ]]; then
    EXPECTED_RAG_APP_ROLE="RAG_ADMIN"
fi
if [[ -z "$REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED" ]]; then
    if [[ "$CLOUD_MYSQL_DATABASE" == "$IDENTITY_MYSQL_DATABASE" ]]; then
        REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED=false
    else
        REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED=true
    fi
fi

printf 'Verifying Alicia identity/cloud route boundary...\n'
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"
printf 'Cloud MySQL database: %s\n' "$CLOUD_MYSQL_DATABASE"
printf 'Identity MySQL database: %s\n' "$IDENTITY_MYSQL_DATABASE"
printf 'Expected JWT: alg=%s iss=%s aud=%s kid=%s\n' "$EXPECTED_TOKEN_ALGORITHM" "$EXPECTED_TOKEN_ISSUER" "$EXPECTED_TOKEN_AUDIENCE" "$EXPECTED_TOKEN_KEY_ID"

if [[ -n "$FORBID_PREVIOUS_KEY_ID" ]]; then
    if previous_key_id_present "$DOTENV_PREVIOUS_KEYS" "$FORBID_PREVIOUS_KEY_ID"; then
        fail "forbidden previous JWT key id is still present in $ENV_FILE: $FORBID_PREVIOUS_KEY_ID"
    fi
    ok "forbidden previous JWT key id is absent from env"
fi

if [[ -n "$FORBID_PREVIOUS_RSA_KEY_ID" ]]; then
    if previous_key_id_present "$DOTENV_PREVIOUS_RSA_PUBLIC_KEYS" "$FORBID_PREVIOUS_RSA_KEY_ID"; then
        fail "forbidden previous RSA JWT key id is still present in $ENV_FILE: $FORBID_PREVIOUS_RSA_KEY_ID"
    fi
    ok "forbidden previous RSA JWT key id is absent from env"
fi

curl_ok_with_wait "cloud health direct" "$CLOUD_BASE_URL/api/health"
curl_ok_with_wait "identity health direct" "$IDENTITY_BASE_URL/api/identity/health"
curl_ok "cloud dependency health direct" "$CLOUD_BASE_URL/api/health/dependencies"
curl_ok "identity dependency health direct" "$IDENTITY_BASE_URL/api/identity/health/dependencies"
curl_ok_with_wait "cloud health through frontend" "$PUBLIC_BASE_URL/api/health"
curl_ok_with_wait "identity health through frontend" "$PUBLIC_BASE_URL/api/identity/health"
curl_ok "cloud dependency health through frontend" "$PUBLIC_BASE_URL/api/health/dependencies"
curl_ok "identity dependency health through frontend" "$PUBLIC_BASE_URL/api/identity/health/dependencies"
jwks_response="$(curl_json_or_fail "identity jwks endpoint" "$PUBLIC_BASE_URL/api/identity/.well-known/jwks.json")"
if [[ "$EXPECTED_TOKEN_ALGORITHM" == "RS256" ]]; then
    jwks_key_id="$(printf '%s' "$jwks_response" | tr -d '\n' | extract_json_string kid)"
    jwks_algorithm="$(printf '%s' "$jwks_response" | tr -d '\n' | extract_json_string alg)"
    [[ "$jwks_key_id" == "$EXPECTED_TOKEN_KEY_ID" ]] || fail "identity jwks kid expected $EXPECTED_TOKEN_KEY_ID, got ${jwks_key_id:-<missing>}"
    [[ "$jwks_algorithm" == "RS256" ]] || fail "identity jwks alg expected RS256, got ${jwks_algorithm:-<missing>}"
    ok "identity jwks exposes current RSA key"
else
    ok "identity jwks endpoint"
fi
unset jwks_response
curl_ok "rag health through frontend" "$RAG_HEALTH_URL"
curl_ok "rag dependency health through frontend" "$RAG_DEPENDENCY_HEALTH_URL"
expect_status "rag assistant access requires identity token" 401 \
    "$PUBLIC_BASE_URL/rag/api/assistant/auth/access"
expect_status "rag assistant contract requires identity token" 401 \
    "$RAG_ACTION_PLAN_CONTRACT_URL"
curl_ok "main site home entry" -I "$PUBLIC_BASE_URL/"
curl_ok "main site login entry" -I "$PUBLIC_BASE_URL/login"
curl_ok "main site login returnTo entry" -I "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/"
curl_ok "main site expired login entry" -I "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/&reason=session-expired"
expect_spa_shell "main site home entry" "$PUBLIC_BASE_URL/" "Alicia Tools" "/assets/index-"
expect_spa_shell "main site login entry" "$PUBLIC_BASE_URL/login" "Alicia Tools" "/assets/index-"
expect_spa_shell "main site login returnTo entry" "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/" "Alicia Tools" "/assets/index-"
expect_spa_shell "main site expired login entry" "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/&reason=session-expired" "Alicia Tools" "/assets/index-"
assetlinks_response="$(curl_json_or_fail "android asset links endpoint" "$PUBLIC_BASE_URL/.well-known/assetlinks.json")"
printf '%s' "$assetlinks_response" | grep -Eq '"package_name"[[:space:]]*:[[:space:]]*"com\.alicia\.cloudstorage\.phone"' \
    || fail "android asset links must expose official package com.alicia.cloudstorage.phone"
if printf '%s' "$assetlinks_response" | grep -Eq 'com\.alicia\.cloudstorage\.phone\.add'; then
    fail "android asset links must not expose the old Android test package"
fi
ok "android asset links official package"
unset assetlinks_response
expect_redirect_location "console gateway bare path redirects to canonical slash" 308 "/console/" "$PUBLIC_BASE_URL/console"
curl_ok "console gateway entry" -I "$PUBLIC_BASE_URL/console/"
curl_ok "identity console frontend entry" -I "$PUBLIC_BASE_URL/console/identity/"
curl_ok "identity console users route" -I "$PUBLIC_BASE_URL/console/identity/users"
curl_ok "identity console roles route" -I "$PUBLIC_BASE_URL/console/identity/roles"
curl_ok "identity console sessions route" -I "$PUBLIC_BASE_URL/console/identity/sessions"
curl_ok "identity console audit route" -I "$PUBLIC_BASE_URL/console/identity/audit"
expect_spa_shell "console gateway entry" "$PUBLIC_BASE_URL/console/" "Alicia Tools" "/assets/index-"
expect_spa_shell "identity console frontend entry" "$PUBLIC_BASE_URL/console/identity/" "Alicia 身份后台" "/console/identity/assets/index-"
expect_spa_shell "identity console users route" "$PUBLIC_BASE_URL/console/identity/users" "Alicia 身份后台" "/console/identity/assets/index-"
expect_spa_shell "identity console roles route" "$PUBLIC_BASE_URL/console/identity/roles" "Alicia 身份后台" "/console/identity/assets/index-"
expect_spa_shell "identity console sessions route" "$PUBLIC_BASE_URL/console/identity/sessions" "Alicia 身份后台" "/console/identity/assets/index-"
expect_spa_shell "identity console audit route" "$PUBLIC_BASE_URL/console/identity/audit" "Alicia 身份后台" "/console/identity/assets/index-"
expect_redirect_location "cloudPan bare path redirects to canonical slash" 308 "/cloudPan/" "$PUBLIC_BASE_URL/cloudPan"
expect_redirect_location "cloudPan legacy login redirects to unified login" 308 "/login?returnTo=/cloudPan/" "$PUBLIC_BASE_URL/cloudPan/login"
curl_ok "cloudPan frontend entry" -I "$PUBLIC_BASE_URL/cloudPan/"
curl_ok "cloudPan share route" -I "$PUBLIC_BASE_URL/cloudPan/share/cache-probe"
expect_redirect_location "cloud console bare path redirects to canonical slash" 308 "/console/cloud/" "$PUBLIC_BASE_URL/console/cloud"
curl_ok "cloud console frontend entry" -I "$PUBLIC_BASE_URL/console/cloud/"
curl_ok "cloud console users route" -I "$PUBLIC_BASE_URL/console/cloud/users"
curl_ok "cloud console operations route" -I "$PUBLIC_BASE_URL/console/cloud/operations"
curl_ok "cloud console app package route" -I "$PUBLIC_BASE_URL/console/cloud/app-package"
expect_spa_shell "cloudPan frontend entry" "$PUBLIC_BASE_URL/cloudPan/" "Alicia 云盘" "/cloudPan/assets/index-"
expect_spa_shell "cloud console frontend entry" "$PUBLIC_BASE_URL/console/cloud/" "Alicia 云盘后台" "/console/cloud/assets/index-"
expect_spa_shell "cloud console users route" "$PUBLIC_BASE_URL/console/cloud/users" "Alicia 云盘后台" "/console/cloud/assets/index-"
expect_spa_shell "cloud console operations route" "$PUBLIC_BASE_URL/console/cloud/operations" "Alicia 云盘后台" "/console/cloud/assets/index-"
expect_spa_shell "cloud console app package route" "$PUBLIC_BASE_URL/console/cloud/app-package" "Alicia 云盘后台" "/console/cloud/assets/index-"
expect_spa_shell "cloudPan share route" "$PUBLIC_BASE_URL/cloudPan/share/cache-probe" "Alicia 云盘" "/cloudPan/assets/index-"

if [[ "$SKIP_CACHE_CHECKS" != "true" ]]; then
    expect_no_store_index "main site index" "$PUBLIC_BASE_URL/"
    expect_no_store_index "main site login index" "$PUBLIC_BASE_URL/login"
    expect_no_store_index "main site login returnTo index" "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/"
    expect_no_store_index "main site expired login index" "$PUBLIC_BASE_URL/login?returnTo=/cloudPan/&reason=session-expired"
    expect_no_store_index "console gateway index" "$PUBLIC_BASE_URL/console/"
    expect_no_store_index "identity console index" "$PUBLIC_BASE_URL/console/identity/"
    expect_no_store_index "identity console users index" "$PUBLIC_BASE_URL/console/identity/users"
    expect_no_store_index "identity console roles index" "$PUBLIC_BASE_URL/console/identity/roles"
    expect_no_store_index "identity console sessions index" "$PUBLIC_BASE_URL/console/identity/sessions"
    expect_no_store_index "identity console audit index" "$PUBLIC_BASE_URL/console/identity/audit"
    expect_no_store_index "cloudPan index" "$PUBLIC_BASE_URL/cloudPan/"
    expect_no_store_index "cloudPan share index" "$PUBLIC_BASE_URL/cloudPan/share/cache-probe"
    expect_no_store_index "cloud console index" "$PUBLIC_BASE_URL/console/cloud/"
    expect_no_store_index "cloud console users index" "$PUBLIC_BASE_URL/console/cloud/users"
    expect_no_store_index "cloud console operations index" "$PUBLIC_BASE_URL/console/cloud/operations"
    expect_no_store_index "cloud console app package index" "$PUBLIC_BASE_URL/console/cloud/app-package"
    expect_spa_asset_cache "main site" "$PUBLIC_BASE_URL" "$PUBLIC_BASE_URL/"
    expect_spa_asset_cache "console gateway" "$PUBLIC_BASE_URL" "$PUBLIC_BASE_URL/console/"
    expect_spa_asset_cache "identity console" "$PUBLIC_BASE_URL" "$PUBLIC_BASE_URL/console/identity/"
    expect_spa_asset_cache "cloudPan" "$PUBLIC_BASE_URL" "$PUBLIC_BASE_URL/cloudPan/"
    expect_spa_asset_cache "cloud console" "$PUBLIC_BASE_URL" "$PUBLIC_BASE_URL/console/cloud/"
fi

if [[ -z "$ACCOUNT" ]]; then
    read -r -p "Identity account/email/phone: " ACCOUNT
fi

if [[ -z "$PASSWORD" ]]; then
    read -r -s -p "Identity password: " PASSWORD
    printf '\n'
fi

login_payload="$(printf '{"identifier":"%s","password":"%s"}' "$(json_escape "$ACCOUNT")" "$(json_escape "$PASSWORD")")"
login_response="$(curl_json_or_fail "identity login" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
    -H "Content-Type: application/json" \
    --data-binary "$login_payload")"

extra_login_response="$(curl_json_or_fail "identity temporary session login" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
    -H "Content-Type: application/json" \
    --data-binary "$login_payload")"
unset PASSWORD login_payload

TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string token)"
if [[ -z "$TOKEN" ]]; then
    fail "identity login did not return a token"
fi
ok "identity login issued token (${#TOKEN} chars)"
require_jwt_token "identity login token" "$TOKEN"
require_jwt_metadata "identity login token" "$TOKEN"
REFRESH_TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string refreshToken)"
if [[ -z "$REFRESH_TOKEN" ]]; then
    fail "identity login did not return a refresh token"
fi
ok "identity login issued refresh token (${#REFRESH_TOKEN} chars)"
expect_cloud_application_role "identity login user" "$login_response" "$EXPECTED_CLOUD_APP_ROLE"
expect_rag_application_role "identity login user" "$login_response" "$EXPECTED_RAG_APP_ROLE"

EXTRA_TOKEN="$(printf '%s' "$extra_login_response" | tr -d '\n' | extract_json_string token)"
if [[ -z "$EXTRA_TOKEN" ]]; then
    fail "identity temporary session login did not return a token"
fi
require_jwt_token "identity temporary session token" "$EXTRA_TOKEN"
require_jwt_metadata "identity temporary session token" "$EXTRA_TOKEN"
EXTRA_REFRESH_TOKEN="$(printf '%s' "$extra_login_response" | tr -d '\n' | extract_json_string refreshToken)"
if [[ -z "$EXTRA_REFRESH_TOKEN" ]]; then
    fail "identity temporary session login did not return a refresh token"
fi
EXTRA_SESSION_ID="$(jwt_number_claim "identity temporary session token" "$EXTRA_TOKEN" sid)"
if [[ -z "$EXTRA_SESSION_ID" ]]; then
    fail "identity temporary session token did not contain a session id"
fi
REVOKED_SESSION_AUDIT_DETAIL="session_revoke:$EXTRA_SESSION_ID"
ok "identity temporary session issued session $EXTRA_SESSION_ID"
unset extra_login_response

refresh_payload="$(printf '{"refreshToken":"%s"}' "$(json_escape "$REFRESH_TOKEN")")"
refresh_response="$(curl_json_or_fail "identity token refresh" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/token/refresh" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary "$refresh_payload")"
unset refresh_payload
REFRESHED_TOKEN="$(printf '%s' "$refresh_response" | tr -d '\n' | extract_json_string token)"
if [[ -z "$REFRESHED_TOKEN" ]]; then
    fail "identity token refresh did not return a token"
fi
REFRESHED_REFRESH_TOKEN="$(printf '%s' "$refresh_response" | tr -d '\n' | extract_json_string refreshToken)"
if [[ -z "$REFRESHED_REFRESH_TOKEN" ]]; then
    fail "identity token refresh did not return a replacement refresh token"
fi
TOKEN="$REFRESHED_TOKEN"
REFRESH_TOKEN="$REFRESHED_REFRESH_TOKEN"
ok "identity token refresh issued replacement token (${#TOKEN} chars) and refresh token (${#REFRESH_TOKEN} chars)"
require_jwt_token "identity refreshed token" "$TOKEN"
require_jwt_metadata "identity refreshed token" "$TOKEN"
expect_cloud_application_role "identity refreshed user" "$refresh_response" "$EXPECTED_CLOUD_APP_ROLE"
expect_rag_application_role "identity refreshed user" "$refresh_response" "$EXPECTED_RAG_APP_ROLE"
rag_access_response="$(curl_json_or_fail "rag assistant access route" \
    "$PUBLIC_BASE_URL/rag/api/assistant/auth/access" \
    -H "Authorization: Bearer $TOKEN")"
rag_access_app="$(printf '%s' "$rag_access_response" | tr -d '\n' | extract_json_string appCode)"
rag_access_role="$(printf '%s' "$rag_access_response" | tr -d '\n' | extract_json_string role)"
[[ "$rag_access_app" == "rag" ]] || fail "rag assistant access route appCode expected rag, got ${rag_access_app:-<missing>}"
if [[ -n "$EXPECTED_RAG_APP_ROLE" && "$rag_access_role" != "$EXPECTED_RAG_APP_ROLE" ]]; then
    fail "rag assistant access route role expected $EXPECTED_RAG_APP_ROLE, got ${rag_access_role:-<missing>}"
fi
[[ -n "$rag_access_role" ]] || fail "rag assistant access route did not return a role"
ok "rag assistant access route returns rag application role $rag_access_role"
unset rag_access_response rag_access_app rag_access_role
expect_rag_dependency_health_telemetry
expect_status "identity token refresh requires refresh token" 401 \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/token/refresh" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary '{}'

sessions_response="$(curl_json_or_fail "identity session list" \
    "$PUBLIC_BASE_URL/api/identity/auth/sessions" \
    -H "Authorization: Bearer $TOKEN")"
SESSION_ID="$(printf '%s' "$sessions_response" | tr -d '\n' | extract_json_number id)"
if [[ -z "$SESSION_ID" ]]; then
    fail "identity session list did not return a session id"
fi
ok "identity session list returned session $SESSION_ID"

curl_ok "identity session revoke succeeds" \
    -X DELETE "$PUBLIC_BASE_URL/api/identity/auth/sessions/$EXTRA_SESSION_ID" \
    -H "Authorization: Bearer $TOKEN"
expect_status "session revoke invalidates revoked access token" 401 \
    "$IDENTITY_BASE_URL/api/identity/auth/me" \
    -H "Authorization: Bearer $EXTRA_TOKEN"
expect_status "session revoke invalidates revoked refresh token" 401 \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/token/refresh" \
    -H "Authorization: Bearer $EXTRA_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary "$(printf '{"refreshToken":"%s"}' "$(json_escape "$EXTRA_REFRESH_TOKEN")")"
unset EXTRA_TOKEN EXTRA_REFRESH_TOKEN EXTRA_SESSION_ID

profile_response="$(curl_json_or_fail "cloud profile aggregation" \
    "$CLOUD_BASE_URL/api/cloud-profile/me" \
    -H "Authorization: Bearer $TOKEN")"
USER_ID="$(printf '%s' "$profile_response" | tr -d '\n' | extract_json_number id)"
if [[ -z "$USER_ID" ]]; then
    fail "cloud profile did not return a user id"
fi
ok "cloud profile aggregation returned user $USER_ID"
expect_cloud_application_role "cloud profile aggregation" "$profile_response" "$EXPECTED_CLOUD_APP_ROLE"
expect_rag_application_role "cloud profile aggregation" "$profile_response" "$EXPECTED_RAG_APP_ROLE"

curl_ok "storage overview accepts identity token" \
    "$CLOUD_BASE_URL/api/storage/overview" \
    -H "Authorization: Bearer $TOKEN"

if [[ "$SKIP_ADMIN_CHECK" == "true" ]]; then
    printf '[SKIP] admin cloud-users check\n'
else
    curl_ok "admin cloud-users route accepts admin identity token" \
        "$CLOUD_BASE_URL/api/admin/cloud-users" \
        -H "Authorization: Bearer $TOKEN"
    cloud_operations_response="$(curl_json_or_fail "admin cloud operations overview route" \
        "$CLOUD_BASE_URL/api/admin/cloud-operations/overview" \
        -H "Authorization: Bearer $TOKEN")"
    cloud_operations_compact="$(printf '%s' "$cloud_operations_response" | tr -d '\n')"
    printf '%s' "$cloud_operations_compact" | grep -q '"capacity"[[:space:]]*:[[:space:]]*{' \
        || fail "admin cloud operations overview did not expose capacity metrics"
    printf '%s' "$cloud_operations_compact" | grep -q '"trash"[[:space:]]*:[[:space:]]*{' \
        || fail "admin cloud operations overview did not expose trash metrics"
    printf '%s' "$cloud_operations_compact" | grep -q '"shares"[[:space:]]*:[[:space:]]*{' \
        || fail "admin cloud operations overview did not expose share metrics"
    printf '%s' "$cloud_operations_compact" | grep -q '"multipartUploads"[[:space:]]*:[[:space:]]*{' \
        || fail "admin cloud operations overview did not expose multipart upload metrics"
    ok "admin cloud operations overview route accepts admin identity token"
    unset cloud_operations_response cloud_operations_compact
    cloud_operation_shares_response="$(curl_json_or_fail "admin cloud operations share detail route" \
        "$CLOUD_BASE_URL/api/admin/cloud-operations/shares?size=1" \
        -H "Authorization: Bearer $TOKEN")"
    cloud_operation_shares_compact="$(printf '%s' "$cloud_operation_shares_response" | tr -d '\n')"
    printf '%s' "$cloud_operation_shares_compact" | grep -q '"items"[[:space:]]*:[[:space:]]*\[' \
        || fail "admin cloud operations share detail did not expose paged items"
    ok "admin cloud operations share detail route accepts admin identity token"
    unset cloud_operation_shares_response cloud_operation_shares_compact
    cloud_operation_trash_response="$(curl_json_or_fail "admin cloud operations trash detail route" \
        "$CLOUD_BASE_URL/api/admin/cloud-operations/trash?size=1" \
        -H "Authorization: Bearer $TOKEN")"
    cloud_operation_trash_compact="$(printf '%s' "$cloud_operation_trash_response" | tr -d '\n')"
    printf '%s' "$cloud_operation_trash_compact" | grep -q '"items"[[:space:]]*:[[:space:]]*\[' \
        || fail "admin cloud operations trash detail did not expose paged items"
    ok "admin cloud operations trash detail route accepts admin identity token"
    unset cloud_operation_trash_response cloud_operation_trash_compact
    cloud_operation_storage_users_response="$(curl_json_or_fail "admin cloud operations storage users route" \
        "$CLOUD_BASE_URL/api/admin/cloud-operations/users/storage?size=1" \
        -H "Authorization: Bearer $TOKEN")"
    cloud_operation_storage_users_compact="$(printf '%s' "$cloud_operation_storage_users_response" | tr -d '\n')"
    printf '%s' "$cloud_operation_storage_users_compact" | grep -q '"items"[[:space:]]*:[[:space:]]*\[' \
        || fail "admin cloud operations storage users did not expose paged items"
    printf '%s' "$cloud_operation_storage_users_compact" | grep -q '"usedBytes"[[:space:]]*:' \
        || fail "admin cloud operations storage users did not expose usedBytes"
    ok "admin cloud operations storage users route accepts admin identity token"
    unset cloud_operation_storage_users_response cloud_operation_storage_users_compact
    curl_ok "identity audit logs admin route accepts admin identity token" \
        "$PUBLIC_BASE_URL/api/identity/admin/audit-logs?size=5" \
        -H "Authorization: Bearer $TOKEN"
    rag_contract_response="$(curl_json_or_fail "rag assistant contract admin route" \
        "$RAG_ACTION_PLAN_CONTRACT_URL" \
        -H "Authorization: Bearer $TOKEN")"
    rag_contract_compact="$(printf '%s' "$rag_contract_response" | tr -d '\n')"
    printf '%s' "$rag_contract_compact" | grep -q '"schema"[[:space:]]*:[[:space:]]*{' \
        || fail "rag assistant contract admin route did not expose schema"
    printf '%s' "$rag_contract_compact" | grep -q '"actions"[[:space:]]*:[[:space:]]*{' \
        || fail "rag assistant contract admin route did not expose actions"
    ok "rag assistant contract admin route accepts rag admin token"
    unset rag_contract_response rag_contract_compact
    app_roles_response="$(curl_json_or_fail "identity app roles admin route" \
        "$PUBLIC_BASE_URL/api/identity/admin/users/$USER_ID/app-roles" \
        -H "Authorization: Bearer $TOKEN")"
    expect_application_role_entry "identity app roles admin route" "$app_roles_response" "cloud" "CLOUD_ADMIN"
    expect_application_role_entry "identity app roles admin route" "$app_roles_response" "rag" "RAG_ADMIN"
    expect_audit_log_filter \
        "identity audit logs filter session revoke event" \
        "SESSION_REVOKE" \
        "SUCCESS" \
        "$USER_ID"

    expect_status "legacy /api/admin/users remains removed" 404 \
        "$CLOUD_BASE_URL/api/admin/users" \
        -H "Authorization: Bearer $TOKEN"
fi

expect_status "legacy /api/auth/me remains removed" 404 \
    "$CLOUD_BASE_URL/api/auth/me" \
    -H "Authorization: Bearer $TOKEN"
expect_status "legacy /api/auth/avatar/{userId} remains removed" 404 \
    "$CLOUD_BASE_URL/api/auth/avatar/$USER_ID"

curl_ok "identity logout succeeds" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/logout" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary "$(printf '{"refreshToken":"%s"}' "$(json_escape "$REFRESH_TOKEN")")"
expect_status "logout invalidates refreshed token" 401 \
    "$IDENTITY_BASE_URL/api/identity/auth/me" \
    -H "Authorization: Bearer $TOKEN"
expect_status "logout invalidates refresh token" 401 \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/token/refresh" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary "$(printf '{"refreshToken":"%s"}' "$(json_escape "$REFRESH_TOKEN")")"
expect_cloud_identity_gateway_telemetry

if [[ "$SKIP_AUDIT_CHECK" == "true" ]]; then
    printf '[SKIP] identity audit log check\n'
else
    expect_recent_audit_event \
        "identity session revoke audit event recorded" \
        "SESSION_REVOKE" \
        "SUCCESS" \
        "$USER_ID" \
        "$REVOKED_SESSION_AUDIT_DETAIL"

    printf '\nLatest identity audit rows:\n'
    mysql_exec "$IDENTITY_MYSQL_DATABASE" "
SELECT id, event_type, outcome, actor_user_id, target_user_id, identifier, created_at
FROM identity_audit_log
ORDER BY id DESC
LIMIT 10;
" || fail "identity audit log query failed"
    ok "identity audit log query completed"
fi

if [[ "$SKIP_IDENTITY_FLYWAY_CHECK" == "true" ]]; then
    printf '[SKIP] identity Flyway history check\n'
else
    printf '\nLatest identity Flyway migrations:\n'
    mysql_exec "$IDENTITY_MYSQL_DATABASE" "
SELECT installed_rank, version, description, success, installed_on
FROM identity_flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
" || fail "identity Flyway history query failed"
    ok "identity Flyway history query completed"
    verify_identity_user_table_boundary
    verify_no_identity_table_foreign_keys
    verify_cloud_object_cleanup_table
    if [[ "$REQUIRE_CLOUD_IDENTITY_TABLES_REMOVED" == "true" ]]; then
        verify_cloud_identity_tables_removed
    else
        printf '[SKIP] cloud identity residue table check\n'
    fi
fi

printf '\nAlicia identity/cloud route verification passed.\n'
