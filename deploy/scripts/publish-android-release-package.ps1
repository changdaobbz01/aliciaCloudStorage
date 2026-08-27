param(
    [string]$RepoRoot = "",
    [ValidateSet("phoneApp", "phoneAppAdd")]
    [string]$App = "phoneAppAdd",
    [string]$ApiBaseUrl = "https://windwindwind-alicia.cn",
    [string]$IdentityBaseUrl = "",
    [string]$OutputRoot = "",
    [string]$PackageDir = "",
    [string]$ReleaseNotes = $env:ALICIA_ANDROID_RELEASE_NOTES,
    [string]$AdminToken = $env:ALICIA_ADMIN_TOKEN,
    [string]$IdentityAccount = "",
    [string]$IdentityPassword = "",
    [string]$SigningKeystore = $env:ALICIA_ANDROID_KEYSTORE_PATH,
    [string]$SigningKeyAlias = $env:ALICIA_ANDROID_KEY_ALIAS,
    [string]$SigningKeystorePassword = $env:ALICIA_ANDROID_KEYSTORE_PASSWORD,
    [string]$SigningKeyPassword = $env:ALICIA_ANDROID_KEY_PASSWORD,
    [switch]$SkipPrepare,
    [switch]$SkipReadiness,
    [switch]$SkipAssemble,
    [switch]$SkipDownloadVerify,
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

function Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Show-Usage {
    Write-Host @"
Alicia Android production publisher

Builds the official Android APK package, uploads it to CloudStorageApi, and
verifies the public version/download endpoints.

Typical production use from the repository root:

  . deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ReleaseNotes "Release notes"

Useful variants:

  # Upload an already prepared package directory.
  powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -SkipPrepare -PackageDir deploy/generated/android-release-packages/phoneAppAdd/<dir>

  # Use a different gateway or direct service pair.
  powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ApiBaseUrl https://windwindwind-alicia.cn -IdentityBaseUrl https://windwindwind-alicia.cn -ReleaseNotes "Release notes"

Auth:
  - Pass an admin token with ALICIA_ADMIN_TOKEN or -AdminToken, or
  - let the script prompt for Identity admin account/password.
  - Non-interactive login can use ALICIA_VERIFY_ACCOUNT/ALICIA_VERIFY_PASSWORD
    or ALICIA_IDENTITY_ACCOUNT/ALICIA_IDENTITY_PASSWORD.
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

function Assert-DirectoryExists {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        Fail "Missing required directory: $Path"
    }
}

function Get-FirstNonBlank {
    param([string[]]$Values)

    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
    }

    return ""
}

function Read-SecretAsPlainText {
    param([string]$Prompt)

    $secure = Read-Host $Prompt -AsSecureString
    if ($secure.Length -eq 0) {
        return ""
    }

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Get-PowerShellExecutable {
    try {
        $currentProcessPath = (Get-Process -Id $PID).Path
        if (-not [string]::IsNullOrWhiteSpace($currentProcessPath) -and
            (Test-Path -LiteralPath $currentProcessPath -PathType Leaf)) {
            return $currentProcessPath
        }
    } catch {
        # Fall through to command lookup.
    }

    foreach ($name in @("pwsh", "powershell")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command -ne $null) {
            return $command.Source
        }
    }

    Fail "Could not find a PowerShell executable for child release scripts."
}

function Get-ChildPowerShellArgs {
    param([string]$ScriptPath)

    $baseArgs = @("-NoProfile")
    if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
        $baseArgs += @("-ExecutionPolicy", "Bypass")
    }
    $baseArgs += @("-File", $ScriptPath)
    return $baseArgs
}

