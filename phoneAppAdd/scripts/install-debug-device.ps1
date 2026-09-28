param(
    [string]$DeviceSerial = "",
    [int]$RagPort = 8081,
    [int]$RagExecutionPort = 8084,
    [switch]$EnableCloudExecution,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$appRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $appRoot "gradlew.bat"
$adb = (Get-Command adb -ErrorAction Stop).Source

$connectedDevices = @(
    & $adb devices |
        Select-String -Pattern '^([^\s]+)\s+device$' |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
)

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($connectedDevices.Count -ne 1) {
        throw "Expected exactly one connected Android device, found $($connectedDevices.Count). Pass -DeviceSerial when needed."
    }
    $DeviceSerial = $connectedDevices[0]
} elseif ($DeviceSerial -notin $connectedDevices) {
    throw "Android device '$DeviceSerial' is not connected and authorized."
}

if (-not $SkipInstall) {
    $cloudExecutionEnabled = if ($EnableCloudExecution) { "true" } else { "false" }
    $gradleExitCode = 0
    Push-Location $appRoot
    try {
        & $gradleWrapper :app:installDebug `
            "-PALICIA_RAG_EXECUTION_BASE_URL=http://127.0.0.1:$RagExecutionPort" `
            "-PALICIA_RAG_CLOUD_EXECUTION_ENABLED=$cloudExecutionEnabled" `
            "-PALICIA_RAG_ACTION_EXECUTION_ENABLED=false"
        $gradleExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($gradleExitCode -ne 0) {
        throw "Debug APK installation failed."
    }
}

$localHealthUri = "http://127.0.0.1:$RagPort/api/health"
try {
    Invoke-RestMethod -Uri $localHealthUri -TimeoutSec 5 | Out-Null
} catch {
    throw "Local RAG health check failed at $localHealthUri. Start the RAG service before installing the app."
}

& $adb -s $DeviceSerial reverse "tcp:$RagPort" "tcp:$RagPort"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create adb reverse mapping for port $RagPort."
}

if ($EnableCloudExecution) {
    $executionHealthUri = "http://127.0.0.1:$RagExecutionPort/api/health/dependencies"
    try {
        $executionHealth = Invoke-RestMethod -Uri $executionHealthUri -TimeoutSec 5
        if ($executionHealth.status -ne "ok") {
            throw "Unexpected health status '$($executionHealth.status)'."
        }
    } catch {
        throw "Local RAG execution health check failed at $executionHealthUri. Start the ragExecution service before installing the app."
    }

    & $adb -s $DeviceSerial reverse "tcp:$RagExecutionPort" "tcp:$RagExecutionPort"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create adb reverse mapping for port $RagExecutionPort."
    }
}

$deviceCurl = (& $adb -s $DeviceSerial shell "command -v curl").Trim()
if ($deviceCurl) {
    & $adb -s $DeviceSerial shell "curl -fsS --max-time 5 $localHealthUri" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "The Android device cannot reach RAG through adb reverse on port $RagPort."
    }
    if ($EnableCloudExecution) {
        & $adb -s $DeviceSerial shell "curl -fsS --max-time 5 $executionHealthUri" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "The Android device cannot reach RAG execution through adb reverse on port $RagExecutionPort."
        }
    }
}

Write-Host "Debug device ready: $DeviceSerial -> RAG tcp:$RagPort"
Write-Host "RAG cloud execution enabled: $EnableCloudExecution -> tcp:$RagExecutionPort"
