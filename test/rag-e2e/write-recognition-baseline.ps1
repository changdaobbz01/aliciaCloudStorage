[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DevelopmentMetricsPath,
    [string]$HoldoutMetricsPath = '',
    [string]$CarryForwardBaselinePath = '',
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
        $invalidSources = @($sources | Where-Object { $_.Name -notin @('local_fallback', 'local_context') })
        $sourceTotal = ($sources | Measure-Object -Property Value -Sum).Sum
        $localContext = @($sources | Where-Object { $_.Name -eq 'local_context' })
        $localContextCount = if ($localContext.Count -eq 0) { 0 } else { [int]$localContext[0].Value }
        $localStructureProperty = $Metrics.recognitionPaths.PSObject.Properties['local_structure']
        $localStructureCount = if ($null -eq $localStructureProperty) { 0 } else { [int]$localStructureProperty.Value }
        if ($sources.Count -eq 0 -or $invalidSources.Count -gt 0 -or
            [int]$sourceTotal -ne [int]$Metrics.totalTurns -or
            $localContextCount -gt $localStructureCount) {
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

function Assert-CarriedSummary {
    param(
        [Parameter(Mandatory)]$Summary,
        [Parameter(Mandatory)][string]$ExpectedLane
    )
    if ([int]$Summary.totalTurns -le 0 -or
        [int]$Summary.unsafeExecutableCount -ne 0 -or
        [int]$Summary.missingResponseCount -ne 0 -or
        [int]$Summary.infrastructureFailures -ne 0) {
        throw 'Carried holdout summary failed coverage, safety, or infrastructure gates.'
    }
    if ($ExpectedLane -eq 'local-fallback') {
        $sources = @($Summary.recognitionSources.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
        $invalidSources = @($sources | Where-Object { $_.Name -notin @('local_fallback', 'local_context') })
        $sourceTotal = ($sources | Measure-Object -Property Value -Sum).Sum
        $localContext = @($sources | Where-Object { $_.Name -eq 'local_context' })
        $localContextCount = if ($localContext.Count -eq 0) { 0 } else { [int]$localContext[0].Value }
        $localStructureProperty = $Summary.recognitionPaths.PSObject.Properties['local_structure']
        $localStructureCount = if ($null -eq $localStructureProperty) { 0 } else { [int]$localStructureProperty.Value }
        if ($sources.Count -eq 0 -or $invalidSources.Count -gt 0 -or
            [int]$sourceTotal -ne [int]$Summary.totalTurns -or
            $localContextCount -gt $localStructureCount) {
            throw 'Carried local fallback holdout contains another recognition source.'
        }
        return
    }
    $paths = @($Summary.recognitionPaths.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 })
    $invalidPaths = @($paths | Where-Object {
            $_.Name -notin @('deepseek', 'local_guard', 'local_structure', 'local_policy')
        })
    $onlineEvidence = ($paths |
            Where-Object { $_.Name -in @('deepseek', 'local_policy') } |
            Measure-Object -Property Value -Sum).Sum
    if ($paths.Count -eq 0 -or $invalidPaths.Count -gt 0 -or [int]$onlineEvidence -le 0) {
        throw 'Carried DeepSeek holdout lacks valid online model evidence.'
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
if ([string]::IsNullOrWhiteSpace($HoldoutMetricsPath) -eq
    [string]::IsNullOrWhiteSpace($CarryForwardBaselinePath)) {
    throw 'Provide exactly one of HoldoutMetricsPath or CarryForwardBaselinePath.'
}

$development = Get-Content -LiteralPath (Resolve-Path $DevelopmentMetricsPath) -Raw -Encoding UTF8 | ConvertFrom-Json
$revision = [string]$development.sourceRevision
if ([string]::IsNullOrWhiteSpace($revision)) {
    throw 'Development metrics do not identify their clean source revision.'
}
& git -C $context.RepositoryRoot cat-file -e "${revision}^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Development metrics source revision is unavailable: $revision"
}
Assert-Metrics -Metrics $development -ExpectedSplit 'development' -ExpectedRevision $revision
$developmentDataset = Get-Content -LiteralPath $context.RecognitionDevFile -Raw -Encoding UTF8 | ConvertFrom-Json
$holdoutDataset = Get-Content -LiteralPath $context.RecognitionHoldoutFile -Raw -Encoding UTF8 | ConvertFrom-Json
$developmentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionDevFile).Hash.ToLowerInvariant()
$holdoutHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.RecognitionHoldoutFile).Hash.ToLowerInvariant()
if ([string]$development.datasetVersion -ne [string]$developmentDataset.version -or
    [string]$development.datasetHash -ne $developmentHash) {
    throw 'Development recognition metrics do not match the current labeled dataset.'
}

$holdoutSummary = $null
$holdoutSourceRevision = ''
if (-not [string]::IsNullOrWhiteSpace($HoldoutMetricsPath)) {
    $holdout = Get-Content -LiteralPath (Resolve-Path $HoldoutMetricsPath) -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Metrics -Metrics $holdout -ExpectedSplit 'holdout' -ExpectedRevision $revision
    if ([string]$development.labelPolicyVersion -ne [string]$holdout.labelPolicyVersion -or
        [string]$holdout.datasetVersion -ne [string]$holdoutDataset.version -or
        [string]$holdout.datasetHash -ne $holdoutHash) {
        throw 'Holdout recognition metrics do not match the current labeled dataset or label policy.'
    }
    $holdoutSummary = Get-MetricsSummary -Metrics $holdout
    $holdoutSourceRevision = $revision
} else {
    $previous = Get-Content -LiteralPath (Resolve-Path $CarryForwardBaselinePath) -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$previous.schemaVersion -ne 'rag_recognition_baseline_v1' -or
        [string]$previous.lane -ne $Lane -or
        [string]$previous.labelPolicyVersion -ne [string]$development.labelPolicyVersion -or
        [string]$previous.datasetHashes.holdout -ne $holdoutHash -or
        [string]$previous.holdout.datasetVersion -ne [string]$holdoutDataset.version) {
        throw 'Carry-forward baseline does not match the current lane, label policy, or sealed holdout dataset.'
    }
    Assert-CarriedSummary -Summary $previous.holdout -ExpectedLane $Lane
    $holdoutSourceRevision = [string](Get-OptionalProperty -Object $previous -Name 'holdoutSourceRevision')
    if ([string]::IsNullOrWhiteSpace($holdoutSourceRevision)) {
        $holdoutSourceRevision = [string]$previous.sourceRevision
    }
    if ([string]::IsNullOrWhiteSpace($holdoutSourceRevision)) {
        throw 'Carry-forward baseline does not identify the original holdout source revision.'
    }
    $holdoutSummary = $previous.holdout
}

$baseline = [pscustomobject][ordered]@{
    schemaVersion = 'rag_recognition_baseline_v1'
    lane = $Lane
    sourceRevision = $revision
    developmentSourceRevision = $revision
    holdoutSourceRevision = $holdoutSourceRevision
    labelPolicyVersion = [string]$development.labelPolicyVersion
    datasetHashes = [ordered]@{
        development = $developmentHash
        holdout = $holdoutHash
    }
    development = Get-MetricsSummary -Metrics $development
    holdout = $holdoutSummary
}
$json = ConvertTo-Json -InputObject $baseline -Depth 20
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($OutputPath),
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Host "[OK] Recognition baseline recorded: lane=$Lane developmentRevision=$revision holdoutRevision=$holdoutSourceRevision"
Write-Host "Baseline: $OutputPath"
