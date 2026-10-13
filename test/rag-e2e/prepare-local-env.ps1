[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

function New-HexSecret {
    param([int]$Bytes = 32)
    [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes($Bytes)).ToLowerInvariant()
}

function Set-DotEnvValue {
    param(
        [Parameter(Mandatory)][string]$Content,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )
    $pattern = "(?m)^$([regex]::Escape($Name))=.*$"
    if ($Content -notmatch $pattern) {
        throw "Template is missing $Name."
    }
    [regex]::Replace($Content, $pattern, "$Name=$Value", 1)
}

$context = Get-RagE2EContext
if (-not (Test-Path -LiteralPath $context.EnvFile -PathType Leaf)) {
    $content = [System.IO.File]::ReadAllText($context.ExampleEnvFile)
} else {
    $content = [System.IO.File]::ReadAllText($context.EnvFile)
}

$generatedNames = @(
    'ALICIA_RAG_E2E_MYSQL_ROOT_PASSWORD',
    'ALICIA_RAG_E2E_EXEC_DB_PASSWORD',
    'ALICIA_RAG_E2E_REGISTRATION_SECRET',
    'ALICIA_RAG_E2E_CLOUD_SECRET',
    'ALICIA_RAG_E2E_SHARE_SECRET'
)
foreach ($name in $generatedNames) {
    $match = [regex]::Match($content, "(?m)^$([regex]::Escape($name))=(.*)$")
    if (-not $match.Success) {
        throw "Template is missing $name."
    }
    $current = $match.Groups[1].Value.Trim()
    if ([string]::IsNullOrWhiteSpace($current) -or $current -eq 'GENERATE_ME') {
        $content = Set-DotEnvValue -Content $content -Name $name -Value (New-HexSecret)
    }
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($context.EnvFile, $content, $utf8NoBom)

Push-Location $context.RepositoryRoot
try {
    & git check-ignore --quiet -- 'test/rag-e2e/.env.local'
    if ($LASTEXITCODE -ne 0) {
        throw 'Safety check failed: test/rag-e2e/.env.local is not ignored by Git.'
    }
} finally {
    Pop-Location
}

Write-Host '[OK] Local-only secrets are present in the Git-ignored .env.local file.'
Write-Host "Edit this file and fill only the blank account, password and DeepSeek key fields:"
Write-Host "  $($context.EnvFile)"
Write-Host 'Secret values were not printed.'
