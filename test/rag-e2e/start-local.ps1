[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$context = Get-RagE2EContext
Import-RagE2EEnv -Path $context.EnvFile

$identityBaseUrl = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_IDENTITY_BASE_URL'
[void](Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_TOKEN_ISSUER')
[void](Assert-RagE2EEnvValue -Name 'DEEPSEEK_API_KEY' -MinimumLength 8)
$registrationSecret = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_REGISTRATION_SECRET' -MinimumLength 32
$cloudSecret = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_CLOUD_SECRET' -MinimumLength 32
[void](Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_MYSQL_ROOT_PASSWORD' -MinimumLength 16)
[void](Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_EXEC_DB_PASSWORD' -MinimumLength 16)
[void](Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_SHARE_SECRET' -MinimumLength 32)

if ($registrationSecret -eq $cloudSecret) {
    throw 'Registration and Cloud dispatch secrets must be different.'
}
if ($identityBaseUrl -notmatch '^https://' -and
    $identityBaseUrl -notmatch '^http://host\.docker\.internal(?::\d+)?$') {
    throw 'Identity base URL must use HTTPS or the explicit host.docker.internal local bridge.'
}

Invoke-RagE2ENative -Command 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
$compose = Get-RagE2EComposeArguments -Context $context
Invoke-RagE2ENative -Command 'docker' -Arguments ($compose + @('config', '--quiet'))

$upArguments = $compose + @('up', '--detach')
if (-not $SkipBuild) {
    $mavenWrapper = if ($IsWindows) {
        Join-Path $context.RepositoryRoot 'mvnw.cmd'
    } else {
        Join-Path $context.RepositoryRoot 'mvnw'
    }
    Invoke-RagE2ENative -Command $mavenWrapper -Arguments @(
        '-pl', 'rag', '-DskipTests', 'package'
    )
    $ragTargetDirectory = Join-Path (Join-Path $context.RepositoryRoot 'rag') 'target'
    $ragArtifact = Get-ChildItem -LiteralPath $ragTargetDirectory `
        -Filter 'rag-service-*.jar' -File |
        Where-Object { -not $_.Name.EndsWith('.original') } |
        Select-Object -First 1
    if ($null -eq $ragArtifact) {
        throw 'RAG build completed without a runnable JAR.'
    }
    $localBuildDirectory = Join-Path $context.SuiteDirectory 'build'
    New-Item -ItemType Directory -Path $localBuildDirectory -Force | Out-Null
    Copy-Item -LiteralPath $ragArtifact.FullName `
        -Destination (Join-Path $localBuildDirectory 'rag-service.jar') -Force
    $upArguments += '--build'
}
Invoke-RagE2ENative -Command 'docker' -Arguments $upArguments

$apiPort = Get-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_API_PORT'
$ragPort = Get-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_RAG_PORT'
$executionPort = Get-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_EXEC_PORT'
Wait-RagE2EHttpOk -Name 'CloudStorageApi' -Uri "http://127.0.0.1:$apiPort/api/health"
Wait-RagE2EHttpOk -Name 'RAG' -Uri "http://127.0.0.1:$ragPort/api/health"
Wait-RagE2EHttpOk -Name 'RAG execution' -Uri "http://127.0.0.1:$executionPort/api/health"

Invoke-RagE2ENative -Command 'docker' -Arguments ($compose + @('ps'))
Write-Host '[OK] Isolated RAG evaluation stack is ready. Production configuration was not used or modified.'
