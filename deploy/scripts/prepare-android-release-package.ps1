param(
    [string]$RepoRoot = "",
    [ValidateSet("phoneApp", "phoneAppAdd")]
    [string]$App = "phoneAppAdd",
    [ValidateSet("debug", "release")]
    [string]$BuildType = "release",
    [string]$OutputRoot = "",
    [string]$ApiBaseUrl = "https://windwindwind-alicia.cn",
    [string]$ReleaseNotes = "",
    [string]$SigningKeystore = $env:ALICIA_ANDROID_KEYSTORE_PATH,
    [string]$SigningKeyAlias = $env:ALICIA_ANDROID_KEY_ALIAS,
    [string]$SigningKeystorePassword = $env:ALICIA_ANDROID_KEYSTORE_PASSWORD,
    [string]$SigningKeyPassword = $env:ALICIA_ANDROID_KEY_PASSWORD,
    [switch]$SkipReadiness,
    [switch]$SkipAssemble,
    [switch]$AllowUnsignedReleaseCandidate
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

function Assert-FileExists {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "Missing required file: $Path"
    }
}

function Get-GradleStringValue {
    param(
        [string]$Content,
        [string]$Name
    )

    $match = [regex]::Match($Content, "(?m)^\s*$([regex]::Escape($Name))\s*=\s*`"([^`"]+)`"")
    if (-not $match.Success) {
        Fail "Could not read $Name from app/build.gradle.kts"
    }
    return $match.Groups[1].Value
}

function Get-GradleIntValue {
    param(
        [string]$Content,
        [string]$Name
    )

    $match = [regex]::Match($Content, "(?m)^\s*$([regex]::Escape($Name))\s*=\s*(\d+)")
    if (-not $match.Success) {
        Fail "Could not read $Name from app/build.gradle.kts"
    }
    return [int]$match.Groups[1].Value
}

function ConvertTo-SafeFilePart {
    param([string]$Value)

    $normalized = if ([string]::IsNullOrWhiteSpace($Value)) { "unknown" } else { $Value.Trim() }
    return ($normalized -replace '[^A-Za-z0-9._-]+', '-').Trim("-")
}

function Invoke-GradleTask {
    param(
        [string]$AppRoot,
        [string]$TaskName
    )

    $gradlewBat = Join-Path $AppRoot "gradlew.bat"
    $gradlew = Join-Path $AppRoot "gradlew"
    $command = if (Test-Path -LiteralPath $gradlewBat) { $gradlewBat } else { $gradlew }
    Assert-FileExists $command

    Push-Location $AppRoot
    try {
        & $command $TaskName
        if ($LASTEXITCODE -ne 0) {
            Fail "Gradle task failed: $TaskName"
        }
    } finally {
        Pop-Location
    }
}

function Find-LatestApk {
    param(
        [string]$AppRoot,
        [string]$BuildType
    )

    $apkRoot = Join-Path $AppRoot "app\build\outputs\apk\$BuildType"
    if (-not (Test-Path -LiteralPath $apkRoot -PathType Container)) {
        Fail "APK output directory does not exist: $apkRoot"
    }

    $apks = @(Get-ChildItem -LiteralPath $apkRoot -Recurse -File -Filter "*.apk" |
        Sort-Object LastWriteTimeUtc -Descending)

    if ($apks.Count -eq 0) {
        Fail "No APK file found in $apkRoot"
    }

    return $apks[0]
}

function Find-ApkSigner {
    $sdkCandidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" } else { $null })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($sdkRoot in $sdkCandidates) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
            continue
        }

        $buildTools = @(Get-ChildItem -LiteralPath $buildToolsRoot -Directory | Sort-Object Name -Descending)
        foreach ($buildTool in $buildTools) {
            foreach ($fileName in @("apksigner.bat", "apksigner.exe", "apksigner")) {
                $candidate = Join-Path $buildTool.FullName $fileName
                if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                    return $candidate
                }
            }
        }
    }

    Fail "Could not find apksigner. Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with build-tools."
}

function Test-SigningConfigPresent {
    return -not [string]::IsNullOrWhiteSpace($SigningKeystore) -and
        -not [string]::IsNullOrWhiteSpace($SigningKeyAlias) -and
        -not [string]::IsNullOrWhiteSpace($SigningKeystorePassword)
}

