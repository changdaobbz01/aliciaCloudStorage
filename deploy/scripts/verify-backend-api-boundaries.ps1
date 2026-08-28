param()

$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Fail {
    param([string]$Message)
    throw $Message
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

function Resolve-MavenCommand {
    $wrapper = Join-Path $RootDir "mvnw.cmd"
    if (Test-Path -LiteralPath $wrapper -PathType Leaf) {
        return $wrapper
    }

    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($maven) {
        return $maven.Source
    }

    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        return $maven.Source
    }

    Fail "Maven is required. Expected mvnw.cmd in the repository root or mvn on PATH."
}

function Invoke-MavenBoundaryTests {
    param(
        [string]$Module,
        [string]$Tests
    )

    $mavenCommand = Resolve-MavenCommand

    Push-Location $RootDir
    try {
        & $mavenCommand "-pl" $Module "-Dtest=$Tests" "test"
        if ($LASTEXITCODE -ne 0) {
            Fail "Maven boundary tests failed for $Module."
        }
    } finally {
        Pop-Location
    }
}

Invoke-Step "CloudStorageApi legacy and route ownership boundaries" {
    Invoke-MavenBoundaryTests "CloudStorageApi" "IdentityRouteBoundaryTest,CloudApiRouteOwnershipTest,CurrentPrincipalTest"
}

Invoke-Step "identityApi source, route, and admin access boundaries" {
    Invoke-MavenBoundaryTests "identityApi" "IdentitySourceBoundaryTest,IdentityApiRouteOwnershipTest"
}

Write-Host "[OK] backend API boundary verification complete"
