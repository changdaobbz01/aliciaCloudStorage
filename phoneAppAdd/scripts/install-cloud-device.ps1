param(
    [string]$DeviceSerial = "",
    [string]$ApiBaseUrl = "https://windwindwind-alicia.cn",
    [string]$RagBaseUrl = "https://windwindwind-alicia.cn/rag",
    [string]$RagExecutionBaseUrl = "https://windwindwind-alicia.cn/rag-execution",
    [switch]$EnableCloudExecution,
    [switch]$EnableActionExecution
)

$ErrorActionPreference = "Stop"
$appRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $appRoot "gradlew.bat"
$adb = (Get-Command adb -ErrorAction Stop).Source
$ApiBaseUrl = $ApiBaseUrl.TrimEnd("/")
$RagBaseUrl = $RagBaseUrl.TrimEnd("/")
$RagExecutionBaseUrl = $RagExecutionBaseUrl.TrimEnd("/")

$healthUri = "$RagBaseUrl/api/health"
$health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 10
if ($health.status -ne "ok" -or -not $health.deepseekConfigured -or -not $health.storageApiConfigured) {
    throw "Cloud RAG is not ready at $healthUri."
}

if ($EnableCloudExecution) {
    $executionHealthUri = "$RagExecutionBaseUrl/api/health/dependencies"
    $executionHealth = Invoke-RestMethod -Uri $executionHealthUri -TimeoutSec 10
    if ($executionHealth.status -ne "ok") {
        throw "Cloud RAG execution is not ready at $executionHealthUri."
    }
}

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

$executionEnabled = if ($EnableActionExecution) { "true" } else { "false" }
$cloudExecutionEnabled = if ($EnableCloudExecution) { "true" } else { "false" }
$gradleExitCode = 0
Push-Location $appRoot
try {
    & $gradleWrapper :app:installDebug `
        "-PALICIA_API_BASE_URL=$ApiBaseUrl" `
        "-PALICIA_RAG_BASE_URL=$RagBaseUrl" `
        "-PALICIA_RAG_EXECUTION_BASE_URL=$RagExecutionBaseUrl" `
        "-PALICIA_RAG_CLOUD_EXECUTION_ENABLED=$cloudExecutionEnabled" `
        "-PALICIA_RAG_ACTION_EXECUTION_ENABLED=$executionEnabled"
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($gradleExitCode -ne 0) {
    throw "Cloud Debug APK installation failed."
}

$reverseMappings = & $adb -s $DeviceSerial reverse --list
foreach ($localPort in @(8081, 8084)) {
    if ($reverseMappings -match "tcp:$localPort\s+tcp:$localPort") {
        & $adb -s $DeviceSerial reverse --remove "tcp:$localPort"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove the local adb reverse mapping for port $localPort."
        }
    }
}

$deviceCurl = (& $adb -s $DeviceSerial shell "command -v curl").Trim()
if ($deviceCurl) {
    & $adb -s $DeviceSerial shell "curl -fsS --max-time 10 $healthUri" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "The Android device cannot reach cloud RAG at $healthUri."
    }
    if ($EnableCloudExecution) {
        & $adb -s $DeviceSerial shell "curl -fsS --max-time 10 $executionHealthUri" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "The Android device cannot reach cloud RAG execution at $executionHealthUri."
        }
    }
}

Write-Host "Cloud Debug device ready: $DeviceSerial -> $RagBaseUrl"
Write-Host "RAG cloud execution enabled: $cloudExecutionEnabled -> $RagExecutionBaseUrl"
Write-Host "Legacy local RAG action execution enabled: $executionEnabled"
