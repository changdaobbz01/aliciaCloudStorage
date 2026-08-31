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

function Read-ProjectText {
    param(
        [string]$ProjectDir,
        [string]$RelativePath
    )

    $path = Join-Path $ProjectDir $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Fail "Missing platform profile contract file: $path"
    }

    return Get-Content -LiteralPath $path -Raw
}

function Require-ProjectContains {
    param(
        [string]$Label,
        [string]$ProjectDir,
        [string]$RelativePath,
        [string]$Needle,
        [string]$Message
    )

    $source = Read-ProjectText $ProjectDir $RelativePath
    if (-not $source.Contains($Needle)) {
        Fail "${Label}: $Message"
    }
}

function Invoke-SharedAccountProfileVerification {
    Write-Host "[RUN] shared account profile contract"

    $profileSources = @(
        @{
            Label = "main site profile dialog"
            ProjectDir = $MainSiteProjectDir
            RelativePath = "webApp\src\App.tsx"
            TitleNeedle = '<DialogHeader kicker="Account" title='
            FieldNeedles = @("form.nickname", "form.phoneNumber", "form.avatarUrl")
        },
        @{
            Label = "identity console profile modal"
            ProjectDir = $MainSiteProjectDir
            RelativePath = "userSite\src\pages\IdentityConsolePage.tsx"
            TitleNeedle = 'title={<AliciaModalTitle eyebrow="Account">'
            FieldNeedles = @('name="nickname"', 'name="phoneNumber"', 'name="avatarUrl"')
        },
        @{
            Label = "cloud web profile modal"
            ProjectDir = $CloudProjectDir
            RelativePath = "webApp\src\features\drive\DriveProfileModals.tsx"
            TitleNeedle = 'title={<AliciaModalTitle eyebrow="Account">'
            FieldNeedles = @('name="nickname"', 'name="phoneNumber"', 'name="avatarUrl"')
        },
        @{
            Label = "cloud console profile modal"
            ProjectDir = $CloudProjectDir
            RelativePath = "sysManage\src\pages\CloudConsolePage.tsx"
            TitleNeedle = 'title={<AliciaModalTitle eyebrow="Account">'
            FieldNeedles = @('name="nickname"', 'name="phoneNumber"', 'name="avatarUrl"')
        }
    )
    $sharedSourceNeedles = @(
        @{ Needle = "account-profile-modal"; Message = "must use the shared profile modal chrome" },
        @{ Needle = "account-profile-form"; Message = "must use the shared profile form layout" },
        @{ Needle = "profile-avatar-row account-profile-hero"; Message = "must use the shared avatar function area" },
        @{ Needle = "account-profile-copy"; Message = "must use the shared profile copy area" },
        @{ Needle = "account-profile-actions"; Message = "must use the shared profile action row" },
        @{ Needle = "account-profile-fields"; Message = "must use the shared profile field stack" }
    )

    foreach ($contract in $profileSources) {
        Require-ProjectContains $contract["Label"] $contract["ProjectDir"] $contract["RelativePath"] $contract["TitleNeedle"] "must use the shared Account profile title chrome"
        foreach ($needle in $sharedSourceNeedles) {
            Require-ProjectContains $contract["Label"] $contract["ProjectDir"] $contract["RelativePath"] $needle["Needle"] $needle["Message"]
        }
        foreach ($fieldNeedle in $contract["FieldNeedles"]) {
            Require-ProjectContains $contract["Label"] $contract["ProjectDir"] $contract["RelativePath"] $fieldNeedle "must expose the shared profile fields in the profile editor"
        }
    }

    $profileStyles = @(
        @{ Label = "main site profile styles"; ProjectDir = $MainSiteProjectDir; RelativePath = "webApp\src\styles.css"; ActionNeedle = ".avatar-upload-action" },
        @{ Label = "identity console profile styles"; ProjectDir = $MainSiteProjectDir; RelativePath = "userSite\src\index.css"; ActionNeedle = ".account-profile-actions .ant-btn" },
        @{ Label = "cloud web profile styles"; ProjectDir = $CloudProjectDir; RelativePath = "webApp\src\index.css"; ActionNeedle = ".account-profile-actions .ant-btn" },
        @{ Label = "cloud console profile styles"; ProjectDir = $CloudProjectDir; RelativePath = "sysManage\src\index.css"; ActionNeedle = ".account-profile-actions .ant-btn" }
    )
    $sharedStyleNeedles = @(
        @{ Needle = ".account-profile-form"; Message = "must define the shared profile form class" },
        @{ Needle = ".account-profile-hero"; Message = "must define the shared avatar function area class" },
        @{ Needle = "grid-template-columns: 64px minmax(0, 1fr);"; Message = "must keep the shared avatar/function grid" },
        @{ Needle = "gap: 14px;"; Message = "must keep the shared avatar/function spacing" },
        @{ Needle = "margin-bottom: 18px;"; Message = "must keep the shared field separation" },
        @{ Needle = "padding: 12px;"; Message = "must keep the shared avatar/function padding" },
        @{ Needle = "border-radius: 8px;"; Message = "must keep the shared modal radius" },
        @{ Needle = "background: #f8fbff;"; Message = "must keep the shared avatar/function background" },
        @{ Needle = ".account-profile-copy"; Message = "must define the shared profile copy class" },
        @{ Needle = ".account-profile-actions"; Message = "must define the shared profile action row" },
        @{ Needle = ".account-profile-fields"; Message = "must define the shared profile field stack" }
    )

    foreach ($contract in $profileStyles) {
        foreach ($needle in $sharedStyleNeedles) {
            Require-ProjectContains $contract["Label"] $contract["ProjectDir"] $contract["RelativePath"] $needle["Needle"] $needle["Message"]
        }
        Require-ProjectContains $contract["Label"] $contract["ProjectDir"] $contract["RelativePath"] $contract["ActionNeedle"] "must style the upload action consistently for its UI framework"
    }

    Write-Host "[OK] shared account profile contract"
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
Invoke-SharedAccountProfileVerification

Write-Host "[OK] Alicia platform frontend split local verification complete"
