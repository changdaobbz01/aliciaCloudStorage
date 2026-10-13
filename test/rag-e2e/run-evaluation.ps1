[CmdletBinding()]
param(
    [switch]$ExecuteFolderCreate,
    [int]$ExecutionTimeoutSeconds = 45
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

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory)][ValidateSet('Get', 'Post')][string]$Method,
        [Parameter(Mandatory)][string]$Uri,
        [hashtable]$Headers = @{},
        [AllowNull()]$Body
    )
    $parameters = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        TimeoutSec  = 40
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

function Add-ExpectationFailure {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Failures,
        [Parameter(Mandatory)][string]$Message
    )
    $Failures.Add($Message)
}

function Test-TurnExpectation {
    param(
        [Parameter(Mandatory)]$Response,
        [Parameter(Mandatory)]$Expected
    )
    $failures = [System.Collections.Generic.List[string]]::new()
    $intentId = [string](Get-OptionalProperty -Object $Response -Name 'intentId')
    $nextAction = [string](Get-OptionalProperty -Object $Response -Name 'nextAction')
    $actionDraft = Get-OptionalProperty -Object $Response -Name 'actionDraft'
    $actionDraftType = [string](Get-OptionalProperty -Object $actionDraft -Name 'type')
    if ([string]::IsNullOrWhiteSpace($actionDraftType)) {
        $actionDraftType = 'none'
    }
    $safety = Get-OptionalProperty -Object $Response -Name 'safety'
    $requiresConfirmation = [bool](Get-OptionalProperty -Object $safety -Name 'requiresConfirmation')
    $executionReference = Get-OptionalProperty -Object $Response -Name 'executionReference'
    $backendActionDraft = Get-OptionalProperty -Object $Response -Name 'backendActionDraft'
    $backendExecutable = [bool](Get-OptionalProperty -Object $backendActionDraft -Name 'executableByBackend')
    $entities = Get-OptionalProperty -Object $Response -Name 'entities'
    $missingSlots = @(Get-OptionalProperty -Object $Response -Name 'missingSlots')
    $semanticFrame = Get-OptionalProperty -Object $Response -Name 'semanticFrame'
    $semanticQuery = Get-OptionalProperty -Object $semanticFrame -Name 'query'
    $semanticScope = Get-OptionalProperty -Object $semanticFrame -Name 'scope'

    $allowedIntentIds = @(
        Get-OptionalProperty -Object $Expected -Name 'intentId' |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
    )
    if ($allowedIntentIds.Count -gt 0 -and $intentId -notin $allowedIntentIds) {
        Add-ExpectationFailure $failures "intentId '$intentId' not in [$($allowedIntentIds -join ', ')]"
    }
    $allowedNextActions = @(
        Get-OptionalProperty -Object $Expected -Name 'nextAction' |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
    )
    if ($allowedNextActions.Count -gt 0 -and $nextAction -notin $allowedNextActions) {
        Add-ExpectationFailure $failures "nextAction '$nextAction' not in [$($allowedNextActions -join ', ')]"
    }
    $allowedDraftTypes = @(
        Get-OptionalProperty -Object $Expected -Name 'actionDraftType' |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
    )
    if ($allowedDraftTypes.Count -gt 0 -and $actionDraftType -notin $allowedDraftTypes) {
        Add-ExpectationFailure $failures "actionDraft.type '$actionDraftType' not in [$($allowedDraftTypes -join ', ')]"
    }

    $expectedConfirmationProperty = $Expected.PSObject.Properties['requiresConfirmation']
    if ($null -ne $expectedConfirmationProperty -and
        $requiresConfirmation -ne [bool]$expectedConfirmationProperty.Value) {
        Add-ExpectationFailure $failures "requiresConfirmation was $requiresConfirmation"
    }
    $expectedReferenceProperty = $Expected.PSObject.Properties['executionReference']
    if ($null -ne $expectedReferenceProperty) {
        $hasReference = $null -ne $executionReference
        if ($hasReference -ne [bool]$expectedReferenceProperty.Value) {
            Add-ExpectationFailure $failures "executionReference presence was $hasReference"
        }
    }
    $noExecutablePlan = [bool](Get-OptionalProperty -Object $Expected -Name 'noExecutablePlan')
    if ($noExecutablePlan -and ($backendExecutable -or $null -ne $executionReference)) {
        Add-ExpectationFailure $failures 'unsafe executable plan was produced'
    }

    $expectedEntities = Get-OptionalProperty -Object $Expected -Name 'entityEquals'
    if ($null -ne $expectedEntities) {
        foreach ($property in $expectedEntities.PSObject.Properties) {
            $actualValue = Get-OptionalProperty -Object $entities -Name $property.Name
            if ([string]$actualValue -ne [string]$property.Value) {
                Add-ExpectationFailure $failures "entity '$($property.Name)' was '$actualValue'"
            }
        }
    }
    $absentEntities = @(
        Get-OptionalProperty -Object $Expected -Name 'absentEntities' |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
    )
    foreach ($entityName in $absentEntities) {
        $actualValue = Get-OptionalProperty -Object $entities -Name ([string]$entityName)
        if ($null -ne $actualValue -and -not [string]::IsNullOrWhiteSpace([string]$actualValue)) {
            Add-ExpectationFailure $failures "entity '$entityName' should be absent but was '$actualValue'"
        }
    }
    $expectedMissingProperty = $Expected.PSObject.Properties['missingSlots']
    if ($null -ne $expectedMissingProperty) {
        $expectedMissing = @($expectedMissingProperty.Value | Sort-Object -Unique)
        $actualMissing = @($missingSlots | Sort-Object -Unique)
        if (($expectedMissing -join '|') -ne ($actualMissing -join '|')) {
            Add-ExpectationFailure $failures "missingSlots were [$($actualMissing -join ', ')]"
        }
    }
    foreach ($semanticExpectation in @(
            @{ Expected = 'semanticOperation'; Actual = Get-OptionalProperty -Object $semanticFrame -Name 'operation' },
            @{ Expected = 'semanticRelation'; Actual = Get-OptionalProperty -Object $semanticFrame -Name 'relation' },
            @{ Expected = 'queryMode'; Actual = Get-OptionalProperty -Object $semanticQuery -Name 'mode' },
            @{ Expected = 'scopeType'; Actual = Get-OptionalProperty -Object $semanticScope -Name 'type' },
            @{ Expected = 'resultType'; Actual = Get-OptionalProperty -Object $semanticQuery -Name 'resultType' }
        )) {
        $allowedValues = @(
            Get-OptionalProperty -Object $Expected -Name $semanticExpectation.Expected |
                Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
        )
        if ($allowedValues.Count -gt 0 -and [string]$semanticExpectation.Actual -notin $allowedValues) {
            Add-ExpectationFailure $failures "$($semanticExpectation.Expected) '$($semanticExpectation.Actual)' not in [$($allowedValues -join ', ')]"
        }
    }

    [pscustomobject]@{
        Passed               = $failures.Count -eq 0
        Failures             = @($failures)
        IntentId             = $intentId
        NextAction           = $nextAction
        ActionDraftType      = $actionDraftType
        RequiresConfirmation = $requiresConfirmation
        HasExecutionReference = $null -ne $executionReference
    }
}

