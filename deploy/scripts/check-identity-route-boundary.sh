#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="${ALICIA_ROUTE_BOUNDARY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

TARGETS=(
    webApp/src
    phoneApp/app/src/main
    phoneAppAdd/app/src/main
    CloudStorageApi/src/main
    deploy
)
RUNTIME_IDENTITY_TABLE_TARGETS=(
    webApp/src
    phoneApp/app/src/main
    phoneAppAdd/app/src/main
    CloudStorageApi/src/main/java
    rag/src
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
SYS_USER_TABLE_PATTERN='(^|[^[:alnum:]_])sys_user([^[:alnum:]_]|$)'
EXISTING_TARGETS=()
for target in "${TARGETS[@]}"; do
    if [[ -e "$target" ]]; then
        EXISTING_TARGETS+=("$target")
    fi
done
EXISTING_RUNTIME_IDENTITY_TABLE_TARGETS=()
for target in "${RUNTIME_IDENTITY_TABLE_TARGETS[@]}"; do
    if [[ -e "$target" ]]; then
        EXISTING_RUNTIME_IDENTITY_TABLE_TARGETS+=("$target")
    fi
done

[[ "${#EXISTING_TARGETS[@]}" -gt 0 ]] || fail "No route boundary scan targets exist."
[[ "${#EXISTING_RUNTIME_IDENTITY_TABLE_TARGETS[@]}" -gt 0 ]] || fail "No runtime identity table scan targets exist."

if command -v rg >/dev/null 2>&1; then
    matches="$(
        rg -n "$PATTERN" "${EXISTING_TARGETS[@]}" \
            --glob '!deploy/generated/**' \
            --glob '!deploy/scripts/check-identity-route-boundary.sh' \
            --glob '!deploy/scripts/collect-production-status.sh' \
            --glob '!deploy/scripts/drop-cloud-identity-residue.sh' \
            --glob '!deploy/scripts/verify-identity-cloud-routes.sh' \
            --glob '!**/dist/**' \
            --glob '!**/target/**' || true
    )"
    sys_user_table_matches="$(
        rg -n "$SYS_USER_TABLE_PATTERN" "${EXISTING_RUNTIME_IDENTITY_TABLE_TARGETS[@]}" \
            --glob '!deploy/generated/**' \
            --glob '!deploy/scripts/check-identity-route-boundary.sh' \
            --glob '!deploy/scripts/collect-production-status.sh' \
            --glob '!deploy/scripts/drop-cloud-identity-residue.sh' \
            --glob '!deploy/scripts/verify-identity-cloud-routes.sh' \
            --glob '!**/dist/**' \
            --glob '!**/target/**' || true
    )"
else
    command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
    matches="$(
        grep -RInE \
            --exclude='check-identity-route-boundary.sh' \
            --exclude='collect-production-status.sh' \
            --exclude='drop-cloud-identity-residue.sh' \
            --exclude='verify-identity-cloud-routes.sh' \
            --exclude-dir='generated' \
            --exclude-dir='dist' \
            --exclude-dir='target' \
            "$PATTERN" "${EXISTING_TARGETS[@]}" || true
    )"
    sys_user_table_matches="$(
        grep -RInE \
            --exclude='check-identity-route-boundary.sh' \
            --exclude='collect-production-status.sh' \
            --exclude='drop-cloud-identity-residue.sh' \
            --exclude='verify-identity-cloud-routes.sh' \
            --exclude-dir='generated' \
            --exclude-dir='dist' \
            --exclude-dir='target' \
            "$SYS_USER_TABLE_PATTERN" "${EXISTING_RUNTIME_IDENTITY_TABLE_TARGETS[@]}" || true
    )"
fi

if [[ -n "$matches" ]]; then
    printf '%s\n' "$matches" >&2
    fail "Legacy identity route references remain in source or deploy files."
fi

if [[ -n "$sys_user_table_matches" ]]; then
    printf '%s\n' "$sys_user_table_matches" >&2
    fail "Legacy sys_user table references remain in runtime source or deploy files."
fi

ok "no legacy /api/auth/** or /api/admin/users references in source/deploy boundary"
ok "no runtime references to legacy sys_user table"
