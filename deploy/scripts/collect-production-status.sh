#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-$HOME/mainSite}"
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
RUN_FRONTEND_SPLIT_CHECK="${ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK:-false}"
MAIN_SITE_ROUTE_VERIFY_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/verify-main-site-routes.sh"
MAIN_SITE_BOUNDARY_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/check-main-site-frontend-boundaries.sh"
PLATFORM_FRONTEND_SPLIT_SCRIPT="$PROJECT_DIR/deploy/scripts/verify-platform-frontend-split-local.sh"

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

git_snapshot() {
    local label="$1"
    local directory="$2"
    local tracked_status
    local tracked_summary
    local tracked_names

    printf '\n-- %s: %s --\n' "$label" "$directory"

    if ! git -C "$directory" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        warn "$label Git repository is missing: $directory"
        return 0
    fi

    pushd "$directory" >/dev/null
    git log --oneline -3 || warn "$label git log failed"
    if ! tracked_status="$(git status --porcelain --untracked-files=no)"; then
        warn "$label tracked status failed"
    elif [[ -n "$tracked_status" ]]; then
        warn "$label tracked server files have local changes"
        printf '%s\n' "$tracked_status"
        if tracked_summary="$(git diff --summary)"; then
            if [[ -n "$tracked_summary" ]]; then
                printf '\nTracked change summary:\n%s\n' "$tracked_summary"
            fi
        else
            warn "$label tracked diff summary failed"
        fi
        if tracked_names="$(git diff --name-status)"; then
            if [[ -n "$tracked_names" ]]; then
                printf '\nTracked changed files:\n%s\n' "$tracked_names"
            fi
        else
            warn "$label tracked diff name-status failed"
        fi
    else
        ok "$label tracked server files are clean"
    fi
    popd >/dev/null
}

run_main_site_route_verify() {
    [[ -f "$MAIN_SITE_ROUTE_VERIFY_SCRIPT" ]] || {
        printf 'Missing main site route verify script: %s\n' "$MAIN_SITE_ROUTE_VERIFY_SCRIPT" >&2
        return 1
    }

    (cd "$MAIN_SITE_PROJECT_DIR" && bash deploy/scripts/verify-main-site-routes.sh)
}

run_main_site_boundary_check() {
    [[ -f "$MAIN_SITE_BOUNDARY_SCRIPT" ]] || {
        printf 'Missing main site boundary script: %s\n' "$MAIN_SITE_BOUNDARY_SCRIPT" >&2
        return 1
    }

    (cd "$MAIN_SITE_PROJECT_DIR" && bash deploy/scripts/check-main-site-frontend-boundaries.sh)
}

