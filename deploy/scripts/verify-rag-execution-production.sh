#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy/scripts/lib/rag-execution-production-common.sh
source "$SCRIPT_DIR/lib/rag-execution-production-common.sh"

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
ENV_FILE="${ALICIA_RAG_EXECUTION_ENV_FILE:-.env}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
STAGE="${1:-${ALICIA_RAG_EXECUTION_EXPECTED_STAGE:-}}"
ACTION="${2:-${ALICIA_RAG_EXECUTION_CANARY_ACTION:-FOLDER_CREATE}}"
MODE="${3:-}"
LOCAL_BASE_URL="${ALICIA_RAG_EXECUTION_LOCAL_BASE_URL:-http://127.0.0.1:8094}"
PUBLIC_BASE_URL="${ALICIA_RAG_EXECUTION_PUBLIC_BASE_URL:-https://windwindwind-alicia.cn/rag-execution}"
CURL_TIMEOUT="${ALICIA_RAG_EXECUTION_VERIFY_TIMEOUT_SECONDS:-12}"
VERIFY_ATTEMPTS="${ALICIA_RAG_EXECUTION_VERIFY_ATTEMPTS:-30}"
VERIFY_RETRY_DELAY="${ALICIA_RAG_EXECUTION_VERIFY_RETRY_DELAY_SECONDS:-2}"
INSECURE_TLS="${ALICIA_RAG_EXECUTION_VERIFY_INSECURE_TLS:-false}"
MAX_RESTARTS="${ALICIA_RAG_EXECUTION_VERIFY_MAX_RESTARTS:-0}"

rag_execution_require_positive_integer ALICIA_RAG_EXECUTION_VERIFY_TIMEOUT_SECONDS "$CURL_TIMEOUT"
rag_execution_require_positive_integer ALICIA_RAG_EXECUTION_VERIFY_ATTEMPTS "$VERIFY_ATTEMPTS"
rag_execution_require_non_negative_integer ALICIA_RAG_EXECUTION_VERIFY_RETRY_DELAY_SECONDS "$VERIFY_RETRY_DELAY"
rag_execution_require_boolean ALICIA_RAG_EXECUTION_VERIFY_INSECURE_TLS "$INSECURE_TLS"
rag_execution_require_non_negative_integer ALICIA_RAG_EXECUTION_VERIFY_MAX_RESTARTS "$MAX_RESTARTS"

if [[ "$MODE" == "--preflight" || "${ALICIA_RAG_EXECUTION_PREFLIGHT_ONLY:-false}" == "true" ]]; then
    PREFLIGHT_ONLY=true
else
    PREFLIGHT_ONLY=false
fi

cd "$PROJECT_DIR"
[[ -f "$ENV_FILE" ]] || rag_execution_fail "Env file not found: $ENV_FILE"
if [[ -z "$STAGE" ]]; then
    STAGE="$(rag_execution_dotenv_value "$ENV_FILE" ALICIA_RAG_EXECUTION_ROLLOUT_STAGE)"
fi
[[ -n "$STAGE" ]] || rag_execution_fail "Expected rollout stage is required."
rag_execution_validate_stage "$STAGE"
ACTION="$(rag_execution_normalize_action "$ACTION")"

rag_execution_assert_env_stage "$ENV_FILE" "$STAGE" "$ACTION"
rag_execution_ok "stage configuration is explicit and internally consistent"

compose() {
    local args=(compose --env-file "$ENV_FILE")
    local file
    for file in $COMPOSE_FILES; do
        [[ -f "$file" ]] || rag_execution_fail "Missing compose file: $file"
        args+=(-f "$file")
    done
    args+=(--profile rag-execution-foundation)
    rag_execution_docker "${args[@]}" "$@"
}

compose config --quiet
rag_execution_ok "Compose configuration resolves with the candidate env"

if [[ "$PREFLIGHT_ONLY" == "true" ]]; then
    printf 'RAG execution %s preflight passed; no runtime request was made.\n' "$STAGE"
    exit 0
fi

CURL_ARGS=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_ARGS+=(-k)
fi

curl_json() {
    local url="$1"
    local output_file="$2"
    local code
    local attempt

    for ((attempt = 1; attempt <= VERIFY_ATTEMPTS; attempt++)); do
        code="$(curl "${CURL_ARGS[@]}" -o "$output_file" -w '%{http_code}' "$url" 2>/dev/null || true)"
        if [[ "$code" == "200" ]]; then
            return 0
        fi
        if [[ "$attempt" -lt "$VERIFY_ATTEMPTS" ]]; then
            sleep "$VERIFY_RETRY_DELAY"
        fi
    done

    rag_execution_fail "Expected HTTP 200 from $url after $VERIFY_ATTEMPTS attempts, got ${code:-request-failed}."
}

