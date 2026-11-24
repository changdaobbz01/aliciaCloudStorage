#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=deploy/scripts/lib/rag-execution-production-common.sh
source "$SCRIPT_DIR/lib/rag-execution-production-common.sh"

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
LOCAL_HEALTH="${ALICIA_LOCAL_RAG_HEALTH_URL:-http://127.0.0.1:8091/api/health}"
PUBLIC_HEALTH="${ALICIA_PUBLIC_RAG_HEALTH_URL:-https://windwindwind-alicia.cn/rag/api/health}"
ATTEMPTS="${ALICIA_RAG_RELEASE_HEALTH_ATTEMPTS:-30}"
RETRY_DELAY="${ALICIA_RAG_RELEASE_HEALTH_RETRY_SECONDS:-2}"
CANDIDATE=""
ROLLBACK=""
APPLY=false

fail() { rag_execution_fail "$1"; }
usage() {
    printf 'Usage: %s [PROJECT_DIR] (--candidate-image sha256:ID | --rollback RELEASE_DIR) [--apply]\n' "$0"
}

if [[ $# -gt 0 && "$1" != --* ]]; then
    PROJECT_DIR="$1"
    shift
fi
while [[ $# -gt 0 ]]; do
    case "$1" in
        --candidate-image)
            [[ $# -ge 2 && -z "$CANDIDATE" ]] || fail "Specify one candidate image ID."
            CANDIDATE="$2"
            shift 2
            ;;
        --rollback)
            [[ $# -ge 2 && -z "$ROLLBACK" ]] || fail "Specify one rollback record directory."
            ROLLBACK="$2"
            shift 2
            ;;
        --apply) APPLY=true; shift ;;
        --help) usage; exit 0 ;;
        *) usage >&2; fail "Unknown argument: $1" ;;
    esac
done

if [[ -n "$CANDIDATE" && -n "$ROLLBACK" || -z "$CANDIDATE" && -z "$ROLLBACK" ]]; then
    usage >&2
    fail "Choose a candidate image or a rollback record."
fi
rag_execution_require_positive_integer ALICIA_RAG_RELEASE_HEALTH_ATTEMPTS "$ATTEMPTS"
rag_execution_require_non_negative_integer ALICIA_RAG_RELEASE_HEALTH_RETRY_SECONDS "$RETRY_DELAY"
cd "$PROJECT_DIR"
[[ -f .env ]] || fail "The project's .env is missing."
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || fail "Tracked deployment files have local changes."

COMPOSE=(compose --env-file .env -f compose.yaml -f compose.https.yaml)
compose() { rag_execution_docker "${COMPOSE[@]}" "$@"; }

image_id() {
    rag_execution_docker image inspect --format '{{.Id}}' "$1"
}

is_image_id() { [[ "$1" =~ ^sha256:[0-9a-f]{64}$ ]]; }

configured_hash() {
    compose config --hash rag | awk '$1 == "rag" { print $2 }'
}

rag_container() { compose ps --all --quiet rag; }

service_health() {
    local url="$1" category="$2"
    curl -fsS --max-time 8 "$url" 2>/dev/null | python3 -c '
import json,sys
try:
    body=json.load(sys.stdin)
    ready=body["status"]=="ok"
    if sys.argv[1]=="liveness":
        ready=ready and body["deepseekConfigured"] is True and body["storageApiConfigured"] is True
except (ValueError, KeyError, TypeError):
    ready=False
sys.exit(0 if ready else 1)
' "$category"
}

rag_ready() {
    service_health "$LOCAL_HEALTH" liveness &&
        service_health "$LOCAL_HEALTH/dependencies" dependencies &&
        service_health "$PUBLIC_HEALTH" liveness
}

wait_for_rag() {
    local expected="$1" container attempt deadline=$((SECONDS + 90))
    for ((attempt = 1; attempt <= ATTEMPTS && SECONDS < deadline; attempt++)); do
        container="$(rag_container)" || return 1
        if [[ -n "$container" &&
            "$(rag_execution_docker inspect --format '{{.Image}} {{.State.Running}} {{.RestartCount}}' "$container" 2>/dev/null)" == "$expected true 0" ]] &&
            rag_ready; then
            return 0
        fi
        if [[ "$attempt" -lt "$ATTEMPTS" && "$SECONDS" -lt "$deadline" ]]; then sleep "$RETRY_DELAY"; fi
    done
    return 1
}

activate_image() {
    local image="$1"
    [[ "$(configured_hash)" == "$config_hash" ]] &&
        rag_execution_docker image tag "$image" "$service_image" &&
        compose up -d --no-deps --no-build --pull never --force-recreate rag &&
        wait_for_rag "$image"
}

project="$(compose config --format json rag | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')"
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "Could not determine the Compose project."
service_image="$project-rag"
config_hash="$(configured_hash)"
[[ "$config_hash" =~ ^[0-9a-f]{64}$ ]] || fail "Could not determine the RAG service configuration hash."
release_root="$PWD/deploy/generated/rag-releases"
state=""
locked=false
needs_restore=false
previous=""
rollback_image=""

finalize() {
    local rc="$?"
    trap - EXIT
    if [[ "$needs_restore" == true ]]; then
        printf '[FAIL] Candidate verification failed. Restoring the previous RAG image.\n' >&2
        if [[ "$(image_id "$rollback_image" 2>/dev/null)" == "$previous" ]] && activate_image "$previous"; then
            printf 'rolled-back\n' > "$state/status"
            printf '[OK] RAG rollback verified. Record: %s\n' "$state" >&2
        else
            printf 'rollback-failed\n' > "$state/status"
            printf '[FAIL] Automatic rollback was not verified. Inspect the release record: %s\n' "$state" >&2
        fi
        rc=1
    fi
    if [[ "$locked" == true ]]; then rmdir -- "$release_root/.lock" || rc=1; fi
    exit "$rc"
}

if [[ "$APPLY" == true ]]; then
    umask 077
    mkdir -p "$release_root"
    mkdir "$release_root/.lock" || fail "Another RAG release or rollback holds the lock."
    locked=true
    trap finalize EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
fi

container="$(rag_container)"
[[ "$container" != *$'\n'* ]] || fail "This release entry supports exactly one RAG replica."
current=""
if [[ -n "$container" ]]; then
    current="$(rag_execution_docker inspect --format '{{.Image}}' "$container")"
    config_label="$(rag_execution_docker inspect --format '{{index .Config.Labels "com.docker.compose.config-hash"}}' "$container")"
    current_reference="$(rag_execution_docker inspect --format '{{.Config.Image}}' "$container")"
    [[ "$config_label" == "$config_hash" &&
        ( "$current_reference" == "$service_image" || "$current_reference" == "$service_image:latest" ) ]] ||
        fail "The running RAG container does not match the current Compose image alias and configuration."
fi
alias_image="$(image_id "$service_image" 2>/dev/null || true)"

if [[ -n "$CANDIDATE" ]]; then
    is_image_id "$CANDIDATE" || fail "The candidate must be a full sha256 Docker image ID."
    [[ "$(image_id "$CANDIDATE" 2>/dev/null)" == "$CANDIDATE" ]] || fail "The exact candidate image is not loaded."
    is_image_id "$current" && [[ "$current" == "$alias_image" && "$current" != "$CANDIDATE" ]] ||
        fail "The service tag must point to the running RAG image, and the candidate must differ."
    [[ "$(rag_execution_docker inspect --format '{{.State.Running}}' "$container")" == "true" ]] && rag_ready ||
        fail "The current RAG instance must be healthy before switching."
    previous="$current"
    printf 'RAG image switch: project=%s previous=%s candidate=%s\n' "$project" "$previous" "$CANDIDATE"
else
    [[ -d "$release_root" ]] || fail "No local RAG release records exist."
    state="$(cd -P -- "$ROLLBACK" && pwd -P)" || fail "The rollback record directory does not exist."
    [[ "$(dirname "$state")" == "$(cd -P -- "$release_root" && pwd -P)" &&
        "$(basename "$state")" =~ ^release-[A-Za-z0-9_-]+$ && -f "$state/images" && ! -L "$state/images" ]] ||
        fail "Choose an existing record under the project's RAG release directory."
    [[ -f "$release_root/latest" && ! -L "$release_root/latest" &&
        "$(cat "$release_root/latest")" == "$(basename "$state")" ]] ||
        fail "Only the most recent RAG release record can be rolled back."
    mapfile -t record < "$state/images"
    [[ "${#record[@]}" -eq 4 && "${record[0]}" == "$project" && "${record[1]}" == "$config_hash" ]] ||
        fail "The rollback record belongs to a different project or configuration."
    previous="${record[2]}"
    CANDIDATE="${record[3]}"
    rollback_image="alicia-rag-rollback:$(basename "$state")"
    is_image_id "$previous" && is_image_id "$CANDIDATE" &&
        [[ "$(image_id "$rollback_image" 2>/dev/null)" == "$previous" &&
            ( "$alias_image" == "$CANDIDATE" || "$alias_image" == "$previous" ) &&
            ( -z "$current" || "$current" == "$CANDIDATE" || "$current" == "$previous" ) ]] ||
        fail "The backup is unavailable or another RAG version has since been deployed."
    printf 'RAG image rollback: project=%s restore=%s record=%s\n' "$project" "$previous" "$state"
fi

if [[ "$APPLY" == false ]]; then
    printf 'Dry run complete. Only the RAG service would be recreated. Supply --apply to proceed.\n'
    exit 0
fi

if [[ -n "$ROLLBACK" ]]; then
    if [[ "$current" != "$previous" || "$alias_image" != "$previous" ]] || ! rag_ready; then
        if ! activate_image "$previous"; then
            printf 'rollback-failed\n' > "$state/status"
            fail "RAG rollback was not verified. Release record: $state"
        fi
    fi
    printf 'rolled-back\n' > "$state/status"
    printf '[OK] RAG rollback verified. Record: %s\n' "$state"
    exit 0
fi

state="$(mktemp -d "$release_root/release-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")"
rollback_image="alicia-rag-rollback:$(basename "$state")"
rag_execution_docker image tag "$previous" "$rollback_image"
printf '%s\n' "$project" "$config_hash" "$previous" "$CANDIDATE" > "$state/images"
printf 'prepared\n' > "$state/status"
printf '%s\n' "$(basename "$state")" > "$state/latest"
mv -f -- "$state/latest" "$release_root/latest"
printf 'Rollback image: %s. Release record: %s\n' "$rollback_image" "$state"
needs_restore=true
activate_image "$CANDIDATE" || fail "The candidate RAG image did not become ready."
printf 'applied\n' > "$state/status"
needs_restore=false
printf '[OK] RAG candidate verified. Record: %s\n' "$state"
