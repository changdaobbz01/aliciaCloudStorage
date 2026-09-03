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

run_node_script() {
    local label="$1"
    local relative_script="$2"

    command -v node >/dev/null 2>&1 || fail "Node.js is required for $label."
    [[ -f "$relative_script" ]] || fail "Missing $label script: $relative_script"

    printf '[RUN] %s\n' "$label"
    node "$relative_script" || fail "$label failed."
    ok "$label"
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

require_source_match() {
    local label="$1"
    local target="$2"
    local pattern="$3"
    local message="$4"

    [[ -e "$target" ]] || fail "$label source target does not exist: $target"

    if command -v rg >/dev/null 2>&1; then
        rg -q "$pattern" "$target" || fail "$message"
    else
        command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
        grep -Eq "$pattern" "$target" || fail "$message"
    fi

    ok "$label"
}

require_source_count_at_least() {
    local label="$1"
    local target="$2"
    local pattern="$3"
    local minimum_count="$4"
    local message="$5"
    local count

    [[ -e "$target" ]] || fail "$label source target does not exist: $target"

    if command -v rg >/dev/null 2>&1; then
        count="$( (rg -o "$pattern" "$target" || true) | wc -l | tr -d '[:space:]')"
    else
        command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
        count="$( (grep -Eo "$pattern" "$target" || true) | wc -l | tr -d '[:space:]')"
    fi

    if (( count < minimum_count )); then
        fail "$message Found $count, expected at least $minimum_count."
    fi

    ok "$label"
}

require_source_no_match() {
    local label="$1"
    local target="$2"
    local pattern="$3"
    local message="$4"
    local matches

    [[ -e "$target" ]] || fail "$label source target does not exist: $target"

    if command -v rg >/dev/null 2>&1; then
        matches="$(rg -n "$pattern" "$target" || true)"
    else
        command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
        matches="$(grep -nE "$pattern" "$target" || true)"
    fi

    if [[ -n "$matches" ]]; then
        printf '%s\n' "$matches" >&2
        fail "$message"
    fi

    ok "$label"
}

cd "$ROOT_DIR"

run_node_script "cloud web returnTo boundary" "webApp/scripts/verify-unified-login-return-to.mjs"
run_node_script "cloud web session sync boundary" "webApp/scripts/verify-session-sync.mjs"
run_node_script "cloud web client boundary" "webApp/scripts/verify-client-boundary.mjs"
run_node_script "cloud web CloudStorageApi contract" "webApp/scripts/verify-api-contracts.mjs"
run_node_script "cloud console returnTo boundary" "sysManage/scripts/verify-unified-login-return-to.mjs"
run_node_script "cloud console session sync boundary" "sysManage/scripts/verify-session-sync.mjs"
run_node_script "cloud console boundary" "sysManage/scripts/verify-console-boundary.mjs"
run_node_script "cloud console CloudStorageApi contract" "sysManage/scripts/verify-api-contracts.mjs"

require_source_no_match \
    "cloud Bash boundary avoids TypeScript-gated returnTo checks" \
    "deploy/scripts/check-frontend-console-boundaries.sh" \
    'run_''typescript_node_script' \
    "Cloud Bash boundary check must not skip returnTo verification when TypeScript packages are missing."

LEGACY_CLOUD_ADMIN_PREFIX='/api/admin'
LEGACY_CLOUD_ADMIN_USERS_PATTERN="${LEGACY_CLOUD_ADMIN_PREFIX}/users($|[^[:alnum:]_])"

scan_target \
    "cloud web has no admin frontend references" \
    "webApp/src" \
    '(/api/admin/|/api/identity/admin/|(^|[^[:alnum:]_])(isCloudAdmin|CLOUD_ADMIN|AdminCloud[[:alnum:]_]*|IdentityAudit[[:alnum:]_]*|fetchUsers|createUser|updateUserStorageQuota|resetUserPassword|fetchAdminCloud[[:alnum:]_]*|fetchAdminAppPackage|uploadAdminAppPackage|deleteAdminAppPackage)($|[^[:alnum:]_]))' \
    "Cloud web user client contains admin API or admin type references."

require_source_no_match \
    "cloud web exposes no console routes" \
    "webApp/src/App.tsx" \
    'path="/console' \
    "Cloud web user client must not mount admin console routes."

require_source_no_match \
    "cloud web account menu exposes no console entry" \
    "webApp/src/pages/DrivePage.tsx" \
    '管理控制台|consoleHome|/console(/|$)' \
    "Cloud web account menu must not expose admin console entry points."

require_source_no_match \
    "cloud web APK download panel uses user copy" \
    "webApp/src/pages/DrivePage.tsx" \
    'APK|等待上传|开放下载' \
    "Cloud web app download panel must not expose APK artifact or upload/admin wording."

require_source_match \
    "cloud web verifies profile wording boundary" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud drive page must describe user-facing profile tools without admin management wording' \
    "Cloud web boundary verifier must reject admin management wording in user-facing drive page copy."

require_source_match \
    "cloud web verifies APK download copy boundary" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web app download panel must use Android installer copy without APK upload/admin wording' \
    "Cloud web boundary verifier must reject APK artifact wording in the user-facing download panel."

require_source_match \
    "cloud web verifies app download copy boundary" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud app download page must describe package availability as a user-facing download state' \
    "Cloud web boundary verifier must reject release workflow wording in the app download page."

require_source_match \
    "cloud web normalizes app download paths" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web app download URL resolver must normalize download paths' \
    "Cloud web boundary verifier must normalize app download URLs."

require_source_match \
    "cloud web keeps app downloads on public endpoint" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web app download URL resolver must stay on the public package endpoint' \
    "Cloud web boundary verifier must keep app downloads on the public package endpoint."

require_source_match \
    "cloud web keeps app downloads same-origin" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web app download URL resolver must produce same-origin public download URLs' \
    "Cloud web boundary verifier must keep app downloads same-origin."

require_source_match \
    "cloud web share revocation tracks pending row" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web share revocation must track the pending share id' \
    "Cloud web boundary verifier must track pending share revocation."

require_source_match \
    "cloud web share revocation blocks duplicates" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web share revocation must block duplicate submissions and clear pending state' \
    "Cloud web boundary verifier must block duplicate share revocation."

require_source_match \
    "cloud web shares view shows pending revocation" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web shares view must surface pending share revocation' \
    "Cloud web shares view must surface pending share revocation."

require_source_match \
    "cloud web shares view blocks refresh during revocation" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web shares view must not refresh while a share revocation is pending' \
    "Cloud web shares view must avoid refresh while share revocation is pending."

require_source_match \
    "cloud drive page wires share revocation pending state" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud drive page must wire share revocation pending state to the UI' \
    "Cloud drive page must wire share revocation pending state."

require_source_match \
    "cloud web storage mutation state covers file actions" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage mutation state must cover all personal file mutations' \
    "Cloud web storage mutation state must cover all personal file mutations."

require_source_match \
    "cloud web storage mutations track pending operation" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage mutations must track a single pending operation' \
    "Cloud web storage mutations must track a single pending operation."

require_source_match \
    "cloud web storage mutations block duplicates" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage mutations must block duplicate submissions' \
    "Cloud web storage mutations must block duplicate submissions."

require_source_match \
    "cloud web storage dialogs block pending close and submit" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage dialogs must block close and submit while a storage mutation is pending' \
    "Cloud web storage dialogs must block pending close and submit."

require_source_match \
    "cloud web storage modals show pending state" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage modals must surface pending create, rename, and move submissions' \
    "Cloud web storage modals must surface pending create, rename, and move submissions."

require_source_match \
    "cloud web explorer toolbar shows storage pending state" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web explorer toolbar must surface pending storage mutations' \
    "Cloud web explorer toolbar must surface pending storage mutations."

require_source_match \
    "cloud web storage table shows pending row mutations" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web storage table must surface pending row mutations and freeze selection' \
    "Cloud web storage table must surface pending row mutations and freeze selection."

require_source_match \
    "cloud drive page wires storage mutation pending state" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud drive page must wire storage mutation pending state to dialogs and tables' \
    "Cloud drive page must wire storage mutation pending state."

require_source_no_match \
    "cloud web has no admin style leftovers" \
    "webApp/src/index.css" \
    '\.account-admin-tabs|\.audit-(filter|quick|result)|\.operations-|\.app-package-(summary|grid|card|link|url|meta|release-notes|list)|\.management-summary-|\.user-cell-copy|\.user-chip|\.table-secondary-text' \
    "Cloud web stylesheet must not keep admin console style leftovers."

require_source_match \
    "cloud web API allowlist is enforced" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'assertApiPathsMatchAllowedPrefixes' \
    "Cloud web boundary verifier must enforce API ownership allowlists."

require_source_match \
    "cloud web API allowlist scans source files" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloudWebApiScopeFiles = files' \
    "Cloud web boundary verifier must scan all source files for API ownership."

require_source_match \
    "cloud web API allowlist reports source scope" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web source' \
    "Cloud web boundary verifier must report full-source API ownership failures."

require_source_match \
    "cloud web API allowlist separates returnTo sentinels" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'isReturnPathBoundarySentinel' \
    "Cloud web boundary verifier must keep returnTo sentinels out of API ownership matches."

require_source_match \
    "cloud web route boundary scans source files" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'assertNoForeignRoutePathLiterals' \
    "Cloud web boundary verifier must scan source files for foreign route exposure."

require_source_match \
    "cloud web route boundary rejects foreign routes" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'must not expose foreign route paths from the cloud web user client' \
    "Cloud web boundary verifier must reject console and RAG route exposure."

require_source_match \
    "cloud web API allowlist keeps personal storage ownership" \
    "webApp/scripts/verify-client-boundary.mjs" \
    "'/api/storage/'" \
    "Cloud web API allowlist must include personal storage APIs."

require_source_match \
    "cloud web API allowlist keeps personal share ownership" \
    "webApp/scripts/verify-client-boundary.mjs" \
    "'/api/share-links'" \
    "Cloud web API allowlist must include personal share APIs."

require_source_match \
    "cloud web API allowlist keeps identity auth ownership" \
    "webApp/scripts/verify-client-boundary.mjs" \
    "'/api/identity/auth/'" \
    "Cloud web API allowlist must include identity auth APIs."

require_source_match \
    "cloud web profile dialog contract is enforced" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web profile modal must keep the shared profile dialog contract' \
    "Cloud web profile modal must enforce the shared account profile contract."

require_source_match \
    "cloud web profile dialog rejects legacy aliases" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web profile modal must not keep legacy profile layout aliases' \
    "Cloud web profile modal must reject legacy profile layout aliases."

require_source_match \
    "cloud web profile styles reject legacy aliases" \
    "webApp/scripts/verify-client-boundary.mjs" \
    'cloud web styles must not keep legacy profile layout aliases' \
    "Cloud web profile style verifier must reject legacy profile layout aliases."

require_source_no_match \
    "cloud web profile source avoids legacy aliases" \
    "webApp/src/features/drive/DriveProfileModals.tsx" \
    'profile-avatar-preview-row|profile-avatar-actions' \
    "Cloud web profile modal must not keep legacy profile layout aliases."

require_source_no_match \
    "cloud web profile stylesheet avoids legacy aliases" \
    "webApp/src/index.css" \
    '\.profile-avatar-preview-row\b|\.profile-avatar-actions\b' \
    "Cloud web styles must not keep legacy profile layout aliases."

require_source_match \
    "cloud web exposes returnTo verifier" \
    "webApp/package.json" \
    '"verify:return-to"[[:space:]]*:[[:space:]]*"node scripts/verify-unified-login-return-to\.mjs"' \
    "Cloud web build must expose the unified login returnTo verifier."

require_source_match \
    "cloud web build runs returnTo verifier" \
    "webApp/package.json" \
    '"build"[[:space:]]*:[[:space:]]*"npm run verify:return-to && npm run verify:session-sync' \
    "Cloud web build must run returnTo verification before session and compile checks."

require_source_match \
    "cloud web returnTo verifier runs without TypeScript packages" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT' \
    "Cloud web returnTo verifier must run without installed TypeScript packages."

require_source_match \
    "cloud Bash boundary runs cloud web returnTo verifier" \
    "deploy/scripts/check-frontend-console-boundaries.sh" \
    'run_node_script "cloud web returnTo boundary"' \
    "Cloud Bash boundary check must run the cloud web returnTo verifier directly."

require_source_match \
    "cloud web exposes API contract verifier" \
    "webApp/package.json" \
    '"verify:api-contracts"[[:space:]]*:[[:space:]]*"node scripts/verify-api-contracts\.mjs"' \
    "Cloud web package must expose the CloudStorageApi contract verifier."

require_source_match \
    "cloud web build verifies API contracts" \
    "webApp/package.json" \
    'npm run verify:client-boundary && npm run verify:api-contracts && tsc -b' \
    "Cloud web build must verify CloudStorageApi contracts before TypeScript compile."

require_source_match \
    "cloud web contract verifier compares current profile response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'UserProfileResponse' \
    "Cloud web contract verifier must compare the CloudStorageApi current profile response."

require_source_match \
    "cloud web contract verifier compares storage node response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'StorageNodeSummaryResponse' \
    "Cloud web contract verifier must compare personal storage node responses."

require_source_match \
    "cloud web contract verifier compares multipart upload request" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'CreateMultipartUploadRequest' \
    "Cloud web contract verifier must compare multipart upload requests."

require_source_match \
    "cloud web contract verifier compares share detail response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'ShareLinkDetailResponse' \
    "Cloud web contract verifier must compare share detail responses."

require_source_match \
    "cloud web contract verifier compares query parameters" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractRequestParamsForMethod' \
    "Cloud web contract verifier must compare storage/share query parameters."

require_source_match \
    "cloud web contract verifier reads frontend path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractTemplatePathVariablesForFunction' \
    "Cloud web contract verifier must read frontend path variables."

require_source_match \
    "cloud web contract verifier reads backend path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractPathVariablesForMethod' \
    "Cloud web contract verifier must read backend path variables."

require_source_match \
    "cloud web contract verifier compares multipart path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'uploadMultipartPart path variables' \
    "Cloud web contract verifier must compare multipart path variables."

require_source_match \
    "cloud web contract verifier compares storage node path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'renameStorageNode path variables' \
    "Cloud web contract verifier must compare storage node path variables."

require_source_match \
    "cloud web contract verifier compares share file path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'fetchShareFileAccessUrl path variables' \
    "Cloud web contract verifier must compare share file path variables."

require_source_match \
    "cloud web contract verifier compares public share path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'fetchPublicShareStatus path variables' \
    "Cloud web contract verifier must compare public share path variables."

require_source_match \
    "cloud web contract verifier compares identity session path variables" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'revokeIdentitySession path variables' \
    "Cloud web contract verifier must compare identity session path variables."

require_source_match \
    "cloud web contract verifier checks blob downloads" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesBlobRequest' \
    "Cloud web contract verifier must ensure downloads use the blob helper."

require_source_match \
    "cloud web contract verifier checks download headers" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertDownloadResponseBuilderContract' \
    "Cloud web contract verifier must verify file download response headers."

require_source_match \
    "cloud web contract verifier compares storage file download responses" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'downloadStorageFile binary response' \
    "Cloud web contract verifier must compare storage file download responses."

require_source_match \
    "cloud web contract verifier compares storage archive download responses" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'downloadStorageArchive binary response' \
    "Cloud web contract verifier must compare storage archive download responses."

require_source_match \
    "cloud web contract verifier compares share file download responses" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'downloadShareFile binary response' \
    "Cloud web contract verifier must compare share file download responses."

require_source_match \
    "cloud web contract verifier compares share archive download responses" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'downloadShareArchive binary response' \
    "Cloud web contract verifier must compare share archive download responses."

require_source_match \
    "cloud web contract verifier checks signed URL disposition" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionSendsDisposition' \
    "Cloud web contract verifier must ensure signed URL calls send disposition."

require_source_match \
    "cloud web contract verifier compares storage access URL disposition" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'fetchStorageFileAccessUrl disposition contract' \
    "Cloud web contract verifier must compare storage access URL disposition."

require_source_match \
    "cloud web contract verifier compares share access URL disposition" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'fetchShareFileAccessUrl disposition contract' \
    "Cloud web contract verifier must compare share access URL disposition."

require_source_match \
    "cloud web contract verifier compares endpoint contracts" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'cloudWebEndpointContracts' \
    "Cloud web contract verifier must compare CloudStorageApi endpoint paths and methods."

require_source_match \
    "cloud web contract verifier binds controller mappings" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodMapping' \
    "Cloud web contract verifier must bind frontend API calls to controller mappings."

require_source_match \
    "cloud web contract verifier checks frontend auth tokens" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesAuthToken' \
    "Cloud web contract verifier must ensure protected frontend API calls send the current auth token."

require_source_match \
    "cloud web contract verifier checks typed payloads" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionStringifiesPayload' \
    "Cloud web contract verifier must ensure typed payloads are sent."

require_source_match \
    "cloud web contract verifier reads FormData fields" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractFormDataAppendKeysForFunction' \
    "Cloud web contract verifier must read multipart upload field names from FormData."

require_source_match \
    "cloud web contract verifier reads multipart annotations" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractNamedRequestAnnotationsForMethod' \
    "Cloud web contract verifier must read backend multipart request field names."

require_source_match \
    "cloud web contract verifier compares avatar multipart fields" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'uploadCurrentUserAvatar multipart fields' \
    "Cloud web contract verifier must compare avatar upload multipart fields."

require_source_match \
    "cloud web contract verifier compares background multipart fields" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'uploadCurrentUserHomeBackground multipart fields' \
    "Cloud web contract verifier must compare background upload multipart fields."

require_source_match \
    "cloud web contract verifier compares storage multipart fields" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'uploadStorageFile multipart fields' \
    "Cloud web contract verifier must compare storage upload multipart fields."

require_source_match \
    "cloud web contract verifier checks share access tokens" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesShareAccessToken' \
    "Cloud web contract verifier must ensure share access tokens are sent."

require_source_match \
    "cloud web contract verifier checks share controller access headers" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodReceivesShareAccessToken' \
    "Cloud web contract verifier must ensure share controllers receive share access tokens."

require_source_match \
    "cloud web contract verifier checks current principal interceptor" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertCloudStorageApiCurrentPrincipalInterceptorContract' \
    "Cloud web contract verifier must ensure user APIs stay protected by CurrentPrincipalInterceptor."

require_source_match \
    "cloud web contract verifier compares identity endpoint contracts" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'cloudWebIdentityEndpointContracts' \
    "Cloud web contract verifier must compare IdentityApi endpoint paths and methods."

require_source_match \
    "cloud web contract verifier compares identity session response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'IdentitySessionResponse' \
    "Cloud web contract verifier must compare identity session responses."

require_source_match \
    "cloud web contract verifier binds profile updates to IdentityApi" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'UpdateIdentityProfileRequest' \
    "Cloud web contract verifier must bind profile updates to IdentityApi."

require_source_match \
    "cloud web contract verifier checks identity authorization headers" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodReceivesAuthorization' \
    "Cloud web contract verifier must ensure protected IdentityApi methods receive Authorization headers."

require_source_match \
    "cloud web returnTo preserves share links" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/share/abc123" \
    "Cloud web returnTo verifier must preserve share deep links."

require_source_match \
    "cloud web returnTo preserves app download links" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/app-download" \
    "Cloud web returnTo verifier must preserve app download deep links."

require_source_match \
    "cloud web returnTo rejects API paths" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/api/storage/overview" \
    "Cloud web returnTo verifier must reject storage API paths."

require_source_match \
    "cloud web returnTo rejects console paths" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/console/cloud/" \
    "Cloud web returnTo verifier must reject cloud console paths."

require_source_match \
    "cloud web returnTo rejects identity console paths" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/console/identity/" \
    "Cloud web returnTo verifier must reject identity console paths."

require_source_match \
    "cloud web returnTo rejects console gateway" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/console/" \
    "Cloud web returnTo verifier must reject the shared console gateway path."

require_source_match \
    "cloud web returnTo rejects rag paths" \
    "webApp/scripts/verify-unified-login-return-to.mjs" \
    "/rag/" \
    "Cloud web returnTo verifier must reject RAG paths."

require_source_match \
    "cloud web restores after history cache" \
    "webApp/scripts/verify-session-sync.mjs" \
    'history cache restores' \
    "Cloud web must restore local session state after browser history cache restores."

require_source_match \
    "cloud web only expires authentication failures" \
    "webApp/scripts/verify-session-sync.mjs" \
    'only expire local sessions on authentication failures' \
    "Cloud web must not clear sessions for transient API failures."

require_source_match \
    "cloud web logout ignores server failures" \
    "webApp/scripts/verify-session-sync.mjs" \
    'server logout failures before local logout' \
    "Cloud web logout must keep local session cleanup independent of backend logout."

require_source_match \
    "cloud web logout clears before broadcast" \
    "webApp/scripts/verify-session-sync.mjs" \
    'clear the local session before notifying logout' \
    "Cloud web logout must clear local session state before broadcasting logout."

require_source_match \
    "cloud web seeds cached user from identity session" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud-safe cached user from identity login sessions' \
    "Cloud web must seed a cached user snapshot from identity login sessions."

require_source_match \
    "cloud web sanitizes legacy sessions" \
    "webApp/scripts/verify-session-sync.mjs" \
    'sanitize legacy browser session residue' \
    "Cloud web must sanitize legacy browser session residue."

require_source_match \
    "cloud web ignores stale 401 responses" \
    "webApp/scripts/verify-session-sync.mjs" \
    'ignore stale-token 401 responses' \
    "Cloud web must ignore stale-token 401 responses."

require_source_match \
    "cloud web suppresses refresh expiry broadcasts" \
    "webApp/scripts/verify-session-sync.mjs" \
    'refresh requests must not broadcast global session expiry' \
    "Cloud web refresh requests must not broadcast global session expiry before snapshot checks."

require_source_match \
    "cloud web suppresses logout expiry broadcasts" \
    "webApp/scripts/verify-session-sync.mjs" \
    'logout requests must not broadcast global session expiry' \
    "Cloud web logout requests must not broadcast global session expiry after local logout starts."

require_source_match \
    "cloud web confirms auth expired events" \
    "webApp/scripts/verify-session-sync.mjs" \
    'confirm the current session before redirecting on auth-expired events' \
    "Cloud web must confirm the current session before redirecting on auth-expired events."

require_source_match \
    "cloud web treats no-session auth expired as logout" \
    "webApp/scripts/verify-session-sync.mjs" \
    'no stored session as logout/no-op rather than session expiry' \
    "Cloud web must treat post-logout auth-expired events as logout/no-op."

require_source_match \
    "cloud web profile sessions load selected revoked filter" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile sessions must load current identity sessions with the selected revoked filter' \
    "Cloud web profile sessions must load IdentityApi sessions with the selected revoked filter."

require_source_match \
    "cloud web profile session toggle keeps selected state" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile sessions include-revoked toggle must reload with the selected state' \
    "Cloud web profile session toggle must preserve the selected revoked filter."

require_source_match \
    "cloud web profile session revocation keeps selected state" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile session revocation must preserve the current revoked filter and block duplicate submissions' \
    "Cloud web profile session revocation must preserve the selected revoked filter and block duplicates."

require_source_match \
    "cloud web profile session revocation tracks pending state" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile session revocation must track pending submissions' \
    "Cloud web profile session revocation must track pending state."

require_source_match \
    "cloud web profile session modal close pauses during revocation" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile session modal close must pause during session revocation' \
    "Cloud web profile session modal close must pause during revocation."

require_source_match \
    "cloud web profile session filter pauses during revocation" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile session filter must pause during session revocation' \
    "Cloud web profile session filter must pause during revocation."

require_source_match \
    "cloud web profile session refresh pauses during revocation" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile session refresh must pause during session revocation' \
    "Cloud web profile session refresh must pause during revocation."

require_source_match \
    "cloud web drive page routes guarded session refresh" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web drive page must route session refresh through the guarded profile action' \
    "Cloud web drive page must route session refresh through the guarded profile action."

require_source_match \
    "cloud web profile session modal disables competing rows" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web session modal must disable competing rows during session revocation' \
    "Cloud web profile session modal must disable competing revocation rows."

require_source_match \
    "cloud web profile updates block duplicates" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile updates must block duplicate submissions and surface pending state' \
    "Cloud web profile updates must block duplicate submissions."

require_source_match \
    "cloud web avatar upload blocks duplicates" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web avatar upload must block duplicate submissions and surface pending state' \
    "Cloud web avatar upload must block duplicate submissions."

require_source_match \
    "cloud web profile modal shows pending updates" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web profile modal must surface pending profile updates' \
    "Cloud web profile modal must surface pending profile updates."

require_source_match \
    "cloud web background mutations block duplicates" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web home background mutations must block duplicate submissions and surface pending state' \
    "Cloud web home background mutations must block duplicate submissions."

require_source_match \
    "cloud web password change blocks duplicates" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web password change must block duplicate submissions and report failures' \
    "Cloud web password changes must block duplicate submissions."

require_source_match \
    "cloud web password modal shows pending state" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web password modal must surface pending password changes' \
    "Cloud web password modal must surface pending password changes."

require_source_match \
    "cloud web session modal shows pending revocation" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web session modal must surface pending session revocation' \
    "Cloud web session modal must surface pending session revocation."

require_source_match \
    "cloud web background controls show pending state" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web home background controls must surface pending state' \
    "Cloud web home background controls must surface pending state."

require_source_match \
    "cloud web drive page wires profile pending states" \
    "webApp/scripts/verify-session-sync.mjs" \
    'cloud web drive page must wire profile pending states to the UI' \
    "Cloud web drive page must wire profile pending states to the UI."

require_source_match \
    "cloud console reads current cloud profile" \
    "sysManage/src/lib/api.ts" \
    '/api/cloud-profile/me' \
    "Cloud console must read the current cloud profile from CloudStorageApi."

require_source_match \
    "cloud console uses cloud-users admin backend" \
    "sysManage/src/lib/api.ts" \
    '/api/admin/cloud-users' \
    "Cloud console users view must use the CloudStorageApi cloud-users admin endpoint."

require_source_match \
    "cloud console uses cloud-operations backend" \
    "sysManage/src/lib/api.ts" \
    '/api/admin/cloud-operations/overview' \
    "Cloud console operations view must use the CloudStorageApi cloud-operations endpoint."

require_source_match \
    "cloud console uses APK admin backend" \
    "sysManage/src/lib/api.ts" \
    '/api/admin/app-package' \
    "Cloud console APK view must use the CloudStorageApi admin app package endpoint."

require_source_match \
    "cloud console uses public APK download backend" \
    "sysManage/src/features/drive/driveShared.ts" \
    '/api/app-package/download/current' \
    "Cloud console APK download link must use the public CloudStorageApi package endpoint."

require_source_match \
    "cloud console normalizes app package download paths" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console app package download URL resolver must normalize download paths' \
    "Cloud console boundary verifier must normalize app package download URLs."

require_source_match \
    "cloud console keeps app package downloads on public endpoint" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console app package download URL resolver must stay on the public package endpoint' \
    "Cloud console boundary verifier must keep app package downloads on the public package endpoint."

require_source_match \
    "cloud console app package panel uses normalized download path" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console app package panel must use the normalized public download path' \
    "Cloud console app package panel must use the normalized public download path."

require_source_match \
    "cloud console API allowlist is enforced" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'assertApiPathsMatchAllowedPrefixes' \
    "Cloud console boundary verifier must enforce API ownership allowlists."

require_source_match \
    "cloud console API allowlist scans source files" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloudConsoleApiScopeFiles = files' \
    "Cloud console boundary verifier must scan all source files for API ownership."

require_source_match \
    "cloud console API allowlist reports source scope" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console source' \
    "Cloud console boundary verifier must report full-source API ownership failures."

require_source_match \
    "cloud console API allowlist separates returnTo sentinels" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'isReturnPathBoundarySentinel' \
    "Cloud console boundary verifier must keep returnTo sentinels out of API ownership matches."

require_source_match \
    "cloud console route boundary scans source files" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'assertNoForbiddenRoutePathLiterals' \
    "Cloud console boundary verifier must scan source files for forbidden route exposure."

require_source_match \
    "cloud console route boundary rejects foreign routes" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'must not expose identity console or RAG routes from the cloud console' \
    "Cloud console boundary verifier must reject identity console and RAG route exposure."

require_source_match \
    "cloud console API allowlist keeps cloud-users ownership" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    "'/api/admin/cloud-users'" \
    "Cloud console API allowlist must include cloud users admin APIs."

require_source_match \
    "cloud console API allowlist keeps cloud-operations ownership" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    "'/api/admin/cloud-operations'" \
    "Cloud console API allowlist must include cloud operations admin APIs."

require_source_match \
    "cloud console API allowlist keeps identity refresh ownership limited" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    "'/api/identity/auth/token/refresh'" \
    "Cloud console API allowlist must include identity refresh only for session continuity."

require_source_match \
    "cloud console API allowlist keeps identity profile ownership limited" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    "'/api/identity/auth/profile'" \
    "Cloud console API allowlist must include identity profile only for current-user settings."

require_source_match \
    "cloud console exposes API contract verifier" \
    "sysManage/package.json" \
    '"verify:api-contracts": "node scripts/verify-api-contracts.mjs"' \
    "Cloud console package must expose the CloudStorageApi contract verifier."

require_source_match \
    "cloud console build verifies API contracts" \
    "sysManage/package.json" \
    'npm run verify:console-boundary && npm run verify:api-contracts && tsc -b' \
    "Cloud console build must verify CloudStorageApi contracts before TypeScript compile."

require_source_match \
    "cloud console contract verifier compares operations responses" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'AdminCloudOperationsOverviewResponse' \
    "Cloud console contract verifier must compare the operations overview response."

require_source_match \
    "cloud console contract verifier compares storage users response" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'AdminCloudStorageUserUsageResponse' \
    "Cloud console contract verifier must compare the storage users response."

require_source_match \
    "cloud console contract verifier compares query parameters" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractRequestParamsForMethod' \
    "Cloud console contract verifier must compare operations query parameters."

require_source_match \
    "cloud console contract verifier reads URLSearchParams keys" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractUrlSearchParamKeysForFunction' \
    "Cloud console contract verifier must read URLSearchParams keys from frontend source."

require_source_match \
    "cloud console contract verifier checks operations query helper" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesQueryHelper' \
    "Cloud console contract verifier must ensure operations APIs use the query helper."

require_source_match \
    "cloud console contract verifier reads frontend path variables" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractTemplatePathVariablesForFunction' \
    "Cloud console contract verifier must read frontend path variables."

require_source_match \
    "cloud console contract verifier reads backend path variables" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractPathVariablesForMethod' \
    "Cloud console contract verifier must read backend path variables."

require_source_match \
    "cloud console contract verifier compares quota path variables" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'updateUserStorageQuota path variables' \
    "Cloud console contract verifier must compare quota path variables."

require_source_match \
    "cloud console contract verifier compares user profile responses" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'UserProfileResponse' \
    "Cloud console contract verifier must compare CloudStorageApi user profile responses."

require_source_match \
    "cloud console contract verifier compares quota requests" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'AdminUpdateUserQuotaRequest' \
    "Cloud console contract verifier must compare quota update requests."

require_source_match \
    "cloud console contract verifier compares endpoint contracts" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'cloudConsoleEndpointContracts' \
    "Cloud console contract verifier must compare CloudStorageApi endpoint paths and methods."

require_source_match \
    "cloud console contract verifier compares allowed identity endpoint contracts" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'cloudConsoleIdentityEndpointContracts' \
    "Cloud console contract verifier must compare allowed IdentityApi endpoint paths and methods."

require_source_match \
    "cloud console contract verifier compares identity login responses" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'IdentityLoginResponse' \
    "Cloud console contract verifier must compare identity login responses."

require_source_match \
    "cloud console contract verifier binds profile updates to IdentityApi" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'UpdateIdentityProfileRequest' \
    "Cloud console contract verifier must bind profile updates to IdentityApi."

require_source_match \
    "cloud console contract verifier binds controller mappings" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodMapping' \
    "Cloud console contract verifier must bind frontend API calls to controller mappings."

require_source_match \
    "cloud console contract verifier checks frontend auth tokens" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesAuthToken' \
    "Cloud console contract verifier must ensure frontend API calls send the current auth token."

require_source_match \
    "cloud console contract verifier checks typed payloads" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionStringifiesPayload' \
    "Cloud console contract verifier must ensure typed payloads are sent."

require_source_match \
    "cloud console contract verifier reads FormData fields" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractFormDataAppendKeysForFunction' \
    "Cloud console contract verifier must read multipart upload field names from FormData."

require_source_match \
    "cloud console contract verifier reads multipart annotations" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'extractNamedRequestAnnotationsForMethod' \
    "Cloud console contract verifier must read backend multipart request field names."

require_source_match \
    "cloud console contract verifier compares APK multipart fields" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'uploadAdminAppPackage multipart fields' \
    "Cloud console contract verifier must compare APK upload multipart fields."

require_source_match \
    "cloud console contract verifier checks controller auth headers" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodReceivesAuthorization' \
    "Cloud console contract verifier must ensure proxying controller methods receive Authorization headers."

require_source_match \
    "cloud console contract verifier checks admin interceptor" \
    "sysManage/scripts/verify-api-contracts.mjs" \
    'assertCloudStorageApiAdminInterceptorContract' \
    "Cloud console contract verifier must ensure /api/admin/** stays protected by AdminPrincipalInterceptor."

require_source_match \
    "cloud console boundary checks runtime cloud admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console page must use the centralized cloud admin predicate' \
    "Cloud console boundary verifier must enforce the runtime cloud admin gate."

require_source_match \
    "cloud console boundary checks role label copy" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console must centralize its role label copy' \
    "Cloud console boundary verifier must enforce centralized role label copy."

require_source_match \
    "cloud console users API contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console users view must load CloudStorageApi cloud-users' \
    "Cloud console boundary verifier must pin the users view to CloudStorageApi cloud-users."

require_source_match \
    "cloud console rejects sysManage user creation API" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console user directory must not expose identity user creation from sysManage' \
    "Cloud console boundary verifier must keep identity user creation out of sysManage."

require_source_match \
    "cloud console rejects identity account creation UI" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console users view must keep identity account creation in the identity console' \
    "Cloud console boundary verifier must keep identity account creation in userSite."

require_source_no_match \
    "cloud console users view avoids identity creation controls" \
    "sysManage/src/features/drive/CloudUsersView.tsx" \
    '新增用户|创建用户|重置密码|Input\.Password|name="password"|inheritAdminBackground' \
    "Cloud console users view must not expose identity account creation controls."

require_source_match \
    "cloud console quota API contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota mutations must use CloudStorageApi cloud-users quota contract' \
    "Cloud console boundary verifier must pin quota updates to CloudStorageApi cloud-users quota."

require_source_match \
    "cloud console quota modal keeps GiB display" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota modal must present backend byte quotas as GiB' \
    "Cloud console boundary verifier must keep quota modal units readable."

require_source_match \
    "cloud console quota submit writes backend bytes" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota submit must convert GiB input to backend bytes' \
    "Cloud console boundary verifier must keep quota writes in backend byte units."

require_source_match \
    "cloud console quota submit rejects below usage" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota submit must reject quotas below current usage' \
    "Cloud console boundary verifier must reject quota writes below current usage."

require_source_match \
    "cloud console quota modal close waits for save" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota modal close must respect pending quota submissions' \
    "Cloud console boundary verifier must keep quota modal close safe while saving."

require_source_match \
    "cloud console quota modal blocks duplicates" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota modal must block duplicate submissions' \
    "Cloud console boundary verifier must block duplicate quota submissions."

require_source_match \
    "cloud console quota updates block duplicates" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota updates must block duplicate submissions and surface pending state' \
    "Cloud console quota updates must block duplicate submissions."

require_source_match \
    "cloud console operations overview contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations view must load CloudStorageApi operations overview' \
    "Cloud console boundary verifier must pin the operations overview to CloudStorageApi."

require_source_match \
    "cloud console operations shares contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations view must load CloudStorageApi share operations' \
    "Cloud console boundary verifier must pin share operations to CloudStorageApi."

require_source_match \
    "cloud console operations trash contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations view must load CloudStorageApi trash operations' \
    "Cloud console boundary verifier must pin trash operations to CloudStorageApi."

require_source_match \
    "cloud console operations storage users contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations view must load CloudStorageApi storage user operations' \
    "Cloud console boundary verifier must pin storage user operations to CloudStorageApi."

require_source_match \
    "cloud console operations APIs remain read-only" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations APIs must stay read-only GET contracts' \
    "Cloud console boundary verifier must keep operations APIs read-only."

require_source_match \
    "cloud console operations view avoids mutation controls" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations view must not expose personal file mutation controls' \
    "Cloud console boundary verifier must keep operations views read-only."

require_source_match \
    "cloud console operations hook avoids mutation flows" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations hook must not import personal file mutation flows' \
    "Cloud console boundary verifier must keep personal file mutations out of operations hooks."

require_source_no_match \
    "cloud console operations source avoids mutation controls" \
    "sysManage/src/features/drive/DriveOperationsView.tsx" \
    "title:[[:space:]]*'操作'|onRestore|onDelete|onRevoke|restore[A-Z][A-Za-z0-9_]*|delete[A-Z][A-Za-z0-9_]*|revoke[A-Z][A-Za-z0-9_]*" \
    "Cloud console operations view must not expose personal file mutation controls."

require_source_no_match \
    "cloud console operations hook avoids mutation helpers" \
    "sysManage/src/features/drive/hooks/useDriveOperationsAdmin.ts" \
    '(^|[^[:alnum:]_])(update|delete|restore|revoke|permanentlyDelete|createShareLink|uploadStorageFile)[A-Za-z0-9_]*\(' \
    "Cloud console operations hook must not import personal file mutation flows."

require_source_match \
    "cloud console APK upload contract is pinned" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload must use CloudStorageApi admin app package' \
    "Cloud console boundary verifier must pin APK uploads to CloudStorageApi admin app package."

require_source_match \
    "cloud console APK picker restricts Android packages" \
    "sysManage/src/features/drive/DriveAppPackageUploadModal.tsx" \
    'accept="\.apk,application/vnd\.android\.package-archive"' \
    "Cloud console APK upload picker must restrict Android packages."

require_source_match \
    "cloud console APK verifier checks picker restrictions" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload modal must restrict picker to Android package files' \
    "Cloud console boundary verifier must keep APK picker restricted to Android packages."

require_source_match \
    "cloud console APK verifier checks version bounds" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload modal must require a bounded version name' \
    "Cloud console boundary verifier must require bounded APK version names."

require_source_match \
    "cloud console APK verifier checks release note bounds" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload modal must require bounded release notes' \
    "Cloud console boundary verifier must require bounded APK release notes."

require_source_match \
    "cloud console APK verifier rejects non-APK drafts" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload hook must reject non-APK files before storing the draft' \
    "Cloud console boundary verifier must reject non-APK upload drafts."

require_source_match \
    "cloud console APK verifier syncs upload state" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload must refresh admin and public package state together' \
    "Cloud console boundary verifier must refresh APK admin and public state together."

require_source_match \
    "cloud console APK verifier syncs delete state" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK delete must clear admin and public package state together' \
    "Cloud console boundary verifier must clear APK admin and public state together."

require_source_match \
    "cloud console APK upload blocks duplicate submissions" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload mutations must stay behind the cloud admin gate and block duplicate submissions' \
    "Cloud console boundary verifier must block duplicate APK uploads."

require_source_match \
    "cloud console APK deletion blocks duplicate submissions" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK delete mutations must stay behind the cloud admin gate and block duplicate submissions' \
    "Cloud console boundary verifier must block duplicate APK deletion."

require_source_match \
    "cloud console APK upload modal controls pause during mutations" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload modal controls must pause during package mutations' \
    "Cloud console APK upload modal controls must pause during package mutations."

require_source_match \
    "cloud console APK upload modal shows pending upload" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload modal must surface pending upload state' \
    "Cloud console APK upload modal must surface pending upload state."

require_source_match \
    "cloud console APK panel surfaces pending deletion" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK package panel must surface pending delete state' \
    "Cloud console boundary verifier must require pending delete UI state."

require_source_match \
    "cloud console APK delete confirmation shows pending state" \
    "sysManage/src/components/AppPackagePanel.tsx" \
    'okButtonProps=\{\{ danger: true, loading: deleting, disabled: uploading \|\| deleting \}\}' \
    "Cloud console APK delete confirmation must surface pending delete state."

require_source_match \
    "cloud console header refresh keeps admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console header refresh must not load admin view data without cloud admin access' \
    "Cloud console boundary verifier must keep header refresh behind the cloud admin gate."

require_source_match \
    "cloud console quota mutations keep admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console quota mutations must stay behind the cloud admin gate' \
    "Cloud console boundary verifier must keep quota mutations behind the cloud admin gate."

require_source_match \
    "cloud console operations refresh stays complete" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console operations refresh must keep overview, storage users, trash, and shares together' \
    "Cloud console boundary verifier must keep operations refresh complete."

require_source_match \
    "cloud console storage user loading keeps backend pagination" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console storage user loading must keep backend pagination state' \
    "Cloud console boundary verifier must keep storage user backend pagination state."

require_source_match \
    "cloud console trash loading keeps backend pagination" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console trash loading must keep backend pagination state' \
    "Cloud console boundary verifier must keep trash backend pagination state."

require_source_match \
    "cloud console share loading keeps backend pagination" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console share loading must keep backend pagination state' \
    "Cloud console boundary verifier must keep share backend pagination state."

require_source_match \
    "cloud console storage user pagination keeps filters" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console storage user pagination must reload with current filters and pagination' \
    "Cloud console boundary verifier must preserve storage user filters during pagination."

require_source_match \
    "cloud console trash pagination keeps filters" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console trash pagination must reload with current filters and pagination' \
    "Cloud console boundary verifier must preserve trash filters during pagination."

require_source_match \
    "cloud console share pagination keeps filters" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console share pagination must reload with current filters and pagination' \
    "Cloud console boundary verifier must preserve share filters during pagination."

require_source_match \
    "cloud console APK uploads keep admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK upload mutations must stay behind the cloud admin gate' \
    "Cloud console boundary verifier must keep APK uploads behind the cloud admin gate."

require_source_match \
    "cloud console APK deletion keeps admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console APK delete mutations must stay behind the cloud admin gate' \
    "Cloud console boundary verifier must keep APK deletion behind the cloud admin gate."

require_source_match \
    "cloud console boundary checks permission denied copy" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console permission denied copy must mention global admins and cloud admins' \
    "Cloud console boundary verifier must enforce accurate permission denied copy."

require_source_match \
    "cloud console boundary checks users role label copy" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud users view role tags must use the centralized cloud role label copy' \
    "Cloud console boundary verifier must enforce role labels in the users view."

require_source_match \
    "cloud console profile dialog contract is enforced" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console profile modal must keep the shared profile dialog contract' \
    "Cloud console profile modal must enforce the shared account profile contract."

require_source_match \
    "cloud console profile dialog rejects legacy aliases" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console profile modal must not keep legacy profile layout aliases' \
    "Cloud console profile modal must reject legacy profile layout aliases."

require_source_match \
    "cloud console profile updates block duplicates" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console profile updates must block duplicate submissions and surface pending state' \
    "Cloud console profile updates must block duplicate submissions."

require_source_match \
    "cloud console profile avatar upload blocks duplicates" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console profile avatar upload must block duplicate submissions and surface pending state' \
    "Cloud console profile avatar upload must block duplicate submissions."

require_source_match \
    "cloud console profile modal shows pending updates" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console profile modal must surface pending profile updates' \
    "Cloud console profile modal must surface pending profile updates."

require_source_match \
    "cloud console profile styles reject legacy aliases" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console styles must not keep legacy profile layout aliases' \
    "Cloud console profile style verifier must reject legacy profile layout aliases."

require_source_no_match \
    "cloud console profile source avoids legacy aliases" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    'profile-avatar-preview-row|profile-avatar-actions' \
    "Cloud console profile modal must not keep legacy profile layout aliases."

require_source_no_match \
    "cloud console profile stylesheet avoids legacy aliases" \
    "sysManage/src/index.css" \
    '\.profile-avatar-preview-row\b|\.profile-avatar-actions\b' \
    "Cloud console styles must not keep legacy profile layout aliases."

require_source_match \
    "cloud console boundary accepts cloud app admins" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'global admins and cloud application admins' \
    "Cloud console boundary verifier must accept global and cloud application administrators."

require_source_match \
    "cloud console centralizes role labels" \
    "sysManage/src/types.ts" \
    'export function cloudRoleLabel' \
    "Cloud console role labels must be centralized."

require_source_match \
    "cloud console permission denied copy matches access contract" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    'subTitle="仅全局管理员或云盘管理员可以访问运营后台。"' \
    "Cloud console permission denied copy must match the runtime access contract."

require_source_match \
    "cloud console sidebar uses role label helper" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    'cloudRoleLabel\(currentUser\)' \
    "Cloud console sidebar must use the centralized role label."

require_source_match \
    "cloud users permission copy matches access contract" \
    "sysManage/src/features/drive/CloudUsersView.tsx" \
    'description="仅全局管理员或云盘管理员可以查看用户画像和调整存储额度。"' \
    "Cloud users permission copy must match the runtime access contract."

require_source_match \
    "cloud users role tags use role label helper" \
    "sysManage/src/features/drive/CloudUsersView.tsx" \
    'cloudRoleLabel\(user\)' \
    "Cloud users role tags must use the centralized role label."

require_source_match \
    "cloud operations permission copy matches access contract" \
    "sysManage/src/features/drive/DriveOperationsView.tsx" \
    'description="仅全局管理员或云盘管理员可以查看全局文件运营明细。"' \
    "Cloud operations permission copy must match the runtime access contract."

require_source_match \
    "cloud operations storage users role tags use role label helper" \
    "sysManage/src/features/drive/DriveOperationsView.tsx" \
    'cloudRoleLabel\(user\)' \
    "Cloud operations storage users role tags must use the centralized role label."

require_source_match \
    "cloud APK package permission copy matches access contract" \
    "sysManage/src/features/drive/DriveAppPackageView.tsx" \
    'description="仅全局管理员或云盘管理员可以上传和替换安卓安装包。"' \
    "Cloud APK package permission copy must match the runtime access contract."

require_source_match \
    "cloud console exposes returnTo verifier" \
    "sysManage/package.json" \
    '"verify:return-to"[[:space:]]*:[[:space:]]*"node scripts/verify-unified-login-return-to\.mjs"' \
    "Cloud console build must expose the unified login returnTo verifier."

require_source_match \
    "cloud console build runs returnTo verifier" \
    "sysManage/package.json" \
    '"build"[[:space:]]*:[[:space:]]*"npm run verify:return-to && npm run verify:session-sync' \
    "Cloud console build must run returnTo verification before session and compile checks."

require_source_match \
    "cloud console returnTo verifier runs without TypeScript packages" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT' \
    "Cloud console returnTo verifier must run without installed TypeScript packages."

require_source_match \
    "cloud Bash boundary runs cloud console returnTo verifier" \
    "deploy/scripts/check-frontend-console-boundaries.sh" \
    'run_node_script "cloud console returnTo boundary"' \
    "Cloud Bash boundary check must run the cloud console returnTo verifier directly."

require_source_match \
    "cloud console returnTo preserves APK route" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "/console/cloud/app-package" \
    "Cloud console returnTo verifier must preserve APK package deep links."

require_source_match \
    "cloud console returnTo covers APK login redirect" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "cloudConsoleReturnTo\\('/app-package'\\), 'login-required'" \
    "Cloud console returnTo verifier must cover APK package login redirects."

require_source_match \
    "cloud console returnTo rejects admin APIs" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "/api/admin/cloud-users" \
    "Cloud console returnTo verifier must reject admin API paths."

require_source_match \
    "cloud console returnTo rejects identity console" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "/console/identity/" \
    "Cloud console returnTo verifier must reject identity console paths."

require_source_match \
    "cloud console returnTo rejects console gateway" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "/console/" \
    "Cloud console returnTo verifier must reject the shared console gateway path."

require_source_match \
    "cloud console returnTo rejects rag paths" \
    "sysManage/scripts/verify-unified-login-return-to.mjs" \
    "/rag/" \
    "Cloud console returnTo verifier must reject RAG paths."

require_source_match \
    "cloud console restores after history cache" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'history cache restores' \
    "Cloud console must restore local session state after browser history cache restores."

require_source_match \
    "cloud console only expires authentication failures" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'only expire local sessions on authentication failures' \
    "Cloud console must not clear sessions for transient API failures."

require_source_match \
    "cloud console logout ignores server failures" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'server logout failures before local logout' \
    "Cloud console logout must keep local session cleanup independent of backend logout."

require_source_match \
    "cloud console logout clears before broadcast" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'clear the local session before notifying logout' \
    "Cloud console logout must clear local session state before broadcasting logout."

require_source_match \
    "cloud console seeds cached user from identity session" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'cloud-safe cached user from identity login sessions' \
    "Cloud console must seed a cached user snapshot from identity login sessions."

require_source_match \
    "cloud console sanitizes legacy sessions" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'sanitize legacy browser session residue' \
    "Cloud console must sanitize legacy browser session residue."

require_source_match \
    "cloud console ignores stale 401 responses" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'ignore stale-token 401 responses' \
    "Cloud console must ignore stale-token 401 responses."

require_source_match \
    "cloud console suppresses refresh expiry broadcasts" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'refresh requests must not broadcast global session expiry' \
    "Cloud console refresh requests must not broadcast global session expiry before snapshot checks."

require_source_match \
    "cloud console suppresses logout expiry broadcasts" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'logout requests must not broadcast global session expiry' \
    "Cloud console logout requests must not broadcast global session expiry after local logout starts."

require_source_match \
    "cloud console confirms auth expired events" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'confirm the current session before redirecting on auth-expired events' \
    "Cloud console must confirm the current session before redirecting on auth-expired events."

require_source_match \
    "cloud console treats no-session auth expired as logout" \
    "sysManage/scripts/verify-session-sync.mjs" \
    'no stored session as logout/no-op rather than session expiry' \
    "Cloud console must treat post-logout auth-expired events as logout/no-op."

require_source_match \
    "CloudStorageApi exposes cloud-users admin backend" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserController.java" \
    '@RequestMapping\("/api/admin/cloud-users"\)' \
    "CloudStorageApi must expose the cloud-users admin backend used by sysManage."

require_source_match \
    "CloudStorageApi exposes cloud user quota admin backend" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserProfileController.java" \
    '@RequestMapping\("/api/admin/cloud-users"\)' \
    "CloudStorageApi must expose the cloud user quota admin backend used by sysManage."

require_source_match \
    "CloudStorageApi exposes cloud-operations backend" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudOperationsController.java" \
    '@RequestMapping\("/api/admin/cloud-operations"\)' \
    "CloudStorageApi must expose the cloud operations backend used by sysManage."

require_source_match \
    "CloudStorageApi exposes APK admin backend" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminAppPackageController.java" \
    '@RequestMapping\("/api/admin/app-package"\)' \
    "CloudStorageApi must expose the APK admin backend used by sysManage."

require_source_match \
    "CloudStorageApi exposes public APK backend" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AppPackageController.java" \
    '@RequestMapping\("/api/app-package"\)' \
    "CloudStorageApi must expose the public APK package backend used by sysManage."

require_source_match \
    "cloud operations storage users expose app roles" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto/AdminCloudStorageUserUsageResponse.java" \
    'Map<String, String> appRoles' \
    "CloudStorageApi storage user operations response must expose application roles for sysManage role labels."

require_source_match \
    "CloudStorageApi protects admin API prefix" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/config/WebMvcConfig.java" \
    '\.addPathPatterns\("/api/admin/\*\*"\);' \
    "CloudStorageApi must protect admin endpoints through AdminPrincipalInterceptor."

require_source_match \
    "CloudStorageApi admin role uses cloud app role" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" \
    'CLOUD_ADMIN_ROLE = "CLOUD_ADMIN"' \
    "CloudStorageApi admin principal must continue to use the cloud app admin role."

require_source_match \
    "CloudStorageApi admin role accepts global and cloud admins" \
    "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" \
    'role == UserRole.ADMIN \|\| CLOUD_ADMIN_ROLE.equals\(appRoles\(\).get\(CLOUD_APP_CODE\)\)' \
    "CloudStorageApi admin principal must accept global admins and cloud app admins."

require_source_match \
    "cloud update defaults to api frontend" \
    "deploy/scripts/update-cloud-production.sh" \
    'ALICIA_CLOUD_DEPLOY_SERVICES:-api frontend' \
    "Cloud production update must publish the CloudStorageApi/sysManage contract by default."

require_source_match \
    "cloud README documents api frontend contract publishing" \
    "README.md" \
    'appRoles.+api frontend' \
    "Cloud README must document the api/frontend publishing pair for appRoles-backed console labels."

require_source_match \
    "main/cloud update can defer main public gateway checks" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'should_defer_main_site_public_boundary\(\)' \
    "Joint production update must support deferring main public gateway checks until cloud frontend is updated."

require_source_match \
    "main/cloud update defers main public boundary by default" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY=true bash "\$MAIN_SITE_UPDATE_SCRIPT" "\$MAIN_SITE_PROJECT_DIR"' \
    "Joint production update must avoid checking the public gateway before the cloud frontend update completes."

require_source_match \
    "main/cloud update reruns main route verification after cloud update" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'run_final_main_site_route_verify' \
    "Joint production update must rerun main route verification after the cloud frontend gateway update."

require_source_match \
    "main/cloud update exposes final main route verify skip" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'ALICIA_SKIP_FINAL_MAIN_SITE_VERIFY' \
    "Joint production update must expose an escape hatch for the final main route verification."

require_source_match \
    "main/cloud update allows cloud-only missing main verifier" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'Skipping final main site route verification; route verify script is missing because main site update was skipped' \
    "Joint production update must allow cloud-only updates when the main site verifier is unavailable."

require_source_no_match \
    "main/cloud update final route verify survives skipped main update" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    '\[\[ "\$SKIP_MAIN_SITE_UPDATE" != "true" \]\] \|\| return 0' \
    "Joint production update must still run final main route verification after cloud updates even when main site update was skipped."

require_source_match \
    "main/cloud update owns platform status snapshot switch" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'COLLECT_STATUS="\$\{ALICIA_COLLECT_STATUS_AFTER_UPDATE:-false\}"' \
    "Joint production update must own post-update platform status snapshots."

require_source_match \
    "main/cloud update locates platform status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'STATUS_SNAPSHOT_SCRIPT="\$CLOUD_PROJECT_DIR/deploy/scripts/collect-production-status.sh"' \
    "Joint production update must locate the platform status snapshot script."

require_source_match \
    "main/cloud update exposes final platform status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'run_platform_status_snapshot\(\)' \
    "Joint production update must expose a final platform status snapshot step."

require_source_match \
    "main/cloud update defers cloud status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'ALICIA_COLLECT_STATUS_AFTER_UPDATE=false bash "\$CLOUD_UPDATE_SCRIPT" "\$CLOUD_PROJECT_DIR" "\$@"' \
    "Joint production update must defer cloud status snapshots until final main route verification completes."

require_source_match \
    "main/cloud update passes main site path to status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'ALICIA_MAIN_SITE_PROJECT_DIR="\$MAIN_SITE_PROJECT_DIR"' \
    "Joint production update must pass the main site path to the platform status snapshot."

require_source_match \
    "main/cloud update passes cloud path to status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'ALICIA_CLOUD_PROJECT_DIR="\$CLOUD_PROJECT_DIR"' \
    "Joint production update must pass the cloud path to the platform status snapshot."

require_source_match \
    "main/cloud update runs platform status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'bash "\$STATUS_SNAPSHOT_SCRIPT"' \
    "Joint production update must run the platform status snapshot after final verification."

require_source_match \
    "main/cloud update fails fast for missing platform status snapshot" \
    "deploy/scripts/update-main-and-cloud-production.sh" \
    'if \[\[ "\$COLLECT_STATUS" == "true" && ! -f "\$STATUS_SNAPSHOT_SCRIPT" \]\]; then' \
    "Joint production update must fail fast when the requested platform status snapshot script is missing."

require_source_match \
    "platform local verifier allows main site path override" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    '\$env:ALICIA_MAIN_SITE_PROJECT_DIR' \
    "Platform local verifier must allow overriding the main site repository path."

require_source_match \
    "platform local verifier allows cloud path override" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    '\$env:ALICIA_CLOUD_PROJECT_DIR' \
    "Platform local verifier must allow overriding the cloud repository path."

require_source_match \
    "platform local verifier defaults to sibling mainSite" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Join-Path \(Split-Path -Parent \$CloudProjectDir\) "mainSite"' \
    "Platform local verifier must default to a sibling mainSite repository."

require_source_match \
    "platform local verifier prints main site commit" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Write-Host "Main site commit:' \
    "Platform local verifier must print the main site commit for diagnostics."

require_source_match \
    "platform local verifier prints cloud commit" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Write-Host "Cloud commit:' \
    "Platform local verifier must print the cloud commit for diagnostics."

require_source_match \
    "platform local verifier runs repository split verifiers" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'deploy\\scripts\\verify-frontend-split-local.ps1' \
    "Platform local verifier must run each repository frontend split verifier."

require_source_match \
    "platform local verifier passes SkipBuild" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    '\$verificationArgs\["SkipBuild"\] = \$true' \
    "Platform local verifier must pass through the SkipBuild switch."

require_source_match \
    "platform local verifier preflights build dependencies" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Invoke-FrontendBuildDependencyPreflight' \
    "Platform local verifier must preflight frontend build dependencies before running full builds."

require_source_match \
    "platform local verifier diagnoses missing frontend tsc" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'foreach \(\$binaryName in @\("tsc", "vite"\)\)' \
    "Platform local verifier must diagnose missing TypeScript and Vite build dependencies before npm build."

require_source_match \
    "platform local verifier reports npm ci fix" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'npm ci --no-audit --no-fund' \
    "Platform local verifier must tell operators how to install missing frontend dependencies."

require_source_match \
    "platform local verifier checks shared account profile contract" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Invoke-SharedAccountProfileVerification' \
    "Platform local verifier must enforce shared account profile layout across all frontends."

require_source_match \
    "platform local verifier covers identity profile modal" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'identity console profile modal' \
    "Platform local verifier must include the identity console profile modal in the shared profile contract."

require_source_match \
    "platform local verifier covers cloud profile modal" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'cloud console profile modal' \
    "Platform local verifier must include the cloud console profile modal in the shared profile contract."

require_source_match \
    "platform local verifier checks main site portal API contracts" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Invoke-MainSitePortalApiContractVerification' \
    "Platform local verifier must enforce main site portal API contracts."

require_source_match \
    "platform local verifier checks identity console API contracts" \
    "deploy/scripts/verify-platform-frontend-split-local.ps1" \
    'Invoke-IdentityConsoleApiContractVerification' \
    "Platform local verifier must enforce identity console IdentityApi contracts."

require_source_match \
    "platform bash verifier allows main site path override" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'ALICIA_MAIN_SITE_PROJECT_DIR' \
    "Platform bash verifier must allow overriding the main site repository path."

require_source_match \
    "platform bash verifier allows cloud path override" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'ALICIA_CLOUD_PROJECT_DIR' \
    "Platform bash verifier must allow overriding the cloud repository path."

require_source_match \
    "platform bash verifier defaults to sibling mainSite" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'mainSite' \
    "Platform bash verifier must default to a sibling mainSite repository."

require_source_match \
    "platform bash verifier runs main site boundary checks" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'check-main-site-frontend-boundaries\.sh' \
    "Platform bash verifier must run the main site frontend boundary checks."

require_source_match \
    "platform bash verifier runs cloud boundary checks" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'check-frontend-console-boundaries\.sh' \
    "Platform bash verifier must run the cloud frontend boundary checks."

require_source_match \
    "platform bash verifier runs identity route boundary checks" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'check-identity-route-boundary\.sh' \
    "Platform bash verifier must run the identity route boundary checks."

require_source_match \
    "platform bash verifier checks shared account profile contract" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'verify_shared_account_profile' \
    "Platform bash verifier must enforce shared account profile layout across all frontends."

require_source_match \
    "platform bash verifier checks main site portal API contracts" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'verify-main-site-portal-api-contracts\.mjs' \
    "Platform bash verifier must enforce main site portal API contracts."

require_source_match \
    "platform bash verifier checks identity console API contracts" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'verify-identity-console-api-contracts\.mjs' \
    "Platform bash verifier must enforce identity console IdentityApi contracts."

require_source_match \
    "platform bash verifier supports static API checks" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'skip-build' \
    "Platform bash verifier must allow static/API-only checks."

require_source_match \
    "platform bash verifier preflights build dependencies" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'verify_frontend_build_dependencies' \
    "Platform bash verifier must preflight frontend build dependencies before running full builds."

require_source_match \
    "platform bash verifier diagnoses missing frontend tsc" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'for binary_name in tsc vite' \
    "Platform bash verifier must diagnose missing TypeScript and Vite build dependencies before npm build."

require_source_match \
    "platform bash verifier reports npm ci fix" \
    "deploy/scripts/verify-platform-frontend-split-local.sh" \
    'npm ci --no-audit --no-fund' \
    "Platform bash verifier must tell operators how to install missing frontend dependencies."

require_source_match \
    "main site portal contract verifier compares login responses" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'IdentityLoginResponse' \
    "Main site portal contract verifier must compare Identity login responses."

require_source_match \
    "main site portal contract verifier compares registration requests" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'RequestEmailRegistrationCodeRequest' \
    "Main site portal contract verifier must compare registration requests."

require_source_match \
    "main site portal contract verifier reads frontend request bodies" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractJsonStringifyObjectFieldsForFunction' \
    "Main site portal contract verifier must read auth request bodies from frontend source."

require_source_match \
    "main site portal contract verifier checks typed payloads" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'assertMainSiteApiStringifiesPayload' \
    "Main site portal contract verifier must ensure typed payloads are sent."

require_source_match \
    "main site portal contract verifier reads inline query parameters" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractInlineQueryParamKeysForFunction' \
    "Main site portal contract verifier must read inline query parameters from frontend source."

require_source_match \
    "main site portal contract verifier compares query parameters" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractRequestParamsForMethod' \
    "Main site portal contract verifier must compare query parameters."

require_source_match \
    "main site portal contract verifier reads frontend path variables" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractTemplatePathVariablesForFunction' \
    "Main site portal contract verifier must read frontend path variables."

require_source_match \
    "main site portal contract verifier reads backend path variables" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractPathVariablesForMethod' \
    "Main site portal contract verifier must read backend path variables."

require_source_match \
    "main site portal contract verifier compares session path variables" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'revokeIdentitySession path variables' \
    "Main site portal contract verifier must compare session path variables."

require_source_match \
    "main site portal contract verifier reads FormData fields" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractFormDataAppendKeysForFunction' \
    "Main site portal contract verifier must read avatar multipart fields from FormData."

require_source_match \
    "main site portal contract verifier reads multipart annotations" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'extractNamedRequestAnnotationsForMethod' \
    "Main site portal contract verifier must read backend avatar multipart fields."

require_source_match \
    "main site portal contract verifier compares avatar multipart fields" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'main site uploadIdentityAvatar multipart fields' \
    "Main site portal contract verifier must compare avatar upload multipart fields."

require_source_match \
    "main site portal contract verifier compares endpoint contracts" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'mainSitePortalEndpointContracts' \
    "Main site portal contract verifier must compare API endpoint paths and methods."

require_source_match \
    "main site portal contract verifier binds cloud avatar route" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'CloudProfileController.java' \
    "Main site portal contract verifier must bind avatar uploads to CloudStorageApi."

require_source_match \
    "main site portal contract verifier checks frontend auth tokens" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'assertMainSiteApiUsesAuthToken' \
    "Main site portal contract verifier must ensure protected API calls send the current auth token."

require_source_match \
    "main site portal contract verifier checks controller auth headers" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'assertControllerMethodReceivesAuthorization' \
    "Main site portal contract verifier must ensure protected backend methods receive Authorization headers."

require_source_match \
    "main site portal contract verifier checks avatar interceptor" \
    "deploy/scripts/verify-main-site-portal-api-contracts.mjs" \
    'assertCloudProfileAvatarInterceptorContract' \
    "Main site portal contract verifier must ensure avatar uploads stay protected by CurrentPrincipalInterceptor."

require_source_match \
    "identity console contract verifier compares user responses" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'IdentityUserResponse' \
    "Identity console contract verifier must compare Identity user responses."

require_source_match \
    "identity console contract verifier compares audit pages" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'IdentityAuditLogPageResponse' \
    "Identity console contract verifier must compare Identity audit log pages."

require_source_match \
    "identity console contract verifier reads frontend request bodies" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractJsonStringifyObjectFieldsForFunction' \
    "Identity console contract verifier must read auth request bodies from frontend source."

require_source_match \
    "identity console contract verifier checks typed payloads" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertUserSiteApiStringifiesPayload' \
    "Identity console contract verifier must ensure typed payloads are sent."

require_source_match \
    "identity console contract verifier reads URLSearchParams keys" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractUrlSearchParamKeysForFunction' \
    "Identity console contract verifier must read URLSearchParams keys from frontend source."

require_source_match \
    "identity console contract verifier checks audit query helper" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertUserSiteApiUsesQueryHelper' \
    "Identity console contract verifier must ensure audit APIs use the query helper."

require_source_match \
    "identity console contract verifier reads inline query parameters" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractInlineQueryParamKeysForFunction' \
    "Identity console contract verifier must read inline query parameters from frontend source."

require_source_match \
    "identity console contract verifier compares query parameters" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractRequestParamsForMethod' \
    "Identity console contract verifier must compare query parameters."

require_source_match \
    "identity console contract verifier reads frontend path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractTemplatePathVariablesForFunction' \
    "Identity console contract verifier must read frontend path variables."

require_source_match \
    "identity console contract verifier reads backend path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractPathVariablesForMethod' \
    "Identity console contract verifier must read backend path variables."

require_source_match \
    "identity console contract verifier compares session path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'revokeIdentitySession path variables' \
    "Identity console contract verifier must compare session path variables."

require_source_match \
    "identity console contract verifier compares password reset path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'resetIdentityUserPassword path variables' \
    "Identity console contract verifier must compare password reset path variables."

require_source_match \
    "identity console contract verifier compares app role list path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'fetchIdentityApplicationRoles path variables' \
    "Identity console contract verifier must compare app role list path variables."

require_source_match \
    "identity console contract verifier compares app role path variables" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'updateIdentityApplicationRole path variables' \
    "Identity console contract verifier must compare app role path variables."

require_source_match \
    "identity console contract verifier reads FormData fields" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractFormDataAppendKeysForFunction' \
    "Identity console contract verifier must read avatar multipart fields from FormData."

require_source_match \
    "identity console contract verifier reads multipart annotations" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'extractNamedRequestAnnotationsForMethod' \
    "Identity console contract verifier must read backend avatar multipart fields."

require_source_match \
    "identity console contract verifier compares avatar multipart fields" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'identity console uploadIdentityAvatar multipart fields' \
    "Identity console contract verifier must compare avatar upload multipart fields."

require_source_match \
    "identity console contract verifier compares endpoint contracts" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'identityEndpointContracts' \
    "Identity console contract verifier must compare IdentityApi endpoint paths and methods."

require_source_match \
    "identity console contract verifier binds cloud avatar route" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'identityConsoleCloudProfileEndpointContracts' \
    "Identity console contract verifier must bind avatar uploads to CloudStorageApi."

require_source_match \
    "identity console contract verifier binds controller mappings" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertControllerMethodMapping' \
    "Identity console contract verifier must bind frontend API calls to controller mappings."

require_source_match \
    "identity console contract verifier checks frontend auth tokens" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertUserSiteApiUsesAuthToken' \
    "Identity console contract verifier must ensure frontend API calls send the current auth token."

require_source_match \
    "identity console contract verifier checks controller auth headers" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertControllerMethodReceivesAuthorization' \
    "Identity console contract verifier must ensure protected IdentityApi controllers receive Authorization headers."

require_source_match \
    "identity console contract verifier checks cloud avatar interceptor" \
    "deploy/scripts/verify-identity-console-api-contracts.mjs" \
    'assertCloudProfileAvatarInterceptorContract' \
    "Identity console contract verifier must ensure avatar uploads stay protected by CurrentPrincipalInterceptor."

require_source_match \
    "production status locates main site repository" \
    "deploy/scripts/collect-production-status.sh" \
    'MAIN_SITE_PROJECT_DIR="\$\{ALICIA_MAIN_SITE_PROJECT_DIR:-\$HOME/mainSite\}"' \
    "Production status snapshot must locate the main site repository."

require_source_match \
    "production status includes main site Git state" \
    "deploy/scripts/collect-production-status.sh" \
    'git_snapshot "main site repository" "\$MAIN_SITE_PROJECT_DIR"' \
    "Production status snapshot must include main site Git state."

require_source_match \
    "production status summarizes tracked change modes" \
    "deploy/scripts/collect-production-status.sh" \
    'git diff --summary' \
    "Production status snapshot must summarize tracked file mode changes."

require_source_match \
    "production status lists tracked changed files" \
    "deploy/scripts/collect-production-status.sh" \
    'git diff --name-status' \
    "Production status snapshot must list tracked changed files."

require_source_match \
    "production status exposes main site route verification" \
    "deploy/scripts/collect-production-status.sh" \
    'run_main_site_route_verify\(\)' \
    "Production status snapshot must expose optional main site route verification."

require_source_match \
    "production status exposes main site boundary verification" \
    "deploy/scripts/collect-production-status.sh" \
    'run_main_site_boundary_check\(\)' \
    "Production status snapshot must expose optional main site boundary verification."

require_source_match \
    "production status runs main site route verification" \
    "deploy/scripts/collect-production-status.sh" \
    'run_optional "main site route verification" run_main_site_route_verify' \
    "Production status snapshot must run main site route verification when full route checks are requested."

require_source_match \
    "production status runs main site boundary check" \
    "deploy/scripts/collect-production-status.sh" \
    'run_optional "main site frontend boundary check" run_main_site_boundary_check' \
    "Production status snapshot must run main site frontend boundary checks when static boundary checks are requested."

require_source_match \
    "production status exposes platform frontend split check" \
    "deploy/scripts/collect-production-status.sh" \
    'ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK' \
    "Production status snapshot must expose optional platform frontend split verification."

require_source_match \
    "production status runs platform frontend split check" \
    "deploy/scripts/collect-production-status.sh" \
    'run_platform_frontend_split_check()' \
    "Production status snapshot must run platform frontend split verification when requested."

require_source_match \
    "production status skips frontend rebuilds" \
    "deploy/scripts/collect-production-status.sh" \
    'bash "\$PLATFORM_FRONTEND_SPLIT_SCRIPT" --skip-build' \
    "Production status snapshot must run platform frontend split verification without rebuilding frontends."

require_source_match \
    "production status disables TypeScript package dependency for platform split" \
    "deploy/scripts/collect-production-status.sh" \
    'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT="\$\{ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT:-1\}"' \
    "Production status platform frontend split snapshot must avoid requiring local TypeScript packages."

require_source_match \
    "production docs expose platform frontend split snapshot" \
    "docs/production-verification-scripts.md" \
    'ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK=true' \
    "Production verification docs must describe the platform frontend split snapshot switch."

require_source_match \
    "production docs describe TypeScript-free frontend split snapshot" \
    "docs/production-verification-scripts.md" \
    'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT' \
    "Production verification docs must describe that frontend split snapshots avoid local TypeScript package dependency."

require_source_match \
    "root README exposes platform frontend split snapshot" \
    "README.md" \
    'ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK=true' \
    "Root README must describe the platform frontend split snapshot switch."

require_source_match \
    "root README describes TypeScript-free frontend split snapshot" \
    "README.md" \
    'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT' \
    "Root README must describe that frontend split snapshots avoid local TypeScript package dependency."

require_source_match \
    "production status exposes canonical redirect probes" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_redirect_probe\(\)' \
    "Production status snapshot must include canonical redirect probes."

require_source_match \
    "production status verifies shared console gateway redirect" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_redirect_probe "console gateway bare path" "\$PUBLIC_BASE_URL/console" "/console/"' \
    "Production status snapshot must verify the shared console gateway redirect."

require_source_match \
    "production status verifies cloud web canonical redirect" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_redirect_probe "cloudPan bare path" "\$PUBLIC_BASE_URL/cloudPan" "/cloudPan/"' \
    "Production status snapshot must verify the cloud web canonical redirect."

require_source_match \
    "production status probes cloud share route" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_probe "cloudPan share route" "\$PUBLIC_BASE_URL/cloudPan/share/cache-probe"' \
    "Production status snapshot must probe cloud share deep links."

require_source_match \
    "production status probes cloud app download route" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_probe "cloudPan app download route" "\$PUBLIC_BASE_URL/cloudPan/app-download"' \
    "Production status snapshot must probe the cloud app download route."

require_source_match \
    "production status verifies cloud console canonical redirect" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_redirect_probe "cloud console bare path" "\$PUBLIC_BASE_URL/console/cloud" "/console/cloud/"' \
    "Production status snapshot must verify the cloud console canonical redirect."

require_source_match \
    "production status verifies legacy cloud login handoff" \
    "deploy/scripts/collect-production-status.sh" \
    'curl_redirect_probe "cloudPan legacy login" "\$PUBLIC_BASE_URL/cloudPan/login" "/login\?returnTo=/cloudPan/"' \
    "Production status snapshot must verify legacy cloud login handoff."

require_source_match \
    "cloud web Dockerfile publishes root Android asset links" \
    "webApp/Dockerfile" \
    'COPY --from=cloud-builder /app/webApp/dist/\.well-known /usr/share/nginx/html/\.well-known' \
    "Cloud web Dockerfile must publish Android asset links at the domain root."

require_source_count_at_least \
    "cloud Dockerfile copies CloudStorageApi source for frontend contracts" \
    "webApp/Dockerfile" \
    'COPY CloudStorageApi/src /app/CloudStorageApi/src' \
    2 \
    "Cloud Dockerfile must copy CloudStorageApi source into both frontend build stages for API contract verification."

require_source_count_at_least \
    "cloud Dockerfile copies identityApi source for frontend contracts" \
    "webApp/Dockerfile" \
    'COPY identityApi/src /app/identityApi/src' \
    2 \
    "Cloud Dockerfile must copy identityApi source into both frontend build stages for API contract verification."

for nginx_conf in webApp/nginx/default.conf webApp/nginx/default.ssl.conf; do
    require_source_match \
        "$nginx_conf serves root Android asset links" \
        "$nginx_conf" \
        'location = /\.well-known/assetlinks\.json' \
        "$nginx_conf must serve Android asset links from the domain root."
done

scan_target \
    "cloud console has no identity or personal drive references" \
    "sysManage/src" \
    "(/api/identity/admin/|${LEGACY_CLOUD_ADMIN_USERS_PATTERN}|/api/storage/|/api/share-links/|/api/public/share-links/|(^|[^[:alnum:]_])(IdentityAudit[[:alnum:]_]*|IdentityApplicationRole[[:alnum:]_]*|UpdateIdentityApplicationRole[[:alnum:]_]*|StorageViewMode|StorageFileCategory|StorageNodeFilter|StorageNodeSortField|fetchIdentitySessions|revokeIdentitySession|changePassword|fetchDriveOverview|fetchStorageNodes|createFolder|uploadStorageFile|createShareLink|downloadStorage[[:alnum:]_]*|renameStorage[[:alnum:]_]*|moveStorage[[:alnum:]_]*|deleteStorage[[:alnum:]_]*|restoreStorage[[:alnum:]_]*|permanentlyDelete[[:alnum:]_]*)($|[^[:alnum:]_]))" \
    "Cloud admin console contains identity-admin or personal drive API/type references."

require_source_match \
    "cloud console account menu exposes profile editing" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "key:[[:space:]]*'profile'" \
    "Cloud console account menu must expose current user profile editing."

require_source_match \
    "cloud console profile modal uses unified layout" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    'className="account-profile-form"' \
    "Cloud console profile modal must use the unified account profile layout."

require_source_match \
    "cloud quota modal uses unified title" \
    "sysManage/src/features/drive/CloudUsersView.tsx" \
    'title=\{<AliciaModalTitle eyebrow="Cloud">调整云盘额度</AliciaModalTitle>\}' \
    "Cloud console quota modal must use the unified Alicia modal title."

require_source_match \
    "cloud quota modal uses unified chrome" \
    "sysManage/src/features/drive/CloudUsersView.tsx" \
    'rootClassName="alicia-modal alicia-account-modal cloud-quota-modal"' \
    "Cloud console quota modal must use the unified Alicia modal chrome."

require_source_match \
    "cloud console account menu exposes console gateway" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "key:[[:space:]]*'consoleHome'" \
    "Cloud console account menu must expose the unified console gateway."

require_source_match \
    "cloud console account menu routes through console gateway" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "window\\.location\\.assign\\('/console/'\\)" \
    "Cloud console account menu must route management navigation through /console/."

require_source_no_match \
    "cloud console avoids identity console hard-link" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "(window\\.location\\.assign\\('/console/identity/?'\\)|href=\"/console/identity(/|\"))" \
    "Cloud console should use the unified /console/ gateway instead of hard-linking to identity console."

require_source_match \
    "cloud console verifier rejects identity console hard-link" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console should use the unified console gateway instead of hard-linking to the identity console' \
    "Cloud console boundary verifier must reject direct identity console links."

require_source_match \
    "cloud console exposes URL child routes" \
    "sysManage/src/App.tsx" \
    'path="/:view"' \
    "Cloud console must expose URL-addressable child routes."

require_source_match \
    "cloud console defaults to users route" \
    "sysManage/src/App.tsx" \
    'to="/users"' \
    "Cloud console root and unknown routes must land on /users."

require_source_match \
    "cloud console default route reads legacy view query" \
    "sysManage/src/App.tsx" \
    "new URLSearchParams\\(search\\)\\.get\\('view'\\)" \
    "Cloud console root route must read the legacy view query."

require_source_match \
    "cloud console default route preserves query and hash" \
    "sysManage/src/App.tsx" \
    "defaultCloudViewRoute\\(location\\.search\\).*location\\.search.*location\\.hash" \
    "Cloud console root route must preserve query and hash when it redirects to a child route."

require_source_match \
    "cloud console reads active route view" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    'useParams<\{ view\?: string \}>' \
    "Cloud console must read the active view from the URL."

require_source_match \
    "cloud console maps APK route" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "appPackage:[[:space:]]*'/app-package'" \
    "Cloud console APK package view must use the /app-package route."

require_source_match \
    "cloud console sets view document title" \
    "sysManage/src/pages/CloudConsolePage.tsx" \
    "document\\.title = .*Alicia 云盘后台" \
    "Cloud console document title must follow the active view."

require_source_match \
    "cloud web exposes bundle size verifier" \
    "webApp/package.json" \
    '"verify:bundle-size"[[:space:]]*:[[:space:]]*"node scripts/verify-bundle-size\.mjs"' \
    "Cloud web build must expose the bundle size verifier."

require_source_match \
    "cloud web build runs bundle size verifier" \
    "webApp/package.json" \
    '"build"[[:space:]]*:[[:space:]]*"[^"]*vite build && npm run verify:built-shell && npm run verify:bundle-size' \
    "Cloud web build must verify built shell and bundle size after vite build."

require_source_match \
    "cloud web exposes built shell verifier" \
    "webApp/package.json" \
    '"verify:built-shell"[[:space:]]*:[[:space:]]*"node scripts/verify-built-shell\.mjs"' \
    "Cloud web build must expose the built shell verifier."

require_source_match \
    "cloud web built shell verifier checks title" \
    "webApp/scripts/verify-built-shell.mjs" \
    'Alicia 云盘' \
    "Cloud web built shell verifier must assert the cloud web title."

require_source_match \
    "cloud web built shell verifier checks asset prefix" \
    "webApp/scripts/verify-built-shell.mjs" \
    '/cloudPan/assets/' \
    "Cloud web built shell verifier must assert the mounted asset prefix."

require_source_match \
    "cloud web exposes API contract verifier" \
    "webApp/package.json" \
    '"verify:api-contracts"[[:space:]]*:[[:space:]]*"node scripts/verify-api-contracts\.mjs"' \
    "Cloud web build must expose the CloudStorageApi contract verifier."

require_source_match \
    "cloud web build verifies API contracts" \
    "webApp/package.json" \
    'npm run verify:client-boundary && npm run verify:api-contracts && tsc -b' \
    "Cloud web build must verify CloudStorageApi contracts before TypeScript compile."

require_source_match \
    "cloud web contract verifier compares current profile response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'UserProfileResponse' \
    "Cloud web contract verifier must compare the CloudStorageApi current profile response."

require_source_match \
    "cloud web contract verifier compares storage node response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'StorageNodeSummaryResponse' \
    "Cloud web contract verifier must compare personal storage node responses."

require_source_match \
    "cloud web contract verifier compares multipart upload request" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'CreateMultipartUploadRequest' \
    "Cloud web contract verifier must compare multipart upload requests."

require_source_match \
    "cloud web contract verifier compares share detail response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'ShareLinkDetailResponse' \
    "Cloud web contract verifier must compare share detail responses."

require_source_match \
    "cloud web contract verifier compares query parameters" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'extractRequestParamsForMethod' \
    "Cloud web contract verifier must compare storage/share query parameters."

require_source_match \
    "cloud web contract verifier compares endpoint contracts" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'cloudWebEndpointContracts' \
    "Cloud web contract verifier must compare CloudStorageApi endpoint paths and methods."

require_source_match \
    "cloud web contract verifier binds controller mappings" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodMapping' \
    "Cloud web contract verifier must bind frontend API calls to controller mappings."

require_source_match \
    "cloud web contract verifier checks frontend auth tokens" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesAuthToken' \
    "Cloud web contract verifier must ensure protected frontend API calls send the current auth token."

require_source_match \
    "cloud web contract verifier checks typed payloads" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionStringifiesPayload' \
    "Cloud web contract verifier must ensure typed payloads are sent."

require_source_match \
    "cloud web contract verifier checks share access tokens" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertApiFunctionUsesShareAccessToken' \
    "Cloud web contract verifier must ensure share access tokens are sent."

require_source_match \
    "cloud web contract verifier checks share controller access headers" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodReceivesShareAccessToken' \
    "Cloud web contract verifier must ensure share controllers receive share access tokens."

require_source_match \
    "cloud web contract verifier checks current principal interceptor" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertCloudStorageApiCurrentPrincipalInterceptorContract' \
    "Cloud web contract verifier must ensure user APIs stay protected by CurrentPrincipalInterceptor."

require_source_match \
    "cloud web contract verifier compares identity endpoint contracts" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'cloudWebIdentityEndpointContracts' \
    "Cloud web contract verifier must compare IdentityApi endpoint paths and methods."

require_source_match \
    "cloud web contract verifier compares identity session response" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'IdentitySessionResponse' \
    "Cloud web contract verifier must compare identity session responses."

require_source_match \
    "cloud web contract verifier binds profile updates to IdentityApi" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'UpdateIdentityProfileRequest' \
    "Cloud web contract verifier must bind profile updates to IdentityApi."

require_source_match \
    "cloud web contract verifier checks identity authorization headers" \
    "webApp/scripts/verify-api-contracts.mjs" \
    'assertControllerMethodReceivesAuthorization' \
    "Cloud web contract verifier must ensure protected IdentityApi methods receive Authorization headers."

require_source_match \
    "cloud web bundle size cap is 500 KiB" \
    "webApp/scripts/verify-bundle-size.mjs" \
    'maxChunkBytes = 500 \* 1024' \
    "Cloud web bundle size verifier must cap JavaScript chunks at 500 KiB."

require_source_match \
    "cloud web keeps vendor chunking" \
    "webApp/vite.config.ts" \
    'manualChunks\(moduleId\)' \
    "Cloud web Vite config must keep explicit vendor chunking."

require_source_match \
    "cloud web keeps Ant Design module chunking" \
    "webApp/vite.config.ts" \
    'getAntdModuleChunk\(id\)' \
    "Cloud web Vite config must keep Ant Design module chunking."

require_source_match \
    "cloud console exposes bundle size verifier" \
    "sysManage/package.json" \
    '"verify:bundle-size"[[:space:]]*:[[:space:]]*"node scripts/verify-bundle-size\.mjs"' \
    "Cloud console build must expose the bundle size verifier."

require_source_match \
    "cloud console build runs bundle size verifier" \
    "sysManage/package.json" \
    '"build"[[:space:]]*:[[:space:]]*"[^"]*vite build && npm run verify:built-shell && npm run verify:bundle-size' \
    "Cloud console build must verify built shell and bundle size after vite build."

require_source_match \
    "cloud console exposes built shell verifier" \
    "sysManage/package.json" \
    '"verify:built-shell"[[:space:]]*:[[:space:]]*"node scripts/verify-built-shell\.mjs"' \
    "Cloud console build must expose the built shell verifier."

require_source_match \
    "cloud console built shell verifier checks title" \
    "sysManage/scripts/verify-built-shell.mjs" \
    'Alicia 云盘后台' \
    "Cloud console built shell verifier must assert the cloud console title."

require_source_match \
    "cloud console built shell verifier checks asset prefix" \
    "sysManage/scripts/verify-built-shell.mjs" \
    '/console/cloud/assets/' \
    "Cloud console built shell verifier must assert the mounted asset prefix."

require_source_match \
    "cloud console bundle size cap is 500 KiB" \
    "sysManage/scripts/verify-bundle-size.mjs" \
    'maxChunkBytes = 500 \* 1024' \
    "Cloud console bundle size verifier must cap JavaScript chunks at 500 KiB."

require_source_match \
    "cloud console bundle verifier reports console target" \
    "sysManage/scripts/verify-bundle-size.mjs" \
    'cloud console bundle size verified' \
    "Cloud console bundle size verifier must report the console target."

require_source_match \
    "cloud console keeps vendor chunking" \
    "sysManage/vite.config.ts" \
    'manualChunks\(moduleId\)' \
    "Cloud console Vite config must keep explicit vendor chunking."

require_source_match \
    "cloud console keeps Ant Design module chunking" \
    "sysManage/vite.config.ts" \
    'getAntdModuleChunk\(id\)' \
    "Cloud console Vite config must keep Ant Design module chunking."

require_source_match \
    "cloud route verifier asserts cloud console shell" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'Alicia 云盘后台' \
    "Cloud route verifier must assert cloud console serves the console shell."

require_source_match \
    "cloud route verifier asserts cloud console asset prefix" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    "/console/cloud/assets/index-" \
    "Cloud route verifier must assert cloud console assets use the mounted prefix."

require_source_match \
    "cloud route verifier asserts cloud web asset prefix" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    "/cloudPan/assets/index-" \
    "Cloud route verifier must assert cloud web assets use the mounted prefix."

require_source_match \
    "cloud route verifier covers identity console login returnTo" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    "login\\?returnTo=/console/identity/users" \
    "Cloud route verifier must assert login preserves identity console returnTo."

require_source_match \
    "cloud route verifier covers cloud console login returnTo" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    "login\\?returnTo=/console/cloud/users" \
    "Cloud route verifier must assert login preserves cloud console returnTo."

require_source_match \
    "cloud route verifier asserts gateway main shell" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_shell "main site home entry"' \
    "Cloud route verifier must assert the gateway still serves the main site shell."

require_source_match \
    "cloud route verifier asserts public login returnTo shell" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_shell "main site login returnTo entry"' \
    "Cloud route verifier must assert public login returnTo serves the main site shell."

require_source_match \
    "cloud route verifier asserts cloud share deep links" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_shell "cloudPan share route"' \
    "Cloud route verifier must assert cloud share deep links serve the cloud web shell."

require_source_match \
    "cloud route verifier asserts app download route status" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'curl_ok "cloudPan app download route"' \
    "Cloud route verifier must assert cloud app download route remains mounted."

require_source_match \
    "cloud route verifier asserts app download route shell" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_shell "cloudPan app download route"' \
    "Cloud route verifier must assert cloud app download route serves the cloud web shell."

require_source_match \
    "cloud route verifier asserts main no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "main site index"' \
    "Cloud route verifier must assert main site index no-store cache."

require_source_match \
    "cloud route verifier asserts login no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "main site login index"' \
    "Cloud route verifier must assert main site login no-store cache."

require_source_match \
    "cloud route verifier asserts identity no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "identity console roles index"' \
    "Cloud route verifier must assert identity console child routes no-store cache."

require_source_match \
    "cloud route verifier asserts cloud web no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "cloudPan index"' \
    "Cloud route verifier must assert cloud web index no-store cache."

require_source_match \
    "cloud route verifier asserts app download no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "cloudPan app download index"' \
    "Cloud route verifier must assert cloud app download route no-store cache."

require_source_match \
    "cloud route verifier asserts cloud console no-store cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_no_store_index "cloud console operations index"' \
    "Cloud route verifier must assert cloud console child routes no-store cache."

require_source_match \
    "cloud route verifier asserts main asset cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_asset_cache "main site"' \
    "Cloud route verifier must assert main site immutable assets through the cloud gateway."

require_source_match \
    "cloud route verifier asserts identity asset cache" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'expect_spa_asset_cache "identity console"' \
    "Cloud route verifier must assert identity console immutable assets."

require_source_match \
    "cloud route verifier exposes cache skip" \
    "deploy/scripts/verify-identity-cloud-routes.sh" \
    'ALICIA_VERIFY_SKIP_CACHE_CHECKS' \
    "Cloud route verifier must support skipping cache checks."
