param(
    [string]$RepoRoot = "",
    [string[]]$Apps = @("phoneApp", "phoneAppAdd"),
    [switch]$SkipGradle
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

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Description
    )

    $content = Get-Content -LiteralPath $Path -Raw
    if ($content -notmatch $Pattern) {
        Fail "$Description is missing in $Path"
    }
}

function Assert-NotContains {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Description
    )

    $matches = Select-String -LiteralPath $Path -Pattern $Pattern -AllMatches
    if ($matches) {
        $matches | ForEach-Object {
            Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)" -ForegroundColor Yellow
        }
        Fail "$Description remains in $Path"
    }
}

function Assert-NoDirectLogoutInAuthBranch {
    param([string]$Path)

    $lines = Get-Content -LiteralPath $Path
    $hits = @()
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -notmatch 'status\s*==\s*401') {
            continue
        }

        $end = [Math]::Min($lines.Count - 1, $index + 12)
        for ($scan = $index; $scan -le $end; $scan++) {
            if ($lines[$scan] -match '\blogout\s*\(') {
                $hits += "${Path}:$($scan + 1):$($lines[$scan])"
            }
        }
    }

    if ($hits.Count -gt 0) {
        $hits | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        Fail "Direct logout call remains inside a 401 branch in $Path"
    }
}

function Get-TextFiles {
    param([string[]]$Roots)

    $extensions = @(
        ".gradle", ".java", ".kt", ".kts", ".md", ".properties", ".pro", ".ps1", ".xml"
    )

    foreach ($root in $Roots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        if (Test-Path -LiteralPath $root -PathType Leaf) {
            $item = Get-Item -LiteralPath $root
            if ($extensions -contains $item.Extension) {
                $item
            }
            continue
        }

        Get-ChildItem -LiteralPath $root -Recurse -File |
            Where-Object {
                $extensions -contains $_.Extension -and
                $_.FullName -notmatch '\\(build|\.gradle)\\'
            }
    }
}

