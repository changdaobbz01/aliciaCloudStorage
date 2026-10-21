[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateCount(3, 50)]
    [string[]]$MetricsPath,

    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

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

function Get-Rate {
    param([int]$Numerator, [int]$Denominator)
    if ($Denominator -eq 0) {
        return 0.0
    }
    [Math]::Round($Numerator / $Denominator, 6)
}

function Get-ValueCounts {
    param([object[]]$Values)
    @(
        $Values |
            Group-Object { if ($null -eq $_) { '<null>' } else { [string]$_ } } |
            Sort-Object Name |
            ForEach-Object {
                [pscustomobject][ordered]@{
                    value = [string]$_.Name
                    count = [int]$_.Count
                }
            }
    )
}

$resolvedPaths = @($MetricsPath | ForEach-Object {
        (Resolve-Path -LiteralPath $_).Path
    })
if (@($resolvedPaths | Select-Object -Unique).Count -ne $resolvedPaths.Count) {
    throw 'Recognition stability input paths must be unique.'
}

$runs = [System.Collections.Generic.List[object]]::new()
$runIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$reference = $null
$referenceRecords = $null
$allowedPaths = @('deepseek', 'local_guard', 'local_structure', 'local_policy')

foreach ($path in $resolvedPaths) {
    $metrics = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$metrics.schemaVersion -ne 'rag_recognition_metrics_v1' -or
        [string]$metrics.split -ne 'development') {
        throw "Stability measurement accepts only development recognition metrics: $path"
    }
    if ([bool]$metrics.sourceDirty) {
        throw "Stability metrics must come from a clean source revision: $path"
    }
    $runId = [string]$metrics.reportRunId
    if ([string]::IsNullOrWhiteSpace($runId) -or -not $runIds.Add($runId)) {
        throw "Recognition stability run IDs must be present and unique: $runId"
    }
    if ([int]$metrics.unsafeExecutableCount -ne 0 -or
        [int]$metrics.missingResponseCount -ne 0 -or
        [int]$metrics.infrastructureFailures -ne 0) {
        throw "Recognition stability input failed a safety or infrastructure gate: $runId"
    }

    $paths = @($metrics.recognitionPaths.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
    $invalidPaths = @($paths | Where-Object { $_.Name -notin $allowedPaths })
    $onlineEvidence = ($paths |
            Where-Object { $_.Name -in @('deepseek', 'local_policy') } |
            Measure-Object -Property Value -Sum).Sum
    if ($paths.Count -eq 0 -or $invalidPaths.Count -gt 0 -or [int]$onlineEvidence -le 0) {
        throw "Recognition stability input lacks valid online model evidence: $runId"
    }

    $records = @($metrics.records)
    if ($records.Count -ne [int]$metrics.totalTurns) {
        throw "Recognition stability input has incomplete record coverage: $runId"
    }
    $recordsByKey = @{}
    foreach ($record in $records) {
        $key = [string]$record.key
        if ([string]::IsNullOrWhiteSpace($key) -or $recordsByKey.ContainsKey($key)) {
            throw "Recognition stability input contains a duplicate or empty key: $runId"
        }
        $recordsByKey[$key] = $record
    }

    if ($null -eq $reference) {
        $reference = $metrics
        $referenceRecords = $recordsByKey
    } else {
        if ([string]$metrics.sourceRevision -ne [string]$reference.sourceRevision -or
            [string]$metrics.datasetVersion -ne [string]$reference.datasetVersion -or
            [string]$metrics.labelPolicyVersion -ne [string]$reference.labelPolicyVersion -or
            [string]$metrics.datasetHash -ne [string]$reference.datasetHash -or
            [int]$metrics.totalTurns -ne [int]$reference.totalTurns) {
            throw "Recognition stability inputs do not share one source and dataset: $runId"
        }
        $missingKeys = @($referenceRecords.Keys | Where-Object { -not $recordsByKey.ContainsKey($_) })
        $extraKeys = @($recordsByKey.Keys | Where-Object { -not $referenceRecords.ContainsKey($_) })
        if ($missingKeys.Count -gt 0 -or $extraKeys.Count -gt 0) {
            throw "Recognition stability record keys differ between runs: $runId"
        }
    }

    $runs.Add([pscustomobject][ordered]@{
        runId = $runId
        path = $path
        metrics = $metrics
        recordsByKey = $recordsByKey
    })
}

$caseResults = [System.Collections.Generic.List[object]]::new()
$stableIntentCount = 0
$stableOperationCount = 0
$stablePathCount = 0
$stableExactCount = 0
$fullyStableCount = 0
$alwaysExactCorrectCount = 0
$intermittentExactCount = 0
$alwaysExactIncorrectCount = 0

