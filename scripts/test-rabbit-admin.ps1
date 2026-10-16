$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $projectRoot '.env'))) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if (-not $values['RABBITMQ_PASSWORD']) { throw 'Prepara el entorno con scripts/start-local.cmd' }
$names = @('TEST_RABBIT_ADMIN','RABBITMQ_USERNAME','RABBITMQ_PASSWORD','TEST_RABBITMQ_PORT','TEST_RABBITMQ_MANAGEMENT_PORT')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$validationDir = Join-Path $projectRoot '.validation'
New-Item -ItemType Directory -Path $validationDir -Force | Out-Null
$log = Join-Path $validationDir 'rabbit-admin-real-tests.log'
try {
    $env:TEST_RABBIT_ADMIN = 'true'
    $env:RABBITMQ_USERNAME = if ($values['RABBITMQ_USERNAME']) { $values['RABBITMQ_USERNAME'] } else { 'andesstay' }
    $env:RABBITMQ_PASSWORD = $values['RABBITMQ_PASSWORD']
    $env:TEST_RABBITMQ_PORT = if ($values['RABBITMQ_PORT']) { $values['RABBITMQ_PORT'] } else { '5672' }
    $env:TEST_RABBITMQ_MANAGEMENT_PORT = if ($values['RABBITMQ_MANAGEMENT_PORT']) { $values['RABBITMQ_MANAGEMENT_PORT'] } else { '15672' }
    Push-Location (Join-Path $projectRoot 'AndesStayRabbitAdmin')
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dtest=RealRabbitAdminIntegrationTests' test *> $log
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
    } finally { Pop-Location }
    Select-String -LiteralPath $log -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { $_.Line }
    if ($testExit -ne 0) { throw "Fallo Rabbit Admin; revisa $log" }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
Write-Output 'Rabbit Admin: CRUD, permisos, politicas y rutas reales verificados; recursos temporales eliminados.'
