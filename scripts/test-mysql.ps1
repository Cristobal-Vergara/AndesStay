$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $projectRoot '.env'))) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$suffix = [Guid]::NewGuid().ToString('N').Substring(0,12)
$schemas = @("andesstay_test_${suffix}_availability","andesstay_test_${suffix}_fresh","andesstay_test_${suffix}_copy")
foreach ($schema in $schemas) {
    if ($schema -notmatch '^andesstay_test_[a-f0-9]{12}_(availability|fresh|copy)$') { throw 'Nombre de esquema no permitido' }
}
if ($values['DB_USERNAME'] -notmatch '^[a-zA-Z0-9_]+$') { throw 'Usuario local no permitido' }
$validationDir = Join-Path $projectRoot '.validation'
New-Item -ItemType Directory -Path $validationDir -Force | Out-Null
$dump = Join-Path $projectRoot 'backup/andesstay.sql'
function Get-BackupHash {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($dump)
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
$dumpHash = Get-BackupHash
if ([IO.File]::ReadAllText($dump) -match '(?im)^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)') {
    throw 'El respaldo intenta cambiar de esquema; no se importara'
}
$composeFile = Join-Path $projectRoot 'docker/docker-compose.yml'
$envFile = Join-Path $projectRoot '.env'
$container = ([string](& docker compose --env-file $envFile -f $composeFile ps -q mysql)).Trim()
if (-not $container) { throw 'Inicia MySQL con scripts/start-local.cmd antes de probar' }
$sqlFile = Join-Path $validationDir 'create-test-schemas.sql'
$statements = foreach ($schema in $schemas) {
    "CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "GRANT ALL PRIVILEGES ON $schema.* TO '$($values['DB_USERNAME'])'@'%';"
}
[IO.File]::WriteAllLines($sqlFile,$statements,(New-Object Text.UTF8Encoding $false))
$testEnvironmentNames = @('TEST_MYSQL','TEST_DB_URL','TEST_DB_DRIVER','TEST_DB_USERNAME','TEST_DB_PASSWORD',
    'TEST_FRESH_DB_URL','TEST_COPY_DB_URL','TEST_REPORT_PATH','RABBITMQ_USERNAME','RABBITMQ_PASSWORD',
    'TEST_RABBITMQ_PORT','TEST_RABBITMQ_MANAGEMENT_PORT','TEST_KAFKA_BOOTSTRAP_SERVERS')
$previousEnvironment = @{}
foreach ($name in $testEnvironmentNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$testExit = 1
try {
    & docker cp $sqlFile "${container}:/tmp/andesstay-create-tests.sql"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar MySQL' }
    & docker exec $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot < /tmp/andesstay-create-tests.sql'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron crear los esquemas temporales' }
    & docker cp $dump "${container}:/tmp/andesstay-copy-test.sql"
    & docker exec $container sh -c ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot ' + $schemas[2] + ' < /tmp/andesstay-copy-test.sql')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo importar la copia de prueba' }
    $port = if ($values['MYSQL_PORT']) { $values['MYSQL_PORT'] } else { '13306' }
    $env:TEST_MYSQL = 'true'
    $env:TEST_DB_URL = "jdbc:mysql://localhost:$port/$($schemas[0])?connectionTimeZone=UTC"
    $env:TEST_DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
    $env:TEST_DB_USERNAME = $values['DB_USERNAME']
    $env:TEST_DB_PASSWORD = $values['DB_PASSWORD']
    $env:TEST_FRESH_DB_URL = "jdbc:mysql://localhost:$port/$($schemas[1])?connectionTimeZone=UTC"
    $env:TEST_COPY_DB_URL = "jdbc:mysql://localhost:$port/$($schemas[2])?connectionTimeZone=UTC"
    $env:TEST_REPORT_PATH = Join-Path $validationDir 'mysql-migrations.json'
    $env:RABBITMQ_USERNAME = $values['RABBITMQ_USERNAME']
    $env:RABBITMQ_PASSWORD = $values['RABBITMQ_PASSWORD']
    $env:TEST_RABBITMQ_PORT = if ($values['RABBITMQ_PORT']) { $values['RABBITMQ_PORT'] } else { '5672' }
    $env:TEST_RABBITMQ_MANAGEMENT_PORT = if ($values['RABBITMQ_MANAGEMENT_PORT']) { $values['RABBITMQ_MANAGEMENT_PORT'] } else { '15672' }
    $kafkaPort = if ($values['KAFKA_PORT']) { $values['KAFKA_PORT'] } else { '9092' }
    $env:TEST_KAFKA_BOOTSTRAP_SERVERS = "localhost:$kafkaPort"
    Push-Location (Join-Path $projectRoot 'AndesStay Backend')
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dtest=MySqlMigrationTests,ReservationAvailabilityIntegrationTests,RealBrokerIntegrationTests' '-Dsurefire.runOrder=alphabetical' test *> (Join-Path $validationDir 'mysql-tests.log')
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
    } finally { Pop-Location }
    Select-String -LiteralPath (Join-Path $validationDir 'mysql-tests.log') -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { $_.Line }
} finally {
    $cleanupFile = Join-Path $validationDir 'drop-test-schemas.sql'
    [IO.File]::WriteAllLines($cleanupFile, @($schemas | ForEach-Object { "DROP DATABASE IF EXISTS $_;" }),(New-Object Text.UTF8Encoding $false))
    & docker cp $cleanupFile "${container}:/tmp/andesstay-drop-tests.sql"
    & docker exec $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot < /tmp/andesstay-drop-tests.sql'
    $cleanupExit = $LASTEXITCODE
    & docker exec $container rm -f /tmp/andesstay-create-tests.sql /tmp/andesstay-copy-test.sql /tmp/andesstay-drop-tests.sql
    foreach ($name in $testEnvironmentNames) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
    if ($dumpHash -ne (Get-BackupHash)) { throw 'El respaldo original fue modificado' }
    if ($cleanupExit -ne 0) { throw 'No se pudieron eliminar los esquemas temporales; revisa MySQL antes de repetir la prueba' }
}
if ($testExit -ne 0) { throw "Fallaron pruebas MySQL; revisa .validation/mysql-tests.log" }
Write-Output 'MySQL: migraciones, copia, concurrencia y brokers verificados. Esquemas temporales eliminados.'