function Invoke-ApkSignerVerify {
    param(
        [string]$ApkSigner,
        [string]$ApkPath
    )

    $verifyOutput = @(& $ApkSigner "verify" "--print-certs" $ApkPath 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $verifyOutput | ForEach-Object { Write-Host $_ }
        Fail "APK signature verification failed: $ApkPath"
    }

    return ($verifyOutput -join [Environment]::NewLine)
}

function Invoke-ApkSignerSign {
    param(
        [string]$SourceApkPath,
        [string]$TargetApkPath
    )

    Assert-FileExists $SigningKeystore
    $apkSigner = Find-ApkSigner
    $effectiveKeyPassword = if ([string]::IsNullOrWhiteSpace($SigningKeyPassword)) {
        $SigningKeystorePassword
    } else {
        $SigningKeyPassword
    }

    $signArgs = @(
        "sign",
        "--ks", $SigningKeystore,
        "--ks-key-alias", $SigningKeyAlias,
        "--ks-pass", "pass:$SigningKeystorePassword",
        "--key-pass", "pass:$effectiveKeyPassword",
        "--out", $TargetApkPath,
        $SourceApkPath
    )

    & $apkSigner @signArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "APK signing failed."
    }

    $verifyOutput = Invoke-ApkSignerVerify -ApkSigner $apkSigner -ApkPath $TargetApkPath
    return [pscustomobject]@{
        ApkSigner = $apkSigner
        VerifyOutput = $verifyOutput
    }
}

function Write-UploadHelper {
    param(
        [string]$Path,
        [string]$ApkFileName,
        [string]$ReleaseNotesFileName,
        [string]$VersionName,
        [string]$ApiBaseUrl,
        [bool]$Signed
    )

    $template = @'
param(
    [string]$AdminToken = $env:ALICIA_ADMIN_TOKEN,
    [string]$ApiBaseUrl = "__API_BASE_URL__",
    [switch]$VerifyDownload
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

$apkPath = Join-Path $PSScriptRoot "__APK_FILE_NAME__"
$releaseNotesPath = Join-Path $PSScriptRoot "__RELEASE_NOTES_FILE_NAME__"
$versionName = "__VERSION_NAME__"
$baseUrl = $ApiBaseUrl.TrimEnd("/")
$isSigned = [System.Convert]::ToBoolean("__SIGNED__")

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    Fail "Set ALICIA_ADMIN_TOKEN or pass -AdminToken before uploading."
}
if (-not $isSigned) {
    Fail "This release package is marked unsigned and must not be uploaded."
}
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    Fail "Missing APK file: $apkPath"
}
if (-not (Test-Path -LiteralPath $releaseNotesPath -PathType Leaf)) {
    Fail "Missing release notes file: $releaseNotesPath"
}

$releaseNotes = (Get-Content -LiteralPath $releaseNotesPath -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($releaseNotes) -or $releaseNotes -match '^TODO:') {
    Fail "Edit release-notes.txt with real production release notes before uploading."
}

Add-Type -AssemblyName System.Net.Http

$client = [System.Net.Http.HttpClient]::new()
$fileStream = $null
$form = $null

try {
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $AdminToken)

    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $fileStream = [System.IO.File]::OpenRead($apkPath)
    $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/vnd.android.package-archive")
    $form.Add($fileContent, "file", [System.IO.Path]::GetFileName($apkPath))
    $form.Add([System.Net.Http.StringContent]::new($versionName, [System.Text.Encoding]::UTF8), "versionName")
    $form.Add([System.Net.Http.StringContent]::new($releaseNotes, [System.Text.Encoding]::UTF8), "releaseNotes")

    $uploadUri = "$baseUrl/api/admin/app-package"
    Write-Host "Uploading APK to $uploadUri"
    $response = $client.PostAsync($uploadUri, $form).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        Write-Host $body
        Fail "Upload failed with HTTP $([int]$response.StatusCode)."
    }

    Ok "APK uploaded"
    Write-Host $body
} finally {
    if ($form -ne $null) {
        $form.Dispose()
    }
    if ($fileStream -ne $null) {
        $fileStream.Dispose()
    }
    $client.Dispose()
}

