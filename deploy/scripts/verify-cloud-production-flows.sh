#!/usr/bin/env bash
set -Eeuo pipefail

DEFAULT_PROJECT_DIR="$HOME/aliciaCloudStorage"
if [[ -f compose.yaml ]]; then
    DEFAULT_PROJECT_DIR="$PWD"
fi

PROJECT_DIR="${ALICIA_CLOUD_PROJECT_DIR:-$DEFAULT_PROJECT_DIR}"
if [[ $# -gt 0 && -d "$1" && -f "$1/compose.yaml" ]]; then
    PROJECT_DIR="$1"
    shift
fi

SKIP_ROUTE_VERIFY="${ALICIA_PRODUCTION_FLOW_SKIP_ROUTE_VERIFY:-false}"
SKIP_BOUNDARY_CHECK="${ALICIA_PRODUCTION_FLOW_SKIP_BOUNDARY_CHECK:-false}"
SKIP_STORAGE_VERIFY="${ALICIA_PRODUCTION_FLOW_SKIP_STORAGE_VERIFY:-false}"
SKIP_SHARE_VERIFY="${ALICIA_PRODUCTION_FLOW_SKIP_SHARE_VERIFY:-false}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

require_script() {
    local script_path="$1"
    [[ -f "$script_path" ]] || fail "Missing $PROJECT_DIR/$script_path"
}

read_identity_credentials() {
    local account="${ALICIA_VERIFY_ACCOUNT:-${ALICIA_IDENTITY_ACCOUNT:-}}"
    local password="${ALICIA_VERIFY_PASSWORD:-${ALICIA_IDENTITY_PASSWORD:-}}"

    if [[ -z "$account" ]]; then
        read -r -p "Identity account/email/phone: " account
    fi

    if [[ -z "$password" ]]; then
        read -r -s -p "Identity password: " password
        printf '\n'
    fi

    export ALICIA_VERIFY_ACCOUNT="$account"
    export ALICIA_VERIFY_PASSWORD="$password"
    export ALICIA_IDENTITY_ACCOUNT="$account"
    export ALICIA_IDENTITY_PASSWORD="$password"
}

run_step() {
    local label="$1"
    shift

    printf '\n==> %s\n' "$label"
    "$@"
    ok "$label"
}

cd "$PROJECT_DIR"

require_script deploy/scripts/verify-identity-cloud-routes.sh
require_script deploy/scripts/check-identity-route-boundary.sh
require_script deploy/scripts/check-frontend-console-boundaries.sh
require_script deploy/scripts/verify-cloud-storage-flow.sh
require_script deploy/scripts/verify-cloud-share-flow.sh

printf 'Verifying Alicia production flows...\n'
printf 'Project: %s\n' "$PROJECT_DIR"

read_identity_credentials

if [[ "$SKIP_ROUTE_VERIFY" != "true" ]]; then
    run_step "identity/cloud route boundary runtime verification" \
        bash deploy/scripts/verify-identity-cloud-routes.sh
fi

if [[ "$SKIP_STORAGE_VERIFY" != "true" ]]; then
    run_step "cloud storage operation flow verification" \
        bash deploy/scripts/verify-cloud-storage-flow.sh
fi

if [[ "$SKIP_SHARE_VERIFY" != "true" ]]; then
    run_step "cloud share operation flow verification" \
        bash deploy/scripts/verify-cloud-share-flow.sh
fi

if [[ "$SKIP_BOUNDARY_CHECK" != "true" ]]; then
    run_step "identity route static boundary check" \
        bash deploy/scripts/check-identity-route-boundary.sh
    run_step "frontend console static boundary check" \
        bash deploy/scripts/check-frontend-console-boundaries.sh
fi

printf '\nAlicia production flow verification passed.\n'
