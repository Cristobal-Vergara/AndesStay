$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')) {
    if ($line -match '^([^#=\s]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$validationDir = Join-Path $projectRoot '.validation'
New-Item -ItemType Directory -Path $validationDir -Force | Out-Null
$names = @('TEST_RABBITMQ','TEST_RABBITMQ_PORT','RABBITMQ_USERNAME','RABBITMQ_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:TEST_RABBITMQ = 'true'
    $env:TEST_RABBITMQ_PORT = if ($values['RABBITMQ_PORT']) { $values['RABBITMQ_PORT'] } else { '5672' }
    $env:RABBITMQ_USERNAME = if ($values['RABBITMQ_USERNAME']) { $values['RABBITMQ_USERNAME'] } else { 'andesstay' }
    $env:RABBITMQ_PASSWORD = $values['RABBITMQ_PASSWORD']
    if (-not $env:RABBITMQ_PASSWORD) { throw 'Falta RABBITMQ_PASSWORD en .env; ejecuta scripts/setup-local.ps1.' }
    Push-Location (Join-Path $projectRoot 'AndesStay Backend')
    try {
        $log = Join-Path $validationDir 'listener-control-real-tests.log'
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dtest=ListenerControlBrokerTests' test *> $log
        $result = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Select-String -LiteralPath $log -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { $_.Line }
        if ($result -ne 0) { throw "Falló el control de consumidores con RabbitMQ real; revisa $log" }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
Write-Output 'RabbitMQ real: pausa gradual, ACK, mensajes en espera, audiencia independiente y reanudación verificados. Colas temporales eliminadas.'
