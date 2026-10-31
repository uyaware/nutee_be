[CmdletBinding()]
param([ValidateRange(1024, 65535)][int]$DatabasePort = 5433)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
. (Join-Path $PSScriptRoot 'local-environment.ps1')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required. Install Docker Desktop and start its Linux-container engine.'
}
docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker is not ready. Start Docker Desktop and try again.'
}

if (-not (Test-Path -LiteralPath $envFile)) {
    $randomBytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($randomBytes)
    } finally {
        $generator.Dispose()
    }
    $databasePassword = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $template = Get-Content -LiteralPath (Join-Path $projectRoot '.env.example') -Raw
    $template = $template -replace '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$databasePassword"
    $template = $template -replace '(?m)^DB_PASSWORD=.*$', "DB_PASSWORD=$databasePassword"
    $template = $template -replace '(?m)^POSTGRES_PORT=.*$', "POSTGRES_PORT=$DatabasePort"
    $template = $template -replace '(?m)^DB_URL=.*$', "DB_URL=jdbc:postgresql://localhost:$DatabasePort/nutee"
    [System.IO.File]::WriteAllText($envFile, $template, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host 'Created .env with a random local database password. The password is not printed.'
} else {
    Write-Host 'Reusing existing .env and database volume.'
}

Import-NuteeLocalEnvironment -ProjectRoot $projectRoot
foreach ($secretName in @('JWT_ACCESS_SECRET', 'JWT_REFRESH_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($secretName, 'Process'))) {
        $keyBytes = New-Object byte[] 32
        $keyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $keyGenerator.GetBytes($keyBytes) } finally { $keyGenerator.Dispose() }
        $secretValue = [Convert]::ToBase64String($keyBytes)
        $envContent = Get-Content -LiteralPath $envFile -Raw
        if ($envContent -match "(?m)^$secretName=") {
            $envContent = $envContent -replace "(?m)^$secretName=.*$", "$secretName=$secretValue"
        } else { $envContent = $envContent.TrimEnd() + "`n$secretName=$secretValue`n" }
        [System.IO.File]::WriteAllText($envFile, $envContent, (New-Object System.Text.UTF8Encoding($false)))
        [Environment]::SetEnvironmentVariable($secretName, $secretValue, 'Process')
        Write-Host "Generated $secretName in ignored .env (value hidden)."
    }
}
$configurationDefaults = [ordered]@{
    GOOGLE_AUTH_ENABLED = 'false'
    GOOGLE_CLIENT_ID = ''
    PASSWORD_RESET_ENABLED = 'false'
    MAIL_USERNAME = ''
    MAIL_PASSWORD = ''
    AUTH_SECURE_COOKIE = 'false'
    CORS_ALLOWED_ORIGINS = 'http://localhost:3000,http://localhost:5173'
    ACCESS_TOKEN_TTL = '15m'
    REFRESH_TOKEN_TTL = '7d'
}
$envContent = Get-Content -LiteralPath $envFile -Raw
# These switches belonged to the old external-JWK verification scaffold.
$envContent = $envContent.Replace('# Optional resource-server JWT verification; no token issuing endpoints exist.',
    '# HS256 access and refresh JWTs use distinct Base64 secrets.')
$envContent = $envContent.Replace('# Set all three values to enable verification. Keys come from the trusted JWK set.',
    '# Run init-local.ps1 to generate missing local secrets.')
$envContent = $envContent.Replace('# Lifetimes reserved for future authentication flows; no token endpoints exist yet.',
    '# Access lifetime and absolute refresh-session lifetime.')
$envContent = $envContent -replace '(?m)^JWT_ENABLED=.*(?:\r?\n|$)', ''
$envContent = $envContent -replace '(?m)^JWT_JWK_SET_URI=.*(?:\r?\n|$)', ''
$envContent = $envContent -replace '(?m)^JWT_(ISSUER|AUDIENCE)=.*(?:\r?\n|$)', ''
foreach ($setting in $configurationDefaults.GetEnumerator()) {
    if ($envContent -notmatch "(?m)^$($setting.Key)=") {
        $envContent = $envContent.TrimEnd() + "`n$($setting.Key)=$($setting.Value)`n"
    }
}
[System.IO.File]::WriteAllText($envFile, $envContent, (New-Object System.Text.UTF8Encoding($false)))
Import-NuteeLocalEnvironment -ProjectRoot $projectRoot
if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'Set POSTGRES_PASSWORD and DB_PASSWORD in .env before initializing the database.'
}
if ($env:POSTGRES_PASSWORD -cne $env:DB_PASSWORD) {
    throw 'Local POSTGRES_PASSWORD and DB_PASSWORD must match.'
}

docker compose --project-directory $projectRoot --env-file $envFile up -d --wait postgres
if ($LASTEXITCODE -ne 0) {
    throw 'Database startup failed. Check Docker output and the configured host port.'
}
# A local socket can use trust authentication, so it cannot verify the application's password.
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    $env:PGPASSWORD = $env:DB_PASSWORD
    docker compose --project-directory $projectRoot --env-file $envFile exec -T -e PGPASSWORD postgres `
        psql -h 127.0.0.1 -U $env:DB_USERNAME -d $env:POSTGRES_DB -w -v ON_ERROR_STOP=1 `
        -c 'SELECT current_database(), current_user, version();'
    if ($LASTEXITCODE -ne 0) {
        throw 'Database authentication failed. Restore the existing database password in .env. POSTGRES_PASSWORD only initializes an empty volume; changing it does not update existing credentials.'
    }
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}
Write-Host "Database is ready: localhost:$($env:POSTGRES_PORT)/$($env:POSTGRES_DB)"
Write-Host 'Start .\scripts\run-local.ps1 to apply Flyway migrations and run the API.'
