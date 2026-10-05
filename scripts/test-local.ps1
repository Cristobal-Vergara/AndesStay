$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$validationDir = Join-Path $projectRoot '.validation'
New-Item -ItemType Directory -Path $validationDir -Force | Out-Null
foreach ($module in @('AndesStay Backend','AndesStayBFF','AndesStayKafkaAdmin','AndesStayRabbitAdmin')) {
    Push-Location (Join-Path $projectRoot $module)
    try {
        $log = Join-Path $validationDir ("tests-" + $module.Replace(' ','-') + '.log')
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd clean test *> $log
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Select-String -LiteralPath $log -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | Select-Object -Last 2 | ForEach-Object { $_.Line }
        if ($testExit -ne 0) { throw "Fallaron pruebas de $module; revisa $log" }
    } finally { Pop-Location }
}
Push-Location (Join-Path $projectRoot 'AndesStay Frontend')
try {
    foreach ($task in @('test','lint','build')) {
        $log = Join-Path $validationDir ("frontend-$task.log")
        $ErrorActionPreference = 'Continue'
        & npm run $task *> $log
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($testExit -ne 0) { throw "Fallo frontend $task; revisa $log" }
        Write-Output "Frontend ${task}: correcto"
    }
} finally { Pop-Location }
Write-Output 'Pruebas locales correctas. Para verificar MySQL y brokers, ejecuta scripts/test-mysql.cmd.'