expect_json_boolean() {
    local file="$1"
    local field="$2"
    local value="$3"
    grep -Eq "\"$field\"[[:space:]]*:[[:space:]]*$value([,}])" "$file" \
        || rag_execution_fail "Health response does not report $field=$value."
}

expect_json_status() {
    local file="$1"
    local status="$2"
    grep -Eq "\"status\"[[:space:]]*:[[:space:]]*\"$status\"" "$file" \
        || rag_execution_fail "Health response does not report status=$status."
}

expect_http_404() {
    local url="$1"
    local code
    code="$(curl "${CURL_ARGS[@]}" -o /dev/null -w '%{http_code}' "$url")" \
        || rag_execution_fail "Route probe failed: $url"
    [[ "$code" == "404" ]] || rag_execution_fail "Internal route must return 404: $url (got $code)."
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
local_health="$tmp_dir/local-health.json"
local_dependencies="$tmp_dir/local-dependencies.json"
public_health="$tmp_dir/public-health.json"

curl_json "${LOCAL_BASE_URL%/}/api/health" "$local_health"
expect_json_status "$local_health" ok

case "$STAGE" in
    foundation)
        expect_json_boolean "$local_health" enabled false
        expect_json_boolean "$local_health" registrationEnabled false
        expect_json_boolean "$local_health" publicConfirmEnabled false
        expect_json_boolean "$local_health" workerEnabled false
        expect_json_boolean "$local_health" cloudDispatchEnabled false
        ;;
    shadow)
        expect_json_boolean "$local_health" enabled true
        expect_json_boolean "$local_health" registrationEnabled true
        expect_json_boolean "$local_health" publicConfirmEnabled false
        expect_json_boolean "$local_health" workerEnabled false
        expect_json_boolean "$local_health" cloudDispatchEnabled false
        ;;
    admin-single)
        expect_json_boolean "$local_health" enabled true
        expect_json_boolean "$local_health" registrationEnabled true
        expect_json_boolean "$local_health" publicConfirmEnabled true
        expect_json_boolean "$local_health" workerEnabled true
        expect_json_boolean "$local_health" cloudDispatchEnabled true
        ;;
esac
expect_json_boolean "$local_health" adminOnly true
rag_execution_ok "local liveness reports the expected $STAGE feature state"

curl_json "${LOCAL_BASE_URL%/}/api/health/dependencies" "$local_dependencies"
expect_json_status "$local_dependencies" ok
grep -Eq '"database"[[:space:]]*:[[:space:]]*\{[^}]*"available"[[:space:]]*:[[:space:]]*true' "$local_dependencies" \
    || rag_execution_fail "Database dependency is not available."
if [[ "$STAGE" != "foundation" ]]; then
    grep -Eq '"identity"[[:space:]]*:[[:space:]]*\{[^}]*"available"[[:space:]]*:[[:space:]]*true' "$local_dependencies" \
        || rag_execution_fail "Identity dependency is not available."
fi
if [[ "$STAGE" == "admin-single" ]]; then
    grep -Eq '"cloudStorage"[[:space:]]*:[[:space:]]*\{[^}]*"available"[[:space:]]*:[[:space:]]*true' "$local_dependencies" \
        || rag_execution_fail "CloudStorageApi dependency is not available."
fi
rag_execution_ok "required runtime dependencies are healthy"

curl_json "${PUBLIC_BASE_URL%/}/api/health" "$public_health"
expect_json_status "$public_health" ok
expect_http_404 "${PUBLIC_BASE_URL%/}/internal/registration"
public_origin="$(printf '%s' "$PUBLIC_BASE_URL" | sed -E 's#^([a-zA-Z][a-zA-Z0-9+.-]*://[^/]+).*#\1#')"
expect_http_404 "$public_origin/internal/rag-execution/actions"
rag_execution_ok "public health is reachable and both internal route families are blocked"

container_id="$(compose ps -q rag-execution)"
[[ -n "$container_id" ]] || rag_execution_fail "rag-execution container is not running."
container_health="$(rag_execution_docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
restart_count="$(rag_execution_docker inspect --format '{{.RestartCount}}' "$container_id")"
[[ "$container_health" == "healthy" ]] || rag_execution_fail "rag-execution container health is $container_health."
[[ "$restart_count" =~ ^[0-9]+$ && "$restart_count" -le "$MAX_RESTARTS" ]] \
    || rag_execution_fail "rag-execution restart count $restart_count exceeds $MAX_RESTARTS."
rag_execution_ok "container is healthy with restart count $restart_count"

printf 'RAG execution production verification passed: stage=%s' "$STAGE"
if [[ "$STAGE" == "admin-single" ]]; then
    printf ' action=%s' "$ACTION"
fi
printf '\n'
