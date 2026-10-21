[CmdletBinding()]
param(
    [string]$BaseUrl = "https://windwindwind-alicia.cn",
    [int]$TerminalTimeoutSeconds = 45,
    [switch]$ExecuteAndCleanup
)

$ErrorActionPreference = "Stop"

function Assert-Condition {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Get-FirstNonBlank {
    param([string[]]$Values)
    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
    }
    return ""
}

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory)][ValidateSet("Get", "Post", "Delete")][string]$Method,
        [Parameter(Mandatory)][string]$Uri,
        [hashtable]$Headers = @{},
        [AllowNull()]$Body
    )
    $parameters = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        TimeoutSec  = 40
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-ExpectedError {
    param(
        [Parameter(Mandatory)][ValidateSet("Get", "Post")][string]$Method,
        [Parameter(Mandatory)][string]$Uri,
        [hashtable]$Headers = @{},
        [AllowNull()]$Body,
        [Parameter(Mandatory)][int[]]$ExpectedStatuses,
        [string]$ExpectedError = ""
    )
    $parameters = @{
        Method                = $Method
        Uri                   = $Uri
        Headers               = $Headers
        TimeoutSec            = 40
        SkipHttpErrorCheck    = $true
        ErrorAction           = "Stop"
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    $response = Invoke-WebRequest @parameters
    $status = [int]$response.StatusCode
    Assert-Condition ($status -in $ExpectedStatuses) "Expected HTTP [$($ExpectedStatuses -join ', ')], got $status from $Uri."
    if (-not [string]::IsNullOrWhiteSpace($ExpectedError)) {
        $payload = $response.Content | ConvertFrom-Json
        Assert-Condition ([string]$payload.error -eq $ExpectedError) `
            "Expected error '$ExpectedError', got '$($payload.error)'."
    }
    [pscustomobject]@{
        Status = $status
        Body   = [string]$response.Content
    }
}

function Get-ExecutionEvents {
    param(
        [Parameter(Mandatory)][string]$ExecutionId,
        [Parameter(Mandatory)][hashtable]$Headers
    )
    $raw = (Invoke-WebRequest `
            -Method Get `
            -Uri "$script:Root/rag-execution/api/executions/$ExecutionId/events" `
            -Headers $Headers `
            -TimeoutSec 40).Content
    $parsed = $raw | ConvertFrom-Json
    @($parsed)
}

function Get-ExactActiveFolders {
    param(
        [Parameter(Mandatory)][string]$FolderName,
        [Parameter(Mandatory)][hashtable]$Headers
    )
    $encoded = [uri]::EscapeDataString($FolderName)
    $response = Invoke-JsonRequest `
            -Method Get `
            -Uri "$script:Root/api/storage/nodes?recursive=true&keyword=$encoded&page=1&size=20&sortBy=updatedAt&sortDirection=DESC" `
            -Headers $Headers
    @($response.items | Where-Object {
        [string]$_.name -eq $FolderName -and [string]$_.type -eq "FOLDER"
    })
}

function New-FolderPlan {
    param(
        [Parameter(Mandatory)][string]$Message,
        [Parameter(Mandatory)][string]$ExpectedFolderName,
        [Parameter(Mandatory)][hashtable]$Headers
    )
    $before = @(Get-ExactActiveFolders -FolderName $ExpectedFolderName -Headers $Headers)
    Assert-Condition ($before.Count -eq 0) "Synthetic folder already exists: $ExpectedFolderName"
    $response = Invoke-JsonRequest `
            -Method Post `
            -Uri "$script:Root/rag/api/assistant/plan" `
            -Headers $Headers `
            -Body @{
                message        = $Message
                conversationId = $null
                clientContext  = @{
                    currentFolderId       = $null
                    currentFolderPath     = "/"
                    availableClientInputs = @{}
                    actionContractVersion = "action_bridge_v2"
                    supportedActionTypes  = @("folder.create")
                }
            }
    Assert-Condition ([string]$response.intentId -eq "folder_create") `
        "Expected folder_create, got '$($response.intentId)' for '$Message'."
    Assert-Condition ($null -ne $response.executionReference) "Plan returned no execution reference."
    $executionId = [string]$response.executionReference.executionId
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($executionId)) "Execution reference has no id."
    # Register cleanup ownership before validating the interpreted payload. A parser
    # mismatch must never leave an unconfirmed production probe behind.
    $script:RegisteredExecutions[$executionId] = $ExpectedFolderName
    Assert-Condition ([long]$response.executionReference.version -eq 0) "Initial execution version is not zero."
    $step = @($response.actionPlan.steps)[0]
    Assert-Condition ([string]$step.action -eq "folder.create") "Plan action is not folder.create."
    Assert-Condition ([string]$step.params.folderName -eq $ExpectedFolderName) `
        "Folder name mismatch. Expected '$ExpectedFolderName', got '$($step.params.folderName)'."
    $execution = Invoke-JsonRequest `
            -Method Get `
            -Uri "$script:Root/rag-execution/api/executions/$executionId" `
            -Headers $Headers
    $executionStep = @($execution.steps)[0]
    Assert-Condition ([string]$execution.status -eq "PENDING_CONFIRMATION") "Task is not pending confirmation."
    Assert-Condition ([long]$execution.version -eq 0) "Registered task version is not zero."
    Assert-Condition ([string]$executionStep.actionType -eq "FOLDER_CREATE") "Registered action is not FOLDER_CREATE."
    Assert-Condition ([string]$executionStep.status -eq "PENDING") "Registered step is not pending."
    Assert-Condition ([int]$executionStep.attempts -eq 0) "Unconfirmed step already has attempts."
    [pscustomobject]@{
        ExecutionId = $executionId
        FolderName  = $ExpectedFolderName
        Response    = $response
    }
}

function Cancel-And-Verify {
    param(
        [Parameter(Mandatory)][string]$ExecutionId,
        [Parameter(Mandatory)][string]$FolderName,
        [Parameter(Mandatory)][hashtable]$Headers,
        [switch]$Repeat
    )
    $cancelled = Invoke-JsonRequest `
            -Method Post `
            -Uri "$script:Root/rag-execution/api/executions/$ExecutionId/cancel" `
            -Headers $Headers
    Assert-Condition ([string]$cancelled.status -eq "CANCELLED") "Cancellation did not return CANCELLED."
    Assert-Condition ([long]$cancelled.version -eq 1) "Cancellation did not advance version to one."
    if ($Repeat) {
        $again = Invoke-JsonRequest `
                -Method Post `
                -Uri "$script:Root/rag-execution/api/executions/$ExecutionId/cancel" `
                -Headers $Headers
        Assert-Condition ([string]$again.status -eq "CANCELLED" -and [long]$again.version -eq 1) `
            "Repeated cancellation is not idempotent."
    }
    Start-Sleep -Milliseconds 500
    $settled = Invoke-JsonRequest `
            -Method Get `
            -Uri "$script:Root/rag-execution/api/executions/$ExecutionId" `
            -Headers $Headers
    Assert-Condition ([string]$settled.status -eq "CANCELLED") "Cancelled task changed status."
    Assert-Condition ([int](@($settled.steps)[0].attempts) -eq 0) "Cancelled task was dispatched."
    $events = @(Get-ExecutionEvents -ExecutionId $ExecutionId -Headers $Headers)
    $types = @($events | ForEach-Object { [string]$_.type })
    Assert-Condition (($types | Where-Object { $_ -eq "EXECUTION_CANCELLED" }).Count -eq 1) `
        "Cancellation event count is not one."
    $nodes = @(Get-ExactActiveFolders -FolderName $FolderName -Headers $Headers)
    Assert-Condition ($nodes.Count -eq 0) "Cancelled task created a storage folder."
}

function Wait-TerminalExecution {
    param(
        [Parameter(Mandatory)][string]$ExecutionId,
        [Parameter(Mandatory)][hashtable]$Headers
    )
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TerminalTimeoutSeconds)
    do {
        $execution = Invoke-JsonRequest `
                -Method Get `
                -Uri "$script:Root/rag-execution/api/executions/$ExecutionId" `
                -Headers $Headers
        if ([string]$execution.status -in @("SUCCEEDED", "FAILED", "PARTIALLY_SUCCEEDED", "CANCELLED", "EXPIRED")) {
            return $execution
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Execution $ExecutionId did not reach a terminal state."
}

function Invoke-ConcurrentPosts {
    param(
        [Parameter(Mandatory)][object[]]$Requests,
        [Parameter(Mandatory)][string]$Token
    )
    $client = [System.Net.Http.HttpClient]::new()
    $messages = [System.Collections.Generic.List[System.Net.Http.HttpRequestMessage]]::new()
    $tasks = [System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]]::new()
    try {
        foreach ($request in $Requests) {
            $message = [System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::Post,
                [string]$request.Uri
            )
            $message.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
            if ($null -ne $request.Body) {
                $json = $request.Body | ConvertTo-Json -Depth 20 -Compress
                $message.Content = [System.Net.Http.StringContent]::new(
                    $json,
                    [System.Text.Encoding]::UTF8,
                    "application/json"
                )
            }
            $messages.Add($message)
            $tasks.Add($client.SendAsync($message))
        }
        [System.Threading.Tasks.Task]::WhenAll($tasks.ToArray()).GetAwaiter().GetResult() | Out-Null
        $results = foreach ($task in $tasks) {
            $response = $task.GetAwaiter().GetResult()
            try {
                $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                [pscustomobject]@{
                    Status  = [int]$response.StatusCode
                    Content = $content
                    Json    = if ([string]::IsNullOrWhiteSpace($content)) { $null } else { $content | ConvertFrom-Json }
                }
            } finally {
                $response.Dispose()
            }
        }
        @($results)
    } finally {
        foreach ($message in $messages) {
            $message.Dispose()
        }
        $client.Dispose()
    }
}

function Remove-SyntheticFolder {
    param(
        [Parameter(Mandatory)][string]$FolderName,
        [Parameter(Mandatory)][hashtable]$Headers
    )
    Assert-Condition ($FolderName.StartsWith("rag-active-", [System.StringComparison]::Ordinal)) `
        "Refusing to clean a folder outside the synthetic prefix."
    $nodes = @(Get-ExactActiveFolders -FolderName $FolderName -Headers $Headers)
    foreach ($node in $nodes) {
        $nodeId = [long]$node.id
        Invoke-JsonRequest -Method Delete -Uri "$script:Root/api/storage/nodes/$nodeId" -Headers $Headers | Out-Null
        Invoke-JsonRequest -Method Delete -Uri "$script:Root/api/storage/trash/$nodeId" -Headers $Headers | Out-Null
    }
    $remaining = @(Get-ExactActiveFolders -FolderName $FolderName -Headers $Headers)
    Assert-Condition ($remaining.Count -eq 0) "Synthetic folder cleanup failed: $FolderName"
}

$script:Root = $BaseUrl.TrimEnd("/")
$account = Get-FirstNonBlank @($env:ALICIA_RAG_ACTIVE_ACCOUNT, $env:ALICIA_VERIFY_ACCOUNT)
$password = Get-FirstNonBlank @($env:ALICIA_RAG_ACTIVE_PASSWORD, $env:ALICIA_VERIFY_PASSWORD)
Assert-Condition (-not [string]::IsNullOrWhiteSpace($account)) "RAG active-test account is required."
Assert-Condition (-not [string]::IsNullOrWhiteSpace($password)) "RAG active-test password is required."

$script:RegisteredExecutions = @{}
$syntheticWriteNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$login = $null
$headers = $null
$failure = $null
$runId = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).ToString("yyyyMMdd-HHmmss")

try {
    $login = Invoke-JsonRequest `
            -Method Post `
            -Uri "$script:Root/api/identity/auth/login" `
            -Body @{ identifier = $account; password = $password }
    Assert-Condition (-not [string]::IsNullOrWhiteSpace([string]$login.token)) "Identity login returned no token."
    $headers = @{ Authorization = "Bearer $($login.token)" }
    $access = Invoke-JsonRequest -Method Get -Uri "$script:Root/rag/api/assistant/auth/access" -Headers $headers
    Assert-Condition ([string]$access.role -eq "RAG_ADMIN") "Test account is not RAG_ADMIN."
    Write-Host "[OK] dedicated RAG_ADMIN authenticated; token remains in process memory"

    Invoke-ExpectedError `
            -Method Get `
            -Uri "$script:Root/rag-execution/api/executions/00000000-0000-0000-0000-000000000001" `
            -ExpectedStatuses @(401, 403) | Out-Null
    Write-Host "[OK] public execution route rejects missing authentication"

    $cases = @(
        @{ Name = "rag-active-en-$runId-01"; Message = "Create a folder named rag-active-en-$runId-01" },
        @{ Name = "rag-active-zh-$runId-02"; Message = "请在根目录创建一个名为 rag-active-zh-$runId-02 的文件夹" },
        @{ Name = "rag-active-cn-$runId-03"; Message = "创建一个文件夹，名称是 rag-active-cn-$runId-03" },
        @{ Name = "rag-active-call-$runId-04"; Message = "Please create a directory called rag-active-call-$runId-04" }
    )

    $index = 0
    foreach ($case in $cases) {
        $index++
        $plan = New-FolderPlan -Message $case.Message -ExpectedFolderName $case.Name -Headers $headers
        if ($index -eq 2) {
            Invoke-ExpectedError `
                    -Method Post `
                    -Uri "$script:Root/rag-execution/api/executions/$($plan.ExecutionId)/confirm" `
                    -Headers $headers `
                    -Body @{ expectedVersion = 1 } `
                    -ExpectedStatuses @(409) `
                    -ExpectedError "execution_version_conflict" | Out-Null
        }
        Cancel-And-Verify `
                -ExecutionId $plan.ExecutionId `
                -FolderName $case.Name `
                -Headers $headers `
                -Repeat:($index -eq 1)
        if ($index -eq 1) {
            Invoke-ExpectedError `
                    -Method Post `
                    -Uri "$script:Root/rag-execution/api/executions/$($plan.ExecutionId)/confirm" `
                    -Headers $headers `
                    -Body @{ expectedVersion = 0 } `
                    -ExpectedStatuses @(409) `
                    -ExpectedError "execution_not_confirmable" | Out-Null
        }
        Write-Host "[OK] plan/cancel case $index exactName=$($case.Name) executionId=$($plan.ExecutionId)"
    }

    if ($ExecuteAndCleanup) {
        $executeName = "rag-active-confirm-$runId"
        [void]$syntheticWriteNames.Add($executeName)
        $executePlan = New-FolderPlan `
                -Message "Create a folder named $executeName" `
                -ExpectedFolderName $executeName `
                -Headers $headers
        $confirmUri = "$script:Root/rag-execution/api/executions/$($executePlan.ExecutionId)/confirm"
        $confirmResults = @(Invoke-ConcurrentPosts `
                -Requests @(
                    @{ Uri = $confirmUri; Body = @{ expectedVersion = 0 } },
                    @{ Uri = $confirmUri; Body = @{ expectedVersion = 0 } }
                ) `
                -Token ([string]$login.token))
        Assert-Condition ($confirmResults.Count -eq 2) "Concurrent confirmation returned the wrong response count."
        Assert-Condition (@($confirmResults | Where-Object { $_.Status -eq 200 }).Count -eq 2) `
            "Concurrent idempotent confirmation did not return two successful responses."
        $terminal = Wait-TerminalExecution -ExecutionId $executePlan.ExecutionId -Headers $headers
        Assert-Condition ([string]$terminal.status -eq "SUCCEEDED") `
            "Confirmed execution ended as '$($terminal.status)'."
        Assert-Condition ([int](@($terminal.steps)[0].attempts) -eq 1) "Confirmed step attempts is not one."
        $nodes = @(Get-ExactActiveFolders -FolderName $executeName -Headers $headers)
        Assert-Condition ($nodes.Count -eq 1) "Confirmed task did not create exactly one folder."
        $events = @(Get-ExecutionEvents -ExecutionId $executePlan.ExecutionId -Headers $headers)
        Assert-Condition (@($events | Where-Object { [string]$_.type -eq "EXECUTION_CONFIRMED" }).Count -eq 1) `
            "Concurrent confirmation produced duplicate confirmation events."
        Remove-SyntheticFolder -FolderName $executeName -Headers $headers
        Write-Host "[OK] concurrent confirmation created once and cleaned executionId=$($executePlan.ExecutionId)"

        $raceName = "rag-active-race-$runId"
        [void]$syntheticWriteNames.Add($raceName)
        $racePlan = New-FolderPlan `
                -Message "Create a folder named $raceName" `
                -ExpectedFolderName $raceName `
                -Headers $headers
        $raceResults = @(Invoke-ConcurrentPosts `
                -Requests @(
                    @{
                        Uri  = "$script:Root/rag-execution/api/executions/$($racePlan.ExecutionId)/confirm"
                        Body = @{ expectedVersion = 0 }
                    },
                    @{
                        Uri  = "$script:Root/rag-execution/api/executions/$($racePlan.ExecutionId)/cancel"
                        Body = $null
                    }
                ) `
                -Token ([string]$login.token))
        Assert-Condition (@($raceResults | Where-Object { $_.Status -eq 200 }).Count -ge 1) `
            "Confirm/cancel race returned no successful transition."
        Assert-Condition (@($raceResults | Where-Object { $_.Status -notin @(200, 409) }).Count -eq 0) `
            "Confirm/cancel race returned an unexpected HTTP status."
        $raceTerminal = Wait-TerminalExecution -ExecutionId $racePlan.ExecutionId -Headers $headers
        Assert-Condition ([string]$raceTerminal.status -in @("SUCCEEDED", "CANCELLED")) `
            "Confirm/cancel race ended as '$($raceTerminal.status)'."
        $raceNodes = @(Get-ExactActiveFolders -FolderName $raceName -Headers $headers)
        if ([string]$raceTerminal.status -eq "SUCCEEDED") {
            Assert-Condition ($raceNodes.Count -eq 1) "Successful race did not create exactly one folder."
            Assert-Condition ([int](@($raceTerminal.steps)[0].attempts) -eq 1) "Successful race dispatched more than once."
            Remove-SyntheticFolder -FolderName $raceName -Headers $headers
        } else {
            Assert-Condition ($raceNodes.Count -eq 0) "Cancelled race created a folder."
            Assert-Condition ([int](@($raceTerminal.steps)[0].attempts) -eq 0) "Cancelled race was dispatched."
        }
        Write-Host "[OK] confirm/cancel race terminal=$($raceTerminal.status) executionId=$($racePlan.ExecutionId)"
    }

    Write-Host "RAG admin active verification passed: cases=$($cases.Count) executeAndCleanup=$ExecuteAndCleanup"
} catch {
    $failure = $_
    throw
} finally {
    if ($headers -ne $null) {
        foreach ($entry in $script:RegisteredExecutions.GetEnumerator()) {
            try {
                $execution = Invoke-JsonRequest `
                        -Method Get `
                        -Uri "$script:Root/rag-execution/api/executions/$($entry.Key)" `
                        -Headers $headers
                if ([string]$execution.status -in @("PENDING_CONFIRMATION", "QUEUED", "WAITING_CLIENT_INPUT")) {
                    Invoke-JsonRequest `
                            -Method Post `
                            -Uri "$script:Root/rag-execution/api/executions/$($entry.Key)/cancel" `
                            -Headers $headers | Out-Null
                } elseif ([string]$execution.status -in @("RUNNING", "RETRY_WAIT")) {
                    # A dispatched write cannot be cancelled safely. Wait for its terminal
                    # state before the synthetic-folder cleanup below, including on failure.
                    Wait-TerminalExecution -ExecutionId $entry.Key -Headers $headers | Out-Null
                }
            } catch {
                Write-Warning "Could not reconcile execution $($entry.Key): $($_.Exception.Message)"
            }
        }
        foreach ($name in $syntheticWriteNames) {
            try {
                Remove-SyntheticFolder -FolderName $name -Headers $headers
            } catch {
                Write-Warning "Could not clean synthetic folder '$name': $($_.Exception.Message)"
            }
        }
        try {
            Invoke-JsonRequest `
                    -Method Post `
                    -Uri "$script:Root/api/identity/auth/logout" `
                    -Headers $headers `
                    -Body @{ refreshToken = $login.refreshToken; allDevices = $false } | Out-Null
            Write-Host "[OK] temporary Identity session logged out"
        } catch {
            Write-Warning "Temporary Identity logout failed."
        }
    }
}
