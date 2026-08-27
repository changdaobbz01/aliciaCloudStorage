#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="${ALICIA_FRONTEND_BOUNDARY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

scan_target() {
    local label="$1"
    local target="$2"
    local pattern="$3"
    local message="$4"
    local matches

    [[ -e "$target" ]] || fail "$label scan target does not exist: $target"

    if command -v rg >/dev/null 2>&1; then
        matches="$(
            rg -n "$pattern" "$target" \
                --glob '*.ts' \
                --glob '*.tsx' \
                --glob '!**/dist/**' \
                --glob '!**/node_modules/**' || true
        )"
    else
        command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
        matches="$(
            find "$target" -type f \( -name '*.ts' -o -name '*.tsx' \) \
                ! -path '*/dist/*' \
                ! -path '*/node_modules/*' \
                -print0 | xargs -0 grep -nE "$pattern" || true
        )"
    fi

    if [[ -n "$matches" ]]; then
        printf '%s\n' "$matches" >&2
        fail "$message"
    fi

    ok "$label"
}

cd "$ROOT_DIR"

scan_target \
    "cloud web has no admin frontend references" \
    "webApp/src" \
    '(/api/admin/|/api/identity/admin/|(^|[^[:alnum:]_])(AdminCloud[[:alnum:]_]*|IdentityAudit[[:alnum:]_]*|fetchUsers|createUser|updateUserStorageQuota|resetUserPassword|fetchAdminCloud[[:alnum:]_]*|fetchAdminAppPackage|uploadAdminAppPackage|deleteAdminAppPackage)($|[^[:alnum:]_]))' \
    "Cloud web user client contains admin API or admin type references."

scan_target \
    "cloud console has no identity or personal drive references" \
    "sysManage/src" \
    '(/api/identity/admin/|/api/admin/users($|[^[:alnum:]_])|/api/storage/|/api/share-links/|/api/public/share-links/|(^|[^[:alnum:]_])(IdentityAudit[[:alnum:]_]*|IdentityApplicationRole[[:alnum:]_]*|UpdateIdentityApplicationRole[[:alnum:]_]*|StorageViewMode|StorageFileCategory|StorageNodeFilter|StorageNodeSortField|fetchIdentitySessions|revokeIdentitySession|changePassword|updateProfile|fetchDriveOverview|fetchStorageNodes|createFolder|uploadStorageFile|createShareLink|downloadStorage[[:alnum:]_]*|renameStorage[[:alnum:]_]*|moveStorage[[:alnum:]_]*|deleteStorage[[:alnum:]_]*|restoreStorage[[:alnum:]_]*|permanentlyDelete[[:alnum:]_]*)($|[^[:alnum:]_]))' \
    "Cloud admin console contains identity-admin or personal drive API/type references."