if ($VerifyDownload) {
    $version = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/app-package/version"
    if (-not $version.available) {
        Fail "Public app-package version endpoint does not report an available package."
    }
    if ($version.versionName -ne $versionName) {
        Fail "Public app-package version mismatch. Expected $versionName, got $($version.versionName)."
    }

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $verifyClient = [System.Net.Http.HttpClient]::new($handler)
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Head, "$baseUrl/api/app-package/download/current")
        $downloadResponse = $verifyClient.SendAsync($request).GetAwaiter().GetResult()
        $downloadStatus = [int]$downloadResponse.StatusCode
        if ($downloadStatus -notin @(200, 302)) {
            Fail "Public app-package download endpoint returned unexpected status $downloadStatus."
        }
    } finally {
        $verifyClient.Dispose()
        $handler.Dispose()
    }

    Ok "public version and download endpoints verified"
}
'@

    $content = $template.
        Replace("__API_BASE_URL__", $ApiBaseUrl).
        Replace("__APK_FILE_NAME__", $ApkFileName).
        Replace("__RELEASE_NOTES_FILE_NAME__", $ReleaseNotesFileName).
        Replace("__VERSION_NAME__", $VersionName).
        Replace("__SIGNED__", $Signed.ToString())

    Set-Content -LiteralPath $Path -Value $content -Encoding UTF8
}

$resolvedRoot = Resolve-RepoRoot
Set-Location $resolvedRoot

$appRoot = Join-Path $resolvedRoot $App
$buildFile = Join-Path $appRoot "app\build.gradle.kts"
Assert-FileExists $buildFile

$buildContent = Get-Content -LiteralPath $buildFile -Raw
$applicationId = Get-GradleStringValue -Content $buildContent -Name "applicationId"
$versionCode = Get-GradleIntValue -Content $buildContent -Name "versionCode"
$versionName = Get-GradleStringValue -Content $buildContent -Name "versionName"
$officialApplicationId = "com.alicia.cloudstorage.phone"
$normalizedBuildType = $BuildType.ToLowerInvariant()
$taskName = if ($normalizedBuildType -eq "release") { ":app:assembleRelease" } else { ":app:assembleDebug" }
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$safeVersionName = ConvertTo-SafeFilePart $versionName
$appSlug = "alicia-cloud-android"

if ($normalizedBuildType -eq "release" -and $applicationId -ne $officialApplicationId) {
    Fail "Release APK must use official applicationId $officialApplicationId, got $applicationId."
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $resolvedRoot "deploy\generated\android-release-packages"
}

$targetDir = Join-Path $OutputRoot (Join-Path $App "$safeVersionName-$versionCode-$normalizedBuildType-$timestamp")

Write-Host "Preparing Alicia Android release package..."
Write-Host "Repo root:      $resolvedRoot"
Write-Host "Android app:    $App"
Write-Host "applicationId:  $applicationId"
Write-Host "version:        $versionName ($versionCode)"
Write-Host "build type:     $normalizedBuildType"
Write-Host "output dir:     $targetDir"

if (-not $SkipReadiness) {
    $readinessScript = Join-Path $resolvedRoot "deploy\scripts\check-android-release-readiness.ps1"
    Assert-FileExists $readinessScript
    & powershell -NoProfile -ExecutionPolicy Bypass -File $readinessScript
    if ($LASTEXITCODE -ne 0) {
        Fail "Android readiness check failed."
    }
    Ok "Android release readiness passed"
} else {
    Write-Host "Android readiness check skipped by -SkipReadiness." -ForegroundColor Yellow
}

if (-not $SkipAssemble) {
    Invoke-GradleTask -AppRoot $appRoot -TaskName $taskName
    Ok "Gradle $taskName completed"
} else {
    Write-Host "Gradle assemble skipped by -SkipAssemble; using the latest existing APK output." -ForegroundColor Yellow
}

$apk = Find-LatestApk -AppRoot $appRoot -BuildType $normalizedBuildType
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$isReleaseBuild = $normalizedBuildType -eq "release"
$sourceApkWasUnsigned = $apk.Name -match 'unsigned'
$signedPackage = $false
$signingMode = "none"
$apkSignerPath = $null
$signatureVerification = $null
$targetSuffix = $normalizedBuildType

if ($isReleaseBuild -and (Test-SigningConfigPresent)) {
    $targetSuffix = "release"
} elseif ($isReleaseBuild -and -not $sourceApkWasUnsigned) {
    $targetSuffix = "release"
} elseif ($isReleaseBuild -and $AllowUnsignedReleaseCandidate) {
    $targetSuffix = "release-unsigned"
}

$targetApkName = "$appSlug-$safeVersionName-$versionCode-$targetSuffix.apk"
$targetApkPath = Join-Path $targetDir $targetApkName