foreach ($key in @($referenceRecords.Keys | Sort-Object)) {
    $records = @($runs | ForEach-Object { $_.recordsByKey[$key] })
    $expectedIntents = @($records | ForEach-Object { [string]$_.expectedIntent } | Select-Object -Unique)
    $groups = @($records | ForEach-Object { [string]$_.group } | Select-Object -Unique)
    if ($expectedIntents.Count -ne 1 -or $groups.Count -ne 1) {
        throw "Recognition labels changed between stability runs: $key"
    }

    $intentValues = Get-ValueCounts @($records | ForEach-Object { [string]$_.actualIntent })
    $operationValues = Get-ValueCounts @($records | ForEach-Object { [string]$_.actualOperation })
    $pathValues = Get-ValueCounts @($records | ForEach-Object { [string]$_.recognitionPath })
    $providerValues = Get-ValueCounts @($records | ForEach-Object { [string]$_.provider })
    $exactValues = @($records | ForEach-Object { [bool]$_.exactMatched })
    $exactCorrectRuns = @($exactValues | Where-Object { $_ }).Count

    $intentStable = $intentValues.Count -eq 1
    $operationStable = $operationValues.Count -eq 1
    $pathStable = $pathValues.Count -eq 1
    $exactStable = @($exactValues | Select-Object -Unique).Count -eq 1
    if ($intentStable) { $stableIntentCount++ }
    if ($operationStable) { $stableOperationCount++ }
    if ($pathStable) { $stablePathCount++ }
    if ($exactStable) { $stableExactCount++ }
    if ($intentStable -and $operationStable -and $pathStable -and $exactStable) {
        $fullyStableCount++
    }
    if ($exactCorrectRuns -eq $runs.Count) {
        $alwaysExactCorrectCount++
    } elseif ($exactCorrectRuns -eq 0) {
        $alwaysExactIncorrectCount++
    } else {
        $intermittentExactCount++
    }

    $caseResults.Add([pscustomobject][ordered]@{
        key = $key
        group = $groups[0]
        expectedIntent = $expectedIntents[0]
        exactCorrectRuns = $exactCorrectRuns
        exactAccuracy = Get-Rate $exactCorrectRuns $runs.Count
        intentStable = $intentStable
        operationStable = $operationStable
        pathStable = $pathStable
        exactStable = $exactStable
        actualIntents = $intentValues
        actualOperations = $operationValues
        recognitionPaths = $pathValues
        providers = $providerValues
    })
}

$runSummaries = @(
    $runs | ForEach-Object {
        $metrics = $_.metrics
        [pscustomobject][ordered]@{
            runId = [string]$metrics.reportRunId
            intentCorrect = [int]$metrics.intentCorrect
            semanticOperationCorrect = [int]$metrics.semanticOperationCorrect
            exactContractCorrect = [int]$metrics.exactContractCorrect
            recognitionSources = $metrics.recognitionSources
            recognitionPaths = $metrics.recognitionPaths
        }
    }
)
$totalTurns = [int]$reference.totalTurns
$stability = [pscustomobject][ordered]@{
    schemaVersion = 'rag_recognition_stability_v1'
    generatedAt = [DateTimeOffset]::Now.ToString('o')
    split = 'development'
    runCount = $runs.Count
    sourceRevision = [string]$reference.sourceRevision
    datasetVersion = [string]$reference.datasetVersion
    labelPolicyVersion = [string]$reference.labelPolicyVersion
    datasetHash = [string]$reference.datasetHash
    totalTurns = $totalTurns
    stableIntentCount = $stableIntentCount
    intentDriftCount = $totalTurns - $stableIntentCount
    stableOperationCount = $stableOperationCount
    operationDriftCount = $totalTurns - $stableOperationCount
    stablePathCount = $stablePathCount
    pathDriftCount = $totalTurns - $stablePathCount
    stableExactCount = $stableExactCount
    exactOutcomeDriftCount = $totalTurns - $stableExactCount
    fullyStableCount = $fullyStableCount
    alwaysExactCorrectCount = $alwaysExactCorrectCount
    intermittentExactCount = $intermittentExactCount
    alwaysExactIncorrectCount = $alwaysExactIncorrectCount
    runs = $runSummaries
    cases = @($caseResults)
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $resultsDirectory = Split-Path -Parent $resolvedPaths[0]
    $stamp = [DateTimeOffset]::Now.ToString('yyyyMMddHHmmss')
    $OutputPath = Join-Path $resultsDirectory "rag-recognition-development-stability-$stamp.json"
}
$resolvedOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.File]::WriteAllText(
    $resolvedOutputPath,
    (ConvertTo-Json -InputObject $stability -Depth 20) + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Recognition stability: runs=$($runs.Count) turns=$totalTurns fullyStable=$fullyStableCount intentDrift=$($totalTurns - $stableIntentCount) operationDrift=$($totalTurns - $stableOperationCount) pathDrift=$($totalTurns - $stablePathCount) exactOutcomeDrift=$($totalTurns - $stableExactCount)"
Write-Host "Exact outcomes: alwaysCorrect=$alwaysExactCorrectCount intermittent=$intermittentExactCount alwaysIncorrect=$alwaysExactIncorrectCount"
Write-Host "Stability report: $resolvedOutputPath"
