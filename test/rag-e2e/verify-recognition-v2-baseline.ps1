[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Resolve-RepositoryPath {
    param([Parameter(Mandatory)][string]$Path)
    Join-Path $repositoryRoot $Path
}

function Read-Json {
    param([Parameter(Mandatory)][string]$Path)
    Get-Content -LiteralPath (Resolve-RepositoryPath $Path) -Raw -Encoding UTF8 |
        ConvertFrom-Json
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)]$Actual,
        [Parameter(Mandatory)]$Expected,
        [Parameter(Mandatory)][string]$Label
    )
    if ([string]$Actual -ne [string]$Expected) {
        throw "$Label changed: expected='$Expected' actual='$Actual'"
    }
}

function Assert-Zero {
    param(
        [Parameter(Mandatory)]$Actual,
        [Parameter(Mandatory)][string]$Label
    )
    Assert-Equal -Actual ([int]$Actual) -Expected 0 -Label $Label
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$Path)
    (Get-FileHash -Algorithm SHA256 -LiteralPath (Resolve-RepositoryPath $Path)).Hash.ToLowerInvariant()
}

function Get-TurnCount {
    param([Parameter(Mandatory)]$Dataset)
    [int](
        @($Dataset.cases | ForEach-Object { @($_.turns).Count }) |
            Measure-Object -Sum
    ).Sum
}

function Assert-DevelopmentSummary {
    param(
        [Parameter(Mandatory)]$Summary,
        [Parameter(Mandatory)][string]$Lane
    )
    Assert-Equal $Summary.datasetVersion 'rag_recognition_dev_v16' "$Lane development version"
    Assert-Equal ([int]$Summary.totalTurns) 516 "$Lane development turns"
    Assert-Equal ([int]$Summary.intentCorrect) 516 "$Lane development intent correct"
    Assert-Equal ([int]$Summary.semanticOperationEligible) 284 "$Lane operation eligible"
    Assert-Equal ([int]$Summary.semanticOperationCorrect) 284 "$Lane operation correct"
    Assert-Equal ([int]$Summary.exactContractCorrect) 516 "$Lane exact contract correct"
    Assert-Zero $Summary.unsafeExecutableCount "$Lane unsafe executable count"
    Assert-Zero $Summary.missingResponseCount "$Lane missing response count"
    Assert-Zero $Summary.infrastructureFailures "$Lane infrastructure failures"
}

$developmentPath = 'test/rag-e2e/recognition-dev.json'
$holdoutPath = 'test/rag-e2e/recognition-holdout.json'
$candidatePath = 'test/rag-e2e/recognition-candidate.json'
$localBaselinePath = 'test/rag-e2e/recognition-baseline.local-fallback.json'
$onlineBaselinePath = 'test/rag-e2e/recognition-baseline.deepseek-online.json'
$stabilityPath = 'test/rag-e2e/recognition-stability.deepseek-online.json'

$development = Read-Json $developmentPath
$holdout = Read-Json $holdoutPath
$candidate = Read-Json $candidatePath
$localBaseline = Read-Json $localBaselinePath
$onlineBaseline = Read-Json $onlineBaselinePath
$stability = Read-Json $stabilityPath

$developmentHash = Get-Sha256 $developmentPath
$holdoutHash = Get-Sha256 $holdoutPath

Assert-Equal $development.version 'rag_recognition_dev_v16' 'development dataset version'
Assert-Equal @($development.cases).Count 402 'development case count'
Assert-Equal (Get-TurnCount $development) 516 'development turn count'
Assert-Equal $holdout.version 'rag_recognition_holdout_v1' 'holdout dataset version'
Assert-Equal @($holdout.cases).Count 24 'holdout case count'
Assert-Equal (Get-TurnCount $holdout) 29 'holdout turn count'
Assert-Equal @($candidate.cases).Count 0 'candidate case count'
Assert-Equal (Get-TurnCount $candidate) 0 'candidate turn count'

foreach ($baseline in @($localBaseline, $onlineBaseline)) {
    Assert-Equal $baseline.schemaVersion 'rag_recognition_baseline_v1' "$($baseline.lane) schema"
    Assert-Equal $baseline.datasetHashes.development $developmentHash "$($baseline.lane) development hash"
    Assert-Equal $baseline.datasetHashes.holdout $holdoutHash "$($baseline.lane) holdout hash"
}