function Get-RelativePath {
    param(
        [string]$BasePath,
        [string]$TargetPath
    )

    $base = [System.IO.Path]::GetFullPath($BasePath).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $target = [System.IO.Path]::GetFullPath($TargetPath)
    $baseUri = [System.Uri]("$base/")
    $targetUri = [System.Uri]$target

    return [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace("/", [System.IO.Path]::DirectorySeparatorChar)
}

function Assert-NoMatches {
    param(
        [System.IO.FileInfo[]]$Files,
        [string]$Pattern,
        [string]$Description,
        [string[]]$AllowedPathPatterns = @()
    )

    $hits = @()
    foreach ($file in $Files) {
        $relative = Get-RelativePath $resolvedRoot $file.FullName
        $allowed = $false
        foreach ($allowedPattern in $AllowedPathPatterns) {
            if ($relative -match $allowedPattern) {
                $allowed = $true
                break
            }
        }
        if ($allowed) {
            continue
        }

        $matches = Select-String -LiteralPath $file.FullName -Pattern $Pattern -AllMatches
        foreach ($match in $matches) {
            $hits += "${relative}:$($match.LineNumber):$($match.Line)"
        }
    }

    if ($hits.Count -gt 0) {
        $hits | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        Fail $Description
    }
}

function Invoke-GradleTest {
    param([string]$AppRoot)

    $gradlewBat = Join-Path $AppRoot "gradlew.bat"
    $gradlew = Join-Path $AppRoot "gradlew"
    $command = if (Test-Path -LiteralPath $gradlewBat) { $gradlewBat } else { $gradlew }

    Assert-FileExists $command

    Push-Location $AppRoot
    try {
        & $command ":app:testDebugUnitTest"
        if ($LASTEXITCODE -ne 0) {
            Fail "Gradle unit tests failed for $AppRoot"
        }
    } finally {
        Pop-Location
    }
}

$resolvedRoot = Resolve-RepoRoot
Set-Location $resolvedRoot

Write-Host "Checking Alicia Android release readiness..."
Write-Host "Repo root: $resolvedRoot"

$scanRoots = @()
foreach ($app in $Apps) {
    $scanRoots += @(
        "$app/README.md",
        "$app/local.properties.example",
        "$app/UI_INTERACTION_DESIGN_STANDARD.md",
        "$app/app/build.gradle.kts",
        "$app/app/src/main"
    )
}
$scanFiles = @(Get-TextFiles $scanRoots)

Assert-NoMatches `
    -Files $scanFiles `
    -Pattern '(/api/auth|api/auth|/api/admin/users|api/admin/users)' `
    -Description "Legacy identity/admin API route references remain in Android release files."
Ok "no legacy /api/auth/** or /api/admin/users references in Android release files"

Assert-NoMatches `
    -Files $scanFiles `
    -Pattern '(HONG_KONG_BASE_URL|香港测试服|环境切换|切换 API 服务)' `
    -Description "Test service or multi-environment UI wording remains in Android release files."
Ok "no test-service Android UI entry remains"

Assert-NoMatches `
    -Files $scanFiles `
    -Pattern 'http://43\.132\.237\.15' `
    -Description "Legacy IP is exposed outside migration compatibility code." `
    -AllowedPathPatterns @('app\\src\\main\\java\\com\\alicia\\cloudstorage\\phone\\AppConfig\.kt$', 'app\\src\\test\\java\\com\\alicia\\cloudstorage\\phone\\AppConfigTest\.kt$')
Ok "legacy IP appears only in migration compatibility coverage"

foreach ($app in $Apps) {
    $appRoot = Join-Path $resolvedRoot $app
    $apiFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/data/AliciaCloudApi.kt"
    $modelsFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/data/Models.kt"
    $repoFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/data/AliciaRepository.kt"
    $sessionStoreFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/data/SessionStore.kt"
    $appConfigFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/AppConfig.kt"
    $viewModelFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/ui/MainViewModel.kt"
    $sessionPolicyFile = Join-Path $appRoot "app/src/main/java/com/alicia/cloudstorage/phone/ui/MobileSessionPolicy.kt"
    $sessionPolicyTestFile = Join-Path $appRoot "app/src/test/java/com/alicia/cloudstorage/phone/ui/MobileSessionPolicyTest.kt"
    $appConfigTestFile = Join-Path $appRoot "app/src/test/java/com/alicia/cloudstorage/phone/AppConfigTest.kt"
    $localPropertiesExample = Join-Path $appRoot "local.properties.example"

    foreach ($file in @(
        $apiFile,
        $modelsFile,
        $repoFile,
        $sessionStoreFile,
        $appConfigFile,
        $viewModelFile,
        $sessionPolicyFile,
        $sessionPolicyTestFile,
        $appConfigTestFile,
        $localPropertiesExample
    )) {
        Assert-FileExists $file
    }

    Assert-Contains $apiFile 'api/identity/auth/token/refresh' "$app identity refresh endpoint"
    Assert-Contains $apiFile 'api/identity/auth/logout' "$app identity logout endpoint"
    Assert-Contains $apiFile 'api/cloud-profile/me' "$app cloud profile endpoint"
    Assert-Contains $apiFile 'api/cloud-profile/avatar' "$app cloud avatar endpoint"
    Assert-Contains $apiFile 'api/admin/cloud-users' "$app cloud users endpoint"
    Assert-Contains $modelsFile 'data class RefreshTokenPayload' "$app refresh token payload"
    Assert-Contains $modelsFile 'data class LogoutPayload' "$app logout payload"
    Assert-Contains $repoFile 'RefreshTokenPayload\(refreshToken\)' "$app refresh token body usage"
    Assert-Contains $repoFile 'LogoutPayload\(refreshToken\)' "$app logout refresh token body usage"
    Assert-Contains $sessionStoreFile 'require\(nextRefreshToken\.isNotBlank\(\)\)' "$app nonblank refresh token persistence guard"
    Assert-Contains $sessionPolicyFile 'MOBILE_SESSION_EXPIRED_MESSAGE' "$app session expired message"
    Assert-Contains $sessionPolicyFile 'status == 401' "$app 401 session expiry classification"
    Assert-Contains $viewModelFile 'clearExpiredSession' "$app local session expiry cleanup"
    Assert-Contains $viewModelFile 'isMobileAuthExpired\(\)' "$app 401 handler"
    Assert-NoDirectLogoutInAuthBranch $viewModelFile
    Assert-Contains $appConfigFile 'MAINLAND_BASE_URL = "https://windwindwind-alicia\.cn"' "$app official base URL"
    Assert-Contains $appConfigFile 'LEGACY_HONG_KONG_HTTP_BASE_URL' "$app legacy IP migration constant"
    Assert-Contains $localPropertiesExample 'ALICIA_API_BASE_URL=https://windwindwind-alicia\.cn' "$app official local properties example"
    Ok "$app identity session contract is release-ready"

    if (-not $SkipGradle) {
        Invoke-GradleTest $appRoot
        Ok "$app debug unit tests passed"
    }
}

if ($SkipGradle) {
    Write-Host "Gradle tests skipped by -SkipGradle." -ForegroundColor Yellow
}

Write-Host "Alicia Android release readiness check passed." -ForegroundColor Green
