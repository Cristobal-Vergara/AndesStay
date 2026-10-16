param(
    [string]$ConfigurationPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'config/rabbit-topology.properties'),
    [string]$OutputPath = (Join-Path (Split-Path -Parent $PSScriptRoot) '.validation/rabbitmq-definitions.json'),
    [switch]$Check
)
$ErrorActionPreference = 'Stop'
$topologyProjectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$topologyOutput = [IO.Path]::GetFullPath($OutputPath)
$topologyValidationRoot = [IO.Path]::GetFullPath((Join-Path $topologyProjectRoot '.validation'))
if (-not $topologyOutput.StartsWith($topologyValidationRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Las definiciones generadas deben quedar dentro de .validation del proyecto'
}

# El catalogo usa propiedades simples, sin escapes ni continuaciones de linea.
$topologyValues = @{}
$topologyLineNumber = 0
foreach ($topologyLine in [IO.File]::ReadAllLines($ConfigurationPath)) {
    $topologyLineNumber++
    if ([string]::IsNullOrWhiteSpace($topologyLine) -or $topologyLine.TrimStart().StartsWith('#')) { continue }
    if ($topologyLine -notmatch '^([a-z][a-z0-9.-]*)=(.*)$') {
        throw "Propiedad invalida en la linea $topologyLineNumber del catalogo"
    }
    $topologyKey = $matches[1]
    if ($topologyValues.ContainsKey($topologyKey)) { throw "Propiedad duplicada: $topologyKey" }
    $topologyValues[$topologyKey] = $matches[2].Trim()
}
function Get-TopologyName([string]$Key) {
    $value = $topologyValues[$Key]
    if ($null -eq $value -or $value -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,254}$') {
        throw "Nombre o ruta invalida en $Key"
    }
    return $value
}
function Get-TopologyPositiveInt([string]$Key) {
    $value = 0
    if (-not [int]::TryParse($topologyValues[$Key], [ref]$value) -or $value -le 0) {
        throw "Se requiere un entero positivo en $Key"
    }
    return $value
}

$reception = Get-TopologyName 'andesstay.rabbitmq.reservation-queue'
$guest = Get-TopologyName 'andesstay.rabbitmq.guest-queue'
$dlx = Get-TopologyName 'andesstay.rabbitmq.dead-letter-exchange'
$dlq = Get-TopologyName 'andesstay.rabbitmq.dead-letter-queue'
$queueNames = @($reception, $guest, $dlq,
    (Get-TopologyName 'andesstay.rabbitmq.demo-queue'), (Get-TopologyName 'andesstay.rabbitmq.demo-dlq'))
$exchangeNames = @((Get-TopologyName 'andesstay.rabbitmq.exchange'), $dlx,
    (Get-TopologyName 'andesstay.rabbitmq.demo-dlx'))
$routingKeys = @((Get-TopologyName 'andesstay.rabbitmq.created-routing-key'),
    (Get-TopologyName 'andesstay.rabbitmq.confirmed-routing-key'), (Get-TopologyName 'andesstay.rabbitmq.cancelled-routing-key'))
foreach ($group in @(@{ Names = $queueNames }, @{ Names = $exchangeNames }, @{ Names = $routingKeys })) {
    if (@($group.Names | Sort-Object -Unique -CaseSensitive).Count -ne $group.Names.Count) {
        throw 'Las colas, exchanges y rutas de cada grupo deben tener nombres distintos'
    }
}
foreach ($key in @('andesstay.rabbitmq.demo-message-ttl-ms', 'andesstay.rabbitmq.demo-max-length',
        'andesstay.messaging.demo-retention-ms')) { Get-TopologyPositiveInt $key | Out-Null }

# Las colas fuente y sus bindings los declara Spring. El broker configura DLX, DLQ y politicas.
$definitions = [ordered]@{
    exchanges = @([ordered]@{
        name = $dlx; vhost = '/'; type = 'fanout'; durable = $true; auto_delete = $false; internal = $false; arguments = @{}
    })
    queues = @([ordered]@{
        name = $dlq; vhost = '/'; durable = $true; auto_delete = $false; arguments = @{}
    })
    bindings = @([ordered]@{
        source = $dlx; vhost = '/'; destination = $dlq; destination_type = 'queue'; routing_key = ''; arguments = @{}
    })
    policies = @(
        [ordered]@{
            vhost = '/'; name = (Get-TopologyName 'andesstay.rabbitmq.delivery-policy-name')
            pattern = '^(' + [regex]::Escape($reception) + '|' + [regex]::Escape($guest) + ')$'
            'apply-to' = 'queues'; priority = 10
            definition = [ordered]@{
                'dead-letter-exchange' = $dlx
                'message-ttl' = (Get-TopologyPositiveInt 'andesstay.rabbitmq.source-message-ttl-ms')
                'max-length' = (Get-TopologyPositiveInt 'andesstay.rabbitmq.source-max-length')
            }
        },
        [ordered]@{
            vhost = '/'; name = (Get-TopologyName 'andesstay.rabbitmq.quarantine-policy-name')
            pattern = '^' + [regex]::Escape($dlq) + '$'; 'apply-to' = 'queues'; priority = 10
            definition = [ordered]@{ 'message-ttl' = (Get-TopologyPositiveInt 'andesstay.rabbitmq.dead-letter-retention-ms') }
        }
    )
}
$topologyJson = ($definitions | ConvertTo-Json -Depth 8) + "`n"
if ($Check) {
    if (-not (Test-Path -LiteralPath $topologyOutput) -or [IO.File]::ReadAllText($topologyOutput) -cne $topologyJson) {
        throw 'Las definiciones no coinciden con el catalogo; ejecuta scripts/export-rabbit-topology.ps1'
    }
    Write-Output 'Definiciones RabbitMQ alineadas con el catalogo compartido.'
} else {
    New-Item -ItemType Directory -Path (Split-Path -Parent $topologyOutput) -Force | Out-Null
    $topologyTemporary = $topologyOutput + '.tmp'
    [IO.File]::WriteAllText($topologyTemporary, $topologyJson, (New-Object Text.UTF8Encoding $false))
    Move-Item -LiteralPath $topologyTemporary -Destination $topologyOutput -Force
    Write-Output 'Definiciones RabbitMQ generadas desde config/rabbit-topology.properties.'
}
