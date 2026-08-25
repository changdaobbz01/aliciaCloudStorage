#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
if [[ $# -gt 0 && -d "$1" && -f "$1/compose.yaml" ]]; then
    PROJECT_DIR="$1"
    shift
fi

GIT_REMOTE="${ALICIA_CLOUD_GIT_REMOTE:-gitee}"
GIT_BRANCH="${ALICIA_CLOUD_GIT_BRANCH:-main}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
GATEWAY_NETWORK="${ALICIA_GATEWAY_NETWORK:-alicia_gateway}"
SKIP_GIT_PULL="${ALICIA_SKIP_GIT_PULL:-false}"
SKIP_ROUTE_VERIFY="${ALICIA_SKIP_ROUTE_VERIFY:-false}"
SKIP_BOUNDARY_CHECK="${ALICIA_SKIP_BOUNDARY_CHECK:-false}"
BACKUP_BEFORE_UPDATE="${ALICIA_BACKUP_BEFORE_UPDATE:-false}"
COLLECT_STATUS="${ALICIA_COLLECT_STATUS_AFTER_UPDATE:-false}"

if [[ $# -gt 0 ]]; then
    SERVICES=("$@")
else
    read -r -a SERVICES <<< "${ALICIA_CLOUD_DEPLOY_SERVICES:-frontend}"
fi

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

fail_if_tracked_changes() {
    if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
        printf 'Tracked server files have local changes; refusing to overwrite them.\n' >&2
        git status --short --untracked-files=no >&2
        exit 1
    fi
}

ensure_gateway_network() {
    if ! docker_cmd network inspect "$GATEWAY_NETWORK" >/dev/null 2>&1; then
        docker_cmd network create "$GATEWAY_NETWORK" >/dev/null
        printf '[OK] created Docker network %s\n' "$GATEWAY_NETWORK"
    fi
}

cd "$PROJECT_DIR"

[[ -f .env ]] || { printf 'Missing %s/.env\n' "$PROJECT_DIR" >&2; exit 1; }
for file in $COMPOSE_FILES; do
    [[ -f "$file" ]] || { printf 'Missing compose file %s/%s\n' "$PROJECT_DIR" "$file" >&2; exit 1; }
done
[[ -f deploy/scripts/verify-identity-cloud-routes.sh ]] || {
    printf 'Missing %s/deploy/scripts/verify-identity-cloud-routes.sh\n' "$PROJECT_DIR" >&2
    exit 1
}
[[ -f deploy/scripts/check-identity-route-boundary.sh ]] || {
    printf 'Missing %s/deploy/scripts/check-identity-route-boundary.sh\n' "$PROJECT_DIR" >&2
    exit 1
}
[[ "$BACKUP_BEFORE_UPDATE" != "true" || -f deploy/scripts/backup-production-data.sh ]] || {
    printf 'Missing %s/deploy/scripts/backup-production-data.sh\n' "$PROJECT_DIR" >&2
    exit 1
}
[[ "$COLLECT_STATUS" != "true" || -f deploy/scripts/collect-production-status.sh ]] || {
    printf 'Missing %s/deploy/scripts/collect-production-status.sh\n' "$PROJECT_DIR" >&2
    exit 1
}

fail_if_tracked_changes
ensure_gateway_network

if [[ "$SKIP_GIT_PULL" != "true" ]]; then
    git fetch "$GIT_REMOTE" "$GIT_BRANCH"
    git pull --ff-only "$GIT_REMOTE" "$GIT_BRANCH"
fi

git log --oneline -3

if [[ "$BACKUP_BEFORE_UPDATE" == "true" ]]; then
    bash deploy/scripts/backup-production-data.sh
fi

compose up -d --build "${SERVICES[@]}"

if [[ "$SKIP_ROUTE_VERIFY" != "true" ]]; then
    bash deploy/scripts/verify-identity-cloud-routes.sh
fi

if [[ "$SKIP_BOUNDARY_CHECK" != "true" ]]; then
    bash deploy/scripts/check-identity-route-boundary.sh
fi

if [[ "$COLLECT_STATUS" == "true" ]]; then
    bash deploy/scripts/collect-production-status.sh
fi

compose ps
printf 'Alicia cloud production update completed: %s\n' "$(git rev-parse --short HEAD)"
