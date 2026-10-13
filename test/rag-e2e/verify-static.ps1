[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$context = Get-RagE2EContext
$scripts = Get-ChildItem -LiteralPath $context.SuiteDirectory -Filter '*.ps1' -File
foreach ($script in $scripts) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $script.FullName,
        [ref]$tokens,
        [ref]$errors
    )
    if ($errors.Count -gt 0) {
        throw "PowerShell parse failed for $($script.Name): $($errors[0].Message)"
    }
}
Write-Host "[OK] Parsed $($scripts.Count) PowerShell scripts."

[void](Get-Content -LiteralPath $context.ScenarioFile -Raw -Encoding UTF8 | ConvertFrom-Json)
Write-Host '[OK] Scenario JSON is valid.'

$composeText = Get-Content -LiteralPath $context.ComposeFile -Raw
foreach ($required in @(
    '127.0.0.1:',
    'ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS: FOLDER_CREATE',
    'ALICIA_CLOUD_OBJECT_CLEANUP_ENABLED: "false"',
    'name: alicia-rag-e2e'
)) {
    if (-not $composeText.Contains($required)) {
        throw "Compose safety marker is missing: $required"
    }
}
Write-Host '[OK] Compose isolation and action allowlist markers are present.'

$previousDeepSeekKey = [System.Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Process')
try {
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY', 'static-validation-only', 'Process')
    Invoke-RagE2ENative -Command 'docker' -Arguments @(
        'compose',
        '--project-name', $context.ProjectName,
        '--env-file', $context.ExampleEnvFile,
        '--file', $context.ComposeFile,
        'config', '--quiet'
    )
} finally {
    [System.Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY', $previousDeepSeekKey, 'Process')
}
Write-Host '[OK] Docker Compose configuration resolves without contacting the daemon.'

Push-Location $context.RepositoryRoot
try {
    & git check-ignore --quiet -- 'test/rag-e2e/.env.local'
    if ($LASTEXITCODE -ne 0) {
        throw '.env.local is not ignored by Git.'
    }
    & git diff --check -- .gitignore test/rag-e2e
    if ($LASTEXITCODE -ne 0) {
        throw 'git diff --check failed for the RAG E2E harness.'
    }
} finally {
    Pop-Location
}
Write-Host '[OK] Git secret exclusion and whitespace checks passed.'
