param(
    [string]$DeviceSerial = "",
    [int]$RagPort = 8081,
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
    & $gradleWrapper :app:installDebug
    if ($LASTEXITCODE -ne 0) {
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

$deviceCurl = (& $adb -s $DeviceSerial shell "command -v curl").Trim()
if ($deviceCurl) {
    & $adb -s $DeviceSerial shell "curl -fsS --max-time 5 $localHealthUri" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "The Android device cannot reach RAG through adb reverse on port $RagPort."
    }
}

Write-Host "Debug device ready: $DeviceSerial -> RAG tcp:$RagPort"
