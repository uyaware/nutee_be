function Import-NuteeLocalEnvironment {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $envFile = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw 'Missing .env. Run .\scripts\init-local.ps1 first.'
    }

    # Parse literal KEY=value pairs. Never execute the contents of the env file.
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
            throw 'Invalid .env syntax: expected an unquoted KEY=value line.'
        }
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
