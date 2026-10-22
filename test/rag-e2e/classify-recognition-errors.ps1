[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$MetricsPath,
    [string]$StabilityPath = '',
    [string]$TaxonomyPath = (Join-Path $PSScriptRoot 'recognition-error-taxonomy.json'),
    [string]$DatasetPath = '',
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

function Get-CategoryId {
    param([Parameter(Mandatory)]$Record)
    if ([bool]$Record.unsafeExecutable) {
        return 'safety_violation'
    }
    if (-not [bool]$Record.intentMatched) {
        $expected = [string]$Record.expectedIntent
        $actual = [string]$Record.actualIntent
        if ($expected.StartsWith('assistant_') -and $actual.StartsWith('assistant_')) {
            return 'conversational_boundary'
        }
        if ([string]$Record.group -eq 'collection' -or $expected.StartsWith('collection_')) {
            return 'collection_semantics'
        }
        return 'intent_routing'
    }
    $operationMatched = Get-OptionalProperty -Object $Record -Name 'operationMatched'
    if ($null -ne $operationMatched -and -not [bool]$operationMatched) {
        if ([string]$Record.group -eq 'safety') {
            return 'safety_semantics'
        }
        return 'operation_semantics'
    }
    return 'parameter_contract'
}

$resolvedMetricsPath = (Resolve-Path -LiteralPath $MetricsPath).Path
$resolvedTaxonomyPath = (Resolve-Path -LiteralPath $TaxonomyPath).Path
$metrics = Get-Content -LiteralPath $resolvedMetricsPath -Raw -Encoding UTF8 | ConvertFrom-Json
$taxonomy = Get-Content -LiteralPath $resolvedTaxonomyPath -Raw -Encoding UTF8 | ConvertFrom-Json

if ([string]$metrics.schemaVersion -ne 'rag_recognition_metrics_v1' -or
    [string]$metrics.split -notin @('development', 'development-candidate', 'holdout')) {
    throw 'Unsupported recognition metrics schema or split.'
}
if ([bool]$metrics.sourceDirty) {
    throw 'Recognition error analysis requires a clean source report.'
}
if ([int]$metrics.missingResponseCount -ne 0 -or [int]$metrics.infrastructureFailures -ne 0) {
    throw 'Availability failures must be resolved before recognition errors are classified.'
}

$selectedDatasetPath = if (-not [string]::IsNullOrWhiteSpace($DatasetPath)) {
    $DatasetPath
} else {
    switch ([string]$metrics.split) {
        'development' { Join-Path $PSScriptRoot 'recognition-dev.json' }
        'development-candidate' { Join-Path $PSScriptRoot 'recognition-candidate.json' }
        'holdout' { Join-Path $PSScriptRoot 'recognition-holdout.json' }
    }
}
$resolvedDatasetPath = (Resolve-Path -LiteralPath $selectedDatasetPath).Path
$datasetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedDatasetPath).Hash.ToLowerInvariant()
if ([string]$metrics.datasetHash -ne $datasetHash) {
    throw 'Recognition metrics do not match the current labeled dataset.'
}

if ([string]$taxonomy.schemaVersion -ne 'rag_recognition_error_taxonomy_v1') {
    throw 'Unsupported recognition error taxonomy schema.'
}
$categoryMap = @{}
foreach ($category in @($taxonomy.categories)) {
    $id = [string]$category.id
    if ([string]::IsNullOrWhiteSpace($id) -or $categoryMap.ContainsKey($id)) {
        throw "Recognition error taxonomy contains a duplicate or empty category: $id"
    }
    $categoryMap[$id] = $category
}

$categoryCounts = [ordered]@{}
foreach ($id in @($categoryMap.Keys | Sort-Object)) {
    $categoryCounts[$id] = 0
}
$errors = [System.Collections.Generic.List[object]]::new()
foreach ($record in @($metrics.records | Where-Object { -not [bool]$_.exactMatched })) {
    $categoryId = Get-CategoryId -Record $record
    if (-not $categoryMap.ContainsKey($categoryId)) {
        throw "Recognition error category is absent from the taxonomy: $categoryId"
    }
    $categoryCounts[$categoryId]++
    $errors.Add([pscustomobject][ordered]@{
        key = [string]$record.key
        group = [string]$record.group
        difficulty = [string]$record.difficulty
        category = $categoryId
        severity = [string]$categoryMap[$categoryId].severity
        expectedIntent = [string]$record.expectedIntent
        actualIntent = [string]$record.actualIntent
        expectedOperations = @($record.expectedOperations | ForEach-Object { [string]$_ })
        actualOperation = [string]$record.actualOperation
        provider = [string]$record.provider
        recognitionPath = [string]$record.recognitionPath
        unsafeExecutable = [bool]$record.unsafeExecutable
    })
}

