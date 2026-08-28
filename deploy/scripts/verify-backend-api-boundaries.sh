#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="${ALICIA_BACKEND_BOUNDARY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

resolve_maven_command() {
    if [[ -f "$ROOT_DIR/mvnw" ]]; then
        MAVEN_COMMAND=(bash "$ROOT_DIR/mvnw")
        return
    fi

    if command -v mvn >/dev/null 2>&1; then
        MAVEN_COMMAND=(mvn)
        return
    fi

    fail "Maven is required. Expected mvnw in the repository root or mvn on PATH."
}

run_step() {
    local label="$1"
    shift

    printf '[RUN] %s\n' "$label"
    "$@"
    ok "$label"
}

run_maven_boundary_tests() {
    local module="$1"
    local tests="$2"

    (cd "$ROOT_DIR" && "${MAVEN_COMMAND[@]}" -pl "$module" "-Dtest=$tests" test)
}

resolve_maven_command

run_step "CloudStorageApi legacy and route ownership boundaries" \
    run_maven_boundary_tests "CloudStorageApi" "IdentityRouteBoundaryTest,CloudApiRouteOwnershipTest,CurrentPrincipalTest"

run_step "identityApi source, route, and admin access boundaries" \
    run_maven_boundary_tests "identityApi" "IdentitySourceBoundaryTest,IdentityApiRouteOwnershipTest"

ok "backend API boundary verification complete"