function Wait-ExecutionTerminalState {
    param(
        [Parameter(Mandatory)][string]$BaseUrl,
        [Parameter(Mandatory)][string]$ExecutionId,
        [Parameter(Mandatory)][hashtable]$Headers,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $execution = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/executions/$ExecutionId" -Headers $Headers
        if ([string]$execution.status -in @('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')) {
            return $execution
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Execution $ExecutionId did not reach a terminal state within $TimeoutSeconds seconds."
}

$context = Get-RagE2EContext
Import-RagE2EEnv -Path $context.EnvFile
$identityBaseUrl = (Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_IDENTITY_BASE_URL').TrimEnd('/')
$account = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_ACCOUNT'
$password = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_PASSWORD'
$ragPort = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_RAG_PORT'
$executionPort = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_EXEC_PORT'
$apiPort = Assert-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_API_PORT'

if ($ExecuteFolderCreate -and
    -not (ConvertTo-RagE2EBool (Get-RagE2EEnvValue -Name 'ALICIA_RAG_E2E_ALLOW_LOCAL_EXECUTION'))) {
    throw 'Local execution needs both -ExecuteFolderCreate and ALICIA_RAG_E2E_ALLOW_LOCAL_EXECUTION=true.'
}

$login = Invoke-JsonRequest -Method Post -Uri "$identityBaseUrl/api/identity/auth/login" -Body @{
    identifier = $account
    password   = $password
}
$token = [string](Get-OptionalProperty -Object $login -Name 'token')
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'Identity login succeeded without returning a token.'
}
$headers = @{ Authorization = "Bearer $token" }
$ragBaseUrl = "http://127.0.0.1:$ragPort"
$executionBaseUrl = "http://127.0.0.1:$executionPort"
$apiBaseUrl = "http://127.0.0.1:$apiPort"

$access = Invoke-JsonRequest -Method Get -Uri "$ragBaseUrl/api/assistant/auth/access" -Headers $headers
if ([string]$access.role -ne 'RAG_ADMIN') {
    throw "The evaluation account must have appRoles.rag=RAG_ADMIN; actual role: $($access.role)"
}
Write-Host '[OK] Dedicated RAG_ADMIN account authenticated; token is held only in process memory.'

$suite = Get-Content -LiteralPath $context.ScenarioFile -Raw -Encoding UTF8 | ConvertFrom-Json
$runId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddHHmmss')
$caseResults = [System.Collections.Generic.List[object]]::new()
$passedTurns = 0
$failedTurns = 0
$executedCount = 0

foreach ($case in $suite.cases) {
    $conversationId = $null
    $turnResults = [System.Collections.Generic.List[object]]::new()
    $registeredExecutionIds = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    $lastResponse = $null
    $turnNumber = 0
    foreach ($turn in $case.turns) {
        $turnNumber++
        $message = ([string]$turn.message).Replace('{{runId}}', $runId)
        try {
            $body = @{
                message        = $message
                conversationId = $conversationId
                clientContext  = @{
                    currentFolderId     = $null
                    currentFolderPath   = '/'
                    availableClientInputs = @{}
                    actionContractVersion = 'action_bridge_v2'
                    supportedActionTypes = @(
                        'folder.create',
                        'rename',
                        'delete',
                        'share',
                        'upload_target',
                        'collection.move_exact',
                        'collection.trash_by_name_contains',
                        'collection.trash_by_category',
                        'collection.move_by_category',
                        'collection.move_by_extension',
                        'collection.move_by_name_contains',
                        'collection.rename_add_prefix',
                        'composite.create_folder_then_upload'
                    )
                }
            }
            $lastResponse = Invoke-JsonRequest -Method Post -Uri "$ragBaseUrl/api/assistant/plan" -Headers $headers -Body $body
            $turnReference = Get-OptionalProperty -Object $lastResponse -Name 'executionReference'
            $turnExecutionId = [string](Get-OptionalProperty -Object $turnReference -Name 'executionId')
            if (-not [string]::IsNullOrWhiteSpace($turnExecutionId)) {
                [void]$registeredExecutionIds.Add($turnExecutionId)
            }
            $conversation = Get-OptionalProperty -Object $lastResponse -Name 'conversation'
            $nextConversationId = [string](Get-OptionalProperty -Object $conversation -Name 'conversationId')
            if (-not [string]::IsNullOrWhiteSpace($nextConversationId)) {
                $conversationId = $nextConversationId
            }
            $assertion = Test-TurnExpectation -Response $lastResponse -Expected $turn.expected
            if ($assertion.Passed) {
                $passedTurns++
                Write-Host "[PASS] $($case.id) turn $turnNumber -> $($assertion.IntentId)"
            } else {
                $failedTurns++
                Write-Host "[FAIL] $($case.id) turn $turnNumber -> $($assertion.Failures -join '; ')"
            }
            $turnResults.Add([pscustomobject]@{
                turn       = $turnNumber
                message    = $message
                assertion  = $assertion
                response   = $lastResponse
                error      = $null
            })
        } catch {
            $failedTurns++
            Write-Host "[FAIL] $($case.id) turn $turnNumber -> $($_.Exception.Message)"
            $turnResults.Add([pscustomobject]@{
                turn       = $turnNumber
                message    = $message
                assertion  = $null
                response   = $null
                error      = $_.Exception.Message
            })
            break
        }
    }

    $executionResult = $null
    $completedExecutionId = ''
    $safeExecution = [bool](Get-OptionalProperty -Object $case -Name 'safeExecution')
    if ($ExecuteFolderCreate -and $safeExecution -and $null -ne $lastResponse) {
        try {
            $reference = Get-OptionalProperty -Object $lastResponse -Name 'executionReference'
            if ($null -eq $reference) {
                throw 'Missing executionReference.'
            }
            $executionId = [string]$reference.executionId
            $confirm = Invoke-JsonRequest -Method Post `
                    -Uri "$executionBaseUrl/api/executions/$executionId/confirm" `
                    -Headers $headers `
                    -Body @{ expectedVersion = [long]$reference.version }
            $terminal = Wait-ExecutionTerminalState `
                    -BaseUrl $executionBaseUrl `
                    -ExecutionId $executionId `
                    -Headers $headers `
                    -TimeoutSeconds $ExecutionTimeoutSeconds
            $executionPassed = [string]$terminal.status -eq 'SUCCEEDED'
            if ($executionPassed) {
                $folderName = "rag-e2e-exec-$runId"
                $encodedName = [uri]::EscapeDataString($folderName)
                $nodes = Invoke-JsonRequest -Method Get `
                        -Uri "$apiBaseUrl/api/storage/nodes?recursive=true&keyword=$encodedName&page=1&size=20&sortBy=updatedAt&sortDirection=DESC" `
                        -Headers $headers
                $matchingNodes = @($nodes.items | Where-Object { $_.name -eq $folderName -and $_.type -eq 'FOLDER' })
                $executionPassed = $matchingNodes.Count -eq 1
            }
            if (-not $executionPassed) {
                throw 'Execution status or isolated folder verification failed.'
            }
            $completedExecutionId = $executionId
            $executedCount++
            Write-Host "[PASS] $($case.id) confirmed once and completed in the isolated CloudStorageApi."
            $executionResult = [pscustomobject]@{
                passed   = $true
                confirm  = $confirm
                terminal = $terminal
                error    = $null
            }
        } catch {
            $failedTurns++
            Write-Host "[FAIL] $($case.id) safe execution -> $($_.Exception.Message)"
            $executionResult = [pscustomobject]@{
                passed   = $false
                confirm  = $null
                terminal = $null
                error    = $_.Exception.Message
            }
        }
    }

    $cleanupResults = [System.Collections.Generic.List[object]]::new()
    foreach ($executionId in $registeredExecutionIds) {
        if ($executionId -eq $completedExecutionId) {
            continue
        }
        try {
            $cancelled = Invoke-JsonRequest -Method Post `
                    -Uri "$executionBaseUrl/api/executions/$executionId/cancel" `
                    -Headers $headers
            $cleanupPassed = [string]$cancelled.status -eq 'CANCELLED'
            if (-not $cleanupPassed) {
                $failedTurns++
                Write-Host "[FAIL] $($case.id) task cleanup returned status $($cancelled.status)."
            } else {
                Write-Host "[OK] $($case.id) unconfirmed task cancelled."
            }
            $cleanupResults.Add([pscustomobject]@{
                executionId = $executionId
                passed      = $cleanupPassed
                status      = [string]$cancelled.status
                error       = $null
            })
        } catch {
            $failedTurns++
            Write-Host "[FAIL] $($case.id) task cleanup -> $($_.Exception.Message)"
            $cleanupResults.Add([pscustomobject]@{
                executionId = $executionId
                passed      = $false
                status      = ''
                error       = $_.Exception.Message
            })
        }
    }

    $caseResults.Add([pscustomobject]@{
        id          = $case.id
        group       = [string](Get-OptionalProperty -Object $case -Name 'group')
        turns       = @($turnResults)
        execution   = $executionResult
        cleanup     = @($cleanupResults)
    })
}

if (-not (Test-Path -LiteralPath $context.ResultsDir -PathType Container)) {
    [void](New-Item -ItemType Directory -Path $context.ResultsDir)
}
$reportPath = Join-Path $context.ResultsDir "rag-e2e-$runId.json"
$report = [pscustomobject]@{
    version       = $suite.version
    runId         = $runId
    startedByMode = if ($ExecuteFolderCreate) { 'recognition-and-safe-local-execution' } else { 'recognition-only' }
    passedTurns   = $passedTurns
    failedTurns   = $failedTurns
    executedCount = $executedCount
    cases         = @($caseResults)
}
$reportJson = $report | ConvertTo-Json -Depth 50
[System.IO.File]::WriteAllText(
    $reportPath,
    $reportJson,
    [System.Text.UTF8Encoding]::new($false)
)

$token = $null
$password = $null
$login = $null
Write-Host "Report: $reportPath"
Write-Host "Summary: passed=$passedTurns failed=$failedTurns executed=$executedCount"
if ($failedTurns -gt 0) {
    throw "RAG evaluation completed with $failedTurns failure(s)."
}
Write-Host '[OK] RAG evaluation passed.'
