#!/usr/bin/env bash
set -Eeuo pipefail

MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-$HOME/mainSite}"
CLOUD_PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
MAIN_SITE_UPDATE_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/update-main-site-production.sh"
CLOUD_UPDATE_SCRIPT="$CLOUD_PROJECT_DIR/deploy/scripts/update-cloud-production.sh"
SKIP_MAIN_SITE_UPDATE="${ALICIA_SKIP_MAIN_SITE_UPDATE:-false}"
SKIP_CLOUD_UPDATE="${ALICIA_SKIP_CLOUD_UPDATE:-false}"

if [[ "$SKIP_MAIN_SITE_UPDATE" != "true" ]]; then
    if [[ -f "$MAIN_SITE_UPDATE_SCRIPT" ]]; then
        bash "$MAIN_SITE_UPDATE_SCRIPT" "$MAIN_SITE_PROJECT_DIR"
    else
        printf 'Main site update script is missing; running fallback update for %s\n' "$MAIN_SITE_PROJECT_DIR"
        cd "$MAIN_SITE_PROJECT_DIR"
        if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
            printf 'Tracked mainSite server files have local changes; refusing to overwrite them.\n' >&2
            git status --short --untracked-files=no >&2
            exit 1
        fi
        git fetch "${ALICIA_MAIN_SITE_GIT_REMOTE:-origin}" "${ALICIA_MAIN_SITE_GIT_BRANCH:-master}"
        git pull --ff-only "${ALICIA_MAIN_SITE_GIT_REMOTE:-origin}" "${ALICIA_MAIN_SITE_GIT_BRANCH:-master}"
        sudo docker compose up -d --build frontend
        bash deploy/scripts/verify-main-site-routes.sh
    fi
fi

if [[ "$SKIP_CLOUD_UPDATE" != "true" ]]; then
    [[ -f "$CLOUD_UPDATE_SCRIPT" ]] || {
        printf 'Missing cloud update script: %s\n' "$CLOUD_UPDATE_SCRIPT" >&2
        exit 1
    }
    bash "$CLOUD_UPDATE_SCRIPT" "$CLOUD_PROJECT_DIR" "$@"
fi

printf 'Alicia main/cloud production update completed.\n'
