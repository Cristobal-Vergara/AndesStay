$ErrorActionPreference = 'Stop'
$clusterInvocationRoot = Split-Path -Parent $PSScriptRoot
if ($env:TEST_RABBITMQ_CLUSTER -ne 'true' -or $env:CLUSTER_PROJECT -notmatch '^andesstay-cluster-test-[a-f0-9]{8}$') {
    throw 'Ejecuta esta prueba mediante scripts/test-rabbit-cluster.cmd.'
}
Push-Location (Join-Path $clusterInvocationRoot 'AndesStay Backend')
try {
    $ErrorActionPreference = 'Continue'
    & .\mvnw.cmd '-Dtest=RabbitClusterRecoveryTests' test *> $env:CLUSTER_MAVEN_LOG
    $clusterMavenExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Select-String -LiteralPath $env:CLUSTER_MAVEN_LOG -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { $_.Line }
    exit $clusterMavenExit
} finally { Pop-Location }
