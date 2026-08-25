#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
CLOUD_BASE_URL="${ALICIA_CLOUD_BASE_URL:-http://127.0.0.1:8090}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
CURL_TIMEOUT="${ALICIA_STATUS_CURL_TIMEOUT_SECONDS:-12}"
INSECURE_TLS="${ALICIA_STATUS_INSECURE_TLS:-true}"
SKIP_DB="${ALICIA_STATUS_SKIP_DB:-false}"
SKIP_DOCKER_DF="${ALICIA_STATUS_SKIP_DOCKER_DF:-false}"
RUN_ROUTE_VERIFY="${ALICIA_STATUS_RUN_ROUTE_VERIFY:-false}"
RUN_BOUNDARY_CHECK="${ALICIA_STATUS_RUN_BOUNDARY_CHECK:-false}"

CLOUD_BASE_URL="${CLOUD_BASE_URL%/}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_ARGS=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_ARGS+=(-k)
fi

FAILURES=0

print_section() {
    printf '\n== %s ==\n' "$1"
}

ok() {
    printf '[OK] %s\n' "$1"
}

warn() {
    printf '[WARN] %s\n' "$1" >&2
    FAILURES=$((FAILURES + 1))
}

docker_cmd() {
    local command=(docker)

    if [[ "${ALICIA_DOCKER_SUDO:-auto}" == "true" ]]; then
        command=(sudo docker)
    elif [[ "${ALICIA_DOCKER_SUDO:-auto}" == "auto" && "${EUID:-$(id -u)}" -ne 0 ]]; then
        if ! docker info >/dev/null 2>&1; then
            command=(sudo docker)
        fi
    fi

    "${command[@]}" "$@"
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

run_optional() {
    local label="$1"
    shift

    if "$@"; then
        ok "$label"
    else
        warn "$label failed"
    fi
}

curl_probe() {
    local label="$1"
    local url="$2"
    local body_file
    local meta
    local code
    local total_time

    body_file="$(mktemp)"
    if meta="$(curl "${CURL_ARGS[@]}" -o "$body_file" -w '%{http_code} %{time_total}' "$url" 2>&1)"; then
        code="${meta%% *}"
        total_time="${meta#* }"
        if [[ "$code" =~ ^[23] ]]; then
            printf '[OK] %-42s HTTP %s %ss\n' "$label" "$code" "$total_time"
        else
            printf '[WARN] %-40s HTTP %s %ss\n' "$label" "$code" "$total_time" >&2
            FAILURES=$((FAILURES + 1))
        fi
    else
        printf '[WARN] %-40s curl failed: %s\n' "$label" "$meta" >&2
        FAILURES=$((FAILURES + 1))
    fi
    rm -f "$body_file"
}

curl_json() {
    local label="$1"
    local url="$2"
    local body_file
    local meta
    local code

    body_file="$(mktemp)"
    if meta="$(curl "${CURL_ARGS[@]}" -o "$body_file" -w '%{http_code} %{time_total}' "$url" 2>&1)"; then
        code="${meta%% *}"
        printf '\n-- %s: HTTP %s --\n' "$label" "$code"
        if command -v python3 >/dev/null 2>&1; then
            python3 -m json.tool "$body_file" 2>/dev/null || cat "$body_file"
        elif command -v python >/dev/null 2>&1; then
            python -m json.tool "$body_file" 2>/dev/null || cat "$body_file"
        else
            cat "$body_file"
        fi
        printf '\n'
        if [[ ! "$code" =~ ^2 ]]; then
            warn "$label returned HTTP $code"
        fi
    else
        warn "$label curl failed: $meta"
    fi
    rm -f "$body_file"
}

mysql_identity_query() {
    local sql="$1"
    compose exec -T db sh -lc '
        db_name="${ALICIA_IDENTITY_MYSQL_DATABASE:-$MYSQL_DATABASE}"
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$db_name" -e "$1"
    ' sh "$sql"
}

mysql_cloud_query() {
    local sql="$1"
    compose exec -T db sh -lc '
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -e "$1"
    ' sh "$sql"
}

cd "$PROJECT_DIR"

for file in $COMPOSE_FILES; do
    [[ -f "$file" ]] || { printf 'Missing compose file %s/%s\n' "$PROJECT_DIR" "$file" >&2; exit 1; }
done

print_section "Alicia Production Snapshot"
date -Is 2>/dev/null || date
printf 'Project: %s\n' "$PROJECT_DIR"
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"

print_section "Git"
git log --oneline -3
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    warn "tracked server files have local changes"
    git status --short --untracked-files=no
else
    ok "tracked server files are clean"
fi

print_section "Containers"
run_optional "docker compose ps" compose ps

if [[ "$SKIP_DOCKER_DF" != "true" ]]; then
    print_section "Capacity"
    run_optional "filesystem capacity" df -h
    run_optional "docker disk usage" docker_cmd system df
fi

print_section "Route Health"
curl_probe "cloud health direct" "$CLOUD_BASE_URL/api/health"
curl_probe "cloud dependency health direct" "$CLOUD_BASE_URL/api/health/dependencies"
curl_probe "identity health direct" "$IDENTITY_BASE_URL/api/identity/health"
curl_probe "identity dependency health direct" "$IDENTITY_BASE_URL/api/identity/health/dependencies"
curl_probe "main site home through frontend" "$PUBLIC_BASE_URL/"
curl_probe "main site login through frontend" "$PUBLIC_BASE_URL/login"
curl_probe "cloudPan frontend entry" "$PUBLIC_BASE_URL/cloudPan/"
curl_probe "identity jwks endpoint" "$PUBLIC_BASE_URL/api/identity/.well-known/jwks.json"
curl_probe "rag health through frontend" "$PUBLIC_BASE_URL/rag/api/health"
curl_probe "rag dependency health through frontend" "$PUBLIC_BASE_URL/rag/api/health/dependencies"

print_section "Dependency Health Details"
curl_json "Cloud dependency health" "$CLOUD_BASE_URL/api/health/dependencies"
curl_json "Identity dependency health" "$IDENTITY_BASE_URL/api/identity/health/dependencies"
curl_json "RAG dependency health" "$PUBLIC_BASE_URL/rag/api/health/dependencies"

if [[ "$SKIP_DB" != "true" ]]; then
    print_section "Identity Database Snapshot"
    run_optional "identity audit latest rows" mysql_identity_query "
SELECT
    id,
    event_type,
    outcome,
    actor_user_id,
    target_user_id,
    CASE
        WHEN identifier IS NULL THEN NULL
        WHEN identifier LIKE '%@%' THEN CONCAT(LEFT(identifier, 2), '***', SUBSTRING(identifier, LOCATE('@', identifier)))
        WHEN CHAR_LENGTH(identifier) > 4 THEN CONCAT(LEFT(identifier, 3), '***', RIGHT(identifier, 2))
        ELSE '***'
    END AS identifier_mask,
    created_at
FROM identity_audit_log
ORDER BY id DESC
LIMIT 10;
"
    run_optional "identity Flyway latest migrations" mysql_identity_query "
SELECT installed_rank, version, description, success, installed_on
FROM identity_flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
"

    print_section "Cloud Database Boundary Snapshot"
    run_optional "cloud identity residue table check" mysql_cloud_query "
SELECT
    table_name,
    COUNT(*) AS present
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'sys_user',
      'identity_user',
      'identity_refresh_token',
      'identity_audit_log',
      'identity_user_app_role',
      'email_verification_code',
      'identity_flyway_schema_history'
  )
GROUP BY table_name
ORDER BY table_name;
"
fi

if [[ "$RUN_ROUTE_VERIFY" == "true" ]]; then
    print_section "Full Route Verification"
    run_optional "verify identity/cloud routes" bash deploy/scripts/verify-identity-cloud-routes.sh
fi

if [[ "$RUN_BOUNDARY_CHECK" == "true" ]]; then
    print_section "Static Boundary Check"
    run_optional "identity route boundary check" bash deploy/scripts/check-identity-route-boundary.sh
fi

print_section "Result"
if [[ "$FAILURES" -eq 0 ]]; then
    printf 'Alicia production status snapshot completed without warnings.\n'
else
    printf 'Alicia production status snapshot completed with %s warning(s).\n' "$FAILURES" >&2
    exit 1
fi
