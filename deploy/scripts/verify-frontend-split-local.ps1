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

function Require-MatchCountAtLeast {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [int]$MinimumCount,
        [string]$Message
    )

    $source = Read-RepoText $RelativePath
    $count = [regex]::Matches($source, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline).Count
    if ($count -lt $MinimumCount) {
        Fail "$Message Found $count, expected at least $MinimumCount."
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

function Invoke-NodeScript {
    param([string]$RelativeScript)

    $node = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $node) {
        $node = Get-Command node -ErrorAction Stop
    }

    $scriptPath = Resolve-RepoPath $RelativeScript
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        Fail "Missing Node verification script: $RelativeScript"
    }

    & $node.Source $scriptPath
    if ($LASTEXITCODE -ne 0) {
        Fail "node $RelativeScript failed"
    }
}

if (-not $SkipBuild) {
    Invoke-Step "build cloud webApp" { Invoke-NpmScript "webApp" "build" }
    Invoke-Step "build cloud sysManage" { Invoke-NpmScript "sysManage" "build" }
} else {
    Invoke-Step "verify cloud webApp returnTo boundary" { Invoke-NodeScript "webApp\scripts\verify-unified-login-return-to.mjs" }
    Invoke-Step "verify cloud webApp session sync boundary" { Invoke-NodeScript "webApp\scripts\verify-session-sync.mjs" }
    Invoke-Step "verify cloud webApp client boundary" { Invoke-NodeScript "webApp\scripts\verify-client-boundary.mjs" }
    Invoke-Step "verify cloud webApp API contracts" { Invoke-NodeScript "webApp\scripts\verify-api-contracts.mjs" }
    Invoke-Step "verify cloud sysManage returnTo boundary" { Invoke-NodeScript "sysManage\scripts\verify-unified-login-return-to.mjs" }
    Invoke-Step "verify cloud sysManage session sync boundary" { Invoke-NodeScript "sysManage\scripts\verify-session-sync.mjs" }
    Invoke-Step "verify cloud sysManage console boundary" { Invoke-NodeScript "sysManage\scripts\verify-console-boundary.mjs" }
    Invoke-Step "verify cloud sysManage API contracts" { Invoke-NodeScript "sysManage\scripts\verify-api-contracts.mjs" }
}

