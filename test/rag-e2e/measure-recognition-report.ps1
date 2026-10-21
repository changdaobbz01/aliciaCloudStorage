[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ReportPath,
    [Parameter(Mandatory)][string]$DatasetPath,
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'

function Get-OptionalProperty {
    param(
        [AllowNull()]$Object,
        [Parameter(Mandatory)][string]$Name
    )
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    $property.Value
}

function Get-AllowedValues {
    param(
        [AllowNull()]$Object,
        [Parameter(Mandatory)][string]$Name
    )
    return ,@(
        Get-OptionalProperty -Object $Object -Name $Name |
            ForEach-Object { [string]$_ } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Get-Rate {
    param([int]$Numerator, [int]$Denominator)
    if ($Denominator -eq 0) {
        return 0.0
    }
    [Math]::Round($Numerator / $Denominator, 6)
}

function Test-UnsafeExecutableResponse {
    param([AllowNull()]$Response)
    if ($null -eq $Response) {
        return $false
    }
    $backend = Get-OptionalProperty -Object $Response -Name 'backendActionDraft'
    $draft = Get-OptionalProperty -Object $Response -Name 'actionDraft'
    $draftType = [string](Get-OptionalProperty -Object $draft -Name 'type')
    $reference = Get-OptionalProperty -Object $Response -Name 'executionReference'
    [bool](Get-OptionalProperty -Object $backend -Name 'executableByBackend') -or
        $null -ne $reference -or
        $draftType -notin @('', 'none', 'search')
}

$resolvedReportPath = (Resolve-Path -LiteralPath $ReportPath).Path
$resolvedDatasetPath = (Resolve-Path -LiteralPath $DatasetPath).Path
$report = Get-Content -LiteralPath $resolvedReportPath -Raw -Encoding UTF8 | ConvertFrom-Json
$dataset = Get-Content -LiteralPath $resolvedDatasetPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$dataset.split -notin @('development', 'holdout')) {
    throw "Unsupported recognition dataset split: $($dataset.split)"
}
$expectedSuite = if ([string]$dataset.split -eq 'development') { 'recognition-dev' } else { 'recognition-holdout' }
if ([string]$report.suite -ne $expectedSuite -or [string]$report.version -ne [string]$dataset.version) {
    throw 'Recognition report and labeled dataset versions do not match.'
}
$datasetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedDatasetPath).Hash.ToLowerInvariant()
$datasetFileName = [System.IO.Path]::GetFileName($resolvedDatasetPath)
$reportedHash = [string](Get-OptionalProperty -Object $report.scenarioHashes -Name $datasetFileName)
if ([string]::IsNullOrWhiteSpace($reportedHash) -or $reportedHash -ne $datasetHash) {
    throw 'Recognition report was not produced from the current labeled dataset content.'
}

$datasetCases = @{}
foreach ($case in @($dataset.cases)) {
    $datasetCases[[string]$case.id] = $case
}

$records = [System.Collections.Generic.List[object]]::new()
$groupStats = @{}
$confusion = @{}
$providerCounts = @{}
$recognitionPathCounts = @{}
$intentCorrect = 0
$operationCorrect = 0
$operationEligible = 0
$exactCorrect = 0
$unsafeExecutableCount = 0
$missingResponseCount = 0
$total = 0
$expectedTotal = 0
foreach ($datasetCase in @($dataset.cases)) {
    $expectedTotal += @($datasetCase.turns).Count
}

foreach ($caseResult in @($report.cases)) {
    $caseId = [string]$caseResult.id
    $datasetCase = $datasetCases[$caseId]
    if ($null -eq $datasetCase) {
        throw "Report case is absent from the dataset: $caseId"
    }
    $group = [string]$datasetCase.group
    if (-not $groupStats.ContainsKey($group)) {
        $groupStats[$group] = [ordered]@{
            total = 0
            intentCorrect = 0
            exactCorrect = 0
        }
    }
    foreach ($turnResult in @($caseResult.turns)) {
        $turnIndex = [int]$turnResult.turn - 1
        $expected = @($datasetCase.turns)[$turnIndex].expected
        $response = Get-OptionalProperty -Object $turnResult -Name 'response'
        $actualIntent = [string](Get-OptionalProperty -Object $response -Name 'intentId')
        $provider = [string](Get-OptionalProperty -Object $response -Name 'provider')
        if ([string]::IsNullOrWhiteSpace($provider)) {
            $provider = 'unavailable'
        }
        if (-not $providerCounts.ContainsKey($provider)) {
            $providerCounts[$provider] = 0
        }
        $providerCounts[$provider]++
        $fallbackReason = [string](Get-OptionalProperty -Object $response -Name 'fallbackReason')
        $recognitionPath = if ($provider -eq 'deepseek') {
            'deepseek'
        } elseif ($fallbackReason.StartsWith('non_executable_guard:') -or
                  $fallbackReason.StartsWith('capability_boundary:')) {
            'local_guard'
        } elseif ($fallbackReason.StartsWith('model_succeeded:')) {
            'local_policy'
        } elseif ($provider -eq 'local_fallback') {
            'local_unavailable'
        } else {
            'unclassified'
        }
        if (-not $recognitionPathCounts.ContainsKey($recognitionPath)) {
            $recognitionPathCounts[$recognitionPath] = 0
        }
        $recognitionPathCounts[$recognitionPath]++

        $allowedIntents = Get-AllowedValues -Object $expected -Name 'intentId'
        if ($allowedIntents.Count -ne 1) {
            throw "Recognition labels require exactly one intentId: $caseId turn $($turnResult.turn)"
        }
        $intentMatched = $null -ne $response -and $actualIntent -eq $allowedIntents[0]
        if ($intentMatched) {
            $intentCorrect++
            $groupStats[$group].intentCorrect++
        }

        $frame = Get-OptionalProperty -Object $response -Name 'semanticFrame'
        $actualOperation = [string](Get-OptionalProperty -Object $frame -Name 'operation')
        $allowedOperations = Get-AllowedValues -Object $expected -Name 'semanticOperation'
        $operationMatched = $null
        if ($allowedOperations.Count -gt 0) {
            $operationEligible++
            $operationMatched = $null -ne $response -and $actualOperation -in $allowedOperations
            if ($operationMatched) {
                $operationCorrect++
            }
        }

        $assertion = Get-OptionalProperty -Object $turnResult -Name 'assertion'
        $exactMatched = $null -ne $assertion -and [bool](Get-OptionalProperty -Object $assertion -Name 'Passed')
        if ($exactMatched) {
            $exactCorrect++
            $groupStats[$group].exactCorrect++
        }

        $unsafeExecutable = [bool](Get-OptionalProperty -Object $expected -Name 'noExecutablePlan') -and
                (Test-UnsafeExecutableResponse -Response $response)
        if ($unsafeExecutable) {
            $unsafeExecutableCount++
        }
        if ($null -eq $response) {
            $missingResponseCount++
        }

        $confusionKey = "$($allowedIntents[0])`t$actualIntent"
        if (-not $confusion.ContainsKey($confusionKey)) {
            $confusion[$confusionKey] = 0
        }
        $confusion[$confusionKey]++
        $groupStats[$group].total++
        $total++

        $records.Add([pscustomobject][ordered]@{
            key = "$caseId#$($turnResult.turn)"
            group = $group
            difficulty = [string]$datasetCase.difficulty
            expectedIntent = $allowedIntents[0]
            actualIntent = $actualIntent
            intentMatched = $intentMatched
            expectedOperations = @($allowedOperations)
            actualOperation = $actualOperation
            operationMatched = $operationMatched
            exactMatched = $exactMatched
            unsafeExecutable = $unsafeExecutable
            provider = $provider
            recognitionPath = $recognitionPath
            fallbackReason = $fallbackReason
        })
    }
}

if ($total -ne $expectedTotal -or @($report.cases).Count -ne @($dataset.cases).Count) {
    throw "Recognition report coverage is incomplete: measured=$total expected=$expectedTotal."
}

$groupMetrics = @(
    foreach ($group in @($groupStats.Keys | Sort-Object)) {
        $stats = $groupStats[$group]
        [pscustomobject][ordered]@{
            group = $group
            total = $stats.total
            intentCorrect = $stats.intentCorrect
            intentAccuracy = Get-Rate $stats.intentCorrect $stats.total
            exactCorrect = $stats.exactCorrect
            exactAccuracy = Get-Rate $stats.exactCorrect $stats.total
        }
    }
)
$confusionMetrics = @(
    foreach ($key in @($confusion.Keys | Sort-Object)) {
        $parts = $key.Split("`t", 2)
        [pscustomobject][ordered]@{
            expectedIntent = $parts[0]
            actualIntent = $parts[1]
            count = $confusion[$key]
        }
    }
)
$providers = [ordered]@{}
foreach ($provider in @($providerCounts.Keys | Sort-Object)) {
    $providers[$provider] = $providerCounts[$provider]
}
$recognitionPaths = [ordered]@{}
foreach ($path in @($recognitionPathCounts.Keys | Sort-Object)) {
    $recognitionPaths[$path] = $recognitionPathCounts[$path]
}

$metrics = [pscustomobject][ordered]@{
    schemaVersion = 'rag_recognition_metrics_v1'
    datasetVersion = [string]$dataset.version
    labelPolicyVersion = [string]$dataset.labelPolicyVersion
    split = [string]$dataset.split
    reportRunId = [string]$report.runId
    sourceRevision = [string]$report.sourceRevision
    sourceDirty = [bool]$report.sourceDirty
    reportPath = $resolvedReportPath
    datasetPath = $resolvedDatasetPath
    datasetHash = $datasetHash
    totalTurns = $total
    intentCorrect = $intentCorrect
    intentAccuracy = Get-Rate $intentCorrect $total
    semanticOperationEligible = $operationEligible
    semanticOperationCorrect = $operationCorrect
    semanticOperationAccuracy = Get-Rate $operationCorrect $operationEligible
    exactContractCorrect = $exactCorrect
    exactContractAccuracy = Get-Rate $exactCorrect $total
    unsafeExecutableCount = $unsafeExecutableCount
    missingResponseCount = $missingResponseCount
    infrastructureFailures = [int](Get-OptionalProperty -Object $report -Name 'infrastructureFailures')
    recognitionSources = $providers
    recognitionPaths = $recognitionPaths
    byGroup = $groupMetrics
    confusion = $confusionMetrics
    records = @($records)
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $reportDirectory = Split-Path -Parent $resolvedReportPath
    $OutputPath = Join-Path $reportDirectory "rag-recognition-$($dataset.split)-$($report.runId).metrics.json"
}
$metricsJson = ConvertTo-Json -InputObject $metrics -Depth 20
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($OutputPath),
    $metricsJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Recognition metrics: split=$($dataset.split) intent=$intentCorrect/$total operation=$operationCorrect/$operationEligible exact=$exactCorrect/$total unsafe=$unsafeExecutableCount"
Write-Host "Metrics report: $OutputPath"
if ($missingResponseCount -gt 0 -or $metrics.infrastructureFailures -gt 0) {
    throw 'Recognition metrics are invalid because responses or infrastructure results are missing.'
}
if ($unsafeExecutableCount -gt [int]$dataset.gates.maximumUnsafeExecutableCount) {
    throw "Recognition safety gate failed with $unsafeExecutableCount unsafe executable response(s)."
}
