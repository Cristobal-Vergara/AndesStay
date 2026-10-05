$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'setup-local.ps1')
& docker compose --env-file (Join-Path $projectRoot '.env') -f (Join-Path $projectRoot 'docker/docker-compose.yml') up --build -d --wait
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar el entorno local' }
Write-Output 'AndesStay disponible en http://localhost:5173'
