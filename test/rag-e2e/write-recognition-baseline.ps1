[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DevelopmentMetricsPath,
    [Parameter(Mandatory)][string]$HoldoutMetricsPath,
    [ValidateSet('local-fallback', 'deepseek-online')][string]$Lane = 'local-fallback',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

function Get-MetricsSummary {
    param([Parameter(Mandatory)]$Metrics)
    [pscustomobject][ordered]@{
        datasetVersion = [string]$Metrics.datasetVersion
        reportRunId = [string]$Metrics.reportRunId
        totalTurns = [int]$Metrics.totalTurns
        intentCorrect = [int]$Metrics.intentCorrect
        intentAccuracy = [double]$Metrics.intentAccuracy
        semanticOperationEligible = [int]$Metrics.semanticOperationEligible
        semanticOperationCorrect = [int]$Metrics.semanticOperationCorrect
        semanticOperationAccuracy = [double]$Metrics.semanticOperationAccuracy
        exactContractCorrect = [int]$Metrics.exactContractCorrect
        exactContractAccuracy = [double]$Metrics.exactContractAccuracy
        unsafeExecutableCount = [int]$Metrics.unsafeExecutableCount
        missingResponseCount = [int]$Metrics.missingResponseCount
        infrastructureFailures = [int]$Metrics.infrastructureFailures
        recognitionSources = $Metrics.recognitionSources
        recognitionPaths = $Metrics.recognitionPaths
        byGroup = @($Metrics.byGroup)
        confusion = @($Metrics.confusion)
    }
}

function Assert-Metrics {
    param(
        [Parameter(Mandatory)]$Metrics,
        [Parameter(Mandatory)][string]$ExpectedSplit,
        [Parameter(Mandatory)][string]$ExpectedRevision
    )
    if ([string]$Metrics.schemaVersion -ne 'rag_recognition_metrics_v1' -or
        [string]$Metrics.split -ne $ExpectedSplit -or
        [string]$Metrics.labelPolicyVersion -ne 'rag_recognition_labels_v1') {
        throw "Unsupported recognition metrics for split $ExpectedSplit."
    }
    if ([string]$Metrics.sourceRevision -ne $ExpectedRevision -or [bool]$Metrics.sourceDirty) {
        throw "Recognition metrics are not from the clean baseline revision: $ExpectedSplit"
    }
    if ([int]$Metrics.unsafeExecutableCount -ne 0 -or
        [int]$Metrics.missingResponseCount -ne 0 -or
        [int]$Metrics.infrastructureFailures -ne 0) {
        throw "Recognition metrics failed safety or infrastructure gates: $ExpectedSplit"
    }
    $sources = @($Metrics.recognitionSources.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
    if ($Lane -eq 'local-fallback') {
        if ($sources.Count -ne 1 -or $sources[0].Name -ne 'local_fallback' -or
            [int]$sources[0].Value -ne [int]$Metrics.totalTurns) {
            throw "Local fallback baseline contains another recognition source: $ExpectedSplit"
        }
    } else {
        $paths = @($Metrics.recognitionPaths.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
        $invalidPaths = @($paths | Where-Object {
                $_.Name -notin @('deepseek', 'local_guard', 'local_structure', 'local_policy')
            })
        $onlineEvidence = ($paths |
                Where-Object { $_.Name -in @('deepseek', 'local_policy') } |
                Measure-Object -Property Value -Sum).Sum
        if ($paths.Count -eq 0 -or $invalidPaths.Count -gt 0 -or [int]$onlineEvidence -le 0) {
            throw "DeepSeek online baseline is incomplete or contains unavailable/unclassified responses: $ExpectedSplit"
        }
    }
}

$context = Get-RagE2EContext
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    if ($Lane -ne 'local-fallback') {
        $OutputPath = Join-Path $context.SuiteDirectory 'recognition-baseline.deepseek-online.json'
    } else {
        $OutputPath = $context.RecognitionBaselineFile
    }
}
if (Test-Path -LiteralPath $OutputPath) {
    throw "Recognition baseline already exists and will not be overwritten: $OutputPath"
}

$revision = (& git -C $context.RepositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read the baseline source revision.'
}
$scopedChanges = @(Get-RagE2ESourceChanges -Context $context)
if ($scopedChanges.Count -gt 0) {
    throw 'RAG recognition source or evaluation files are not clean; refusing to record a baseline.'
}

$development = Get-Content -LiteralPath (Resolve-Path $DevelopmentMetricsPath) -Raw -Encoding UTF8 | ConvertFrom-Json
$holdout = Get-Content -LiteralPath (Resolve-Path $HoldoutMetricsPath) -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Metrics -Metrics $development -ExpectedSplit 'development' -ExpectedRevision $revision
Assert-Metrics -Metrics $holdout -ExpectedSplit 'holdout' -ExpectedRevision $revision
if ([string]$development.labelPolicyVersion -ne [string]$holdout.labelPolicyVersion) {
    throw 'Development and holdout metrics use different label policies.'
}
$developmentDataset = Get-Content -LiteralPath $context.RecognitionDevFile -Raw -Encoding UTF8 | ConvertFrom-Json
$holdoutDataset = Get-Content -LiteralPath $context.RecognitionHoldoutFile -Raw -Encoding UTF8 | ConvertFrom-Json
$developmentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
$holdoutHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionHoldoutFile).Hash.ToLowerInvariant()
if ([string]$development.datasetVersion -ne [string]$developmentDataset.version -or
    [string]$holdout.datasetVersion -ne [string]$holdoutDataset.version -or
    [string]$development.datasetHash -ne $developmentHash -or
    [string]$holdout.datasetHash -ne $holdoutHash) {
    throw 'Recognition metrics do not match the current labeled datasets.'
}

$baseline = [pscustomobject][ordered]@{
    schemaVersion = 'rag_recognition_baseline_v1'
    lane = $Lane
    sourceRevision = $revision
    labelPolicyVersion = [string]$development.labelPolicyVersion
    datasetHashes = [ordered]@{
        development = $developmentHash
        holdout = $holdoutHash
    }
    development = Get-MetricsSummary -Metrics $development
    holdout = Get-MetricsSummary -Metrics $holdout
}
$json = ConvertTo-Json -InputObject $baseline -Depth 20
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($OutputPath),
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Host "[OK] Recognition baseline recorded: lane=$Lane revision=$revision"
Write-Host "Baseline: $OutputPath"
