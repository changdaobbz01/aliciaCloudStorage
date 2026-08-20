param(
    [string]$Branch = "",
    [string[]]$Remotes = @("gitee", "origin")
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$GitArgs
    )

    & git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE."
    }
}

$repoRoot = & git rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) {
    throw "Not in a git repository."
}

Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = (& git rev-parse --abbrev-ref HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to resolve the current git branch."
    }
}

if ($Branch -eq "HEAD") {
    throw "Detached HEAD is not supported. Checkout a branch before pushing."
}

$knownRemotes = git remote
foreach ($remote in $Remotes) {
    if ($knownRemotes -notcontains $remote) {
        throw "Missing git remote '$remote'. Configure it before running this script."
    }
}

$dirtyStatus = git status --porcelain
if ($dirtyStatus) {
    Write-Host "Working tree has uncommitted changes. Only committed changes will be pushed:" -ForegroundColor Yellow
    git status --short
    Write-Host ""
}

foreach ($remote in $Remotes) {
    Write-Host "Pushing $Branch to $remote..." -ForegroundColor Cyan
    Invoke-Git push $remote "${Branch}:${Branch}"
}

Write-Host "Pushed $Branch to: $($Remotes -join ', ')" -ForegroundColor Green
