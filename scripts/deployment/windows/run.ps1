param([ValidateSet('container', 'local')][string]$Mode = 'container')
. "$PSScriptRoot\common.ps1"
if (Test-DeploymentComplete) {
    Write-Host "Deployment $(Get-DemoVersion) is already complete; composing existing state."
    New-Item -ItemType Directory -Force -Path $script:LogDir | Out-Null
    Set-DeploymentStatus 'OLLAMA_MODE' $Mode
    Set-DeploymentStatus 'LAST_STARTED_AT' ([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))
    & "$PSScriptRoot\2_compose.ps1" $Mode resume 2>&1 | Tee-Object -FilePath (Join-Path $script:LogDir 'run.log')
    if (-not $?) { throw 'Compose resume failed.' }
} else {
    Write-Host 'No completed deployment state found; running the full lifecycle.'
    & "$PSScriptRoot\deploy.ps1" $Mode
}