run_platform_frontend_split_check() {
    [[ -f "$PLATFORM_FRONTEND_SPLIT_SCRIPT" ]] || {
        printf 'Missing platform frontend split verifier: %s\n' "$PLATFORM_FRONTEND_SPLIT_SCRIPT" >&2
        return 1
    }

    ALICIA_MAIN_SITE_PROJECT_DIR="$MAIN_SITE_PROJECT_DIR" \
    ALICIA_CLOUD_PROJECT_DIR="$PROJECT_DIR" \
    ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT="${ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT:-1}" \
        bash "$PLATFORM_FRONTEND_SPLIT_SCRIPT" --skip-build
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

curl_redirect_probe() {
    local label="$1"
    local url="$2"
    local expected_location="$3"
    local header_file
    local meta
    local status
    local total_time
    local location
    local location_path
    local request_origin
    local absolute_expected_location

    header_file="$(mktemp)"
    if meta="$(curl "${CURL_ARGS[@]}" -I -o /dev/null -D "$header_file" -w '%{http_code} %{time_total}' "$url" 2>&1)"; then
        status="${meta%% *}"
        total_time="${meta#* }"
        location="$(awk 'BEGIN { IGNORECASE = 1 } /^location:/ { sub(/^[^:]+:[[:space:]]*/, ""); sub(/\r$/, ""); print; exit }' "$header_file")"
        absolute_expected_location="$expected_location"
        if [[ "$expected_location" == /* ]]; then
            request_origin="$(printf '%s' "$url" | sed -E 's#^([a-zA-Z][a-zA-Z0-9+.-]*://[^/]+).*#\1#')"
            absolute_expected_location="${request_origin}${expected_location}"
        fi
        location_path="$(printf '%s' "$location" | sed -E 's#^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*##')"

        if [[ ! "$status" =~ ^(301|302|307|308)$ ]]; then
            printf '[WARN] %-40s expected redirect to %s, got HTTP %s %ss\n' "$label" "$expected_location" "$status" "$total_time" >&2
            sed -n '1,20p' "$header_file" >&2 || true
            FAILURES=$((FAILURES + 1))
        elif [[ "$location" != "$expected_location" && "$location" != "${PUBLIC_BASE_URL}${expected_location}" && "$location" != "$absolute_expected_location" && "$location_path" != "$expected_location" ]]; then
            printf '[WARN] %-40s expected Location %s, got %s\n' "$label" "$expected_location" "${location:-<missing>}" >&2
            sed -n '1,20p' "$header_file" >&2 || true
            FAILURES=$((FAILURES + 1))
        else
            printf '[OK] %-42s HTTP %s -> %s %ss\n' "$label" "$status" "$expected_location" "$total_time"
        fi
    else
        printf '[WARN] %-40s curl failed: %s\n' "$label" "$meta" >&2
        FAILURES=$((FAILURES + 1))
    fi
    rm -f "$header_file"
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
printf 'Main site project: %s\n' "$MAIN_SITE_PROJECT_DIR"
printf 'Cloud API: %s\n' "$CLOUD_BASE_URL"
printf 'Identity API: %s\n' "$IDENTITY_BASE_URL"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"

print_section "Git"
git_snapshot "cloud repository" "$PROJECT_DIR"
git_snapshot "main site repository" "$MAIN_SITE_PROJECT_DIR"

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
curl_redirect_probe "cloudPan bare path" "$PUBLIC_BASE_URL/cloudPan" "/cloudPan/"
curl_redirect_probe "cloudPan legacy login" "$PUBLIC_BASE_URL/cloudPan/login" "/login?returnTo=/cloudPan/"
curl_probe "cloud console frontend entry" "$PUBLIC_BASE_URL/console/cloud/"
curl_redirect_probe "cloud console bare path" "$PUBLIC_BASE_URL/console/cloud" "/console/cloud/"
curl_probe "cloud console users route" "$PUBLIC_BASE_URL/console/cloud/users"
curl_probe "cloud console operations route" "$PUBLIC_BASE_URL/console/cloud/operations"
curl_probe "cloud console app package route" "$PUBLIC_BASE_URL/console/cloud/app-package"
curl_redirect_probe "console gateway bare path" "$PUBLIC_BASE_URL/console" "/console/"
curl_probe "identity console frontend entry" "$PUBLIC_BASE_URL/console/identity/"
curl_probe "identity console users route" "$PUBLIC_BASE_URL/console/identity/users"
curl_probe "identity console roles route" "$PUBLIC_BASE_URL/console/identity/roles"
curl_probe "identity console sessions route" "$PUBLIC_BASE_URL/console/identity/sessions"
curl_probe "identity console audit route" "$PUBLIC_BASE_URL/console/identity/audit"
curl_probe "android asset links endpoint" "$PUBLIC_BASE_URL/.well-known/assetlinks.json"
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

    print_section "Cloud Object Cleanup Snapshot"
    run_optional "cloud object cleanup queue summary" mysql_cloud_query "
SELECT
    status,
    source,
    COUNT(*) AS tasks,
    MIN(next_retry_at) AS next_retry_at,
    MAX(updated_at) AS latest_update
FROM cloud_object_cleanup_task
GROUP BY status, source
ORDER BY status, source;
"
fi

if [[ "$RUN_ROUTE_VERIFY" == "true" ]]; then
    print_section "Full Route Verification"
    run_optional "main site route verification" run_main_site_route_verify
    run_optional "verify identity/cloud routes" bash deploy/scripts/verify-identity-cloud-routes.sh
fi

if [[ "$RUN_BOUNDARY_CHECK" == "true" ]]; then
    print_section "Static Boundary Check"
    run_optional "main site frontend boundary check" run_main_site_boundary_check
    run_optional "identity route boundary check" bash deploy/scripts/check-identity-route-boundary.sh
    run_optional "frontend console boundary check" bash deploy/scripts/check-frontend-console-boundaries.sh
    run_optional "backend API ownership boundary check" bash deploy/scripts/verify-backend-api-boundaries.sh
fi

if [[ "$RUN_FRONTEND_SPLIT_CHECK" == "true" ]]; then
    print_section "Platform Frontend Split Check"
    run_optional "platform frontend split verification" run_platform_frontend_split_check
fi

print_section "Result"
if [[ "$FAILURES" -eq 0 ]]; then
    printf 'Alicia production status snapshot completed without warnings.\n'
else
    printf 'Alicia production status snapshot completed with %s warning(s).\n' "$FAILURES" >&2
    exit 1
fi
