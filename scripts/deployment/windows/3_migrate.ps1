param([ValidateSet('container', 'local')][string]$Mode = 'container')
. "$PSScriptRoot\common.ps1"
Assert-Mode $Mode
if (-not (Test-Path $script:EnvFile)) { throw 'Run scripts/deployment/windows/1_prepare.ps1 first.' }
if ($Mode -eq 'local') { $env:ETHNOWEAR_OLLAMA_BASE_URL = 'http://host.docker.internal:11434' }

function Wait-ServiceSuccess([string]$Service, [string]$Profile) {
    $id = & docker compose --project-directory $script:ProjectRoot --env-file $script:EnvFile --profile $Profile ps -aq $Service
    if ($LASTEXITCODE -ne 0 -or -not $id) { throw "Missing Compose service container: $Service" }
    $running = & docker inspect --format '{{.State.Running}}' $id
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect Compose service: $Service" }
    if ($running -eq 'true') {
        $exitCode = & docker wait $id
        if ($LASTEXITCODE -ne 0) { throw "Cannot wait for Compose service: $Service" }
    } else {
        $exitCode = & docker inspect --format '{{.State.ExitCode}}' $id
    }
    if ([int]$exitCode -ne 0) {
        & docker compose --project-directory $script:ProjectRoot --env-file $script:EnvFile --profile $Profile logs $Service
        throw "$Service failed with exit code $exitCode"
    }
    Write-Host "$Service completed successfully."
}

foreach ($service in @('database-migration', 'demo-media-bootstrap', 'demo-qdrant-bootstrap')) {
    Wait-ServiceSuccess $service 'demo'
}
if ($Mode -eq 'container') {
    Write-Host 'Waiting for the required Ollama models...'
    Wait-ServiceSuccess 'ollama-models' 'container-ai'
}
Wait-ServiceSuccess 'demo-bootstrap' 'demo'
Write-Host 'Database migration, demo restoration, and readiness completed successfully.'