Invoke-Step "verify cloud frontend split wiring" {
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-builder /app/webApp/dist /usr/share/nginx/html/cloudPan" "cloud Dockerfile must package webApp under /cloudPan"
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-builder /app/webApp/dist/.well-known /usr/share/nginx/html/.well-known" "cloud Dockerfile must publish Android asset links at the domain root"
    Require-Contains "webApp/Dockerfile" "COPY --from=cloud-console-builder /app/sysManage/dist /usr/share/nginx/html/console/cloud" "cloud Dockerfile must package sysManage under /console/cloud"
    Require-MatchCountAtLeast "webApp/Dockerfile" "COPY CloudStorageApi/src /app/CloudStorageApi/src" 2 "cloud Dockerfile must copy CloudStorageApi source into both frontend build stages for API contract verification."
    Require-MatchCountAtLeast "webApp/Dockerfile" "COPY identityApi/src /app/identityApi/src" 2 "cloud Dockerfile must copy identityApi source into both frontend build stages for API contract verification."
    Require-Contains "webApp/vite.config.ts" "base: '/cloudPan/'" "cloud web Vite base must stay mounted under /cloudPan/"
    Require-Contains "sysManage/vite.config.ts" "base: '/console/cloud/'" "cloud console Vite base must stay mounted under /console/cloud/"
    Require-Contains "webApp/package.json" '"verify:return-to": "node scripts/verify-unified-login-return-to.mjs"' "cloud web package must expose the unified login returnTo verifier"
    Require-Contains "webApp/package.json" "npm run verify:return-to && npm run verify:session-sync" "cloud web build must run returnTo verification before session and compile checks"
    Require-Contains "webApp/package.json" '"verify:api-contracts": "node scripts/verify-api-contracts.mjs"' "cloud web package must expose the CloudStorageApi contract verifier"
    Require-Contains "webApp/package.json" "npm run verify:client-boundary && npm run verify:api-contracts && tsc -b" "cloud web build must verify CloudStorageApi contracts before TypeScript compile"
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
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloudWebApiScopeFiles = files" "cloud web boundary verifier must scan all source files for API ownership"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web source" "cloud web boundary verifier must report full-source API ownership failures"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "isReturnPathBoundarySentinel" "cloud web boundary verifier must keep returnTo sentinels out of API ownership matches"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "assertNoForeignRoutePathLiterals" "cloud web boundary verifier must scan source files for foreign route exposure"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "must not expose foreign route paths from the cloud web user client" "cloud web boundary verifier must reject console and RAG route exposure"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud drive page must describe user-facing profile tools without admin management wording" "cloud web boundary verifier must reject admin management wording in user-facing drive page copy"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web app download panel must use Android installer copy without APK upload/admin wording" "cloud web boundary verifier must reject APK artifact wording in the user-facing download panel"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud app download page must describe package availability as a user-facing download state" "cloud web boundary verifier must reject release workflow wording in the app download page"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud app download page must track synchronous pending guards" "cloud web boundary verifier must track app download pending guards"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud app download package load must block duplicate reads and expose retry state" "cloud web boundary verifier must block duplicate app package reads"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud app download actions must block duplicate navigation" "cloud web boundary verifier must block duplicate app download navigation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud app download controls must surface pending loading and navigation state" "cloud web app download controls must surface pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud drive app package reads must keep synchronous loading guards" "cloud drive app package reads must keep synchronous loading guards"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud drive app package loading must be wired to list refresh" "cloud drive app package loading must be wired to list refresh"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web explorer refresh must surface app package loading state" "cloud web explorer refresh must surface app package loading state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web app download URL resolver must normalize download paths" "cloud web boundary verifier must normalize app download URLs"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web app download URL resolver must stay on the public package endpoint" "cloud web boundary verifier must keep app downloads on the public package endpoint"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web app download URL resolver must produce same-origin public download URLs" "cloud web boundary verifier must keep app downloads same-origin"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share revocation must track the pending share id" "cloud web boundary verifier must track pending share revocation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share list reads must track request identity" "cloud web share list reads must track request identity"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share list reads must compare the visible auth scope" "cloud web share list reads must compare visible auth scope"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share list reads must invalidate when auth scope changes" "cloud web share list reads must invalidate auth scope changes"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share list reads must block duplicate same-scope requests" "cloud web share list reads must block duplicate same-scope requests"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share list reads must ignore stale responses" "cloud web share list reads must ignore stale responses"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share mutations must force share list refresh after successful changes" "cloud web share mutations must force share list refresh"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share revocation must block duplicate submissions and clear pending state" "cloud web boundary verifier must block duplicate share revocation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web shares view must surface pending share revocation" "cloud web shares view must surface pending share revocation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web shares view must not refresh while list loading or revocation is pending" "cloud web shares view must avoid refresh while list loading or share revocation is pending"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud drive page must wire share revocation pending state to the UI" "cloud drive page must wire share revocation pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web shares view must track pending share link copies" "cloud web shares view must track pending share link copies"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web share link copies must block duplicate submissions" "cloud web share link copies must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web shares view must surface pending share link copies" "cloud web shares view must surface pending share link copies"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page must track synchronous pending guards" "cloud share page must track synchronous pending guards"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page mobile app handoff must track pending navigation" "cloud share page mobile app handoff must track pending navigation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page mobile app handoff must block duplicate navigation" "cloud share page mobile app handoff must block duplicate navigation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page mobile app handoff controls must surface pending navigation" "cloud share page mobile app handoff controls must surface pending navigation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page password check must block duplicate submissions" "cloud share page password check must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page save flow must block duplicate submissions and pending close" "cloud share page save flow must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page downloads must block competing submissions" "cloud share page downloads must block competing submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page row downloads must surface pending state" "cloud share page row downloads must surface pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page password form must surface pending state" "cloud share page password form must surface pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page toolbar actions must surface pending save and download state" "cloud share page toolbar actions must surface pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share page save modal must block pending close" "cloud share page save modal must block pending close"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share create modal must track pending copy actions" "cloud share create modal must track pending copy actions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share create modal copy actions must block duplicate submissions and pending close" "cloud share create modal copy actions must block duplicates and pending close"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud share create modal copy controls must surface pending state" "cloud share create modal copy controls must surface pending state"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web download tasks must update the synchronous task ref before React state" "cloud web download tasks must update synchronous task refs"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web download cancellation must only target cancelable transfer states" "cloud web download cancellation must only target cancelable states"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web download progress must ignore aborted transfers" "cloud web download progress must ignore aborted transfers"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web download cancellation must synchronously mark tasks canceled" "cloud web download cancellation must synchronously mark canceled tasks"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web downloads view must hide cancel for non-cancelable download states" "cloud web downloads view must hide cancel for non-cancelable states"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web list reads must track request identity" "cloud web list reads must track request identity"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web list reads must compare the full visible list scope" "cloud web list reads must compare visible list scope"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web list reads must block duplicate same-scope requests" "cloud web list reads must block duplicate same-scope requests"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web list reads must ignore stale responses" "cloud web list reads must ignore stale responses"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage mutations must force list refresh after successful changes" "cloud web storage mutations must force list refresh"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage mutation state must cover all personal file mutations" "cloud web storage mutation state must cover all personal file mutations"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage mutations must track a single pending operation" "cloud web storage mutations must track a single pending operation"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage mutations must block duplicate submissions" "cloud web storage mutations must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage dialogs must block close and submit while a storage mutation is pending" "cloud web storage dialogs must block close and submit while a storage mutation is pending"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage modals must surface pending create, rename, and move submissions" "cloud web storage modals must surface pending create, rename, and move submissions"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web explorer toolbar must surface pending storage mutations" "cloud web explorer toolbar must surface pending storage mutations"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud web storage table must surface pending row mutations and freeze selection" "cloud web storage table must surface pending row mutations and freeze selection"
    Require-Contains "webApp/scripts/verify-client-boundary.mjs" "cloud drive page must wire storage mutation pending state to dialogs and tables" "cloud drive page must wire storage mutation pending state"
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
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "server logout failures before local logout" "cloud web logout must keep local session cleanup independent of backend logout"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "clear the local session before notifying logout" "cloud web logout must clear local session state before broadcasting logout"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web logout must block duplicate submissions and surface pending state" "cloud web logout must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile menu logout must block duplicate submissions" "cloud web profile menu logout must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web account menu must surface pending logout state" "cloud web account menu must surface pending logout state"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud-safe cached user from identity login sessions" "cloud web must seed a cached user snapshot from identity login sessions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "sanitize legacy browser session residue" "cloud web must sanitize legacy browser session residue"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "ignore stale-token 401 responses" "cloud web must ignore stale-token 401 responses"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "refresh requests must not broadcast global session expiry" "cloud web refresh requests must not broadcast global session expiry before snapshot checks"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "logout requests must not broadcast global session expiry" "cloud web logout requests must not broadcast global session expiry after local logout starts"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "confirm the current session before redirecting on auth-expired events" "cloud web must confirm the current session before redirecting on auth-expired events"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "no stored session as logout/no-op rather than session expiry" "cloud web must treat post-logout auth-expired events as logout/no-op"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile sessions must load current identity sessions with the selected revoked filter" "cloud web profile sessions must load IdentityApi sessions with the selected revoked filter"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile sessions include-revoked toggle must reload with the selected state" "cloud web profile session toggle must preserve the selected revoked filter"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile session revocation must preserve the current revoked filter and block duplicate submissions" "cloud web profile session revocation must preserve the selected revoked filter and block duplicates"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile session revocation must track pending submissions" "cloud web profile session revocation must track pending state"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile session modal close must pause during session revocation" "cloud web profile session modal close must pause during revocation"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile session filter must pause during session revocation" "cloud web profile session filter must pause during revocation"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile session refresh must pause during session revocation" "cloud web profile session refresh must pause during revocation"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web drive page must route session refresh through the guarded profile action" "cloud web drive page must route session refresh through the guarded profile action"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web session modal must disable competing rows during session revocation" "cloud web profile session modal must disable competing revocation rows"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile updates must block duplicate submissions and surface pending state" "cloud web profile updates must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web avatar upload must block duplicate submissions and surface pending state" "cloud web avatar upload must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web profile modal must surface pending profile updates" "cloud web profile modal must surface pending profile updates"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web home background mutations must block duplicate submissions and surface pending state" "cloud web home background mutations must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web password change must block duplicate submissions and report failures" "cloud web password changes must block duplicate submissions"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web password modal must surface pending password changes" "cloud web password modal must surface pending password changes"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web session modal must surface pending session revocation" "cloud web session modal must surface pending session revocation"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web home background controls must surface pending state" "cloud web home background controls must surface pending state"
    Require-Contains "webApp/scripts/verify-session-sync.mjs" "cloud web drive page must wire profile pending states to the UI" "cloud web drive page must wire profile pending states to the UI"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<User>('/api/cloud-profile/me'" "cloud console must read current cloud profile from CloudStorageApi"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<User[]>('/api/admin/cloud-users'" "cloud console users view must use the CloudStorageApi cloud-users admin endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<AdminCloudOperationsOverview>('/api/admin/cloud-operations/overview'" "cloud console operations view must use the CloudStorageApi cloud-operations overview endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestJson<AppPackageInfo>('/api/admin/app-package'" "cloud console APK view must read the CloudStorageApi admin app package endpoint"
    Require-Contains "sysManage/src/lib/api.ts" "requestUploadJson<AppPackageInfo>('/api/admin/app-package'" "cloud console APK upload must use the CloudStorageApi admin app package endpoint"
    Require-Contains "sysManage/src/features/drive/driveShared.ts" "APP_DOWNLOAD_PUBLIC_PATH = '/api/app-package/download/current'" "cloud console APK download link must use the public CloudStorageApi package endpoint"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console app package download URL resolver must normalize download paths" "cloud console boundary verifier must normalize app package download URLs"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console app package download URL resolver must stay on the public package endpoint" "cloud console boundary verifier must keep app package downloads on the public package endpoint"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console app package panel must use the normalized public download path" "cloud console app package panel must use the normalized public download path"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "assertApiPathsMatchAllowedPrefixes" "cloud console boundary verifier must enforce API ownership allowlists"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloudConsoleApiScopeFiles = files" "cloud console boundary verifier must scan all source files for API ownership"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console source" "cloud console boundary verifier must report full-source API ownership failures"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "isReturnPathBoundarySentinel" "cloud console boundary verifier must keep returnTo sentinels out of API ownership matches"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "assertNoForbiddenRoutePathLiterals" "cloud console boundary verifier must scan source files for forbidden route exposure"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "must not expose identity console or RAG routes from the cloud console" "cloud console boundary verifier must reject identity console and RAG route exposure"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/admin/cloud-users'" "cloud console API allowlist must include cloud users admin APIs"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/admin/cloud-operations'" "cloud console API allowlist must include cloud operations admin APIs"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/identity/auth/token/refresh'" "cloud console API allowlist must include identity refresh only for session continuity"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "'/api/identity/auth/profile'" "cloud console API allowlist must include identity profile only for current-user settings"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console page must use the centralized cloud admin predicate" "cloud console boundary verifier must enforce the runtime cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console must centralize its role label copy" "cloud console boundary verifier must enforce centralized role label copy"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console users view must load CloudStorageApi cloud-users" "cloud console boundary verifier must pin the users view to CloudStorageApi cloud-users"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console users reads must keep synchronous loading guards" "cloud console boundary verifier must guard duplicate cloud users reads"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota modal must pause during user refreshes" "cloud console boundary verifier must pause quota modal while users refresh"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console users controls must surface pending loading state" "cloud console boundary verifier must surface cloud users loading state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console user directory must not expose identity user creation from sysManage" "cloud console boundary verifier must keep identity user creation out of sysManage"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console users view must keep identity account creation in the identity console" "cloud console boundary verifier must keep identity account creation in userSite"
    Require-NoMatch "sysManage/src/features/drive/CloudUsersView.tsx" '新增用户|创建用户|重置密码|Input\.Password|name="password"|inheritAdminBackground' "cloud console users view must not expose identity account creation controls"
    Require-NoMatch "sysManage/src/lib/api.ts" "export\s+function\s+create[A-Za-z]*User|createIdentityUser|resetIdentityUserPassword|fetchIdentityApplicationRoles|updateIdentityApplicationRole" "cloud console API must not expose identity user management helpers through sysManage"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota mutations must use CloudStorageApi cloud-users quota contract" "cloud console boundary verifier must pin quota updates to CloudStorageApi cloud-users quota"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota modal must present backend byte quotas as GiB" "cloud console boundary verifier must keep quota modal units readable"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota submit must convert GiB input to backend bytes" "cloud console boundary verifier must keep quota writes in backend byte units"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota submit must reject quotas below current usage" "cloud console boundary verifier must reject quota writes below current usage"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota modal close must respect pending quota submissions" "cloud console boundary verifier must keep quota modal close safe while saving"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota modal must block duplicate submissions" "cloud console boundary verifier must block duplicate quota submissions"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota updates must block duplicate submissions and surface pending state" "cloud console quota updates must block duplicate submissions"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations view must load CloudStorageApi operations overview" "cloud console boundary verifier must pin the operations overview to CloudStorageApi"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations view must load CloudStorageApi share operations" "cloud console boundary verifier must pin share operations to CloudStorageApi"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations view must load CloudStorageApi trash operations" "cloud console boundary verifier must pin trash operations to CloudStorageApi"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations view must load CloudStorageApi storage user operations" "cloud console boundary verifier must pin storage user operations to CloudStorageApi"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations APIs must stay read-only GET contracts" "cloud console boundary verifier must keep operations APIs read-only"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations view must not expose personal file mutation controls" "cloud console boundary verifier must keep operations views read-only"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations hook must not import personal file mutation flows" "cloud console boundary verifier must keep personal file mutations out of operations hooks"
    Require-NoMatch "sysManage/src/features/drive/DriveOperationsView.tsx" "title:\s*'操作'|onRestore|onDelete|onRevoke|restore[A-Z][A-Za-z0-9_]*|delete[A-Z][A-Za-z0-9_]*|revoke[A-Z][A-Za-z0-9_]*" "cloud console operations view must not expose personal file mutation controls"
    Require-NoMatch "sysManage/src/features/drive/hooks/useDriveOperationsAdmin.ts" "\b(update|delete|restore|revoke|permanentlyDelete|createShareLink|uploadStorageFile)[A-Za-z0-9_]*\(" "cloud console operations hook must not import personal file mutation flows"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload must use CloudStorageApi admin app package" "cloud console boundary verifier must pin APK uploads to CloudStorageApi admin app package"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload modal must restrict picker to Android package files" "cloud console boundary verifier must keep APK picker restricted to Android packages"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload modal must require a bounded version name" "cloud console boundary verifier must require bounded APK version names"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload modal must require bounded release notes" "cloud console boundary verifier must require bounded APK release notes"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload hook must reject non-APK files before storing the draft" "cloud console boundary verifier must reject non-APK upload drafts"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload must refresh admin and public package state together" "cloud console boundary verifier must refresh APK admin and public state together"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK delete must clear admin and public package state together" "cloud console boundary verifier must clear APK admin and public state together"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload mutations must stay behind the cloud admin gate and block duplicate submissions" "cloud console boundary verifier must block duplicate APK uploads"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK delete mutations must stay behind the cloud admin gate and block duplicate submissions" "cloud console boundary verifier must block duplicate APK deletion"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload modal controls must pause during package mutations" "cloud console APK upload modal controls must pause during package mutations"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload modal must surface pending upload state" "cloud console APK upload modal must surface pending upload state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK package panel must surface pending delete state" "cloud console boundary verifier must require pending delete UI state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK reads must keep synchronous loading guards" "cloud console boundary verifier must guard duplicate APK reads"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK package panel must pause package actions during loading" "cloud console boundary verifier must pause APK package actions during loading"
    Require-Contains "sysManage/src/components/AppPackagePanel.tsx" "okButtonProps={{ danger: true, loading: deleting, disabled: packageBusy }}" "cloud console APK delete confirmation must surface pending delete state"
    Require-Contains "sysManage/src/features/drive/DriveAppPackageUploadModal.tsx" 'accept=".apk,application/vnd.android.package-archive"' "cloud console APK upload picker must restrict Android packages"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console header refresh must not load admin view data without cloud admin access" "cloud console boundary verifier must keep header refresh behind the cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console header refresh must pause duplicate active-view refreshes" "cloud console boundary verifier must pause duplicate active-view refreshes"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console quota mutations must stay behind the cloud admin gate" "cloud console boundary verifier must keep quota mutations behind the cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations refresh must keep overview, storage users, trash, and shares together" "cloud console boundary verifier must keep operations refresh complete"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations reads must keep synchronous loading guards" "cloud console boundary verifier must guard duplicate operations reads"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations filters and pagination must pause during loading" "cloud console boundary verifier must pause operations query changes during loading"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console operations controls must surface pending loading state" "cloud console boundary verifier must surface operations loading state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console storage user loading must keep backend pagination state" "cloud console boundary verifier must keep storage user backend pagination state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console trash loading must keep backend pagination state" "cloud console boundary verifier must keep trash backend pagination state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console share loading must keep backend pagination state" "cloud console boundary verifier must keep share backend pagination state"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console storage user pagination must reload with current filters and pagination" "cloud console boundary verifier must preserve storage user filters during pagination"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console trash pagination must reload with current filters and pagination" "cloud console boundary verifier must preserve trash filters during pagination"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console share pagination must reload with current filters and pagination" "cloud console boundary verifier must preserve share filters during pagination"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK upload mutations must stay behind the cloud admin gate" "cloud console boundary verifier must keep APK uploads behind the cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console APK delete mutations must stay behind the cloud admin gate" "cloud console boundary verifier must keep APK deletion behind the cloud admin gate"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console permission denied copy must mention global admins and cloud admins" "cloud console boundary verifier must enforce accurate permission denied copy"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud users view role tags must use the centralized cloud role label copy" "cloud console boundary verifier must enforce role labels in the users view"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must use the unified account profile form layout" "cloud console boundary verifier must enforce the unified profile modal layout"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must keep the shared profile dialog contract" "cloud console profile modal verifier must enforce the shared account profile contract"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must not keep legacy profile layout aliases" "cloud console profile modal verifier must reject legacy profile layout aliases"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile updates must block duplicate submissions and surface pending state" "cloud console profile updates must block duplicate submissions"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile avatar upload must block duplicate submissions and surface pending state" "cloud console profile avatar upload must block duplicate submissions"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console profile modal must surface pending profile updates" "cloud console profile modal must surface pending updates"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console styles must not keep legacy profile layout aliases" "cloud console profile style verifier must reject legacy profile layout aliases"
    Require-NoMatch "sysManage/src/pages/CloudConsolePage.tsx" "profile-avatar-preview-row|profile-avatar-actions" "cloud console profile modal must not keep legacy profile layout aliases"
    Require-NoMatch "sysManage/src/index.css" "\.profile-avatar-preview-row\b|\.profile-avatar-actions\b" "cloud console styles must not keep legacy profile layout aliases"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "global admins and cloud application admins" "cloud console boundary verifier must accept global and cloud application administrators"
    Require-Contains "sysManage/src/types.ts" "export function cloudRoleLabel" "cloud console role labels must be centralized"
    Require-Contains "sysManage/src/pages/CloudConsolePage.tsx" 'subTitle="仅全局管理员或云盘管理员可以访问运营后台。"' "cloud console permission denied copy must match the runtime access contract"
    Require-Contains "sysManage/src/pages/CloudConsolePage.tsx" "cloudRoleLabel(currentUser)" "cloud console sidebar must use the centralized role label"
    Require-Contains "sysManage/src/features/drive/CloudUsersView.tsx" 'description="仅全局管理员或云盘管理员可以查看用户画像和调整存储额度。"' "cloud users permission copy must match the runtime access contract"
    Require-Contains "sysManage/src/features/drive/CloudUsersView.tsx" "cloudRoleLabel(user)" "cloud users role tags must use the centralized role label"
    Require-Contains "sysManage/src/features/drive/DriveOperationsView.tsx" 'description="仅全局管理员或云盘管理员可以查看全局文件运营明细。"' "cloud operations permission copy must match the runtime access contract"
    Require-Contains "sysManage/src/features/drive/DriveOperationsView.tsx" "cloudRoleLabel(user)" "cloud operations storage users role tags must use the centralized role label"
    Require-Contains "sysManage/src/features/drive/DriveAppPackageView.tsx" 'description="仅全局管理员或云盘管理员可以上传和替换安卓安装包。"' "cloud APK package permission copy must match the runtime access contract"
    Require-Contains "sysManage/scripts/verify-console-boundary.mjs" "cloud console should use the unified console gateway instead of hard-linking to the identity console" "cloud console boundary verifier must reject direct identity console links"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/app-package', '?release=current', '#upload']" "cloud console returnTo verifier must preserve APK package deep links"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/cloud/app-package', '', '']" "cloud console returnTo verifier must preserve mounted APK package routes"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "buildUnifiedLoginUrl(cloudConsoleReturnTo('/app-package'), 'login-required')" "cloud console returnTo verifier must cover APK package login redirects"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/api/admin/cloud-users', '', '']" "cloud console returnTo verifier must reject admin API paths"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/identity/', '', '']" "cloud console returnTo verifier must reject identity console paths"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/console/', '', '']" "cloud console returnTo verifier must reject the shared console gateway path"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "['/rag/', '', '']" "cloud console returnTo verifier must reject RAG paths"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "history cache restores" "cloud console must restore local session state after browser history cache restores"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "only expire local sessions on authentication failures" "cloud console must not clear sessions for transient API failures"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "server logout failures before local logout" "cloud console logout must keep local session cleanup independent of backend logout"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "clear the local session before notifying logout" "cloud console logout must clear local session state before broadcasting logout"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "cloud console logout must block duplicate submissions and surface pending state" "cloud console logout must block duplicate submissions"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "cloud console account menu must surface pending logout state" "cloud console account menu must surface pending logout state"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "cloud-safe cached user from identity login sessions" "cloud console must seed a cached user snapshot from identity login sessions"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "sanitize legacy browser session residue" "cloud console must sanitize legacy browser session residue"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "ignore stale-token 401 responses" "cloud console must ignore stale-token 401 responses"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "refresh requests must not broadcast global session expiry" "cloud console refresh requests must not broadcast global session expiry before snapshot checks"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "logout requests must not broadcast global session expiry" "cloud console logout requests must not broadcast global session expiry after local logout starts"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "confirm the current session before redirecting on auth-expired events" "cloud console must confirm the current session before redirecting on auth-expired events"
    Require-Contains "sysManage/scripts/verify-session-sync.mjs" "no stored session as logout/no-op rather than session expiry" "cloud console must treat post-logout auth-expired events as logout/no-op"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserController.java" '@RequestMapping("/api/admin/cloud-users")' "CloudStorageApi must expose the cloud-users admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudUserProfileController.java" '@RequestMapping("/api/admin/cloud-users")' "CloudStorageApi must expose the cloud user quota admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudOperationsController.java" '@RequestMapping("/api/admin/cloud-operations")' "CloudStorageApi must expose the cloud operations admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminAppPackageController.java" '@RequestMapping("/api/admin/app-package")' "CloudStorageApi must expose the APK admin backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AppPackageController.java" '@RequestMapping("/api/app-package")' "CloudStorageApi must expose the public APK package backend used by sysManage"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto/AdminCloudStorageUserUsageResponse.java" "Map<String, String> appRoles" "CloudStorageApi storage user operations response must expose application roles for sysManage role labels"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/config/WebMvcConfig.java" '.addPathPatterns("/api/admin/**");' "CloudStorageApi must protect admin endpoints through AdminPrincipalInterceptor"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" 'CLOUD_ADMIN_ROLE = "CLOUD_ADMIN"' "CloudStorageApi admin principal must continue to use the cloud app admin role"
    Require-Contains "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/principal/CurrentPrincipal.java" "role == UserRole.ADMIN || CLOUD_ADMIN_ROLE.equals(appRoles().get(CLOUD_APP_CODE))" "CloudStorageApi admin principal must accept global admins and cloud app admins"
    Require-Contains "deploy/scripts/update-cloud-production.sh" 'ALICIA_CLOUD_DEPLOY_SERVICES:-api frontend' "cloud production update must publish the CloudStorageApi/sysManage contract by default"
    Require-Match "README.md" 'appRoles.+api frontend' "cloud README must document the api/frontend publishing pair for appRoles-backed console labels"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "should_defer_main_site_public_boundary()" "main/cloud update script must support deferring main public gateway checks"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_VERIFY_SKIP_PUBLIC_BOUNDARY=true bash "$MAIN_SITE_UPDATE_SCRIPT" "$MAIN_SITE_PROJECT_DIR"' "main/cloud update script must defer main public checks while cloud gateway is pending"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "run_final_main_site_route_verify" "main/cloud update script must rerun main route verification after cloud gateway update"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_SKIP_FINAL_MAIN_SITE_VERIFY' "main/cloud update script must expose a final main route verification skip switch"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "Skipping final main site route verification; route verify script is missing because main site update was skipped" "main/cloud update script must allow cloud-only updates when the main site verifier is unavailable"
    Require-NoMatch "deploy/scripts/update-main-and-cloud-production.sh" '\[\[ "\$SKIP_MAIN_SITE_UPDATE" != "true" \]\] \|\| return 0' "main/cloud update script must still run final main route verification after cloud updates even when main site update was skipped"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'COLLECT_STATUS="${ALICIA_COLLECT_STATUS_AFTER_UPDATE:-false}"' "main/cloud update script must own post-update platform status snapshots"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'STATUS_SNAPSHOT_SCRIPT="$CLOUD_PROJECT_DIR/deploy/scripts/collect-production-status.sh"' "main/cloud update script must locate the platform status snapshot script"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" "run_platform_status_snapshot()" "main/cloud update script must expose a final platform status snapshot step"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_COLLECT_STATUS_AFTER_UPDATE=false bash "$CLOUD_UPDATE_SCRIPT" "$CLOUD_PROJECT_DIR" "$@"' "main/cloud update script must defer cloud status snapshots until final main route verification completes"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_MAIN_SITE_PROJECT_DIR="$MAIN_SITE_PROJECT_DIR" \' "main/cloud update script must pass the main site path to the platform status snapshot"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'ALICIA_CLOUD_PROJECT_DIR="$CLOUD_PROJECT_DIR" \' "main/cloud update script must pass the cloud path to the platform status snapshot"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'bash "$STATUS_SNAPSHOT_SCRIPT"' "main/cloud update script must run the platform status snapshot script"
    Require-Contains "deploy/scripts/update-main-and-cloud-production.sh" 'if [[ "$COLLECT_STATUS" == "true" && ! -f "$STATUS_SNAPSHOT_SCRIPT" ]]; then' "main/cloud update script must fail fast when the requested platform status snapshot script is missing"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" '$env:ALICIA_MAIN_SITE_PROJECT_DIR' "platform local verifier must allow overriding the main site repository path"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" '$env:ALICIA_CLOUD_PROJECT_DIR' "platform local verifier must allow overriding the cloud repository path"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'Join-Path (Split-Path -Parent $CloudProjectDir) "mainSite"' "platform local verifier must default to a sibling mainSite repository"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "Get-GitRevision" "platform local verifier must print repository commits"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'Write-Host "Main site commit:' "platform local verifier must print the main site commit"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'Write-Host "Cloud commit:' "platform local verifier must print the cloud commit"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'deploy\scripts\verify-frontend-split-local.ps1' "platform local verifier must run each repository frontend split verifier"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" '$verificationArgs["SkipBuild"] = $true' "platform local verifier must pass through the SkipBuild switch"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "Invoke-FrontendBuildDependencyPreflight" "platform local verifier must preflight frontend build dependencies before full builds"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'foreach ($binaryName in @("tsc", "vite"))' "platform local verifier must diagnose missing TypeScript and Vite build dependencies"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" 'npm ci --no-audit --no-fund' "platform local verifier must tell operators how to install missing frontend dependencies"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud web returnTo boundary"' "cloud Bash boundary check must run webApp returnTo verifier without TypeScript packages"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud web session sync boundary"' "cloud Bash boundary check must run webApp session sync boundary"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud web client boundary"' "cloud Bash boundary check must run webApp client boundary"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud web CloudStorageApi contract"' "cloud Bash boundary check must run webApp CloudStorageApi contracts"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud console returnTo boundary"' "cloud Bash boundary check must run sysManage returnTo verifier without TypeScript packages"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud console session sync boundary"' "cloud Bash boundary check must run sysManage session sync boundary"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud console boundary"' "cloud Bash boundary check must run sysManage console boundary"
    Require-Contains "deploy/scripts/check-frontend-console-boundaries.sh" 'run_node_script "cloud console CloudStorageApi contract"' "cloud Bash boundary check must run sysManage CloudStorageApi contracts"
    Require-NoMatch "deploy/scripts/check-frontend-console-boundaries.sh" "run_typescript_node_script" "cloud Bash boundary check must not skip returnTo verification when TypeScript packages are missing"
    Require-Contains "webApp/scripts/verify-unified-login-return-to.mjs" "ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT" "cloud web returnTo verifier must run without installed TypeScript packages"
    Require-Contains "sysManage/scripts/verify-unified-login-return-to.mjs" "ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT" "cloud console returnTo verifier must run without installed TypeScript packages"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "Invoke-SharedAccountProfileVerification" "platform local verifier must enforce shared account profile layout across all frontends"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "cloud console profile modal" "platform local verifier must include the cloud console profile modal in the shared contract"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "identity console profile modal" "platform local verifier must include the identity console profile modal in the shared contract"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "Invoke-MainSitePortalApiContractVerification" "platform local verifier must enforce main site portal API contracts"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.ps1" "Invoke-IdentityConsoleApiContractVerification" "platform local verifier must enforce identity console IdentityApi contracts"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'ALICIA_MAIN_SITE_PROJECT_DIR' "platform bash verifier must allow overriding the main site repository path"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'ALICIA_CLOUD_PROJECT_DIR' "platform bash verifier must allow overriding the cloud repository path"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'mainSite' "platform bash verifier must default to a sibling mainSite repository"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'check-main-site-frontend-boundaries.sh' "platform bash verifier must run the main site frontend boundary checks"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'check-frontend-console-boundaries.sh' "platform bash verifier must run the cloud frontend boundary checks"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'check-identity-route-boundary.sh' "platform bash verifier must run the identity route boundary checks"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'verify_shared_account_profile' "platform bash verifier must enforce shared account profile layout across all frontends"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'verify-main-site-portal-api-contracts.mjs' "platform bash verifier must enforce main site portal API contracts"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'verify-identity-console-api-contracts.mjs' "platform bash verifier must enforce identity console IdentityApi contracts"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'skip-build' "platform bash verifier must allow static/API-only checks"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'verify_frontend_build_dependencies' "platform bash verifier must preflight frontend build dependencies before full builds"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'for binary_name in tsc vite' "platform bash verifier must diagnose missing TypeScript and Vite build dependencies"
    Require-Contains "deploy/scripts/verify-platform-frontend-split-local.sh" 'npm ci --no-audit --no-fund' "platform bash verifier must tell operators how to install missing frontend dependencies"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "IdentityLoginResponse" "main site portal contract verifier must compare Identity login responses"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "RequestEmailRegistrationCodeRequest" "main site portal contract verifier must compare registration requests"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractJsonStringifyObjectFieldsForFunction" "main site portal contract verifier must read auth request bodies from frontend source"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "assertMainSiteApiStringifiesPayload" "main site portal contract verifier must ensure typed payloads are sent"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractInlineQueryParamKeysForFunction" "main site portal contract verifier must read inline query parameters from frontend source"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractRequestParamsForMethod" "main site portal contract verifier must compare query parameters"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractTemplatePathVariablesForFunction" "main site portal contract verifier must read frontend path variables"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractPathVariablesForMethod" "main site portal contract verifier must read backend path variables"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "revokeIdentitySession path variables" "main site portal contract verifier must compare session path variables"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractFormDataAppendKeysForFunction" "main site portal contract verifier must read avatar multipart fields from FormData"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "extractNamedRequestAnnotationsForMethod" "main site portal contract verifier must read backend avatar multipart fields"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "main site uploadIdentityAvatar multipart fields" "main site portal contract verifier must compare avatar upload multipart fields"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "mainSitePortalEndpointContracts" "main site portal contract verifier must compare API endpoint paths and methods"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "CloudProfileController.java" "main site portal contract verifier must bind avatar uploads to CloudStorageApi"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "assertMainSiteApiUsesAuthToken" "main site portal contract verifier must ensure protected API calls send the current auth token"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "assertControllerMethodReceivesAuthorization" "main site portal contract verifier must ensure protected backend methods receive Authorization headers"
    Require-Contains "deploy/scripts/verify-main-site-portal-api-contracts.mjs" "assertCloudProfileAvatarInterceptorContract" "main site portal contract verifier must ensure avatar uploads stay protected by CurrentPrincipalInterceptor"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "IdentityUserResponse" "identity console contract verifier must compare Identity user responses"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "IdentityAuditLogPageResponse" "identity console contract verifier must compare Identity audit log pages"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractJsonStringifyObjectFieldsForFunction" "identity console contract verifier must read auth request bodies from frontend source"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertUserSiteApiStringifiesPayload" "identity console contract verifier must ensure typed payloads are sent"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractUrlSearchParamKeysForFunction" "identity console contract verifier must read URLSearchParams keys from frontend source"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertUserSiteApiUsesQueryHelper" "identity console contract verifier must ensure audit APIs use the query helper"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractInlineQueryParamKeysForFunction" "identity console contract verifier must read inline query parameters from frontend source"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractRequestParamsForMethod" "identity console contract verifier must compare query parameters"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractTemplatePathVariablesForFunction" "identity console contract verifier must read frontend path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractPathVariablesForMethod" "identity console contract verifier must read backend path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "revokeIdentitySession path variables" "identity console contract verifier must compare session path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "resetIdentityUserPassword path variables" "identity console contract verifier must compare password reset path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "fetchIdentityApplicationRoles path variables" "identity console contract verifier must compare app role list path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "updateIdentityApplicationRole path variables" "identity console contract verifier must compare app role path variables"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractFormDataAppendKeysForFunction" "identity console contract verifier must read avatar multipart fields from FormData"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "extractNamedRequestAnnotationsForMethod" "identity console contract verifier must read backend avatar multipart fields"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "identity console uploadIdentityAvatar multipart fields" "identity console contract verifier must compare avatar upload multipart fields"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "identityEndpointContracts" "identity console contract verifier must compare IdentityApi endpoint paths and methods"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "identityConsoleCloudProfileEndpointContracts" "identity console contract verifier must bind avatar uploads to CloudStorageApi"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertControllerMethodMapping" "identity console contract verifier must bind frontend API calls to controller mappings"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertUserSiteApiUsesAuthToken" "identity console contract verifier must ensure frontend API calls send the current auth token"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertControllerMethodReceivesAuthorization" "identity console contract verifier must ensure protected IdentityApi controllers receive Authorization headers"
    Require-Contains "deploy/scripts/verify-identity-console-api-contracts.mjs" "assertCloudProfileAvatarInterceptorContract" "identity console contract verifier must ensure avatar uploads stay protected by CurrentPrincipalInterceptor"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'MAIN_SITE_PROJECT_DIR="${ALICIA_MAIN_SITE_PROJECT_DIR:-$HOME/mainSite}"' "production status snapshot must locate the main site repository"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'git_snapshot "main site repository" "$MAIN_SITE_PROJECT_DIR"' "production status snapshot must include main site Git state"
    Require-Contains "deploy/scripts/collect-production-status.sh" "git diff --summary" "production status snapshot must summarize tracked change modes"
    Require-Contains "deploy/scripts/collect-production-status.sh" "git diff --name-status" "production status snapshot must list tracked changed files"
    Require-Contains "deploy/scripts/collect-production-status.sh" "run_main_site_route_verify()" "production status snapshot must expose optional main site route verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" "run_main_site_boundary_check()" "production status snapshot must expose optional main site boundary verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'run_optional "main site route verification" run_main_site_route_verify' "production status snapshot must run main site route verification when full route checks are requested"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'run_optional "main site frontend boundary check" run_main_site_boundary_check' "production status snapshot must run main site frontend boundary checks when static boundary checks are requested"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK' "production status snapshot must expose optional platform frontend split verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" "run_platform_frontend_split_check()" "production status snapshot must expose platform frontend split verification"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'bash "$PLATFORM_FRONTEND_SPLIT_SCRIPT" --skip-build' "production status snapshot must run platform frontend split verification without rebuilding"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT="${ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT:-1}"' "production status platform split check must avoid requiring local TypeScript packages"
    Require-Contains "docs/production-verification-scripts.md" "ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK=true" "production verification docs must describe the platform frontend split snapshot switch"
    Require-Contains "docs/production-verification-scripts.md" "ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT" "production verification docs must describe TypeScript-free frontend split snapshots"
    Require-Contains "README.md" "ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK=true" "root README must describe the platform frontend split snapshot switch"
    Require-Contains "README.md" "ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT" "root README must describe TypeScript-free frontend split snapshots"
    Require-Contains "deploy/scripts/collect-production-status.sh" "curl_redirect_probe()" "production status snapshot must include canonical redirect probes"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "console gateway bare path" "$PUBLIC_BASE_URL/console" "/console/"' "production status snapshot must verify the shared console gateway redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloudPan bare path" "$PUBLIC_BASE_URL/cloudPan" "/cloudPan/"' "production status snapshot must verify the cloud web canonical redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_probe "cloudPan share route" "$PUBLIC_BASE_URL/cloudPan/share/cache-probe"' "production status snapshot must probe cloud share deep links"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_probe "cloudPan app download route" "$PUBLIC_BASE_URL/cloudPan/app-download"' "production status snapshot must probe the cloud app download route"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloud console bare path" "$PUBLIC_BASE_URL/console/cloud" "/console/cloud/"' "production status snapshot must verify the cloud console canonical redirect"
    Require-Contains "deploy/scripts/collect-production-status.sh" 'curl_redirect_probe "cloudPan legacy login" "$PUBLIC_BASE_URL/cloudPan/login" "/login?returnTo=/cloudPan/"' "production status snapshot must verify legacy cloud login handoff"
    Require-Contains "webApp/src/lib/mobileApp.ts" "DEFAULT_ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone'" "cloud web intent fallback package must match the official Android applicationId"
    Require-Contains "webApp/public/.well-known/assetlinks.json" '"package_name": "com.alicia.cloudstorage.phone"' "cloud asset links must authorize the official Android package"
    Require-NoMatch "webApp/public/.well-known/assetlinks.json" 'com\.alicia\.cloudstorage\.phone\.add' "cloud asset links must not authorize the old Android test package"
    Require-Contains "webApp/package.json" '"verify:bundle-size": "node scripts/verify-bundle-size.mjs"' "cloud web package must expose the bundle size verifier"
    Require-Contains "webApp/package.json" '"verify:built-shell": "node scripts/verify-built-shell.mjs"' "cloud web package must expose the built shell verifier"
    Require-Contains "webApp/package.json" "vite build && npm run verify:built-shell && npm run verify:bundle-size" "cloud web build must verify built shell and bundle size after vite build"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "UserProfileResponse" "cloud web contract verifier must compare the CloudStorageApi current profile response"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "StorageNodeSummaryResponse" "cloud web contract verifier must compare personal storage node responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "CreateMultipartUploadRequest" "cloud web contract verifier must compare multipart upload requests"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "ShareLinkDetailResponse" "cloud web contract verifier must compare share detail responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "extractRequestParamsForMethod" "cloud web contract verifier must compare storage/share query parameters"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "extractTemplatePathVariablesForFunction" "cloud web contract verifier must read frontend path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "extractPathVariablesForMethod" "cloud web contract verifier must read backend path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "uploadMultipartPart path variables" "cloud web contract verifier must compare multipart path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "renameStorageNode path variables" "cloud web contract verifier must compare storage node path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "fetchShareFileAccessUrl path variables" "cloud web contract verifier must compare share file path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "fetchPublicShareStatus path variables" "cloud web contract verifier must compare public share path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "revokeIdentitySession path variables" "cloud web contract verifier must compare identity session path variables"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertApiFunctionUsesBlobRequest" "cloud web contract verifier must ensure downloads use the blob helper"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertDownloadResponseBuilderContract" "cloud web contract verifier must verify file download response headers"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "downloadStorageFile binary response" "cloud web contract verifier must compare storage file download responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "downloadStorageArchive binary response" "cloud web contract verifier must compare storage archive download responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "downloadShareFile binary response" "cloud web contract verifier must compare share file download responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "downloadShareArchive binary response" "cloud web contract verifier must compare share archive download responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertApiFunctionSendsDisposition" "cloud web contract verifier must ensure signed URL calls send disposition"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "fetchStorageFileAccessUrl disposition contract" "cloud web contract verifier must compare storage access URL disposition"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "fetchShareFileAccessUrl disposition contract" "cloud web contract verifier must compare share access URL disposition"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "cloudWebEndpointContracts" "cloud web contract verifier must compare CloudStorageApi endpoint paths and methods"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertControllerMethodMapping" "cloud web contract verifier must bind frontend API calls to controller mappings"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertApiFunctionUsesAuthToken" "cloud web contract verifier must ensure protected frontend API calls send the current auth token"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertApiFunctionStringifiesPayload" "cloud web contract verifier must ensure typed payloads are sent"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "extractFormDataAppendKeysForFunction" "cloud web contract verifier must read multipart upload field names from FormData"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "extractNamedRequestAnnotationsForMethod" "cloud web contract verifier must read backend multipart request field names"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "uploadCurrentUserAvatar multipart fields" "cloud web contract verifier must compare avatar upload multipart fields"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "uploadCurrentUserHomeBackground multipart fields" "cloud web contract verifier must compare background upload multipart fields"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "uploadStorageFile multipart fields" "cloud web contract verifier must compare storage upload multipart fields"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertApiFunctionUsesShareAccessToken" "cloud web contract verifier must ensure share access tokens are sent"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertControllerMethodReceivesShareAccessToken" "cloud web contract verifier must ensure share controllers receive share access tokens"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertCloudStorageApiCurrentPrincipalInterceptorContract" "cloud web contract verifier must ensure user APIs stay protected by CurrentPrincipalInterceptor"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "cloudWebIdentityEndpointContracts" "cloud web contract verifier must compare IdentityApi endpoint paths and methods"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "IdentitySessionResponse" "cloud web contract verifier must compare identity session responses"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "UpdateIdentityProfileRequest" "cloud web contract verifier must bind profile updates to IdentityApi"
    Require-Contains "webApp/scripts/verify-api-contracts.mjs" "assertControllerMethodReceivesAuthorization" "cloud web contract verifier must ensure protected IdentityApi methods receive Authorization headers"
    Require-Contains "webApp/scripts/verify-built-shell.mjs" "Alicia 云盘" "cloud web built shell verifier must assert the cloud web title"
    Require-Contains "webApp/scripts/verify-built-shell.mjs" "/cloudPan/assets/" "cloud web built shell verifier must assert mounted asset prefix"
    Require-Contains "webApp/scripts/verify-bundle-size.mjs" "maxChunkBytes = 500 * 1024" "cloud web bundle size verifier must cap JS chunks at 500 KiB"
    Require-Contains "webApp/vite.config.ts" "manualChunks(moduleId)" "cloud web Vite config must keep explicit vendor chunking"
    Require-Contains "webApp/vite.config.ts" "getAntdModuleChunk(id)" "cloud web Vite config must keep Ant Design module chunking"
    Require-Contains "sysManage/package.json" '"verify:bundle-size": "node scripts/verify-bundle-size.mjs"' "cloud console package must expose the bundle size verifier"
    Require-Contains "sysManage/package.json" '"verify:built-shell": "node scripts/verify-built-shell.mjs"' "cloud console package must expose the built shell verifier"
    Require-Contains "sysManage/package.json" '"verify:api-contracts": "node scripts/verify-api-contracts.mjs"' "cloud console package must expose the CloudStorageApi contract verifier"
    Require-Contains "sysManage/package.json" "npm run verify:console-boundary && npm run verify:api-contracts && tsc -b" "cloud console build must verify CloudStorageApi contracts before TypeScript compile"
    Require-Contains "sysManage/package.json" "vite build && npm run verify:built-shell && npm run verify:bundle-size" "cloud console build must verify built shell and bundle size after vite build"
    Require-Contains "sysManage/scripts/verify-built-shell.mjs" "Alicia 云盘后台" "cloud console built shell verifier must assert the cloud console title"
    Require-Contains "sysManage/scripts/verify-built-shell.mjs" "/console/cloud/assets/" "cloud console built shell verifier must assert mounted asset prefix"
    Require-Contains "sysManage/scripts/verify-bundle-size.mjs" "maxChunkBytes = 500 * 1024" "cloud console bundle size verifier must cap JS chunks at 500 KiB"
    Require-Contains "sysManage/scripts/verify-bundle-size.mjs" "cloud console bundle size verified" "cloud console bundle size verifier must report the console target"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "AdminCloudOperationsOverviewResponse" "cloud console contract verifier must compare the operations overview response"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "AdminCloudStorageUserUsageResponse" "cloud console contract verifier must compare the storage users response"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractRequestParamsForMethod" "cloud console contract verifier must compare operations query parameters"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractUrlSearchParamKeysForFunction" "cloud console contract verifier must read URLSearchParams keys from frontend source"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertApiFunctionUsesQueryHelper" "cloud console contract verifier must ensure operations APIs use the query helper"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractTemplatePathVariablesForFunction" "cloud console contract verifier must read frontend path variables"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractPathVariablesForMethod" "cloud console contract verifier must read backend path variables"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "updateUserStorageQuota path variables" "cloud console contract verifier must compare quota path variables"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "UserProfileResponse" "cloud console contract verifier must compare CloudStorageApi user profile responses"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "AdminUpdateUserQuotaRequest" "cloud console contract verifier must compare quota update requests"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "cloudConsoleEndpointContracts" "cloud console contract verifier must compare CloudStorageApi endpoint paths and methods"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "cloudConsoleIdentityEndpointContracts" "cloud console contract verifier must compare allowed IdentityApi endpoint paths and methods"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "IdentityLoginResponse" "cloud console contract verifier must compare identity login responses"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "UpdateIdentityProfileRequest" "cloud console contract verifier must bind profile updates to IdentityApi"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertControllerMethodMapping" "cloud console contract verifier must bind frontend API calls to controller mappings"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertApiFunctionUsesAuthToken" "cloud console contract verifier must ensure frontend API calls send the current auth token"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertApiFunctionStringifiesPayload" "cloud console contract verifier must ensure typed payloads are sent"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractFormDataAppendKeysForFunction" "cloud console contract verifier must read multipart upload field names from FormData"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "extractNamedRequestAnnotationsForMethod" "cloud console contract verifier must read backend multipart request field names"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "uploadAdminAppPackage multipart fields" "cloud console contract verifier must compare APK upload multipart fields"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertControllerMethodReceivesAuthorization" "cloud console contract verifier must ensure proxying controller methods receive Authorization headers"
    Require-Contains "sysManage/scripts/verify-api-contracts.mjs" "assertCloudStorageApiAdminInterceptorContract" "cloud console contract verifier must ensure /api/admin/** stays protected by AdminPrincipalInterceptor"
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
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "did not expose appRoles" "cloud route verifier must assert storage users expose application roles"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "main site home entry"' "cloud route verifier must assert the gateway still serves the main site shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "main site login returnTo entry"' "cloud route verifier must assert public login returnTo serves the main site shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "cloudPan share route"' "cloud route verifier must assert cloud share deep links serve the cloud web shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'curl_ok "cloudPan app download route"' "cloud route verifier must assert cloud app download route remains mounted"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_shell "cloudPan app download route"' "cloud route verifier must assert cloud app download route serves the cloud web shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "main site index"' "cloud route verifier must assert main site index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "main site login index"' "cloud route verifier must assert main site login no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "identity console roles index"' "cloud route verifier must assert identity console child routes no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloudPan index"' "cloud route verifier must assert cloud web index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloudPan app download index"' "cloud route verifier must assert cloud app download route no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloud console index"' "cloud route verifier must assert cloud console index no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_no_store_index "cloud console operations index"' "cloud route verifier must assert cloud console child routes no-store cache"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_asset_cache "main site"' "cloud route verifier must assert main site immutable assets through the cloud gateway"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'expect_spa_asset_cache "identity console"' "cloud route verifier must assert identity console immutable assets"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" 'SKIP_CACHE_CHECKS="${ALICIA_VERIFY_SKIP_CACHE_CHECKS:-false}"' "cloud route verifier must support skipping cache checks"
}

Write-Host "[OK] cloud frontend split local verification complete"