if ($isReleaseBuild -and (Test-SigningConfigPresent)) {
    $signResult = Invoke-ApkSignerSign -SourceApkPath $apk.FullName -TargetApkPath $targetApkPath
    $signedPackage = $true
    $signingMode = "apksigner"
    $apkSignerPath = $signResult.ApkSigner
    $signatureVerification = $signResult.VerifyOutput
    Ok "Release APK signed and verified"
} elseif ($isReleaseBuild -and -not $sourceApkWasUnsigned) {
    Copy-Item -LiteralPath $apk.FullName -Destination $targetApkPath -Force
    $apkSignerPath = Find-ApkSigner
    $signatureVerification = Invoke-ApkSignerVerify -ApkSigner $apkSignerPath -ApkPath $targetApkPath
    $signedPackage = $true
    $signingMode = "gradle"
    Ok "Release APK signature verified"
} elseif ($isReleaseBuild) {
    if (-not $AllowUnsignedReleaseCandidate) {
        Fail "Release build produced an unsigned APK. Configure ALICIA_ANDROID_KEYSTORE_PATH, ALICIA_ANDROID_KEY_ALIAS and ALICIA_ANDROID_KEYSTORE_PASSWORD, or run with -AllowUnsignedReleaseCandidate for local inspection only."
    }
    Copy-Item -LiteralPath $apk.FullName -Destination $targetApkPath -Force
    Write-Host "Unsigned release candidate generated for local inspection only." -ForegroundColor Yellow
} else {
    Copy-Item -LiteralPath $apk.FullName -Destination $targetApkPath -Force
}

$hash = (Get-FileHash -LiteralPath $targetApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$hashFile = "$targetApkPath.sha256"
Set-Content -LiteralPath $hashFile -Value "$hash  $targetApkName" -Encoding ASCII

$notesFileName = "release-notes.txt"
$notesPath = Join-Path $targetDir $notesFileName
if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
    Set-Content -LiteralPath $notesPath -Value "TODO: Fill Android production release notes before uploading." -Encoding UTF8
} else {
    Set-Content -LiteralPath $notesPath -Value $ReleaseNotes.Trim() -Encoding UTF8
}

$uploadHelperName = "upload-current-package.ps1"
$uploadHelperPath = Join-Path $targetDir $uploadHelperName
Write-UploadHelper `
    -Path $uploadHelperPath `
    -ApkFileName $targetApkName `
    -ReleaseNotesFileName $notesFileName `
    -VersionName $versionName `
    -ApiBaseUrl $ApiBaseUrl.TrimEnd("/") `
    -Signed $signedPackage

$manifest = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    app = $App
    applicationId = $applicationId
    versionName = $versionName
    versionCode = $versionCode
    buildType = $normalizedBuildType
    sourceApk = $apk.FullName
    sourceApkWasUnsigned = $sourceApkWasUnsigned
    apkFileName = $targetApkName
    fileSizeBytes = (Get-Item -LiteralPath $targetApkPath).Length
    sha256 = $hash
    signed = $signedPackage
    signingMode = $signingMode
    apkSigner = $apkSignerPath
    signatureVerification = $signatureVerification
    apiBaseUrl = $ApiBaseUrl.TrimEnd("/")
    publicVersionEndpoint = "/api/app-package/version"
    publicDownloadEndpoint = "/api/app-package/download/current"
    adminUploadEndpoint = "/api/admin/app-package"
    uploadHelper = $uploadHelperName
    releaseNotesFile = $notesFileName
}

$manifestPath = Join-Path $targetDir "manifest.json"
($manifest | ConvertTo-Json -Depth 4) | Set-Content -LiteralPath $manifestPath -Encoding UTF8

Ok "APK prepared in release package directory"
Ok "SHA-256 checksum written"
Ok "release manifest written"
Ok "upload helper written"

Write-Host ""
Write-Host "Prepared Android release package:"
Write-Host $targetDir
Write-Host ""
if ($signedPackage) {
    Write-Host "Before production upload, edit release-notes.txt if it still starts with TODO."
    Write-Host "Upload command:"
    Write-Host "  `$env:ALICIA_ADMIN_TOKEN = '<identity admin access token>'"
    Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File `"$uploadHelperPath`" -VerifyDownload"
} else {
    Write-Host "This package is unsigned and is for local inspection only. Configure Android release signing before uploading."
}
