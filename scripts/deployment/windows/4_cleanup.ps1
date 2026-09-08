param()
. "$PSScriptRoot\common.ps1"
if (-not (Test-Path $script:EnvFile)) { throw "Missing deployment environment: $script:EnvFile" }
Invoke-Compose --profile demo --profile container-ai rm -f database-migration demo-media-bootstrap demo-qdrant-bootstrap demo-bootstrap ollama-models
Write-Host 'Completed migration and bootstrap containers removed.'
