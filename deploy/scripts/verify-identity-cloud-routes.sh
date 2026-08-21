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
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"

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

curl_ok "cloud health direct" "$CLOUD_BASE_URL/api/health"
curl_ok "identity health direct" "$IDENTITY_BASE_URL/api/identity/health"
curl_ok "cloud health through frontend" "$PUBLIC_BASE_URL/api/health"
curl_ok "identity health through frontend" "$PUBLIC_BASE_URL/api/identity/health"
curl_ok "rag health through frontend" "$RAG_HEALTH_URL"
curl_ok "cloudPan frontend entry" -I "$PUBLIC_BASE_URL/cloudPan/"

login_payload="$(printf '{"identifier":"%s","password":"%s"}' "$(json_escape "$ACCOUNT")" "$(json_escape "$PASSWORD")")"
login_response="$(printf '%s' "$login_payload" | curl -fsS "${CURL_ARGS[@]}" \
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/login" \
    -H "Content-Type: application/json" \
    --data-binary @-)"
unset PASSWORD login_payload

TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string token)"
if [[ -z "$TOKEN" ]]; then
    fail "identity login did not return a token"
fi
ok "identity login issued token (${#TOKEN} chars)"
REFRESH_TOKEN="$(printf '%s' "$login_response" | tr -d '\n' | extract_json_string refreshToken)"
if [[ -z "$REFRESH_TOKEN" ]]; then
    fail "identity login did not return a refresh token"
fi
ok "identity login issued refresh token (${#REFRESH_TOKEN} chars)"

refresh_payload="$(printf '{"refreshToken":"%s"}' "$(json_escape "$REFRESH_TOKEN")")"
refresh_response="$(curl -fsS "${CURL_ARGS[@]}" \
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

profile_response="$(curl -fsS "${CURL_ARGS[@]}" \
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
    -X POST "$IDENTITY_BASE_URL/api/identity/auth/token/refresh" \
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

printf '\nAlicia identity/cloud route verification passed.\n'
