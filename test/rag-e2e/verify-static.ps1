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
foreach ($recognitionFile in @(
        $context.RecognitionDevFile,
        $context.RecognitionCandidateFile,
        $context.RecognitionHoldoutFile
    )) {
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
if ([int]$recognitionCounts.'development-candidate' -lt 1) {
    throw 'Recognition candidate dataset is empty.'
}
Write-Host "[OK] Recognition datasets are valid: development=$($recognitionCounts.development), candidate=$($recognitionCounts.'development-candidate'), holdout=$($recognitionCounts.holdout)."

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

if (Test-Path -LiteralPath $context.OnlineRecognitionBaselineFile -PathType Leaf) {
    $onlineBaseline = Get-Content -LiteralPath $context.OnlineRecognitionBaselineFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$onlineBaseline.schemaVersion -ne 'rag_recognition_baseline_v1' -or
        [string]$onlineBaseline.lane -ne 'deepseek-online' -or
        [string]$onlineBaseline.labelPolicyVersion -ne 'rag_recognition_labels_v1') {
        throw 'Online recognition baseline uses an unsupported schema or lane.'
    }
    $devHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
    $holdoutHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionHoldoutFile).Hash.ToLowerInvariant()
    if ([string]$onlineBaseline.datasetHashes.development -ne $devHash -or
        [string]$onlineBaseline.datasetHashes.holdout -ne $holdoutHash) {
        throw 'Recognition datasets changed without recording a new DeepSeek online baseline.'
    }
    foreach ($split in @('development', 'holdout')) {
        $metrics = $onlineBaseline.$split
        $expectedCount = [int]$recognitionCounts[$split]
        if ([int]$metrics.totalTurns -ne $expectedCount -or
            [int]$metrics.unsafeExecutableCount -ne 0 -or
            [int]$metrics.missingResponseCount -ne 0 -or
            [int]$metrics.infrastructureFailures -ne 0) {
            throw "Online recognition baseline coverage or safety state is invalid: $split"
        }
        $paths = @($metrics.recognitionPaths.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
        $invalidPaths = @($paths | Where-Object {
                $_.Name -notin @('deepseek', 'local_guard', 'local_structure', 'local_policy')
            })
        $onlineEvidence = ($paths |
                Where-Object { $_.Name -in @('deepseek', 'local_policy') } |
                Measure-Object -Property Value -Sum).Sum
        if ($paths.Count -eq 0 -or $invalidPaths.Count -gt 0 -or [int]$onlineEvidence -le 0) {
            throw "Online recognition baseline lacks valid model evidence: $split"
        }
    }
    Write-Host '[OK] DeepSeek online recognition baseline matches both labeled datasets and contains no unavailable path.'
}

