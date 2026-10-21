[CmdletBinding(DefaultParameterSetName = 'Verify')]
param(
    [Parameter(Mandatory)][ValidateCount(1, 20)][string[]]$ReportPath,
    [string]$BaselinePath = '',
    [Parameter(ParameterSetName = 'Write')][switch]$WriteBaseline,
    [Parameter(ParameterSetName = 'Write')][ValidateRange(3, 20)][int]$MinimumConsistentRuns = 3
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

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

function ConvertTo-NormalizedValue {
    param(
        [AllowNull()]$Value,
        [Parameter(Mandatory)][string]$RunId
    )
    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [string]) {
        if ([string]::IsNullOrEmpty($RunId)) {
            return $Value
        }
        return $Value.Replace($RunId, '{{runId}}')
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $result = [ordered]@{}
        foreach ($key in @($Value.Keys | ForEach-Object { [string]$_ } | Sort-Object)) {
            $result[$key] = ConvertTo-NormalizedValue -Value $Value[$key] -RunId $RunId
        }
        return $result
    }
    if ($Value -is [pscustomobject]) {
        $result = [ordered]@{}
        foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
            $result[$property.Name] = ConvertTo-NormalizedValue -Value $property.Value -RunId $RunId
        }
        return $result
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        return ,@($Value | ForEach-Object {
            ConvertTo-NormalizedValue -Value $_ -RunId $RunId
        })
    }
    $Value
}

function ConvertTo-CompactJson {
    param([AllowNull()]$Value)
    ConvertTo-Json -InputObject $Value -Depth 30 -Compress
}

