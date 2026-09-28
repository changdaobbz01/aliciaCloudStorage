#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy/scripts/lib/rag-execution-production-common.sh
source "$SCRIPT_DIR/lib/rag-execution-production-common.sh"

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
ENV_FILE="${ALICIA_RAG_EXECUTION_ENV_FILE:-.env}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
GIT_REMOTE="${ALICIA_CLOUD_GIT_REMOTE:-gitee}"
GIT_BRANCH="${ALICIA_CLOUD_GIT_BRANCH:-main}"
SKIP_GIT_PULL="${ALICIA_SKIP_GIT_PULL:-false}"
BACKUP_BEFORE_UPDATE="${ALICIA_RAG_EXECUTION_BACKUP_BEFORE_UPDATE:-true}"
STOP_ON_VERIFY_FAILURE="${ALICIA_RAG_EXECUTION_STOP_ON_VERIFY_FAILURE:-true}"
VERIFY_SCRIPT="${ALICIA_RAG_EXECUTION_VERIFY_SCRIPT:-deploy/scripts/verify-rag-execution-production.sh}"
STAGE=""
ACTION="FOLDER_CREATE"
APPLY=false

rag_execution_require_boolean ALICIA_SKIP_GIT_PULL "$SKIP_GIT_PULL"
rag_execution_require_boolean ALICIA_RAG_EXECUTION_BACKUP_BEFORE_UPDATE "$BACKUP_BEFORE_UPDATE"
rag_execution_require_boolean ALICIA_RAG_EXECUTION_STOP_ON_VERIFY_FAILURE "$STOP_ON_VERIFY_FAILURE"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --stage)
            [[ $# -ge 2 ]] || rag_execution_fail "--stage requires a value."
            STAGE="$2"
            shift 2
            ;;
        --action)
            [[ $# -ge 2 ]] || rag_execution_fail "--action requires a value."
            ACTION="$2"
            shift 2
            ;;
        --apply)
            APPLY=true
            shift
            ;;
        *)
            rag_execution_fail "Unknown argument: $1"
            ;;
    esac
done

cd "$PROJECT_DIR"
[[ -f "$ENV_FILE" ]] || rag_execution_fail "Env file not found: $ENV_FILE"
[[ -f "$VERIFY_SCRIPT" ]] || rag_execution_fail "Verify script not found: $VERIFY_SCRIPT"
if [[ -z "$STAGE" ]]; then
    STAGE="$(rag_execution_dotenv_value "$ENV_FILE" ALICIA_RAG_EXECUTION_ROLLOUT_STAGE)"
fi
[[ -n "$STAGE" ]] || rag_execution_fail "--stage is required when the env has no rollout stage."
rag_execution_validate_stage "$STAGE"
ACTION="$(rag_execution_normalize_action "$ACTION")"

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

ensure_gateway_network() {
    if ! rag_execution_docker network inspect alicia_gateway >/dev/null 2>&1; then
        rag_execution_docker network create alicia_gateway >/dev/null
        rag_execution_ok "created Docker network alicia_gateway"
    fi
}

ALICIA_RAG_EXECUTION_ENV_FILE="$ENV_FILE" \
ALICIA_RAG_EXECUTION_PREFLIGHT_ONLY=true \
    bash "$VERIFY_SCRIPT" "$STAGE" "$ACTION"

printf 'RAG execution deployment plan:\n'
printf '  project:  %s\n' "$PROJECT_DIR"
printf '  stage:    %s\n' "$STAGE"
printf '  action:   %s\n' "$ACTION"
printf '  services: api rag rag-execution frontend\n'
printf '  backup:   %s\n' "$BACKUP_BEFORE_UPDATE"

if [[ "$APPLY" != "true" ]]; then
    printf 'Dry run complete. Re-run with --apply only during an approved production window.\n'
    exit 0
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    git status --short --untracked-files=no >&2
    rag_execution_fail "Tracked server files have local changes; refusing production update."
fi

if [[ "$SKIP_GIT_PULL" != "true" ]]; then
    git fetch "$GIT_REMOTE" "$GIT_BRANCH"
    git pull --ff-only "$GIT_REMOTE" "$GIT_BRANCH"
fi

ALICIA_RAG_EXECUTION_ENV_FILE="$ENV_FILE" \
ALICIA_RAG_EXECUTION_PREFLIGHT_ONLY=true \
    bash "$VERIFY_SCRIPT" "$STAGE" "$ACTION"

if [[ "$BACKUP_BEFORE_UPDATE" == "true" ]]; then
    ALICIA_BACKUP_ENV_FILE="$ENV_FILE" bash deploy/scripts/backup-production-data.sh
fi

ensure_gateway_network
compose up -d --build --wait --wait-timeout 240 api rag rag-execution frontend

if ! ALICIA_RAG_EXECUTION_ENV_FILE="$ENV_FILE" bash "$VERIFY_SCRIPT" "$STAGE" "$ACTION"; then
    printf '[FAIL] RAG execution verification failed after deployment.\n' >&2
    if [[ "$STOP_ON_VERIFY_FAILURE" == "true" ]]; then
        compose stop rag-execution
        printf '[SAFE] rag-execution stopped; CloudStorageApi and manual cloud operations remain available.\n' >&2
    fi
    exit 1
fi

compose ps api rag rag-execution frontend
printf 'RAG execution production update completed: stage=%s commit=%s\n' "$STAGE" "$(git rev-parse --short HEAD)"
