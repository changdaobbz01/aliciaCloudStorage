param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Fail {
    param([string]$Message)
    throw $Message
}

function Resolve-RepoPath {
    param([string]$RelativePath)
    return (Join-Path $RootDir $RelativePath)
}

function Read-RepoText {
    param([string]$RelativePath)

    $path = Resolve-RepoPath $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Fail "Missing file: $RelativePath"
    }

    return Get-Content -LiteralPath $path -Raw
}

function Require-Contains {
    param(
        [string]$RelativePath,
        [string]$Needle,
        [string]$Message
    )

    $source = Read-RepoText $RelativePath
    if (-not $source.Contains($Needle)) {
        Fail $Message
    }
}

function Require-NoMatch {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Message
    )

    $source = Read-RepoText $RelativePath
    if ([regex]::IsMatch($source, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        Fail $Message
    }
}

function Require-Match {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Message
    )

    $source = Read-RepoText $RelativePath
    if (-not [regex]::IsMatch($source, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        Fail $Message
    }
}

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Script
    )

    Write-Host "[RUN] $Name"
    & $Script
    Write-Host "[OK] $Name"
}

function Invoke-NpmScript {
    param(
        [string]$RelativeDirectory,
        [string]$ScriptName
    )

    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) {
        $npm = Get-Command npm -ErrorAction Stop
    }

    Push-Location (Resolve-RepoPath $RelativeDirectory)
    try {
        & $npm.Source run $ScriptName
        if ($LASTEXITCODE -ne 0) {
            Fail "npm run $ScriptName failed in $RelativeDirectory"
        }
    } finally {
        Pop-Location
    }
}

if (-not $SkipBuild) {
    Invoke-Step "build cloud webApp" { Invoke-NpmScript "webApp" "build" }
    Invoke-Step "build cloud sysManage" { Invoke-NpmScript "sysManage" "build" }
}