if (Test-Path -LiteralPath $context.OnlineRecognitionStabilityFile -PathType Leaf) {
    $stability = Get-Content -LiteralPath $context.OnlineRecognitionStabilityFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $developmentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
    if ([string]$stability.schemaVersion -ne 'rag_recognition_stability_v1' -or
        [string]$stability.split -ne 'development' -or
        [int]$stability.runCount -lt 3 -or
        [string]::IsNullOrWhiteSpace([string]$stability.sourceRevision) -or
        [string]$stability.datasetHash -ne $developmentHash -or
        [int]$stability.totalTurns -ne [int]$recognitionCounts.development) {
        throw 'DeepSeek online stability baseline uses an unsupported source or dataset.'
    }
    if ([int]$stability.unsafeExecutableCount -ne 0 -or
        [int]$stability.missingResponseCount -ne 0 -or
        [int]$stability.infrastructureFailures -ne 0 -or
        [int]$stability.unavailablePathCount -ne 0 -or
        [int]$stability.intentDriftCount -ne 0 -or
        [int]$stability.operationDriftCount -ne 0 -or
        [int]$stability.exactOutcomeDriftCount -ne 0 -or
        [int]$stability.alwaysExactCorrectCount -ne [int]$stability.totalTurns -or
        [int]$stability.intermittentExactCount -ne 0 -or
        [int]$stability.alwaysExactIncorrectCount -ne 0) {
        throw 'DeepSeek online stability baseline contains a safety, availability, or semantic outcome regression.'
    }
    if (@($stability.runs).Count -ne [int]$stability.runCount -or
        @($stability.cases).Count -ne [int]$stability.totalTurns) {
        throw 'DeepSeek online stability baseline coverage is incomplete.'
    }
    $runIds = @($stability.runs | ForEach-Object { [string]$_.runId })
    if (@($runIds | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0 -or
        @($runIds | Select-Object -Unique).Count -ne $runIds.Count) {
        throw 'DeepSeek online stability baseline run IDs are incomplete or duplicated.'
    }
    foreach ($run in @($stability.runs)) {
        $invalidPaths = @($run.recognitionPaths.PSObject.Properties | Where-Object {
                [int]$_.Value -gt 0 -and $_.Name -notin @('deepseek', 'local_guard', 'local_structure', 'local_policy')
            })
        if ($invalidPaths.Count -gt 0 -or
            [int]$run.intentCorrect -ne [int]$stability.totalTurns -or
            [int]$run.exactContractCorrect -ne [int]$stability.totalTurns) {
            throw "DeepSeek online stability baseline contains an invalid run summary: $($run.runId)"
        }
    }
    $intentDrifts = @($stability.cases | Where-Object { -not [bool]$_.intentStable }).Count
    $operationDrifts = @($stability.cases | Where-Object { -not [bool]$_.operationStable }).Count
    $pathDrifts = @($stability.cases | Where-Object { -not [bool]$_.pathStable }).Count
    $exactDrifts = @($stability.cases | Where-Object { -not [bool]$_.exactStable }).Count
    $fullyStable = @($stability.cases | Where-Object {
            [bool]$_.intentStable -and [bool]$_.operationStable -and
            [bool]$_.pathStable -and [bool]$_.exactStable
        }).Count
    if ($intentDrifts -ne [int]$stability.intentDriftCount -or
        $operationDrifts -ne [int]$stability.operationDriftCount -or
        $pathDrifts -ne [int]$stability.pathDriftCount -or
        $exactDrifts -ne [int]$stability.exactOutcomeDriftCount -or
        $fullyStable -ne [int]$stability.fullyStableCount) {
        throw 'DeepSeek online stability summary does not match its case records.'
    }
    Write-Host "[OK] DeepSeek online stability baseline covers $($stability.runCount) clean development runs without semantic outcome drift."
}

$errorTaxonomy = Get-Content -LiteralPath $context.RecognitionErrorTaxonomyFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$errorTaxonomy.schemaVersion -ne 'rag_recognition_error_taxonomy_v1' -or
    [string]::IsNullOrWhiteSpace([string]$errorTaxonomy.version)) {
    throw 'Recognition error taxonomy uses an unsupported schema or version.'
}
$requiredErrorCategories = @(
    'availability_failure',
    'safety_violation',
    'safety_semantics',
    'parameter_contract',
    'operation_semantics',
    'collection_semantics',
    'conversational_boundary',
    'intent_routing',
    'sampling_outcome_variance',
    'arbitration_path_variance'
)
$errorCategoryMap = @{}
foreach ($category in @($errorTaxonomy.categories)) {
    $categoryId = [string]$category.id
    if ([string]::IsNullOrWhiteSpace($categoryId) -or $errorCategoryMap.ContainsKey($categoryId)) {
        throw "Recognition error taxonomy contains a duplicate or empty category: $categoryId"
    }
    if ([string]$category.severity -notin @('blocker', 'high', 'medium', 'info') -or
        [string]::IsNullOrWhiteSpace([string]$category.description) -or
        [string]::IsNullOrWhiteSpace([string]$category.policy)) {
        throw "Recognition error taxonomy category is incomplete: $categoryId"
    }
    $errorCategoryMap[$categoryId] = $category
}
foreach ($requiredCategory in $requiredErrorCategories) {
    if (-not $errorCategoryMap.ContainsKey($requiredCategory)) {
        throw "Recognition error taxonomy is missing a required category: $requiredCategory"
    }
}
Write-Host "[OK] Recognition error taxonomy defines $($errorCategoryMap.Count) complete categories."

