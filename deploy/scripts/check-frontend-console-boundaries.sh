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
    "cloud console API allowlist is enforced" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'assertApiPathsMatchAllowedPrefixes' \
    "Cloud console boundary verifier must enforce API ownership allowlists."

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
    "cloud console boundary checks runtime cloud admin gate" \
    "sysManage/scripts/verify-console-boundary.mjs" \
    'cloud console page must use the centralized cloud admin predicate' \
    "Cloud console boundary verifier must enforce the runtime cloud admin gate."

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
    "window\\.location\\.assign\\('/console/identity/'\\)" \
    "Cloud console should use the unified /console/ gateway instead of hard-linking to identity console."

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