function Get-StringSha256 {
    param([Parameter(Mandatory)][string]$Value)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Get-SortedStrings {
    param([AllowNull()]$Value)
    return ,@($Value | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique)
}

function Get-ContractsHash {
    param([Parameter(Mandatory)]$Contracts)
    $contractJson = ConvertTo-CompactJson -Value @($Contracts)
    Get-StringSha256 -Value $contractJson
}

function New-TurnContract {
    param(
        [Parameter(Mandatory)]$Turn,
        [Parameter(Mandatory)][string]$RunId
    )
    $response = Get-OptionalProperty -Object $Turn -Name 'response'
    if ($null -eq $response) {
        throw 'A baseline turn has no response.'
    }
    $assertion = Get-OptionalProperty -Object $Turn -Name 'assertion'
    if ($null -eq $assertion -or -not [bool](Get-OptionalProperty -Object $assertion -Name 'Passed')) {
        throw 'A baseline turn has a failed or missing assertion.'
    }

    $safety = Get-OptionalProperty -Object $response -Name 'safety'
    $draft = Get-OptionalProperty -Object $response -Name 'actionDraft'
    $backend = Get-OptionalProperty -Object $response -Name 'backendActionDraft'
    $plan = Get-OptionalProperty -Object $response -Name 'actionPlan'
    $frame = Get-OptionalProperty -Object $response -Name 'semanticFrame'
    $query = Get-OptionalProperty -Object $frame -Name 'query'
    $scope = Get-OptionalProperty -Object $frame -Name 'scope'
    $reference = Get-OptionalProperty -Object $frame -Name 'reference'
    $interaction = Get-OptionalProperty -Object $response -Name 'interaction'
    $steps = @(
        Get-OptionalProperty -Object $plan -Name 'steps' | ForEach-Object {
            [ordered]@{
                actionType = [string](Get-OptionalProperty -Object $_ -Name 'actionType')
                operation = [string](Get-OptionalProperty -Object $_ -Name 'operation')
                status = [string](Get-OptionalProperty -Object $_ -Name 'status')
            }
        }
    )

    [ordered]@{
        intentId = [string](Get-OptionalProperty -Object $response -Name 'intentId')
        taskType = [string](Get-OptionalProperty -Object $response -Name 'taskType')
        nextAction = [string](Get-OptionalProperty -Object $response -Name 'nextAction')
        missingSlots = Get-SortedStrings (Get-OptionalProperty -Object $response -Name 'missingSlots')
        entities = ConvertTo-NormalizedValue `
                -Value (Get-OptionalProperty -Object $response -Name 'entities') `
                -RunId $RunId
        safety = [ordered]@{
            risk = [string](Get-OptionalProperty -Object $safety -Name 'risk')
            requiresConfirmation = [bool](Get-OptionalProperty -Object $safety -Name 'requiresConfirmation')
            allowedToExecute = [bool](Get-OptionalProperty -Object $safety -Name 'allowedToExecute')
        }
        actionDraft = [ordered]@{
            type = [string](Get-OptionalProperty -Object $draft -Name 'type')
            needsBackendBinding = [bool](Get-OptionalProperty -Object $draft -Name 'needsBackendBinding')
            parameters = ConvertTo-NormalizedValue `
                    -Value (Get-OptionalProperty -Object $draft -Name 'parameters') `
                    -RunId $RunId
        }
        backendActionDraft = [ordered]@{
            status = [string](Get-OptionalProperty -Object $backend -Name 'status')
            actionType = [string](Get-OptionalProperty -Object $backend -Name 'actionType')
            nextAction = [string](Get-OptionalProperty -Object $backend -Name 'nextAction')
            executableByBackend = [bool](Get-OptionalProperty -Object $backend -Name 'executableByBackend')
            authorizationRequired = [bool](Get-OptionalProperty -Object $backend -Name 'authorizationRequired')
            requiredClientFields = Get-SortedStrings (Get-OptionalProperty -Object $backend -Name 'requiredClientFields')
        }
        actionPlan = [ordered]@{
            status = [string](Get-OptionalProperty -Object $plan -Name 'status')
            planKind = [string](Get-OptionalProperty -Object $plan -Name 'planKind')
            actionType = [string](Get-OptionalProperty -Object $plan -Name 'actionType')
            risk = [string](Get-OptionalProperty -Object $plan -Name 'risk')
            confirmationLevel = [string](Get-OptionalProperty -Object $plan -Name 'confirmationLevel')
            requiredClientFields = Get-SortedStrings (Get-OptionalProperty -Object $plan -Name 'requiredClientFields')
            steps = $steps
        }
        semanticFrame = [ordered]@{
            relation = [string](Get-OptionalProperty -Object $frame -Name 'relation')
            operation = [string](Get-OptionalProperty -Object $frame -Name 'operation')
            query = [ordered]@{
                mode = [string](Get-OptionalProperty -Object $query -Name 'mode')
                resultType = [string](Get-OptionalProperty -Object $query -Name 'resultType')
                nameSurface = ConvertTo-NormalizedValue `
                        -Value ([string](Get-OptionalProperty -Object $query -Name 'nameSurface')) `
                        -RunId $RunId
                nameNormalized = ConvertTo-NormalizedValue `
                        -Value ([string](Get-OptionalProperty -Object $query -Name 'nameNormalized')) `
                        -RunId $RunId
                filters = ConvertTo-NormalizedValue `
                        -Value (Get-OptionalProperty -Object $query -Name 'filters') `
                        -RunId $RunId
            }
            scope = [ordered]@{
                type = [string](Get-OptionalProperty -Object $scope -Name 'type')
                folderSurface = ConvertTo-NormalizedValue `
                        -Value ([string](Get-OptionalProperty -Object $scope -Name 'folderSurface')) `
                        -RunId $RunId
                folderNormalized = ConvertTo-NormalizedValue `
                        -Value ([string](Get-OptionalProperty -Object $scope -Name 'folderNormalized')) `
                        -RunId $RunId
            }
            reference = [ordered]@{
                type = [string](Get-OptionalProperty -Object $reference -Name 'type')
                candidateIndex = Get-OptionalProperty -Object $reference -Name 'candidateIndex'
            }
        }
        interactionStage = [string](Get-OptionalProperty -Object $interaction -Name 'stage')
        hasExecutionReference = $null -ne (Get-OptionalProperty -Object $response -Name 'executionReference')
    }
}

function New-ReportSnapshot {
    param([Parameter(Mandatory)][string]$Path)
    $report = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([int]$report.failedTurns -ne 0) {
        throw "Report contains failed turns: $Path"
    }
    if ([int]$report.executedCount -ne 0 -or [string]$report.startedByMode -ne 'recognition-only') {
        throw "A recognition baseline must not execute tasks: $Path"
    }
    if ([string]$report.suite -ne 'all') {
        throw "A contract baseline requires suite=all: $Path"
    }

    $contracts = [System.Collections.Generic.List[object]]::new()
    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($case in @($report.cases)) {
        foreach ($turn in @($case.turns)) {
            $key = "$($case.id)#$($turn.turn)"
            if (-not $keys.Add($key)) {
                throw "Duplicate case/turn key in ${Path}: $key"
            }
            $contracts.Add([pscustomobject][ordered]@{
                key = $key
                contract = New-TurnContract -Turn $turn -RunId ([string]$report.runId)
            })
        }
    }
    if ($contracts.Count -eq 0 -or [int]$report.passedTurns -ne $contracts.Count) {
        throw "Report pass count does not match its contract entries: $Path"
    }
    [pscustomobject][ordered]@{
        path = $Path
        runId = [string]$report.runId
        suite = [string]$report.suite
        suiteVersion = [string]$report.version
        passedTurns = [int]$report.passedTurns
        contracts = @($contracts)
        contractHash = Get-ContractsHash @($contracts)
    }
}

function Assert-SnapshotMatches {
    param(
        [Parameter(Mandatory)]$Expected,
        [Parameter(Mandatory)]$Actual,
        [Parameter(Mandatory)][string]$ActualLabel
    )
    if ([string]$Expected.suite -ne [string]$Actual.suite -or
        [string]$Expected.suiteVersion -ne [string]$Actual.suiteVersion) {
        throw "Suite identity differs for $ActualLabel."
    }

    $expectedByKey = @{}
    foreach ($entry in @($Expected.contracts)) {
        $expectedByKey[[string]$entry.key] = ConvertTo-CompactJson $entry.contract
    }
    $actualByKey = @{}
    foreach ($entry in @($Actual.contracts)) {
        $actualByKey[[string]$entry.key] = ConvertTo-CompactJson $entry.contract
    }
    $allKeys = @($expectedByKey.Keys + $actualByKey.Keys | Sort-Object -Unique)
    $differences = @(
        foreach ($key in $allKeys) {
            if ($expectedByKey[$key] -ne $actualByKey[$key]) {
                $key
            }
        }
    )
    if ($differences.Count -gt 0) {
        $preview = $differences | Select-Object -First 12
        throw "Contract drift in $ActualLabel for $($differences.Count) turn(s): $($preview -join ', ')"
    }
}

$context = Get-RagE2EContext
if ([string]::IsNullOrWhiteSpace($BaselinePath)) {
    $BaselinePath = $context.ContractBaselineFile
}
$resolvedReports = @(
    foreach ($path in $ReportPath) {
        (Resolve-Path -LiteralPath $path).Path
    }
) | Select-Object -Unique
$snapshots = @($resolvedReports | ForEach-Object { New-ReportSnapshot -Path $_ })

if ($WriteBaseline) {
    if ($snapshots.Count -lt $MinimumConsistentRuns) {
        throw "Writing a baseline requires at least $MinimumConsistentRuns consistent reports."
    }
    $distinctRunIds = @($snapshots.runId | Sort-Object -Unique)
    if ($distinctRunIds.Count -lt $MinimumConsistentRuns) {
        throw "Writing a baseline requires at least $MinimumConsistentRuns distinct run IDs."
    }
    $reference = $snapshots[0]
    foreach ($snapshot in $snapshots | Select-Object -Skip 1) {
        Assert-SnapshotMatches -Expected $reference -Actual $snapshot -ActualLabel $snapshot.path
    }
    if (Test-Path -LiteralPath $BaselinePath) {
        throw "Baseline already exists and will not be overwritten automatically: $BaselinePath"
    }
    $revision = (& git -C $context.RepositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to read the Git revision for baseline provenance.'
    }
    $baseline = [pscustomobject][ordered]@{
        schemaVersion = 'rag_e2e_contract_baseline_v1'
        suite = $reference.suite
        suiteVersion = $reference.suiteVersion
        sourceRevision = $revision
        sourceRunIds = @($snapshots.runId)
        scenarioHashes = [ordered]@{
            baseline = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.ScenarioFile).Hash.ToLowerInvariant()
            stress = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.StressScenarioFile).Hash.ToLowerInvariant()
        }
        passedTurns = $reference.passedTurns
        contractHash = $reference.contractHash
        contracts = $reference.contracts
    }
    $baselineJson = ConvertTo-Json -InputObject $baseline -Depth 30
    [System.IO.File]::WriteAllText(
        [System.IO.Path]::GetFullPath($BaselinePath),
        $baselineJson + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Host "[OK] Contract baseline written from $($snapshots.Count) consistent runs: $BaselinePath"
    Write-Host "Contract hash: $($reference.contractHash)"
    exit 0
}

