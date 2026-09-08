$ErrorActionPreference = 'Stop'

$script:DeploymentDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:ProjectRoot = (Resolve-Path (Join-Path $script:DeploymentDir '..\..\..')).Path
$script:EnvFile = if ($env:ETHNOWEAR_DEPLOY_ENV_FILE) { $env:ETHNOWEAR_DEPLOY_ENV_FILE } else { Join-Path $script:ProjectRoot '.env' }
$script:StateDir = Join-Path $script:ProjectRoot '.deployment'
$script:StateFile = Join-Path $script:StateDir 'status.env'
$script:LogDir = Join-Path $script:StateDir 'logs'

function Invoke-Compose {
    & docker compose --project-directory $script:ProjectRoot --env-file $script:EnvFile @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE" }
}

function Assert-Mode([string]$Mode) {
    if ($Mode -notin @('container', 'local')) { throw 'Mode must be container or local.' }
}

function Set-DeploymentStatus([string]$Key, [string]$Value) {
    New-Item -ItemType Directory -Force -Path $script:StateDir, $script:LogDir | Out-Null
    $lines = if (Test-Path $script:StateFile) { Get-Content $script:StateFile | Where-Object { $_ -notmatch "^$([regex]::Escape($Key))=" } } else { @() }
    @($lines) + "$Key=$Value" | Set-Content -Encoding utf8 $script:StateFile
}

function Get-DeploymentStatus([string]$Key) {
    if (-not (Test-Path $script:StateFile)) { return $null }
    $line = Get-Content $script:StateFile | Where-Object { $_ -like "$Key=*" } | Select-Object -Last 1
    if ($line) { return $line.Substring($Key.Length + 1) }
    return $null
}

function Get-DemoVersion {
    $line = Get-Content (Join-Path $script:ProjectRoot 'demo-state\manifest.env') | Where-Object { $_ -like 'ETHNOWEAR_DEMO_VERSION=*' } | Select-Object -Last 1
    return $line.Substring('ETHNOWEAR_DEMO_VERSION='.Length)
}

function Test-DeploymentComplete {
    return (Get-DeploymentStatus 'DEPLOYMENT_STATUS') -eq 'complete' -and (Get-DeploymentStatus 'DEMO_VERSION') -eq (Get-DemoVersion)
}
