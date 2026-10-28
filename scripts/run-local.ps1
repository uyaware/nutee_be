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
