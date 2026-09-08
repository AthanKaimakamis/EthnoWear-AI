param(
    [ValidateSet('container', 'local')][string]$Mode = 'container',
    [ValidateSet('deploy', 'resume')][string]$Action = 'deploy'
)
. "$PSScriptRoot\common.ps1"
Assert-Mode $Mode
if (-not (Test-Path $script:EnvFile)) { throw 'Run scripts/deployment/windows/1_prepare.ps1 first.' }

if ($Mode -eq 'local') {
    Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 10 | Out-Null
    $env:ETHNOWEAR_OLLAMA_BASE_URL = 'http://host.docker.internal:11434'
    try { Invoke-Compose --profile container-ai stop ollama ollama-models } catch { }
    $profiles = @('--profile', 'demo')
} else {
    $profiles = @('--profile', 'demo', '--profile', 'container-ai')
}

if ($Action -eq 'resume') {
    $infrastructure = @('sqlserver', 'qdrant')
    if ($Mode -eq 'container') { $infrastructure += 'ollama' }
    Invoke-Compose @profiles up -d --build --no-deps @infrastructure
    Invoke-Compose @profiles up -d --build --no-deps api document-worker indexing-worker quality-worker figure-worker vision-worker frontend
} else {
    Invoke-Compose @profiles up -d --build
}
Write-Host "Compose $Action complete for Ollama mode: $Mode"