function Invoke-ChildPowerShellScript {
    param(
        [string]$PowerShellExecutable,
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    Assert-FileExists $ScriptPath
    $childArgs = Get-ChildPowerShellArgs -ScriptPath $ScriptPath
    $childArgs += $Arguments
    & $PowerShellExecutable @childArgs
    if ($LASTEXITCODE -ne 0) {
        Fail $FailureMessage
    }
}

function Invoke-PrepareAndroidPackage {
    param(
        [string]$PowerShellExecutable,
        [string]$ResolvedRoot,
        [string]$ResolvedOutputRoot,
        [string]$BaseUrl
    )

    $prepareScript = Join-Path $ResolvedRoot "deploy\scripts\prepare-android-release-package.ps1"
    $arguments = @(
        "-RepoRoot", $ResolvedRoot,
        "-App", $App,
        "-BuildType", "release",
        "-ApiBaseUrl", $BaseUrl
    )

    if (-not [string]::IsNullOrWhiteSpace($ResolvedOutputRoot)) {
        $arguments += @("-OutputRoot", $ResolvedOutputRoot)
    }
    if (-not [string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $arguments += @("-ReleaseNotes", $ReleaseNotes.Trim())
    }
    if ($SkipReadiness) {
        $arguments += "-SkipReadiness"
    }
    if ($SkipAssemble) {
        $arguments += "-SkipAssemble"
    }

    $previousKeystorePath = $env:ALICIA_ANDROID_KEYSTORE_PATH
    $previousKeyAlias = $env:ALICIA_ANDROID_KEY_ALIAS
    $previousKeystorePassword = $env:ALICIA_ANDROID_KEYSTORE_PASSWORD
    $previousKeyPassword = $env:ALICIA_ANDROID_KEY_PASSWORD

    try {
        if (-not [string]::IsNullOrWhiteSpace($SigningKeystore)) {
            $env:ALICIA_ANDROID_KEYSTORE_PATH = $SigningKeystore
        }
        if (-not [string]::IsNullOrWhiteSpace($SigningKeyAlias)) {
            $env:ALICIA_ANDROID_KEY_ALIAS = $SigningKeyAlias
        }
        if (-not [string]::IsNullOrWhiteSpace($SigningKeystorePassword)) {
            $env:ALICIA_ANDROID_KEYSTORE_PASSWORD = $SigningKeystorePassword
        }
        if (-not [string]::IsNullOrWhiteSpace($SigningKeyPassword)) {
            $env:ALICIA_ANDROID_KEY_PASSWORD = $SigningKeyPassword
        }

        Invoke-ChildPowerShellScript `
            -PowerShellExecutable $PowerShellExecutable `
            -ScriptPath $prepareScript `
            -Arguments $arguments `
            -FailureMessage "Android release package preparation failed."
    } finally {
        if ($previousKeystorePath -eq $null) {
            Remove-Item Env:\ALICIA_ANDROID_KEYSTORE_PATH -ErrorAction SilentlyContinue
        } else {
            $env:ALICIA_ANDROID_KEYSTORE_PATH = $previousKeystorePath
        }
        if ($previousKeyAlias -eq $null) {
            Remove-Item Env:\ALICIA_ANDROID_KEY_ALIAS -ErrorAction SilentlyContinue
        } else {
            $env:ALICIA_ANDROID_KEY_ALIAS = $previousKeyAlias
        }
        if ($previousKeystorePassword -eq $null) {
            Remove-Item Env:\ALICIA_ANDROID_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
        } else {
            $env:ALICIA_ANDROID_KEYSTORE_PASSWORD = $previousKeystorePassword
        }
        if ($previousKeyPassword -eq $null) {
            Remove-Item Env:\ALICIA_ANDROID_KEY_PASSWORD -ErrorAction SilentlyContinue
        } else {
            $env:ALICIA_ANDROID_KEY_PASSWORD = $previousKeyPassword
        }
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

function Get-AndroidPackage {
    param(
        [string]$ResolvedRoot,
        [string]$ResolvedOutputRoot
    )

    $packagePath = ""
    if (-not [string]::IsNullOrWhiteSpace($PackageDir)) {
        $packagePath = (Resolve-Path -LiteralPath $PackageDir).Path
    } else {
        $packageRoot = if ([string]::IsNullOrWhiteSpace($ResolvedOutputRoot)) {
            Join-Path $ResolvedRoot "deploy\generated\android-release-packages"
        } else {
            $ResolvedOutputRoot
        }

        Assert-DirectoryExists $packageRoot
        $manifestFiles = @(Get-ChildItem -LiteralPath $packageRoot -Recurse -File -Filter "manifest.json" |
            Sort-Object LastWriteTimeUtc -Descending)

        foreach ($manifestFile in $manifestFiles) {
            $candidate = Read-PackageManifest -ManifestPath $manifestFile.FullName
            if ($candidate.app -eq $App -and $candidate.buildType -eq "release") {
                $packagePath = $manifestFile.DirectoryName
                break
            }
        }

        if ([string]::IsNullOrWhiteSpace($packagePath)) {
            Fail "No prepared release package manifest found for $App under $packageRoot."
        }
    }

    $manifestPath = Join-Path $packagePath "manifest.json"
    Assert-FileExists $manifestPath
    $manifest = Read-PackageManifest -ManifestPath $manifestPath

    if ($manifest.app -ne $App) {
        Fail "Package app mismatch. Expected $App, got $($manifest.app)."
    }
    if ($manifest.buildType -ne "release") {
        Fail "Package build type must be release, got $($manifest.buildType)."
    }
    if (-not [System.Convert]::ToBoolean($manifest.signed)) {
        Fail "Package is not marked as signed and must not be uploaded."
    }

    $apkFileName = [string]$manifest.apkFileName
    if ([string]::IsNullOrWhiteSpace($apkFileName)) {
        Fail "Release manifest is missing apkFileName."
    }

    $apkPath = Join-Path $packagePath $apkFileName
    $helperName = if ([string]::IsNullOrWhiteSpace([string]$manifest.uploadHelper)) {
        "upload-current-package.ps1"
    } else {
        [string]$manifest.uploadHelper
    }
    $helperPath = Join-Path $packagePath $helperName
    $notesName = if ([string]::IsNullOrWhiteSpace([string]$manifest.releaseNotesFile)) {
        "release-notes.txt"
    } else {
        [string]$manifest.releaseNotesFile
    }
    $notesPath = Join-Path $packagePath $notesName

    Assert-FileExists $apkPath
    Assert-FileExists $helperPath
    Assert-FileExists $notesPath

    if (-not [string]::IsNullOrWhiteSpace($manifest.sha256)) {
        $expectedHash = ([string]$manifest.sha256).Trim().ToLowerInvariant()
        $actualHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            Fail "APK SHA-256 mismatch. Expected $expectedHash, got $actualHash."
        }
        Ok "APK SHA-256 matches manifest"
    }

    if (-not [string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        Set-Content -LiteralPath $notesPath -Value $ReleaseNotes.Trim() -Encoding UTF8
    }

    $notes = (Get-Content -LiteralPath $notesPath -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($notes) -or $notes -match '^TODO:') {
        Fail "Release notes are empty or still TODO. Pass -ReleaseNotes or edit $notesPath."
    }

    return [pscustomobject]@{
        Directory = $packagePath
        Manifest = $manifest
        ApkPath = $apkPath
        HelperPath = $helperPath
        ReleaseNotesPath = $notesPath
    }
}

function Invoke-IdentityLogin {
    param([string]$BaseUrl)

    if (-not [string]::IsNullOrWhiteSpace($AdminToken)) {
        return [pscustomobject]@{
            Token = $AdminToken.Trim()
            RefreshToken = $null
            ShouldLogout = $false
        }
    }

    $account = Get-FirstNonBlank -Values @(
        $IdentityAccount,
        $env:ALICIA_VERIFY_ACCOUNT,
        $env:ALICIA_IDENTITY_ACCOUNT
    )
    $password = Get-FirstNonBlank -Values @(
        $IdentityPassword,
        $env:ALICIA_VERIFY_PASSWORD,
        $env:ALICIA_IDENTITY_PASSWORD
    )

    if ([string]::IsNullOrWhiteSpace($account)) {
        $account = Read-Host "Identity admin account/email/phone"
    }
    if ([string]::IsNullOrWhiteSpace($password)) {
        $password = Read-SecretAsPlainText "Identity admin password"
    }
    if ([string]::IsNullOrWhiteSpace($account) -or [string]::IsNullOrWhiteSpace($password)) {
        Fail "Identity admin account and password are required when ALICIA_ADMIN_TOKEN is not set."
    }

    $loginUri = "$BaseUrl/api/identity/auth/login"
    $payload = @{ identifier = $account; password = $password } | ConvertTo-Json -Compress

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri $loginUri `
            -ContentType "application/json; charset=utf-8" `
            -Body $payload
    } catch {
        $responseBody = ""
        if ($_.Exception.Response -ne $null) {
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {
                $responseBody = ""
            }
        }
        if (-not [string]::IsNullOrWhiteSpace($responseBody)) {
            Write-Host $responseBody
        }
        Fail "Identity admin login failed."
    }

    if ($response -eq $null -or [string]::IsNullOrWhiteSpace([string]$response.token)) {
        Fail "Identity admin login did not return an access token."
    }

    Ok "Identity admin login succeeded"
    return [pscustomobject]@{
        Token = ([string]$response.token).Trim()
        RefreshToken = if ($response.refreshToken -eq $null) { $null } else { [string]$response.refreshToken }
        ShouldLogout = $true
    }
}

function Invoke-IdentityLogout {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$RefreshToken
    )

    if ([string]::IsNullOrWhiteSpace($Token)) {
        return
    }

    $logoutUri = "$BaseUrl/api/identity/auth/logout"
    $payload = @{ refreshToken = $RefreshToken; allDevices = $false } | ConvertTo-Json -Compress

    try {
        Invoke-RestMethod `
            -Method Post `
            -Uri $logoutUri `
            -Headers @{ Authorization = "Bearer $Token" } `
            -ContentType "application/json; charset=utf-8" `
            -Body $payload | Out-Null
        Ok "Temporary Identity admin session logged out"
    } catch {
        Warn "Temporary Identity admin logout failed; the uploaded package is not affected."
    }
}

if ($Help) {
    Show-Usage
    exit 0
}

$resolvedRoot = Resolve-RepoRoot
Set-Location $resolvedRoot

$baseUrl = $ApiBaseUrl.TrimEnd("/")
$identityBase = if ([string]::IsNullOrWhiteSpace($IdentityBaseUrl)) {
    $baseUrl
} else {
    $IdentityBaseUrl.TrimEnd("/")
}
$resolvedOutputRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Join-Path $resolvedRoot "deploy\generated\android-release-packages"
} else {
    if (Test-Path -LiteralPath $OutputRoot) {
        (Resolve-Path -LiteralPath $OutputRoot).Path
    } else {
        $OutputRoot
    }
}

if (-not $SkipPrepare -and [string]::IsNullOrWhiteSpace($ReleaseNotes)) {
    $ReleaseNotes = Read-Host "Android release notes"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        Fail "Release notes are required before publishing a new Android package."
    }
}

Write-Host "Publishing Alicia Android package..."
Write-Host "Repo root:      $resolvedRoot"
Write-Host "Android app:    $App"
Write-Host "Cloud API:      $baseUrl"
Write-Host "Identity API:   $identityBase"

$powerShell = Get-PowerShellExecutable

if (-not $SkipPrepare) {
    Invoke-PrepareAndroidPackage `
        -PowerShellExecutable $powerShell `
        -ResolvedRoot $resolvedRoot `
        -ResolvedOutputRoot $resolvedOutputRoot `
        -BaseUrl $baseUrl
    Ok "Android release package prepared"
} else {
    Write-Host "Package preparation skipped; using an existing release package." -ForegroundColor Yellow
}

$package = Get-AndroidPackage -ResolvedRoot $resolvedRoot -ResolvedOutputRoot $resolvedOutputRoot
Write-Host "Package dir:    $($package.Directory)"
Write-Host "APK file:       $($package.Manifest.apkFileName)"
Write-Host "Version:        $($package.Manifest.versionName) ($($package.Manifest.versionCode))"

$session = Invoke-IdentityLogin -BaseUrl $identityBase
$previousAdminToken = $env:ALICIA_ADMIN_TOKEN

try {
    $env:ALICIA_ADMIN_TOKEN = $session.Token
    $uploadArgs = @("-ApiBaseUrl", $baseUrl)
    if (-not $SkipDownloadVerify) {
        $uploadArgs += "-VerifyDownload"
    }

    Invoke-ChildPowerShellScript `
        -PowerShellExecutable $powerShell `
        -ScriptPath $package.HelperPath `
        -Arguments $uploadArgs `
        -FailureMessage "Android package upload failed."

    Ok "Android package published to server"
} finally {
    if ($previousAdminToken -eq $null) {
        Remove-Item Env:\ALICIA_ADMIN_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:ALICIA_ADMIN_TOKEN = $previousAdminToken
    }

    if ($session -ne $null -and $session.ShouldLogout) {
        Invoke-IdentityLogout -BaseUrl $identityBase -Token $session.Token -RefreshToken $session.RefreshToken
    }
}

Write-Host ""
Write-Host "Alicia Android production update completed."
Write-Host "Public version endpoint:  $baseUrl/api/app-package/version"
Write-Host "Public download endpoint: $baseUrl/api/app-package/download/current"