if (-not (Test-Path -LiteralPath $BaselinePath -PathType Leaf)) {
    throw "Missing contract baseline: $BaselinePath"
}
$baseline = Get-Content -LiteralPath $BaselinePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$baseline.schemaVersion -ne 'rag_e2e_contract_baseline_v1') {
    throw "Unsupported contract baseline schema: $($baseline.schemaVersion)"
}
$calculatedBaselineHash = Get-ContractsHash @($baseline.contracts)
if ([string]$baseline.contractHash -ne $calculatedBaselineHash) {
    throw 'Contract baseline content does not match its recorded hash.'
}
$baselineHashes = Get-OptionalProperty -Object $baseline -Name 'scenarioHashes'
$currentBaselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.ScenarioFile).Hash.ToLowerInvariant()
$currentStressHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $context.StressScenarioFile).Hash.ToLowerInvariant()
if ([string]$baselineHashes.baseline -ne $currentBaselineHash -or
    [string]$baselineHashes.stress -ne $currentStressHash) {
    throw 'Scenario files changed after the contract baseline was recorded.'
}
foreach ($snapshot in $snapshots) {
    Assert-SnapshotMatches -Expected $baseline -Actual $snapshot -ActualLabel $snapshot.path
    Write-Host "[OK] $($snapshot.runId) matches baseline contract $($baseline.contractHash)."
}
