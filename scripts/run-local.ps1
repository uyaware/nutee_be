[CmdletBinding()]
param([string]$JdkHome)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-NuteeLocalEnvironment -ProjectRoot $projectRoot

# Prefer an explicitly supplied JDK; otherwise detect Java 21 without changing system settings.
$javaCandidates = @($JdkHome, $env:JAVA_HOME, (Join-Path $env:ProgramFiles 'Java\jdk-21'))
$selectedJdk = $null
foreach ($candidate in $javaCandidates) {
    if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
    $releaseFile = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath $releaseFile) -and
            (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="21(?:\.|\")' -Quiet)) {
        $selectedJdk = $candidate
        break
    }
}
if ($null -eq $selectedJdk) {
    throw 'JDK 21 was not found. Set JAVA_HOME or pass -JdkHome with your JDK 21 directory.'
}
$env:JAVA_HOME = $selectedJdk
$env:Path = "$selectedJdk\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = 'local'

# Report the owner of a busy port before Maven starts; never stop another process automatically.
$serverPort = 8080
if (-not [string]::IsNullOrWhiteSpace($env:SERVER_PORT)) {
    if (-not [int]::TryParse($env:SERVER_PORT, [ref]$serverPort) -or $serverPort -lt 1 -or $serverPort -gt 65535) {
        throw 'Invalid SERVER_PORT in .env. Use a port between 1 and 65535.'
    }
}
$listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
if (@($listeners | Where-Object { $_.Port -eq $serverPort }).Count -gt 0) {
    $portOwners = @(Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    $ownerHint = if ($portOwners.Count -gt 0) { " PID: $($portOwners -join ', ')." } else { '' }
    throw "Port $serverPort is already in use.$ownerHint Stop the existing service, then retry. To identify it, run: netstat -ano | findstr :$serverPort"
}

Push-Location $projectRoot
try {
    Write-Host 'Starting NUTEE. Press Ctrl+C to stop.'
    & (Join-Path $projectRoot 'mvnw.cmd') spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "NUTEE exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
