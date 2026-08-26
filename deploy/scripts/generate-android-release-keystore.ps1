param(
    [string]$RepoRoot = "",
    [string]$OutputRoot = "",
    [string]$Alias = "alicia-android-release",
    [string]$DistinguishedName = "CN=Alicia, OU=Alicia Tools, O=Alicia, L=Wuhan, ST=Hubei, C=CN",
    [int]$ValidityDays = 10000
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

function Resolve-RepoRoot {
    if (-not [string]::IsNullOrWhiteSpace($RepoRoot)) {
        return (Resolve-Path $RepoRoot).Path
    }

    $gitRoot = (& git rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($gitRoot)) {
        return $gitRoot.Trim()
    }

    return (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

function Find-Keytool {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    Fail "Could not find keytool. Install a JDK or set JAVA_HOME."
}

function New-Secret {
    $bytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

$resolvedRoot = Resolve-RepoRoot
Set-Location $resolvedRoot

if ($ValidityDays -lt 3650) {
    Fail "ValidityDays must be at least 3650 for a long-lived Android release key."
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $resolvedRoot "deploy\generated\android-signing"
}

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$targetDir = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$keytool = Find-Keytool
$keystorePath = Join-Path $targetDir "alicia-android-release-$timestamp.p12"
$envPath = Join-Path $targetDir "android-release-signing.env.ps1"
$readmePath = Join-Path $targetDir "README.txt"
$password = New-Secret

Write-Host "Generating Alicia Android release keystore..."
Write-Host "output dir: $targetDir"
Write-Host "alias:      $Alias"

$keytoolArgs = @(
    "-genkeypair",
    "-v",
    "-storetype", "PKCS12",
    "-keystore", $keystorePath,
    "-alias", $Alias,
    "-keyalg", "RSA",
    "-keysize", "4096",
    "-validity", $ValidityDays.ToString(),
    "-dname", $DistinguishedName,
    "-storepass", $password,
    "-keypass", $password
)

& $keytool @keytoolArgs
if ($LASTEXITCODE -ne 0) {
    Fail "keytool failed to generate Android release keystore."
}

$escapedKeystorePath = $keystorePath.Replace("'", "''")
$escapedAlias = $Alias.Replace("'", "''")
$escapedPassword = $password.Replace("'", "''")

$envContent = @"
# Dot-source this file before preparing a signed Android release package:
# . "$envPath"
`$env:ALICIA_ANDROID_KEYSTORE_PATH = '$escapedKeystorePath'
`$env:ALICIA_ANDROID_KEY_ALIAS = '$escapedAlias'
`$env:ALICIA_ANDROID_KEYSTORE_PASSWORD = '$escapedPassword'
`$env:ALICIA_ANDROID_KEY_PASSWORD = '$escapedPassword'
"@

Set-Content -LiteralPath $envPath -Value $envContent -Encoding UTF8

$readmeContent = @"
Alicia Android release signing material

Keep every file in this directory private and backed up. Losing this keystore means future APKs cannot upgrade the same Android applicationId.

applicationId: com.alicia.cloudstorage.phone
alias: $Alias
keystore: $keystorePath
env snippet: $envPath

Use:
. "$envPath"
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/prepare-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
"@

Set-Content -LiteralPath $readmePath -Value $readmeContent -Encoding UTF8

Ok "Android release keystore generated"
Write-Host ""
Write-Host "Generated Android signing files:"
Write-Host "keystore:    $keystorePath"
Write-Host "env snippet: $envPath"
Write-Host ""
Write-Host "Sensitive passwords are written only to the env snippet and are not printed here."
