$ErrorActionPreference = 'Stop'
$topologyTestRoot = Split-Path -Parent $PSScriptRoot
$topologyTestDirectory = Join-Path $topologyTestRoot '.validation/rabbit-topology-tests'
New-Item -ItemType Directory -Path $topologyTestDirectory -Force | Out-Null
$topologyExporter = Join-Path $PSScriptRoot 'export-rabbit-topology.ps1'
function Assert-Topology([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }

# Verificar las politicas originales y sus limites; la configuracion no debe alterar la operacion existente.
& $topologyExporter | Out-Null
& $topologyExporter -Check | Out-Null
$topologyOriginal = [IO.File]::ReadAllText((Join-Path $topologyTestRoot 'config/rabbit-topology.properties'))
$topologyGenerated = [IO.File]::ReadAllText((Join-Path $topologyTestRoot '.validation/rabbitmq-definitions.json')) | ConvertFrom-Json
Assert-Topology ($topologyGenerated.policies[0].definition.'message-ttl' -eq 86400000) 'Cambio inesperado del TTL real'
Assert-Topology ($topologyGenerated.policies[0].definition.'max-length' -eq 10000) 'Cambio inesperado del limite real'
Assert-Topology ($topologyGenerated.policies[1].definition.'message-ttl' -eq 604800000) 'Cambio inesperado de retencion real'
Assert-Topology ($topologyGenerated.bindings[0].source -ceq $topologyGenerated.exchanges[0].name) 'DLX y binding inconsistentes'
Assert-Topology ($topologyGenerated.bindings[0].destination -ceq $topologyGenerated.queues[0].name) 'DLQ y binding inconsistentes'

# Cambiar nombres en una copia debe actualizar DLX, DLQ y regex sin afectar otras colas.
$topologyFixture = Join-Path $topologyTestDirectory 'renamed.properties'
$topologyFixtureOutput = Join-Path $topologyTestDirectory 'renamed-definitions.json'
$topologyRenamed = $topologyOriginal -replace '(?m)^andesstay.rabbitmq.reservation-queue=.*$', 'andesstay.rabbitmq.reservation-queue=verify.reception.v2'
$topologyRenamed = $topologyRenamed -replace '(?m)^andesstay.rabbitmq.guest-queue=.*$', 'andesstay.rabbitmq.guest-queue=verify.guest.v2'
$topologyRenamed = $topologyRenamed -replace '(?m)^andesstay.rabbitmq.dead-letter-exchange=.*$', 'andesstay.rabbitmq.dead-letter-exchange=verify.dead.exchange'
$topologyRenamed = $topologyRenamed -replace '(?m)^andesstay.rabbitmq.dead-letter-queue=.*$', 'andesstay.rabbitmq.dead-letter-queue=verify.dead.queue'
[IO.File]::WriteAllText($topologyFixture, $topologyRenamed, (New-Object Text.UTF8Encoding $false))
& $topologyExporter -ConfigurationPath $topologyFixture -OutputPath $topologyFixtureOutput | Out-Null
$topologyChanged = [IO.File]::ReadAllText($topologyFixtureOutput) | ConvertFrom-Json
$topologySourceRegex = $topologyChanged.policies[0].pattern
Assert-Topology ([regex]::IsMatch('verify.reception.v2', $topologySourceRegex)) 'La politica no reconoce recepcion renombrada'
Assert-Topology ([regex]::IsMatch('verify.guest.v2', $topologySourceRegex)) 'La politica no reconoce huespedes renombrados'
Assert-Topology (-not [regex]::IsMatch('verifyXreceptionXv2', $topologySourceRegex)) 'La politica trata los puntos como comodines'
Assert-Topology (-not [regex]::IsMatch('verify.reception.v2.other', $topologySourceRegex)) 'La politica afecta colas ajenas'
Assert-Topology ($topologyChanged.exchanges[0].name -ceq 'verify.dead.exchange') 'DLX renombrado no aplicado'
Assert-Topology ($topologyChanged.bindings[0].destination -ceq 'verify.dead.queue') 'DLQ renombrada no aplicada'
Assert-Topology ($topologyChanged.policies[0].definition.'dead-letter-exchange' -ceq 'verify.dead.exchange') 'Politica sigue apuntando al DLX anterior'
Assert-Topology ([regex]::IsMatch('verify.dead.queue', $topologyChanged.policies[1].pattern)) 'Retencion no reconoce la DLQ renombrada'

# Una configuracion invalida debe fallar antes de reemplazar definiciones que ya eran correctas.
$topologyBeforeInvalid = [IO.File]::ReadAllText($topologyFixtureOutput)
$topologyInvalid = $topologyRenamed -replace '(?m)^andesstay.rabbitmq.reservation-queue=.*$', 'andesstay.rabbitmq.reservation-queue='
[IO.File]::WriteAllText($topologyFixture, $topologyInvalid, (New-Object Text.UTF8Encoding $false))
$topologyWasRejected = $false
try { & $topologyExporter -ConfigurationPath $topologyFixture -OutputPath $topologyFixtureOutput | Out-Null }
catch { $topologyWasRejected = $true }
Assert-Topology $topologyWasRejected 'Un nombre vacio no fue rechazado'
Assert-Topology ([IO.File]::ReadAllText($topologyFixtureOutput) -ceq $topologyBeforeInvalid) 'El fallo sobrescribio las definiciones validas'
Write-Output 'Topologia RabbitMQ: 3 escenarios correctos (politicas, cambio de nombres y rechazo sin sobrescritura).'
