param(
    [int]$Port = 8081,
    [switch]$SkipEnvironmentFile
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ragRoot = Join-Path $repositoryRoot 'rag'
$jarPath = Join-Path $ragRoot 'target\rag-service-0.0.1-SNAPSHOT.jar'
$runtimeRoot = Join-Path $ragRoot 'target\runtime'
$environmentPath = Join-Path $repositoryRoot '.env'

if (-not $SkipEnvironmentFile -and (Test-Path -LiteralPath $environmentPath)) {
    foreach ($line in Get-Content -LiteralPath $environmentPath -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            continue
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            continue
        }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "RAG executable JAR was not found: $jarPath"
}

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$runtimeStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runtimeJarPath = Join-Path $runtimeRoot "rag-service-$Port-$runtimeStamp.jar"
Copy-Item -LiteralPath $jarPath -Destination $runtimeJarPath

[Environment]::SetEnvironmentVariable('SERVER_PORT', $Port.ToString(), 'Process')
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath $javaPath)) {
    $javaPath = (Get-Command java.exe -ErrorAction Stop).Source
}

$logPath = Join-Path $ragRoot "target\rag-service-$Port.log"
$errorLogPath = Join-Path $ragRoot "target\rag-service-$Port.error.log"
$process = Start-Process `
    -FilePath $javaPath `
    -ArgumentList @('-jar', $runtimeJarPath) `
    -WorkingDirectory $ragRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError $errorLogPath `
    -PassThru

[PSCustomObject]@{
    ProcessId = $process.Id
    Port = $Port
    RuntimeJarPath = $runtimeJarPath
    LogPath = $logPath
    ErrorLogPath = $errorLogPath
}
