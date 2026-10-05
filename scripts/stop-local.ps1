$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& docker compose --env-file (Join-Path $projectRoot '.env') -f (Join-Path $projectRoot 'docker/docker-compose.yml') --profile operations stop
if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener el entorno local' }
Write-Output 'Servicios detenidos. Los volúmenes y datos se conservan.'
