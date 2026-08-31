param(
    [string]$MainSiteProjectDir,
    [string]$CloudProjectDir,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

function Fail {
    param([string]$Message)
    throw $Message
}

function Resolve-Directory {
    param(
        [string]$Label,
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        Fail "$Label path is empty"
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolved.Path -PathType Container)) {
        Fail "$Label path is not a directory: $Path"
    }

    return $resolved.Path
}

function Invoke-FrontendSplitVerification {
    param(
        [string]$Label,
        [string]$ProjectDir
    )

    $scriptPath = Join-Path $ProjectDir "deploy\scripts\verify-frontend-split-local.ps1"
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        Fail "Missing $Label frontend split verifier: $scriptPath"
    }

    $verificationArgs = @{}
    if ($SkipBuild) {
        $verificationArgs["SkipBuild"] = $true
    }

    Write-Host "[RUN] $Label frontend split verification"
    Push-Location $ProjectDir
    try {
        & $scriptPath @verificationArgs
    } finally {
        Pop-Location
    }
    Write-Host "[OK] $Label frontend split verification"
}

$defaultCloudProjectDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path

if ([string]::IsNullOrWhiteSpace($CloudProjectDir)) {
    if (-not [string]::IsNullOrWhiteSpace($env:ALICIA_CLOUD_PROJECT_DIR)) {
        $CloudProjectDir = $env:ALICIA_CLOUD_PROJECT_DIR
    } else {
        $CloudProjectDir = $defaultCloudProjectDir
    }
}

$CloudProjectDir = Resolve-Directory "Cloud project" $CloudProjectDir

if ([string]::IsNullOrWhiteSpace($MainSiteProjectDir)) {
    if (-not [string]::IsNullOrWhiteSpace($env:ALICIA_MAIN_SITE_PROJECT_DIR)) {
        $MainSiteProjectDir = $env:ALICIA_MAIN_SITE_PROJECT_DIR
    } else {
        $MainSiteProjectDir = Join-Path (Split-Path -Parent $CloudProjectDir) "mainSite"
    }
}

$MainSiteProjectDir = Resolve-Directory "Main site project" $MainSiteProjectDir

Write-Host "[RUN] Alicia platform frontend split local verification"
Write-Host "Main site project: $MainSiteProjectDir"
Write-Host "Cloud project: $CloudProjectDir"

Invoke-FrontendSplitVerification "main site" $MainSiteProjectDir
Invoke-FrontendSplitVerification "cloud" $CloudProjectDir

Write-Host "[OK] Alicia platform frontend split local verification complete"