$stabilitySummary = $null
$variances = [System.Collections.Generic.List[object]]::new()
if (-not [string]::IsNullOrWhiteSpace($StabilityPath)) {
    $resolvedStabilityPath = (Resolve-Path -LiteralPath $StabilityPath).Path
    $stability = Get-Content -LiteralPath $resolvedStabilityPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$stability.schemaVersion -ne 'rag_recognition_stability_v1' -or
        [int]$stability.runCount -lt 3) {
        throw 'Unsupported recognition stability report.'
    }
    foreach ($case in @($stability.cases | Where-Object {
                -not [bool]$_.intentStable -or -not [bool]$_.operationStable -or
                -not [bool]$_.pathStable -or -not [bool]$_.exactStable
            })) {
        $categoryId = if (-not [bool]$case.intentStable -or
            -not [bool]$case.operationStable -or
            -not [bool]$case.exactStable) {
            'sampling_outcome_variance'
        } else {
            'arbitration_path_variance'
        }
        $categoryCounts[$categoryId]++
        $variances.Add([pscustomobject][ordered]@{
            key = [string]$case.key
            group = [string]$case.group
            category = $categoryId
            severity = [string]$categoryMap[$categoryId].severity
            intentStable = [bool]$case.intentStable
            operationStable = [bool]$case.operationStable
            pathStable = [bool]$case.pathStable
            exactStable = [bool]$case.exactStable
            exactCorrectRuns = [int]$case.exactCorrectRuns
            recognitionPaths = @($case.recognitionPaths)
        })
    }
    $stabilitySummary = [pscustomobject][ordered]@{
        sourceRevision = [string]$stability.sourceRevision
        runCount = [int]$stability.runCount
        datasetHash = [string]$stability.datasetHash
        totalTurns = [int]$stability.totalTurns
        varianceCount = $variances.Count
    }
}

$analysis = [pscustomobject][ordered]@{
    schemaVersion = 'rag_recognition_error_analysis_v1'
    generatedAt = [DateTimeOffset]::Now.ToString('o')
    taxonomyVersion = [string]$taxonomy.version
    taxonomyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedTaxonomyPath).Hash.ToLowerInvariant()
    metrics = [pscustomobject][ordered]@{
        split = [string]$metrics.split
        reportRunId = [string]$metrics.reportRunId
        sourceRevision = [string]$metrics.sourceRevision
        datasetVersion = [string]$metrics.datasetVersion
        datasetHash = [string]$metrics.datasetHash
        totalTurns = [int]$metrics.totalTurns
        exactContractCorrect = [int]$metrics.exactContractCorrect
        unsafeExecutableCount = [int]$metrics.unsafeExecutableCount
    }
    stability = $stabilitySummary
    accuracyErrorCount = $errors.Count
    varianceCount = $variances.Count
    categoryCounts = $categoryCounts
    errors = @($errors)
    variances = @($variances)
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $outputDirectory = Split-Path -Parent $resolvedMetricsPath
    $OutputPath = Join-Path $outputDirectory "rag-recognition-error-analysis-$($metrics.split)-$($metrics.reportRunId).json"
}
$resolvedOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.File]::WriteAllText(
    $resolvedOutputPath,
    (ConvertTo-Json -InputObject $analysis -Depth 20) + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

$nonZeroCategories = @($categoryCounts.GetEnumerator() |
        Where-Object { [int]$_.Value -gt 0 } |
        Sort-Object Name |
        ForEach-Object { "$($_.Name)=$($_.Value)" })
Write-Host "Recognition error analysis: split=$($metrics.split) errors=$($errors.Count) variances=$($variances.Count) categories=$($nonZeroCategories -join ',')"
Write-Host "Error analysis report: $resolvedOutputPath"
