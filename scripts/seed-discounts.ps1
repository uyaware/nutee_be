[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-NuteeLocalEnvironment -ProjectRoot $projectRoot
Push-Location $projectRoot
try {
    docker compose cp (Join-Path $PSScriptRoot 'seed-discounts.sql') postgres:/tmp/nutee-discounts-2026.sql
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the discount seed to PostgreSQL.' }
    docker compose exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB `
        -v ON_ERROR_STOP=1 -f /tmp/nutee-discounts-2026.sql
    if ($LASTEXITCODE -ne 0) { throw 'Discount seed failed. Start the backend to apply migrations first.' }
    Write-Host 'Discount seed complete: 4 promotions and 7 vouchers (existing rules are preserved).'
} finally {
    Pop-Location
}