if (Test-Path -LiteralPath $context.OnlineRecognitionErrorBaselineFile -PathType Leaf) {
    $errorBaseline = Get-Content -LiteralPath $context.OnlineRecognitionErrorBaselineFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $taxonomyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionErrorTaxonomyFile).Hash.ToLowerInvariant()
    $developmentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
    $holdoutHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionHoldoutFile).Hash.ToLowerInvariant()
    if ([string]$errorBaseline.schemaVersion -ne 'rag_recognition_error_analysis_v1' -or
        [string]$errorBaseline.taxonomyVersion -ne [string]$errorTaxonomy.version -or
        [string]$errorBaseline.taxonomyHash -ne $taxonomyHash) {
        throw 'Online recognition error baseline does not match the current taxonomy.'
    }
    if ([string]$errorBaseline.metrics.split -ne 'holdout' -or
        [string]$errorBaseline.metrics.datasetHash -ne $holdoutHash -or
        [int]$errorBaseline.metrics.totalTurns -ne [int]$recognitionCounts.holdout -or
        [int]$errorBaseline.metrics.unsafeExecutableCount -ne 0 -or
        [string]::IsNullOrWhiteSpace([string]$errorBaseline.metrics.sourceRevision) -or
        [string]::IsNullOrWhiteSpace([string]$errorBaseline.metrics.reportRunId)) {
        throw 'Online recognition error baseline uses an invalid holdout source or safety state.'
    }
    if (-not (Test-Path -LiteralPath $context.OnlineRecognitionBaselineFile -PathType Leaf) -or
        [string]$errorBaseline.metrics.sourceRevision -ne [string]$onlineBaseline.sourceRevision -or
        [string]$errorBaseline.metrics.reportRunId -ne [string]$onlineBaseline.holdout.reportRunId -or
        [string]$errorBaseline.metrics.datasetVersion -ne [string]$onlineBaseline.holdout.datasetVersion -or
        [int]$errorBaseline.metrics.exactContractCorrect -ne [int]$onlineBaseline.holdout.exactContractCorrect) {
        throw 'Online recognition error baseline does not match the tracked online holdout baseline.'
    }
    if ($null -eq $errorBaseline.stability -or
        [string]$errorBaseline.stability.datasetHash -ne $developmentHash -or
        [int]$errorBaseline.stability.totalTurns -ne [int]$recognitionCounts.development -or
        [int]$errorBaseline.stability.runCount -lt 3 -or
        [string]::IsNullOrWhiteSpace([string]$errorBaseline.stability.sourceRevision)) {
        throw 'Online recognition error baseline uses an invalid development stability source.'
    }
    if (Test-Path -LiteralPath $context.OnlineRecognitionStabilityFile -PathType Leaf) {
        if ([string]$errorBaseline.stability.sourceRevision -ne [string]$stability.sourceRevision -or
            [int]$errorBaseline.stability.runCount -ne [int]$stability.runCount -or
            [int]$errorBaseline.stability.varianceCount -ne [int]$errorBaseline.varianceCount) {
            throw 'Online recognition error baseline does not match the tracked stability baseline.'
        }
    }

    $errors = @($errorBaseline.errors)
    $variances = @($errorBaseline.variances)
    if ([int]$errorBaseline.accuracyErrorCount -ne $errors.Count -or
        [int]$errorBaseline.accuracyErrorCount -ne
            ([int]$errorBaseline.metrics.totalTurns - [int]$errorBaseline.metrics.exactContractCorrect) -or
        [int]$errorBaseline.varianceCount -ne $variances.Count) {
        throw 'Online recognition error baseline summary does not match its records.'
    }
    $observedCategoryCounts = @{}
    foreach ($categoryId in $errorCategoryMap.Keys) {
        $observedCategoryCounts[$categoryId] = 0
    }
    $recordKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($record in @($errors + $variances)) {
        $recordCategory = [string]$record.category
        $recordKey = [string]$record.key
        if (-not $errorCategoryMap.ContainsKey($recordCategory) -or
            [string]::IsNullOrWhiteSpace($recordKey) -or
            -not $recordKeys.Add($recordKey) -or
            $null -ne $record.PSObject.Properties['message'] -or
            [string]$record.severity -ne [string]$errorCategoryMap[$recordCategory].severity) {
            throw "Online recognition error baseline contains an invalid record: $recordCategory/$recordKey"
        }
        $observedCategoryCounts[$recordCategory]++
    }
    $baselineCategoryProperties = @($errorBaseline.categoryCounts.PSObject.Properties)
    if ($baselineCategoryProperties.Count -ne $errorCategoryMap.Count) {
        throw 'Online recognition error baseline category coverage is incomplete.'
    }
    foreach ($property in $baselineCategoryProperties) {
        if (-not $errorCategoryMap.ContainsKey($property.Name) -or
            [int]$property.Value -ne [int]$observedCategoryCounts[$property.Name]) {
            throw "Online recognition error baseline category count is invalid: $($property.Name)"
        }
    }
    foreach ($blockerCategory in @('availability_failure', 'safety_violation')) {
        if ([int]$observedCategoryCounts[$blockerCategory] -ne 0) {
            throw "Online recognition error baseline contains a blocker category: $blockerCategory"
        }
    }
    Write-Host "[OK] Online recognition error baseline classifies $($errors.Count) holdout errors and $($variances.Count) stability variance without blockers."
} else {
    throw 'Online recognition error baseline is missing.'
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
