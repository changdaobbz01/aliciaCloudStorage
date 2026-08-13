param(
    [string]$DeviceSerial = "",
    [string]$ApiBaseUrl = "https://windwindwind-alicia.cn",
    [string]$RagBaseUrl = "https://windwindwind-alicia.cn/rag",
    [switch]$EnableActionExecution
)

$ErrorActionPreference = "Stop"
$appRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $appRoot "gradlew.bat"
$adb = (Get-Command adb -ErrorAction Stop).Source
$ApiBaseUrl = $ApiBaseUrl.TrimEnd("/")
$RagBaseUrl = $RagBaseUrl.TrimEnd("/")

$healthUri = "$RagBaseUrl/api/health"
$health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 10
if ($health.status -ne "ok" -or -not $health.deepseekConfigured -or -not $health.storageApiConfigured) {
    throw "Cloud RAG is not ready at $healthUri."
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
& $gradleWrapper :app:installDebug `
    "-PALICIA_API_BASE_URL=$ApiBaseUrl" `
    "-PALICIA_RAG_BASE_URL=$RagBaseUrl" `
    "-PALICIA_RAG_ACTION_EXECUTION_ENABLED=$executionEnabled"
if ($LASTEXITCODE -ne 0) {
    throw "Cloud Debug APK installation failed."
}

$reverseMappings = & $adb -s $DeviceSerial reverse --list
if ($reverseMappings -match 'tcp:8081\s+tcp:8081') {
    & $adb -s $DeviceSerial reverse --remove tcp:8081
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to remove the local RAG adb reverse mapping."
    }
}

$deviceCurl = (& $adb -s $DeviceSerial shell "command -v curl").Trim()
if ($deviceCurl) {
    & $adb -s $DeviceSerial shell "curl -fsS --max-time 10 $healthUri" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "The Android device cannot reach cloud RAG at $healthUri."
    }
}

Write-Host "Cloud Debug device ready: $DeviceSerial -> $RagBaseUrl"
Write-Host "RAG action execution enabled: $executionEnabled"
