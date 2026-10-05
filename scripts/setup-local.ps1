$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
if (-not (Test-Path -LiteralPath $envPath)) {
    $template = [IO.File]::ReadAllText((Join-Path $projectRoot '.env.example'))
    foreach ($name in @('MYSQL_ROOT_PASSWORD','DB_PASSWORD','RABBITMQ_PASSWORD')) {
        $template = $template.Replace("$name=GENERAR", "$name=$(New-LocalPassword)")
    }
    [IO.File]::WriteAllText($envPath, $template, (New-Object Text.UTF8Encoding $false))
}
$values = @{}
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$frontendEnv = Join-Path $projectRoot 'AndesStay Frontend/.env.local'
$frontendLines = @("VITE_ENTRA_TENANT_ID=$($values['ENTRA_TENANT_ID'])",
    "VITE_ENTRA_CLIENT_ID=$($values['ENTRA_CLIENT_ID'])",
    "VITE_ENTRA_SCOPE=$($values['ENTRA_SCOPE'])", "VITE_APP_TIME_ZONE=$($values['APP_TIME_ZONE'])", 'VITE_API_BASE_URL=')
[IO.File]::WriteAllLines($frontendEnv, $frontendLines, (New-Object Text.UTF8Encoding $false))
Write-Output 'Configuración local preparada. Las contraseñas permanecen en .env y no se muestran.'
Write-Output 'En Microsoft Entra debe estar registrado http://localhost:5173 como redirect URI de SPA.'