Invoke-Step "verify cloud frontend split wiring" {
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-builder /app/webApp/dist /usr/share/nginx/html/cloudPan" "cloud Dockerfile must package webApp under /cloudPan"
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-builder /app/webApp/dist/.well-known /usr/share/nginx/html/.well-known" "cloud Dockerfile must publish Android asset links at the domain root"
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-console-builder /app/sysManage/dist /usr/share/nginx/html/console/cloud" "cloud Dockerfile must package sysManage under /console/cloud"
    Require-Contains "webApp/vite.config.ts" "base: '/cloudPan/'" "cloud web Vite base must stay mounted under /cloudPan/"
    Require-Contains "sysManage/vite.config.ts" "base: '/console/cloud/'" "cloud console Vite base must stay mounted under /console/cloud/"
    Require-Contains "webApp/package.json" '"verify:return-to": "node scripts/verify-unified-login-return-to.mjs"' "cloud web package must expose the unified login returnTo verifier"
    Require-Contains "webApp/package.json" "npm run verify:return-to && npm run verify:session-sync" "cloud web build must run returnTo verification before session and compile checks"
    Require-Contains "sysManage/package.json" '"verify:return-to": "node scripts/verify-unified-login-return-to.mjs"' "cloud console package must expose the unified login returnTo verifier"
    Require-Contains "sysManage/package.json" "npm run verify:return-to && npm run verify:session-sync" "cloud console build must run returnTo verification before session and compile checks"
    Require-Contains "webApp/Dockerfile" "ARG VITE_ANDROID_PACKAGE_NAME=com.alicia.cloudstorage.phone" "cloud Dockerfile must default Android package name to the official applicationId"
    Require-Contains "compose.yaml" "VITE_ANDROID_PACKAGE_NAME: ${VITE_ANDROID_PACKAGE_NAME:-com.alicia.cloudstorage.phone}" "cloud compose must pass the Android package build arg"
    Require-Contains ".env.example" "VITE_ANDROID_PACKAGE_NAME=com.alicia.cloudstorage.phone" "cloud root env example must document the Android package build arg"
    Require-Contains "webApp/.env.example" "VITE_ANDROID_PACKAGE_NAME=com.alicia.cloudstorage.phone" "cloud web env example must document the Android package name"

    foreach ($conf in @("webApp/nginx/default.conf", "webApp/nginx/default.ssl.conf")) {
        Require-Contains $conf "location ^~ /cloudPan/" "$conf must mount cloudPan deep links"
        Require-Contains $conf 'try_files $uri $uri/ /cloudPan/index.html;' "$conf must serve cloudPan SPA fallback"
        Require-Contains $conf "location = /.well-known/assetlinks.json" "$conf must serve Android asset links from the domain root"
        Require-Contains $conf "location ^~ /console/cloud/" "$conf must mount cloud console deep links"
        Require-Contains $conf 'try_files $uri $uri/ /console/cloud/index.html;' "$conf must serve cloud console SPA fallback"
        Require-Contains $conf "return 308 /login?returnTo=/cloudPan/;" "$conf must redirect legacy cloudPan login to unified login"
    }

    Require-NoMatch "webApp/src/App.tsx" 'path="/console' "cloud webApp must not mount console routes"
    Require-NoMatch "webApp/src/pages/DrivePage.tsx" 'consoleHome|/console(/|$)' "cloud web account menu must not expose console entry points"
    Require-NoMatch "webApp/src/index.css" '\.account-admin-tabs|\.audit-(filter|quick|result)|\.operations-|\.app-package-(summary|grid|card|link|url|meta|release-notes|list)|\.management-summary-|\.user-cell-copy|\.user-chip|\.table-secondary-text' "cloud web stylesheet must not keep admin console leftovers"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "assertApiPathsMatchAllowedPrefixes" "cloud web boundary verifier must enforce API ownership allowlists"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "'/api/storage/'" "cloud web API allowlist must include personal storage APIs"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "'/api/share-links'" "cloud web API allowlist must include personal share APIs"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "'/api/identity/auth/'" "cloud web API allowlist must include only identity auth APIs"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web profile modal must keep the shared profile dialog contract" "cloud web profile modal verifier must enforce the shared account profile contract"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web profile modal must not keep legacy profile layout aliases" "cloud web profile modal verifier must reject legacy profile layout aliases"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web styles must not keep legacy profile layout aliases" "cloud web profile style verifier must reject legacy profile layout aliases"
    Require-NoMatch "webApp/src/features/drive/DriveProfileModals.tsx" "profile-avatar-preview-row|profile-avatar-actions" "cloud web profile modal must not keep legacy profile layout aliases"
    Require-NoMatch "webApp/src/index.css" "\.profile-avatar-preview-row\b|\.profile-avatar-actions\b" "cloud web styles must not keep legacy profile layout aliases"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/share/abc123', '?mode=save', '#files']" "cloud web returnTo verifier must preserve share deep links"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/app-download', '?share=abc123', '']" "cloud web returnTo verifier must preserve app download deep links"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/api/storage/overview', '', '']" "cloud web returnTo verifier must reject storage API paths"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/console/cloud/', '', '']" "cloud web returnTo verifier must reject cloud console paths"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/console/identity/', '', '']" "cloud web returnTo verifier must reject identity console paths"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/console/', '', '']" "cloud web returnTo verifier must reject the shared console gateway path"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "['/rag/', '', '']" "cloud web returnTo verifier must reject RAG paths"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "history cache restores" "cloud web must restore local session state after browser history cache restores"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "only expire local sessions on authentication failures" "cloud web must not clear sessions for transient API failures"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud-safe cached user from identity login sessions" "cloud web must seed a cached user snapshot from identity login sessions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "sanitize legacy browser session residue" "cloud web must sanitize legacy browser session residue"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "ignore stale-token 401 responses" "cloud web must ignore stale-token 401 responses"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "refresh requests must not broadcast global session expiry" "cloud web refresh requests must not broadcast global session expiry before snapshot checks"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "confirm the current session before redirecting on auth-expired events" "cloud web must confirm the current session before redirecting on auth-expired events"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "no stored session as logout/no-op rather than session expiry" "cloud web must treat post-logout auth-expired events as logout/no-op"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<User>('/api/cloud-profile/me'" "cloud console must read current cloud profile from CloudStorageApi"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<User[]>('/api/admin/cloud-users'" "cloud console users view must use the CloudStorageApi cloud-users admin endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<AdminCloudOperationsOverview>('/api/admin/cloud-operations/overview'" "cloud console operations view must use the CloudStorageApi cloud-operations overview endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<AppPackageInfo>('/api/admin/app-package'" "cloud console APK view must read the CloudStorageApi admin app package endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestUploadJson<AppPackageInfo>('/api/admin/app-package'" "cloud console APK upload must use the CloudStorageApi admin app package endpoint"
    Require-Contains "sysManage/src/features/drive/driveShared.ts" "APP_DOWNLOAD_PUBLIC_PATH = '/api/app-package/download/current'" "cloud console APK download link must use the public CloudStorageApi package endpoint"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "assertApiPathsMatchAllowedPrefixes" "cloud console boundary verifier must enforce API ownership allowlists"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/admin/cloud-users'" "cloud console API allowlist must include cloud users admin APIs"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/admin/cloud-operations'" "cloud console API allowlist must include cloud operations admin APIs"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/identity/auth/token/refresh'" "cloud console API allowlist must include identity refresh only for session continuity"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/identity/auth/profile'" "cloud console API allowlist must include identity profile only for current-user settings"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console page must use the centralized cloud admin predicate" "cloud console boundary verifier must enforce the runtime cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must use the unified account profile form layout" "cloud console boundary verifier must enforce the unified profile modal layout"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must keep the shared profile dialog contract" "cloud console profile modal verifier must enforce the shared account profile contract"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must not keep legacy profile layout aliases" "cloud console profile modal verifier must reject legacy profile layout aliases"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console styles must not keep legacy profile layout aliases" "cloud console profile style verifier must reject legacy profile layout aliases"
    Require-NoMatch "sysManage/src/pages/CloudConsolePage.tsx" "profile-avatar-preview-row|profile-avatar-actions" "cloud console profile modal must not keep legacy profile layout aliases"
    Require-NoMatch "sysManage/src/index.css" "\.profile-avatar-preview-row\b|\.profile-avatar-actions\b" "cloud console styles must not keep legacy profile layout aliases"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "global admins and cloud application admins" "cloud console boundary verifier must accept global and cloud application administrators"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/app-package', '?release=current', '#upload']" "cloud console returnTo verifier must preserve APK package deep links"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/cloud/app-package', '', '']" "cloud console returnTo verifier must preserve mounted APK package routes"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "buildUnifiedLoginUrl(cloudConsoleReturnTo('/app-package'), 'login-required')" "cloud console returnTo verifier must cover APK package login redirects"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/api/admin/cloud-users', '', '']" "cloud console returnTo verifier must reject admin API paths"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/identity/', '', '']" "cloud console returnTo verifier must reject identity console paths"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/', '', '']" "cloud console returnTo verifier must reject the shared console gateway path"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/rag/', '', '']" "cloud console returnTo verifier must reject RAG paths"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "history cache restores" "cloud console must restore local session state after browser history cache restores"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "only expire local sessions on authentication failures" "cloud console must not clear sessions for transient API failures"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "cloud-safe cached user from identity login sessions" "cloud console must seed a cached user snapshot from identity login sessions"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "sanitize legacy browser session residue" "cloud console must sanitize legacy browser session residue"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "ignore stale-token 401 responses" "cloud console must ignore stale-token 401 responses"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "refresh requests must not broadcast global session expiry" "cloud console refresh requests must not broadcast global session expiry before snapshot checks"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "confirm the current session before redirecting on auth-expired events" "cloud console must confirm the current session before redirecting on auth-expired events"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "no stored session as logout/no-op rather than session expiry" "cloud console must treat post-logout auth-expired events as logout/no-op"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserController.java" '@RequestMapping("/api/admin/cloud-users")' "CloudStorageApi must expose the cloud-users admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserProfileController.java" '@RequestMapping("/api/admin/cloud-users")' "CloudStorageApi must expose the cloud user quota admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudOperationsController.java" '@RequestMapping("/api/admin/cloud-operations")' "CloudStorageApi must expose the cloud operations admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminAppPackageController.java" '@RequestMapping("/api/admin/app-package")' "CloudStorageApi must expose the APK admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AppPackageController.java" '@RequestMapping("/api/app-package")' "CloudStorageApi must expose the public APK package backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/config/WebMvcConfig.java" '.addPathPatterns("/api/admin/**");' "CloudStorageApi must protect admin endpoints through AdminPrincipalInterceptor"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" 'CLOUD_ADMIN_ROLE = "CLOUD_ADMIN"' "CloudStorageApi admin principal must continue to use the cloud app admin role"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" "role == UserRole.ADMIN || CLOUD_ADMIN_ROLE.equals(appRoles().get(CLOUD_APP_CODE))" "CloudStorageApi admin principal must accept global admins and cloud app admins"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "should_defer_main_site_public_boundary()" "main/cloud update script must support deferring main public gateway checks"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY=true bash "$MAIN_SITE_UPDATE_SCRIPT" "$MAIN_SITE_PROJECT_DIR"' "main/cloud update script must defer main public checks while cloud gateway is pending"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "run_final_main_site_route_verify" "main/cloud update script must rerun main route verification after cloud gateway update"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_SKIP_FINAL_MAIN_SITE_VERIFY' "main/cloud update script must expose a final main route verification skip switch"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "Skipping final main site route verification; route verify script is missing because main site update was skipped" "main/cloud update script must allow cloud-only updates when the main site verifier is unavailable"
    Require-NoMatch "deploy/scripts/update-main-and-cloud-production.sh" '\[\[ "\$SKIP_MAIN_SITE_UPDATE" != "true" \]\] \|\| return 0' "main/cloud update script must still run final main route verification after cloud updates even when main site update was skipped"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-$HOME/mainSite}"' "production status snapshot must locate the main site repository"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'git_snapshot "main site repository" "$MAIN_SITE_PROJECT_DIR"' "production status snapshot must include main site Git state"
    Require-Contains "deploy/scripts/collect-production-status.sh" "run_main_site_route_verify()" "production status snapshot must expose optional main site route verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" "run_main_site_boundary_check()" "production status snapshot must expose optional main site boundary verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'run_optional "main site route verification" run_main_site_route_verify' "production status snapshot must run main site route verification when full route checks are requested"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'run_optional "main site frontend boundary check" run_main_site_boundary_check' "production status snapshot must run main site frontend boundary checks when static boundary checks are requested"
    Require-Contains "deploy/scripts/collect-production-status.sh" "curl_redirect_probe()" "production status snapshot must include canonical redirect probes"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "console gateway bare path" "$PUBLIC_BASE_URL/console" "/console/"' "production status snapshot must verify the shared console gateway redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloudPan bare path" "$PUBLIC_BASE_URL/cloudPan" "/cloudPan/"' "production status snapshot must verify the cloud web canonical redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloud console bare path" "$PUBLIC_BASE_URL/console/cloud" "/console/cloud/"' "production status snapshot must verify the cloud console canonical redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloudPan legacy login" "$PUBLIC_BASE_URL/cloudPan/login" "/login?returnTo=/cloudPan/"' "production status snapshot must verify legacy cloud login handoff"
    Require-Contains "webApp/src/lib/mobileApp.ts" "DEFAULT_ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone'" "cloud web intent fallback package must match the official Android applicationId"
    Require-Contains "webApp/public/.well-known/assetlinks.json" '"package_name": "com.alicia.cloudstorage.phone"' "cloud asset links must authorize the official Android package"
    Require-NoMatch "webApp/public/.well-known/assetlinks.json" 'com\.alicia\.cloudstorage\.phone\.add' "cloud asset links must not authorize the old Android test package"
    Require-Contains "webApp/package.json" '"verify:bundle-size": "node scripts/verify-bundle-size.mjs"' "cloud web package must expose the bundle size verifier"
    Require-Contains "webApp/package.json" '"verify:built-shell": "node scripts/verify-built-shell.mjs"' "cloud web package must expose the built shell verifier"
    Require-Contains "webApp/package.json" "vite build && npm run verify:built-shell && npm run verify:bundle-size" "cloud web build must verify built shell and bundle size after vite build"
    Require-Contains "webApp/scripts/verify-built-shell.mjs" "Alicia 云盘" "cloud web built shell verifier must assert the cloud web title"
    Require-Contains "webApp/scripts/verify-built-shell.mjs" "/cloudPan/assets/" "cloud web built shell verifier must assert mounted asset prefix"
    Require-Contains "webApp/scripts/verify-bundle-size.mjs" "maxChunkBytes = 500 * 1024" "cloud web bundle size verifier must cap JS chunks at 500 KiB"
    Require-Contains "webApp/vite.config.ts" "manualChunks(moduleId)" "cloud web Vite config must keep explicit vendor chunking"
    Require-Contains "webApp/vite.config.ts" "getAntdModuleChunk(id)" "cloud web Vite config must keep Ant Design module chunking"
    Require-Contains "sysManage/package.json" '"verify:bundle-size": "node scripts/verify-bundle-size.mjs"' "cloud console package must expose the bundle size verifier"
    Require-Contains "sysManage/package.json" '"verify:built-shell": "node scripts/verify-built-shell.mjs"' "cloud console package must expose the built shell verifier"
    Require-Contains "sysManage/package.json" "vite build && npm run verify:built-shell && npm run verify:bundle-size" "cloud console build must verify built shell and bundle size after vite build"
    Require-Contains "sysManage/scripts/verify-built-shell.mjs" "Alicia 云盘后台" "cloud console built shell verifier must assert the cloud console title"
    Require-Contains "sysManage/scripts/verify-built-shell.mjs" "/console/cloud/assets/" "cloud console built shell verifier must assert mounted asset prefix"
    Require-Contains "sysManage/scripts/verify-bundle-size.mjs" "maxChunkBytes = 500 * 1024" "cloud console bundle size verifier must cap JS chunks at 500 KiB"
    Require-Contains "sysManage/scripts/verify-bundle-size.mjs" "cloud console bundle size verified" "cloud console bundle size verifier must report the console target"
    Require-Contains "sysManage/vite.config.ts" "manualChunks(moduleId)" "cloud console Vite config must keep explicit vendor chunking"
    Require-Contains "sysManage/vite.config.ts" "getAntdModuleChunk(id)" "cloud console Vite config must keep Ant Design module chunking"
    Require-Match "sysManage/src/pages/CloudConsolePage.tsx" 'document\.title = `\$\{activeMeta\.title\} - Alicia .+`;' "cloud console document title must follow the active view"
    Require-Contains "sysManage/src/pages/CloudConsolePage.tsx" 'className="account-profile-form"' "cloud console profile modal must use the unified account profile layout"
    Require-Contains "sysManage/src/features/drive/CloudUsersView.tsx" 'title={<AliciaModalTitle eyebrow="Cloud">调整云盘额度</AliciaModalTitle>}' "cloud console quota modal must use the unified Alicia modal title"
    Require-Contains "sysManage/src/features/drive/CloudUsersView.tsx" 'rootClassName="alicia-modal alicia-account-modal cloud-quota-modal"' "cloud console quota modal must use the unified Alicia modal chrome"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "Alicia 云盘后台" "cloud route verifier must assert cloud console serves the console shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "/console/cloud/assets/index-" "cloud route verifier must assert cloud console assets use the mounted prefix"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "/cloudPan/assets/index-" "cloud route verifier must assert cloud web assets use the mounted prefix"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "login?returnTo=/console/identity/users" "cloud route verifier must assert login preserves identity console returnTo"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "login?returnTo=/console/cloud/users" "cloud route verifier must assert login preserves cloud console returnTo"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "main site home entry"' "cloud route verifier must assert the gateway still serves the main site shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "main site login returnTo entry"' "cloud route verifier must assert public login returnTo serves the main site shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "cloudPan share route"' "cloud route verifier must assert cloud share deep links serve the cloud web shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "main site index"' "cloud route verifier must assert main site index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "main site login index"' "cloud route verifier must assert main site login no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "identity console roles index"' "cloud route verifier must assert identity console child routes no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloudPan index"' "cloud route verifier must assert cloud web index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloud console index"' "cloud route verifier must assert cloud console index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloud console operations index"' "cloud route verifier must assert cloud console child routes no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_asset_cache "main site"' "cloud route verifier must assert main site immutable assets through the cloud gateway"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_asset_cache "identity console"' "cloud route verifier must assert identity console immutable assets"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'SKIP_CACHE_CHECKS="${ALICIA_VERIFY_SKIP_CACHE_CHECKS:-false}"' "cloud route verifier must support skipping cache checks"
}

Write-Host "[OK] cloud frontend split local verification complete"
