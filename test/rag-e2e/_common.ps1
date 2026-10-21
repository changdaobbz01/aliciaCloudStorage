Set-StrictMode -Version Latest

function Get-RagE2EContext {
    $suiteDirectory = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $suiteDirectory '..\..'))
    [pscustomobject]@{
        ProjectName    = 'alicia-rag-e2e'
        SuiteDirectory = $suiteDirectory
        RepositoryRoot = $repositoryRoot
        EnvFile        = Join-Path $suiteDirectory '.env.local'
        ExampleEnvFile = Join-Path $suiteDirectory '.env.example'
        ComposeFile    = Join-Path $suiteDirectory 'compose.yaml'
        ScenarioFile   = Join-Path $suiteDirectory 'scenarios.json'
        StressScenarioFile = Join-Path $suiteDirectory 'stress-scenarios.json'
        ContractBaselineFile = Join-Path $suiteDirectory 'contract-baseline.json'
        ResultsDir     = Join-Path $suiteDirectory 'results'
    }
}

function Import-RagE2EEnv {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing local environment file: $Path. Run prepare-local-env.ps1 first."
    }

    foreach ($rawLine in [System.IO.File]::ReadAllLines($Path)) {
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid dotenv entry in ${Path}: $rawLine"
        }
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw "Invalid dotenv variable name: $name"
        }
        [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Get-RagE2EEnvValue {
    param([Parameter(Mandatory)][string]$Name)
    [System.Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Assert-RagE2EEnvValue {
    param(
        [Parameter(Mandatory)][string]$Name,
        [int]$MinimumLength = 1
    )
    $value = Get-RagE2EEnvValue -Name $Name
    if ([string]::IsNullOrWhiteSpace($value) -or
        $value -eq 'GENERATE_ME' -or
        $value.Length -lt $MinimumLength) {
        throw "$Name is missing or invalid in .env.local."
    }
    $value
}

function Invoke-RagE2ENative {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

function Get-RagE2EComposeArguments {
    param([Parameter(Mandatory)]$Context)
    @(
        'compose',
        '--project-name', $Context.ProjectName,
        '--env-file', $Context.EnvFile,
        '--file', $Context.ComposeFile
    )
}

function Wait-RagE2EHttpOk {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Uri,
        [int]$Attempts = 60,
        [int]$DelaySeconds = 2
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Host "[OK] $Name is ready: $Uri"
                return
            }
        } catch {
            if ($attempt -eq $Attempts) {
                throw "$Name did not become ready at $Uri. $($_.Exception.Message)"
            }
        }
        Start-Sleep -Seconds $DelaySeconds
    }
}

function ConvertTo-RagE2EBool {
    param([AllowNull()][string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }
    switch ($Value.Trim().ToLowerInvariant()) {
        'true' { return $true }
        '1' { return $true }
        'yes' { return $true }
        default { return $false }
    }
}
