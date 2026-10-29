[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-NuteeLocalEnvironment -ProjectRoot $projectRoot
Push-Location $projectRoot
try {
    # Copy bytes directly so Vietnamese text is not changed by PowerShell pipe encoding.
    docker compose cp (Join-Path $PSScriptRoot 'seed-catalog.sql') postgres:/tmp/nutee-seed-catalog.sql
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy catalog seed into PostgreSQL container.' }
    docker compose exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -f /tmp/nutee-seed-catalog.sql
    if ($LASTEXITCODE -ne 0) { throw 'Catalog seed failed; check PostgreSQL output.' }
} finally {
    Pop-Location
}
