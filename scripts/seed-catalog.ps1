[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-NuteeLocalEnvironment -ProjectRoot $projectRoot
Push-Location $projectRoot
try {
    docker compose cp (Join-Path $PSScriptRoot 'seed-catalog.sql') postgres:/tmp/nutee-catalog-2026.sql
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the catalog seed to PostgreSQL.' }
    docker compose exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB `
        -v ON_ERROR_STOP=1 -f /tmp/nutee-catalog-2026.sql
    if ($LASTEXITCODE -ne 0) { throw 'Catalog seed failed; check migrations and PostgreSQL output.' }
} finally {
    Pop-Location
}
