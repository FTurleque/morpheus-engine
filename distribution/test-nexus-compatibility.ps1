param(
    [Parameter(Mandatory = $true)]
    [string]$NexusRunnerJar,
    [string]$NexusJava = "java",
    [string]$MorpheusLauncher = "dist\.m13-windows\image\morpheus\morpheus.exe",
    [string]$NexusHome = ""
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$resolvedNexusJar = (Resolve-Path $NexusRunnerJar -ErrorAction Stop).Path
$launcherCandidate = if ([System.IO.Path]::IsPathRooted($MorpheusLauncher)) {
    $MorpheusLauncher
} else {
    Join-Path $repo $MorpheusLauncher
}
$resolvedLauncher = (Resolve-Path $launcherCandidate -ErrorAction Stop).Path

$previousJar = $env:MORPHEUS_NEXUS_JAR
$previousJava = $env:MORPHEUS_NEXUS_JAVA
$previousHome = $env:MORPHEUS_NEXUS_HOME

try {
    $env:MORPHEUS_NEXUS_JAR = $resolvedNexusJar
    $env:MORPHEUS_NEXUS_JAVA = $NexusJava
    if ([string]::IsNullOrWhiteSpace($NexusHome)) {
        Remove-Item Env:MORPHEUS_NEXUS_HOME -ErrorAction SilentlyContinue
    } else {
        $env:MORPHEUS_NEXUS_HOME = (Resolve-Path $NexusHome -ErrorAction Stop).Path
    }

    Write-Host "Checking MORPHEUS <-> real NEXUS MCP compatibility..."
    Write-Host "MORPHEUS launcher: $resolvedLauncher"
    Write-Host "NEXUS runner JAR:  $resolvedNexusJar"
    Write-Host "NEXUS Java:        $NexusJava"

    $status = & $resolvedLauncher --json nexus-status
    if ($LASTEXITCODE -ne 0) {
        throw "MORPHEUS nexus-status exited with code $LASTEXITCODE"
    }
    Write-Host $status
    if ($status -notmatch '"system":"NEXUS"') {
        throw "NEXUS compatibility smoke did not identify system NEXUS: $status"
    }
    if ($status -notmatch '"state":"AVAILABLE"') {
        throw "NEXUS compatibility smoke expected AVAILABLE: $status"
    }

    Write-Host "Real NEXUS MCP compatibility smoke: PASS"
} finally {
    if ($null -eq $previousJar) { Remove-Item Env:MORPHEUS_NEXUS_JAR -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_JAR = $previousJar }
    if ($null -eq $previousJava) { Remove-Item Env:MORPHEUS_NEXUS_JAVA -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_JAVA = $previousJava }
    if ($null -eq $previousHome) { Remove-Item Env:MORPHEUS_NEXUS_HOME -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_HOME = $previousHome }
}
