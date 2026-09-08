param([ValidateSet('container', 'local')][string]$Mode = 'container')
. "$PSScriptRoot\common.ps1"
Assert-Mode $Mode
New-Item -ItemType Directory -Force -Path $script:LogDir | Out-Null
Set-DeploymentStatus 'DEPLOYMENT_STATUS' 'running'
Set-DeploymentStatus 'DEMO_VERSION' (Get-DemoVersion)
Set-DeploymentStatus 'OLLAMA_MODE' $Mode
Set-DeploymentStatus 'STARTED_AT' ([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))

$stages = @(
    @{ Number='1'; Name='PREPARE'; Script='1_prepare.ps1'; Args=@() },
    @{ Number='2'; Name='COMPOSE'; Script='2_compose.ps1'; Args=@($Mode) },
    @{ Number='3'; Name='MIGRATION'; Script='3_migrate.ps1'; Args=@($Mode) },
    @{ Number='4'; Name='CLEANUP'; Script='4_cleanup.ps1'; Args=@() }
)
try {
    foreach ($stage in $stages) {
        $log = Join-Path $script:LogDir "$($stage.Number)_$($stage.Name).log"
        $stageArgs = $stage.Args
        Set-DeploymentStatus "$($stage.Name)_STATUS" 'running'
        Set-DeploymentStatus "$($stage.Name)_LOG" $log
        & (Join-Path $PSScriptRoot $stage.Script) @stageArgs 2>&1 | Tee-Object -FilePath $log
        if (-not $?) { throw "$($stage.Name) failed" }
        Set-DeploymentStatus "$($stage.Name)_STATUS" 'complete'
    }
    Set-DeploymentStatus 'DEPLOYMENT_STATUS' 'complete'
    Set-DeploymentStatus 'FINISHED_AT' ([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))
} catch {
    Set-DeploymentStatus 'DEPLOYMENT_STATUS' 'failed'
    throw
}
Write-Host 'EthnoWear demo deployment is ready.'
Write-Host 'Login: admin / admin'
Write-Host "Deployment status: $script:StateFile"
