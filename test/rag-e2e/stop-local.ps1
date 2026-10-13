[CmdletBinding()]
param(
    [switch]$PurgeData
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$context = Get-RagE2EContext
Import-RagE2EEnv -Path $context.EnvFile
if ($context.ProjectName -ne 'alicia-rag-e2e' -or
    [System.IO.Path]::GetFileName($context.SuiteDirectory) -ne 'rag-e2e') {
    throw 'Unexpected project identity; refusing to stop or remove resources.'
}

# Compose evaluates the complete file even for `down`. Keep teardown available
# before the user has filled the model key; start-local.ps1 still rejects it.
if ([string]::IsNullOrWhiteSpace((Get-RagE2EEnvValue -Name 'DEEPSEEK_API_KEY'))) {
    [System.Environment]::SetEnvironmentVariable(
        'DEEPSEEK_API_KEY',
        'teardown-placeholder-not-used',
        'Process'
    )
}

$compose = Get-RagE2EComposeArguments -Context $context
$arguments = $compose + @('down', '--remove-orphans')
if ($PurgeData) {
    $arguments += '--volumes'
}
Invoke-RagE2ENative -Command 'docker' -Arguments $arguments

if ($PurgeData) {
    Write-Host '[OK] Isolated containers, network and test database volume were removed.'
} else {
    Write-Host '[OK] Isolated containers and network were removed; the test database volume was preserved.'
}
