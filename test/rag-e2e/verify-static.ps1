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

foreach ($scenarioFile in @($context.ScenarioFile, $context.StressScenarioFile)) {
    $scenario = Get-Content -LiteralPath $scenarioFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if (@($scenario.cases).Count -eq 0) {
        throw "Scenario suite is empty: $scenarioFile"
    }
}
Write-Host '[OK] Baseline and stress scenario JSON are valid and non-empty.'

$validIntentIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
[void]$validIntentIds.Add('fallback')
foreach ($rule in @(Import-Csv (Join-Path $context.RepositoryRoot 'rag/src/main/resources/rag/conversation/intent_rules.csv'))) {
    [void]$validIntentIds.Add([string]$rule.intent_id)
}
$recognitionIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$singleTurnMessages = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$recognitionCounts = [ordered]@{}
foreach ($recognitionFile in @($context.RecognitionDevFile, $context.RecognitionHoldoutFile)) {
    $dataset = Get-Content -LiteralPath $recognitionFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$dataset.labelPolicyVersion -ne 'rag_recognition_labels_v1') {
        throw "Unsupported recognition label policy: $recognitionFile"
    }
    if ([int]$dataset.gates.maximumUnsafeExecutableCount -ne 0) {
        throw "Recognition safety gate must remain zero: $recognitionFile"
    }
    $turnCount = 0
    foreach ($case in @($dataset.cases)) {
        $caseId = [string]$case.id
        if ([string]::IsNullOrWhiteSpace($caseId) -or -not $recognitionIds.Add($caseId)) {
            throw "Duplicate or missing recognition case id: $caseId"
        }
        if ([string]::IsNullOrWhiteSpace([string]$case.group) -or
            [string]$case.difficulty -notin @('easy', 'medium', 'hard')) {
            throw "Recognition case metadata is incomplete: $caseId"
        }
        $turns = @($case.turns)
        if ($turns.Count -eq 0) {
            throw "Recognition case has no turns: $caseId"
        }
        if ($turns.Count -eq 1 -and -not $singleTurnMessages.Add(([string]$turns[0].message).Trim())) {
            throw "Duplicate single-turn recognition message: $($turns[0].message)"
        }
        foreach ($turn in $turns) {
            $turnCount++
            if ([string]::IsNullOrWhiteSpace([string]$turn.message)) {
                throw "Recognition case contains an empty message: $caseId"
            }
            $intentIds = @($turn.expected.intentId | ForEach-Object { [string]$_ })
            if ($intentIds.Count -ne 1 -or -not $validIntentIds.Contains($intentIds[0])) {
                throw "Recognition case must have one supported intent label: $caseId"
            }
        }
    }
    $recognitionCounts[[string]$dataset.split] = $turnCount
}
if ([int]$recognitionCounts.development -lt 50 -or [int]$recognitionCounts.holdout -lt 25) {
    throw 'Recognition datasets do not meet the minimum development/holdout coverage.'
}
Write-Host "[OK] Recognition datasets are valid: development=$($recognitionCounts.development), holdout=$($recognitionCounts.holdout)."

if (Test-Path -LiteralPath $context.RecognitionBaselineFile -PathType Leaf) {
    $recognitionBaseline = Get-Content -LiteralPath $context.RecognitionBaselineFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$recognitionBaseline.schemaVersion -ne 'rag_recognition_baseline_v1' -or
        [string]$recognitionBaseline.lane -ne 'local-fallback' -or
        [string]$recognitionBaseline.labelPolicyVersion -ne 'rag_recognition_labels_v1') {
        throw 'Local recognition baseline uses an unsupported schema or lane.'
    }
    $devHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
    $holdoutHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionHoldoutFile).Hash.ToLowerInvariant()
    if ([string]$recognitionBaseline.datasetHashes.development -ne $devHash -or
        [string]$recognitionBaseline.datasetHashes.holdout -ne $holdoutHash) {
        throw 'Recognition datasets changed without recording a new local fallback baseline.'
    }
    if ([int]$recognitionBaseline.development.totalTurns -ne [int]$recognitionCounts.development -or
        [int]$recognitionBaseline.holdout.totalTurns -ne [int]$recognitionCounts.holdout -or
        [int]$recognitionBaseline.development.unsafeExecutableCount -ne 0 -or
        [int]$recognitionBaseline.holdout.unsafeExecutableCount -ne 0) {
        throw 'Local recognition baseline coverage or safety state is invalid.'
    }
    Write-Host '[OK] Local fallback recognition baseline matches both labeled datasets.'
}

if (Test-Path -LiteralPath $context.ContractBaselineFile -PathType Leaf) {
    $contractBaseline = Get-Content -LiteralPath $context.ContractBaselineFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$contractBaseline.schemaVersion -ne 'rag_e2e_contract_baseline_v1') {
        throw 'RAG contract baseline uses an unsupported schema.'
    }
    $expectedTurnCount = 0
    foreach ($scenarioFile in @($context.ScenarioFile, $context.StressScenarioFile)) {
        $scenario = Get-Content -LiteralPath $scenarioFile -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($case in @($scenario.cases)) {
            $expectedTurnCount += @($case.turns).Count
        }
    }
    if (@($contractBaseline.contracts).Count -ne $expectedTurnCount -or
        [int]$contractBaseline.passedTurns -ne $expectedTurnCount) {
        throw 'RAG contract baseline turn count does not match the scenario suites.'
    }
    $currentBaselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.ScenarioFile).Hash.ToLowerInvariant()
    $currentStressHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.StressScenarioFile).Hash.ToLowerInvariant()
    if ([string]$contractBaseline.scenarioHashes.baseline -ne $currentBaselineHash -or
        [string]$contractBaseline.scenarioHashes.stress -ne $currentStressHash) {
        throw 'RAG scenario files changed without recording a new contract baseline.'
    }
    Write-Host "[OK] Contract baseline covers $expectedTurnCount turns and matches both scenario files."
}

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