Assert-DevelopmentSummary $localBaseline.development 'local-fallback'
Assert-DevelopmentSummary $onlineBaseline.development 'deepseek-online'

Assert-Equal ([int]$localBaseline.holdout.totalTurns) 29 'local holdout turns'
Assert-Equal ([int]$localBaseline.holdout.intentCorrect) 23 'local holdout intent correct'
Assert-Equal ([int]$localBaseline.holdout.semanticOperationCorrect) 12 'local holdout operation correct'
Assert-Equal ([int]$localBaseline.holdout.exactContractCorrect) 22 'local holdout exact correct'
Assert-Zero $localBaseline.holdout.unsafeExecutableCount 'local holdout unsafe count'

Assert-Equal ([int]$onlineBaseline.holdout.totalTurns) 29 'online holdout turns'
Assert-Equal ([int]$onlineBaseline.holdout.intentCorrect) 28 'online holdout intent correct'
Assert-Equal ([int]$onlineBaseline.holdout.semanticOperationCorrect) 16 'online holdout operation correct'
Assert-Equal ([int]$onlineBaseline.holdout.exactContractCorrect) 28 'online holdout exact correct'
Assert-Zero $onlineBaseline.holdout.unsafeExecutableCount 'online holdout unsafe count'

Assert-Equal $stability.schemaVersion 'rag_recognition_stability_v1' 'stability schema'
Assert-Equal ([int]$stability.runCount) 3 'stability run count'
Assert-Equal ([int]$stability.totalTurns) 516 'stability turns per run'
Assert-Zero $stability.intentDriftCount 'stability intent drift'
Assert-Zero $stability.operationDriftCount 'stability operation drift'
Assert-Zero $stability.exactOutcomeDriftCount 'stability exact outcome drift'
Assert-Equal ([int]$stability.pathDriftCount) 3 'stability path drift'
Assert-Equal ([int]$stability.fullyStableCount) 513 'stability fully stable count'
Assert-Equal ([int]$stability.alwaysExactCorrectCount) 516 'stability always exact count'
Assert-Zero $stability.unsafeExecutableCount 'stability unsafe count'
Assert-Zero $stability.infrastructureFailures 'stability infrastructure failures'
Assert-Zero $stability.unavailablePathCount 'stability unavailable paths'

$examples = Read-Json 'rag/src/main/resources/rag/corpus/cloud_drive_semantic_examples.json'
$boundaries = Read-Json 'rag/src/main/resources/rag/corpus/cloud_drive_semantic_boundaries.json'
$intentRules = @(
    Import-Csv -LiteralPath (Resolve-RepositoryPath 'rag/src/main/resources/rag/conversation/intent_rules.csv')
)
$entityRules = @(
    Import-Csv -LiteralPath (Resolve-RepositoryPath 'rag/src/main/resources/rag/conversation/entity_rules.csv')
)
$deepSeek = Read-Json 'rag/src/main/resources/rag/llm/deepseek.json'

Assert-Equal @($examples.examples).Count 338 'semantic example count'
Assert-Equal @($boundaries.boundaries).Count 449 'semantic boundary count'
Assert-Equal $intentRules.Count 49 'intent rule count'
Assert-Equal $entityRules.Count 38 'entity rule count'
Assert-Equal ([bool]$deepSeek.enabled) $true 'DeepSeek enabled baseline'
Assert-Equal $deepSeek.model 'deepseek-v4-flash' 'DeepSeek model baseline'
Assert-Equal ([double]$deepSeek.temperature) 0.1 'DeepSeek temperature baseline'
Assert-Equal ([int]$deepSeek.max_tokens) 900 'DeepSeek max token baseline'
Assert-Equal ([int]$deepSeek.timeout_seconds) 8 'DeepSeek timeout baseline'
Assert-Equal $deepSeek.prompt.version 'semantic_frame_v2' 'DeepSeek prompt version baseline'

Write-Host '[OK] Recognition V2 frozen baseline verified.'
Write-Host "  development: cases=402 turns=516 local=516/516 online=516/516 unsafe=0"
Write-Host "  sealed holdout: cases=24 turns=29 local=22/29 online=28/29 unsafe=0"
Write-Host "  stability: runs=3 intentDrift=0 operationDrift=0 exactDrift=0 pathDrift=3"
Write-Host "  retrieval inventory: examples=338 boundaries=449 intentRules=49 entityRules=38"
Write-Host "  development sha256=$developmentHash"
Write-Host "  holdout sha256=$holdoutHash"
