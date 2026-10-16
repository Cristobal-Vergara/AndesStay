param(
    [ValidateRange(30,3000)][int]$Messages = 300,
    [ValidateRange(1,10)][int]$Repeats = 3,
    [ValidateRange(1,25)][int]$WorkMs = 5
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $projectRoot '.env'))) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if (-not $values['RABBITMQ_PASSWORD']) { throw 'Prepara el entorno con scripts/start-local.cmd.' }
$validationDir = Join-Path $projectRoot '.validation'
New-Item -ItemType Directory -Path $validationDir -Force | Out-Null
$names = @('TEST_RABBIT_BENCHMARK','TEST_RABBITMQ_PORT','RABBITMQ_USERNAME','RABBITMQ_PASSWORD',
    'BENCHMARK_MESSAGES','BENCHMARK_REPEATS','BENCHMARK_WORK_MS','BENCHMARK_RUN_ID')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$runId = [Guid]::NewGuid().ToString()
try {
    $env:TEST_RABBIT_BENCHMARK = 'true'
    $env:TEST_RABBITMQ_PORT = if ($values['RABBITMQ_PORT']) { $values['RABBITMQ_PORT'] } else { '5672' }
    $env:RABBITMQ_USERNAME = if ($values['RABBITMQ_USERNAME']) { $values['RABBITMQ_USERNAME'] } else { 'andesstay' }
    $env:RABBITMQ_PASSWORD = $values['RABBITMQ_PASSWORD']
    $env:BENCHMARK_MESSAGES = $Messages.ToString()
    $env:BENCHMARK_REPEATS = $Repeats.ToString()
    $env:BENCHMARK_WORK_MS = $WorkMs.ToString()
    $env:BENCHMARK_RUN_ID = $runId
    Push-Location (Join-Path $projectRoot 'AndesStay Backend')
    try {
        $log = Join-Path $validationDir 'rabbit-benchmark-tests.log'
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dtest=RabbitThroughputBrokerTests' test *> $log
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Select-String -LiteralPath $log -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { $_.Line }
        if ($testExit -ne 0) { throw "Falló la comparación RabbitMQ; revisa $log" }
    } finally { Pop-Location }
    $reportPath = Join-Path $validationDir 'rabbit-benchmark.json'
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    if ($report.runId -ne $runId -or -not $report.allTemporaryQueuesDeleted -or
            @($report.trials).Count -ne 4 * $Repeats) { throw 'No se generó un informe completo de esta ejecución.' }
    $baselineDir = Join-Path $projectRoot '.ecc/benchmarks'
    New-Item -ItemType Directory -Path $baselineDir -Force | Out-Null
    Copy-Item -LiteralPath $reportPath -Destination (Join-Path $baselineDir 'rabbitmq-latest.json') -Force
    $report.summaries | ForEach-Object {
        [pscustomobject]@{
            Consumidores = $_.consumers
            Prefetch = $_.prefetch
            'Mediana ms' = [Math]::Round($_.medianElapsedMs,1)
            'Mediana msg/s' = [Math]::Round($_.medianMessagesPerSecond,1)
            'Min msg/s' = [Math]::Round($_.minMessagesPerSecond,1)
            'Max msg/s' = [Math]::Round($_.maxMessagesPerSecond,1)
        }
    } | Format-Table -AutoSize
    Write-Output "Cada escenario: $Messages mensajes, $WorkMs ms de trabajo simulado y $Repeats repeticiones."
    Write-Output 'Verificados: lote en pausa, IDs completos, sin efectos duplicados, ACK y colas temporales eliminadas.'
    Write-Output 'Resultados compartibles: .ecc/benchmarks/rabbitmq-latest.json. Log local: .validation/rabbit-benchmark-tests.log.'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
