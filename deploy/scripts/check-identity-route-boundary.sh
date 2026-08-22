#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="${ALICIA_ROUTE_BOUNDARY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

TARGETS=(
    webApp/src
    phoneApp/app/src/main
    phoneAppAdd/app/src/main
    CloudStorageApi/src/main
    identityApi/src/main
    deploy
)

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

cd "$ROOT_DIR"

PATTERN='(/api/auth|api/auth|/api/admin/users|api/admin/users)'
EXISTING_TARGETS=()
for target in "${TARGETS[@]}"; do
    if [[ -e "$target" ]]; then
        EXISTING_TARGETS+=("$target")
    fi
done

[[ "${#EXISTING_TARGETS[@]}" -gt 0 ]] || fail "No route boundary scan targets exist."

if command -v rg >/dev/null 2>&1; then
    matches="$(
        rg -n "$PATTERN" "${EXISTING_TARGETS[@]}" \
            --glob '!deploy/generated/**' \
            --glob '!deploy/scripts/check-identity-route-boundary.sh' \
            --glob '!deploy/scripts/verify-identity-cloud-routes.sh' \
            --glob '!**/dist/**' \
            --glob '!**/target/**' || true
    )"
else
    command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
    matches="$(
        grep -RInE \
            --exclude='check-identity-route-boundary.sh' \
            --exclude='verify-identity-cloud-routes.sh' \
            --exclude-dir='generated' \
            --exclude-dir='dist' \
            --exclude-dir='target' \
            "$PATTERN" "${EXISTING_TARGETS[@]}" || true
    )"
fi

if [[ -n "$matches" ]]; then
    printf '%s\n' "$matches" >&2
    fail "Legacy identity route references remain in source or deploy files."
fi

ok "no legacy /api/auth/** or /api/admin/users references in source/deploy boundary"
