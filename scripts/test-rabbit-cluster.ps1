$ErrorActionPreference = 'Stop'
foreach ($clusterTool in @('node','docker','java')) {
    if (-not (Get-Command $clusterTool -ErrorAction SilentlyContinue)) { throw "Falta $clusterTool. Necesitas Node 24, JDK 17 y Docker Desktop en modo Linux." }
}
& node (Join-Path $PSScriptRoot 'test-rabbit-cluster.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Fallo la prueba del cluster. Revisa .validation/rabbit-cluster-tests.log y rabbitmq-cluster-latest.json.' }
