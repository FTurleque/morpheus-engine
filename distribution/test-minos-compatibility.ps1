param(
    [Parameter(Mandatory = $true)]
    [string]$MinosJar,
    [string]$MinosJava = "java",
    [string]$MorpheusLauncher = "dist\.m12-windows\image\morpheus\morpheus.exe",
    [string]$MinosHome = ""
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$resolvedMinosJar = (Resolve-Path $MinosJar -ErrorAction Stop).Path
$launcherCandidate = if ([System.IO.Path]::IsPathRooted($MorpheusLauncher)) {
    $MorpheusLauncher
} else {
    Join-Path $repo $MorpheusLauncher
}
$resolvedLauncher = (Resolve-Path $launcherCandidate -ErrorAction Stop).Path

$previousJar = $env:MORPHEUS_MINOS_JAR
$previousJava = $env:MORPHEUS_MINOS_JAVA
$previousHome = $env:MORPHEUS_MINOS_HOME

try {
    $env:MORPHEUS_MINOS_JAR = $resolvedMinosJar
    $env:MORPHEUS_MINOS_JAVA = $MinosJava
    if ([string]::IsNullOrWhiteSpace($MinosHome)) {
        Remove-Item Env:MORPHEUS_MINOS_HOME -ErrorAction SilentlyContinue
    } else {
        $env:MORPHEUS_MINOS_HOME = (Resolve-Path $MinosHome -ErrorAction Stop).Path
    }

    Write-Host "Checking MORPHEUS <-> real MINOS MCP compatibility..."
    Write-Host "MORPHEUS launcher: $resolvedLauncher"
    Write-Host "MINOS JAR:        $resolvedMinosJar"
    Write-Host "MINOS Java:       $MinosJava"

    $status = & $resolvedLauncher --json minos-status
    if ($LASTEXITCODE -ne 0) {
        throw "MORPHEUS minos-status exited with code $LASTEXITCODE"
    }
    Write-Host $status
    if ($status -notmatch '"system":"MINOS"') {
        throw "MINOS compatibility smoke did not identify system MINOS: $status"
    }
    if ($status -notmatch '"state":"AVAILABLE"') {
        throw "MINOS compatibility smoke expected AVAILABLE: $status"
    }

    Write-Host "Real MINOS MCP compatibility smoke: PASS"
} finally {
    if ($null -eq $previousJar) { Remove-Item Env:MORPHEUS_MINOS_JAR -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_JAR = $previousJar }
    if ($null -eq $previousJava) { Remove-Item Env:MORPHEUS_MINOS_JAVA -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_JAVA = $previousJava }
    if ($null -eq $previousHome) { Remove-Item Env:MORPHEUS_MINOS_HOME -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_HOME = $previousHome }
}
