param(
    [string]$RepoRoot = "",
    [ValidateSet("phoneApp", "phoneAppAdd")]
    [string]$App = "phoneAppAdd",
    [string]$PackageDir = "",
    [string]$OutputDir = "deploy/android-app-package",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

function Fail {
    param([string]$Message)
    Write-Error "[FAIL] $Message"
    exit 1
}

function Ok {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Show-Usage {
    Write-Host @"
Alicia Android Git artifact staging

Copies a prepared signed Android release package into deploy/android-app-package/
so the server production update script can publish it after git pull.

Typical use after prepare-android-release-package.ps1:

  powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/stage-android-git-package.ps1

Use a specific prepared package:

  powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/stage-android-git-package.ps1 -PackageDir deploy/generated/android-release-packages/phoneAppAdd/<dir>
"@
}

function Resolve-RepoRoot {
    if (-not [string]::IsNullOrWhiteSpace($RepoRoot)) {
        return (Resolve-Path -LiteralPath $RepoRoot).Path
    }

    $gitRoot = (& git rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($gitRoot)) {
        return $gitRoot.Trim()
    }

    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
}

function Assert-FileExists {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "Missing required file: $Path"
    }
}

function Read-PackageManifest {
    param([string]$ManifestPath)

    try {
        return Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    } catch {
        Fail "Could not read release manifest: $ManifestPath"
    }
}

function Find-ReleasePackageDir {
    param([string]$ResolvedRoot)

    if (-not [string]::IsNullOrWhiteSpace($PackageDir)) {
        return (Resolve-Path -LiteralPath $PackageDir).Path
    }

    $packageRoot = Join-Path $ResolvedRoot "deploy\generated\android-release-packages"
    if (-not (Test-Path -LiteralPath $packageRoot -PathType Container)) {
        Fail "No Android release package output directory exists: $packageRoot"
    }

    $manifestFiles = @(Get-ChildItem -LiteralPath $packageRoot -Recurse -File -Filter "manifest.json" |
        Sort-Object LastWriteTimeUtc -Descending)
    foreach ($manifestFile in $manifestFiles) {
        $manifest = Read-PackageManifest -ManifestPath $manifestFile.FullName
        if ($manifest.app -eq $App -and $manifest.buildType -eq "release") {
            return $manifestFile.DirectoryName
        }
    }

    Fail "No prepared release package manifest found for $App under $packageRoot."
}

if ($Help) {
    Show-Usage
    exit 0
}

$resolvedRoot = Resolve-RepoRoot
Set-Location $resolvedRoot

$resolvedPackageDir = Find-ReleasePackageDir -ResolvedRoot $resolvedRoot
$manifestPath = Join-Path $resolvedPackageDir "manifest.json"
Assert-FileExists $manifestPath

$manifest = Read-PackageManifest -ManifestPath $manifestPath
if ($manifest.app -ne $App) {
    Fail "Package app mismatch. Expected $App, got $($manifest.app)."
}
if ($manifest.buildType -ne "release") {
    Fail "Package build type must be release, got $($manifest.buildType)."
}
if (-not [System.Convert]::ToBoolean($manifest.signed)) {
    Fail "Package is not marked as signed and must not be staged for Git release."
}
if ([string]::IsNullOrWhiteSpace([string]$manifest.versionName)) {
    Fail "Release manifest is missing versionName."
}
if ([string]::IsNullOrWhiteSpace([string]$manifest.apkFileName)) {
    Fail "Release manifest is missing apkFileName."
}

$apkPath = Join-Path $resolvedPackageDir ([string]$manifest.apkFileName)
$notesFileName = if ([string]::IsNullOrWhiteSpace([string]$manifest.releaseNotesFile)) {
    "release-notes.txt"
} else {
    [string]$manifest.releaseNotesFile
}
$notesPath = Join-Path $resolvedPackageDir $notesFileName
Assert-FileExists $apkPath
Assert-FileExists $notesPath

$releaseNotes = (Get-Content -LiteralPath $notesPath -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($releaseNotes) -or $releaseNotes -match '^TODO:') {
    Fail "Release notes are empty or still TODO. Edit $notesPath before staging."
}

if (-not [string]::IsNullOrWhiteSpace([string]$manifest.sha256)) {
    $expectedHash = ([string]$manifest.sha256).Trim().ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        Fail "APK SHA-256 mismatch. Expected $expectedHash, got $actualHash."
    }
    Ok "APK SHA-256 matches manifest"
}

$resolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir
} else {
    Join-Path $resolvedRoot $OutputDir
}

New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

$targetApkPath = Join-Path $resolvedOutputDir "current.apk"
$targetVersionPath = Join-Path $resolvedOutputDir "version-name.txt"
$targetNotesPath = Join-Path $resolvedOutputDir "release-notes.txt"
$targetShaPath = Join-Path $resolvedOutputDir "current.apk.sha256"

Copy-Item -LiteralPath $apkPath -Destination $targetApkPath -Force
Set-Content -LiteralPath $targetVersionPath -Value ([string]$manifest.versionName) -Encoding UTF8
Set-Content -LiteralPath $targetNotesPath -Value $releaseNotes -Encoding UTF8

$sha256 = (Get-FileHash -LiteralPath $targetApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $targetShaPath -Value "$sha256  current.apk" -Encoding ASCII

Ok "Android Git release artifact staged"
Write-Host ""
Write-Host "Staged files:"
Write-Host "  $targetApkPath"
Write-Host "  $targetVersionPath"
Write-Host "  $targetNotesPath"
Write-Host "  $targetShaPath"
Write-Host ""
Write-Host "Review the APK size before committing, then stage these files explicitly."
