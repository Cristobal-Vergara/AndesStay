$ErrorActionPreference = 'Stop'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Instala Node 24 LTS para ejecutar la prueba del gateway.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Instala e inicia Docker Desktop con contenedores Linux.' }
& node (Join-Path $PSScriptRoot 'test-api-gateway.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Fallo la prueba del gateway; revisa el mensaje anterior.' }
