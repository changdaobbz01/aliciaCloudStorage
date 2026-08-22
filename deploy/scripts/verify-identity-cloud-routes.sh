#!/usr/bin/env bash
set -Eeuo pipefail

CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
RAG_HEALTH_URL="${ALICIA_RAG_HEALTH_URL:-${PUBLIC_BASE_URL%/}/rag/api/health}"
CURL_TIMEOUT="${ALICIA_VERIFY_CURL_TIMEOUT_SECONDS:-12}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
SKIP_ADMIN_CHECK="${ALICIA_VERIFY_SKIP_ADMIN_CHECK:-false}"
SKIP_AUDIT_CHECK="${ALICIA_VERIFY_SKIP_AUDIT_CHECK:-false}"
SKIP_IDENTITY_FLYWAY_CHECK="${ALICIA_VERIFY_SKIP_IDENTITY_FLYWAY_CHECK:-false}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
ENV_FILE="${ALICIA_VERIFY_ENV_FILE:-.env}"

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

    [[ "$algorithm" == "HS256" ]] || fail "$label alg expected HS256, got ${algorithm:-<missing>}"
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

ACCOUNT="${ALICIA_VERIFY_ACCOUNT:-}"
PASSWORD="${ALICIA_VERIFY_PASSWORD:-}"
DOTENV_TOKEN_ISSUER="$(dotenv_value ALICIA_AUTH_TOKEN_ISSUER)"
DOTENV_TOKEN_AUDIENCE="$(dotenv_value ALICIA_AUTH_TOKEN_AUDIENCE)"
DOTENV_TOKEN_KEY_ID="$(dotenv_value ALICIA_AUTH_TOKEN_KEY_ID)"
EXPECTED_TOKEN_ISSUER="${ALICIA_VERIFY_TOKEN_ISSUER:-${DOTENV_TOKEN_ISSUER:-https://windwindwind-alicia.cn}}"
EXPECTED_TOKEN_AUDIENCE="${ALICIA_VERIFY_TOKEN_AUDIENCE:-${DOTENV_TOKEN_AUDIENCE:-alicia-tools}}"
EXPECTED_TOKEN_KEY_ID="${ALICIA_VERIFY_TOKEN_KEY_ID:-${DOTENV_TOKEN_KEY_ID:-alicia-hs256-v1}}"

if [[ -z "$ACCOUNT" ]]; then
    read -r -p "Identity account/email/phone: " ACCOUNT
fi

if [[ -z "$PASSWORD" ]]; then
    read -r -s -p "Identity password: " PASSWORD
    printf '\n'
fi

printf 'Verifying Alicia identity/cloud route boundary...\n'
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"
printf 'Expected JWT: iss=%s aud=%s kid=%s\n' "$EXPECTED_TOKEN_ISSUER" "$EXPECTED_TOKEN_AUDIENCE" "$EXPECTED_TOKEN_KEY_ID"

curl_ok "cloud health direct" "$CLOUD_BASE_URL/api/health"
curl_ok "identity health direct" "$IDENTITY_BASE_URL/api/identity/health"
curl_ok "cloud health through frontend" "$PUBLIC_BASE_URL/api/health"
curl_ok "identity health through frontend" "$PUBLIC_BASE_URL/api/identity/health"
curl_ok "rag health through frontend" "$RAG_HEALTH_URL"
curl_ok "cloudPan frontend entry" -I "$PUBLIC_BASE_URL/cloudPan/"

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

curl_ok "storage overview accepts identity token" \
    "$CLOUD_BASE_URL/api/storage/overview" \
    -H "Authorization: Bearer $TOKEN"

if [[ "$SKIP_ADMIN_CHECK" == "true" ]]; then
    printf '[SKIP] admin cloud-users check\n'
else
    curl_ok "admin cloud-users route accepts admin identity token" \
        "$CLOUD_BASE_URL/api/admin/cloud-users" \
        -H "Authorization: Bearer $TOKEN"
    curl_ok "identity audit logs admin route accepts admin identity token" \
        "$PUBLIC_BASE_URL/api/identity/admin/audit-logs?size=5" \
        -H "Authorization: Bearer $TOKEN"

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

if [[ "$SKIP_AUDIT_CHECK" == "true" ]]; then
    printf '[SKIP] identity audit log check\n'
else
    printf '\nLatest identity audit rows:\n'
    compose exec -T db sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "
SELECT id, event_type, outcome, actor_user_id, target_user_id, identifier, created_at
FROM identity_audit_log
ORDER BY id DESC
LIMIT 10;
"' || fail "identity audit log query failed"
    ok "identity audit log query completed"
fi

if [[ "$SKIP_IDENTITY_FLYWAY_CHECK" == "true" ]]; then
    printf '[SKIP] identity Flyway history check\n'
else
    printf '\nLatest identity Flyway migrations:\n'
    compose exec -T db sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "
SELECT installed_rank, version, description, success, installed_on
FROM identity_flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
"' || fail "identity Flyway history query failed"
    ok "identity Flyway history query completed"
fi

printf '\nAlicia identity/cloud route verification passed.\n'
