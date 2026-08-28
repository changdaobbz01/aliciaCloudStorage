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
    Require-Contains "webApp/src/lib/mobileApp.ts" "DEFAULT_ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone'" "cloud web intent fallback package must match the official Android applicationId"
    Require-Contains "webApp/public/.well-known/assetlinks.json" '"package_name": "com.alicia.cloudstorage.phone"' "cloud asset links must authorize the official Android package"
    Require-NoMatch "webApp/public/.well-known/assetlinks.json" 'com\.alicia\.cloudstorage\.phone\.add' "cloud asset links must not authorize the old Android test package"
    Require-Match "sysManage/src/pages/CloudConsolePage.tsx" 'document\.title = `\$\{activeMeta\.title\} - Alicia .+`;' "cloud console document title must follow the active view"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "Alicia 云盘后台" "cloud route verifier must assert cloud console serves the console shell"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "/console/cloud/assets/index-" "cloud route verifier must assert cloud console assets use the mounted prefix"
    Require-Contains "deploy/scripts/verify-identity-cloud-routes.sh" "/cloudPan/assets/index-" "cloud route verifier must assert cloud web assets use the mounted prefix"
}

Write-Host "[OK] cloud frontend split local verification complete"
