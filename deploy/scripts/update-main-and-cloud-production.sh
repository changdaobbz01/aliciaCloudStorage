#!/usr/bin/env bash
set -Eeuo pipefail

MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-$HOME/mainSite}"
CLOUD_PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$HOME/aliciaCloudStorage}"
MAIN_SITE_UPDATE_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/update-main-site-production.sh"
MAIN_SITE_ROUTE_VERIFY_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/verify-main-site-routes.sh"
MAIN_SITE_BOUNDARY_SCRIPT="$MAIN_SITE_PROJECT_DIR/deploy/scripts/check-main-site-frontend-boundaries.sh"
CLOUD_UPDATE_SCRIPT="$CLOUD_PROJECT_DIR/deploy/scripts/update-cloud-production.sh"
SKIP_MAIN_SITE_UPDATE="${ALICIA_SKIP_MAIN_SITE_UPDATE:-false}"
SKIP_CLOUD_UPDATE="${ALICIA_SKIP_CLOUD_UPDATE:-false}"
SKIP_FINAL_MAIN_SITE_VERIFY="${ALICIA_SKIP_FINAL_MAIN_SITE_VERIFY:-${ALICIA_SKIP_VERIFY:-false}}"
VERIFY_MAIN_SITE_PUBLIC_DURING_JOINT_UPDATE="${ALICIA_VERIFY_MAIN_SITE_PUBLIC_DURING_JOINT_UPDATE:-false}"

should_defer_main_site_public_boundary() {
    [[ "$SKIP_CLOUD_UPDATE" != "true" ]] \
        && [[ "$VERIFY_MAIN_SITE_PUBLIC_DURING_JOINT_UPDATE" != "true" ]] \
        && [[ -z "${ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY+x}" ]]
}

run_main_site_update_script() {
    if should_defer_main_site_public_boundary; then
        printf 'Deferring main site public gateway checks until the cloud frontend update completes.\n'
        ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY=true bash "$MAIN_SITE_UPDATE_SCRIPT" "$MAIN_SITE_PROJECT_DIR"
    else
        bash "$MAIN_SITE_UPDATE_SCRIPT" "$MAIN_SITE_PROJECT_DIR"
    fi
}

run_main_site_route_verify() {
    if should_defer_main_site_public_boundary; then
        printf 'Deferring main site public gateway checks until the cloud frontend update completes.\n'
        ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY=true bash deploy/scripts/verify-main-site-routes.sh
    else
        bash deploy/scripts/verify-main-site-routes.sh
    fi
}

run_final_main_site_route_verify() {
    [[ "$SKIP_FINAL_MAIN_SITE_VERIFY" != "true" ]] || return 0
    [[ "$SKIP_MAIN_SITE_UPDATE" != "true" ]] || return 0
    [[ "$SKIP_CLOUD_UPDATE" != "true" ]] || return 0
    [[ -f "$MAIN_SITE_ROUTE_VERIFY_SCRIPT" ]] || {
        printf 'Missing main site route verify script: %s\n' "$MAIN_SITE_ROUTE_VERIFY_SCRIPT" >&2
        exit 1
    }

    printf 'Running final main site route verification through the updated cloud frontend gateway.\n'
    (cd "$MAIN_SITE_PROJECT_DIR" && bash deploy/scripts/verify-main-site-routes.sh)
}

if [[ "$SKIP_MAIN_SITE_UPDATE" != "true" ]]; then
    if [[ -f "$MAIN_SITE_UPDATE_SCRIPT" ]]; then
        run_main_site_update_script
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
        run_main_site_route_verify
        [[ -f "$MAIN_SITE_BOUNDARY_SCRIPT" ]] || {
            printf 'Missing main site boundary script: %s\n' "$MAIN_SITE_BOUNDARY_SCRIPT" >&2
            exit 1
        }
        bash deploy/scripts/check-main-site-frontend-boundaries.sh
    fi
fi

if [[ "$SKIP_CLOUD_UPDATE" != "true" ]]; then
    [[ -f "$CLOUD_UPDATE_SCRIPT" ]] || {
        printf 'Missing cloud update script: %s\n' "$CLOUD_UPDATE_SCRIPT" >&2
        exit 1
    }
    bash "$CLOUD_UPDATE_SCRIPT" "$CLOUD_PROJECT_DIR" "$@"
fi

run_final_main_site_route_verify

printf 'Alicia main/cloud production update completed.\n'
