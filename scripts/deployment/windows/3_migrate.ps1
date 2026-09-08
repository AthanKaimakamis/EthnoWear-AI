param([ValidateSet('container', 'local')][string]$Mode = 'container')
. "$PSScriptRoot\common.ps1"
Assert-Mode $Mode
if ($Mode -eq 'local') { $env:ETHNOWEAR_OLLAMA_BASE_URL = 'http://host.docker.internal:11434' }

foreach ($service in @('database-migration', 'demo-media-bootstrap', 'demo-qdrant-bootstrap')) {
    $id = & docker compose --project-directory $script:ProjectRoot --env-file $script:EnvFile --profile demo ps -aq $service
    if (-not $id) { throw "Missing Compose service container: $service" }
    Invoke-Compose --profile demo wait $service
}
if ($Mode -eq 'container') {
    Write-Host 'Waiting for the required Ollama models...'
    Invoke-Compose --profile container-ai wait ollama-models
}
Invoke-Compose --profile demo wait demo-bootstrap
Write-Host 'Database migration, demo restoration, and readiness completed successfully.'
