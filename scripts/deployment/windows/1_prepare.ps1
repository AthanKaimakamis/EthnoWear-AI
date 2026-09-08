param()
. "$PSScriptRoot\common.ps1"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required.' }
& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required.' }
& docker info | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker is not running.' }

if (-not (Test-Path $script:EnvFile)) {
    Copy-Item (Join-Path $script:ProjectRoot '.env.demo.example') $script:EnvFile
    Write-Host "Created $script:EnvFile from .env.demo.example"
} else {
    Write-Host "Using existing $script:EnvFile"
}
New-Item -ItemType Directory -Force -Path (Join-Path $script:ProjectRoot 'media') | Out-Null

function Test-ChecksumFile([string]$Manifest, [string]$BaseDirectory) {
    foreach ($line in Get-Content $Manifest) {
        if ($line -notmatch '^([0-9a-fA-F]{64})  (.+)$') { throw "Invalid checksum line in $Manifest" }
        $expected = $Matches[1].ToLowerInvariant()
        $target = Join-Path $BaseDirectory ($Matches[2] -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path $target)) { throw "Missing demo artifact: $target" }
        $actual = (Get-FileHash -Algorithm SHA256 $target).Hash.ToLowerInvariant()
        if ($actual -ne $expected) { throw "Checksum mismatch: $target" }
        Write-Host "$target`: OK"
    }
}

Test-ChecksumFile (Join-Path $script:ProjectRoot 'demo-state\artifacts.sha256') $script:ProjectRoot
Test-ChecksumFile (Join-Path $script:ProjectRoot 'demo-state\media.sha256') (Join-Path $script:ProjectRoot 'demo-state')
Invoke-Compose --profile demo config --quiet
Write-Host 'Preparation complete.'
