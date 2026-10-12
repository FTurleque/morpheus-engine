param(
    [Parameter(Mandatory = $true)]
    [string]$MinosJar,
    # Empty: MINOS runs on the runtime of the MORPHEUS under test, which is what an installation does by default.
    # Otherwise the absolute path of a Java launcher; MORPHEUS refuses a bare command or a relative path.
    [string]$MinosJava = "",
    [string]$MorpheusLauncher = "dist\.m20-windows\image\morpheus\morpheus.exe",
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
$previousPin = $env:MORPHEUS_MINOS_JAR_SHA256
$previousJava = $env:MORPHEUS_MINOS_JAVA
$previousHome = $env:MORPHEUS_MINOS_HOME

try {
    $env:MORPHEUS_MINOS_JAR = $resolvedMinosJar
    # MORPHEUS executes a peer JAR only when it is pinned; the pin of a smoke is the digest of the JAR it was given.
    $env:MORPHEUS_MINOS_JAR_SHA256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedMinosJar).Hash.ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($MinosJava)) {
        Remove-Item Env:MORPHEUS_MINOS_JAVA -ErrorAction SilentlyContinue
        $launcherDescription = "(the runtime of the MORPHEUS under test)"
    } else {
        $env:MORPHEUS_MINOS_JAVA = (Resolve-Path $MinosJava -ErrorAction Stop).Path
        $launcherDescription = $env:MORPHEUS_MINOS_JAVA
    }
    if ([string]::IsNullOrWhiteSpace($MinosHome)) {
        Remove-Item Env:MORPHEUS_MINOS_HOME -ErrorAction SilentlyContinue
    } else {
        $env:MORPHEUS_MINOS_HOME = (Resolve-Path $MinosHome -ErrorAction Stop).Path
    }

    Write-Host "Checking MORPHEUS <-> real MINOS MCP compatibility..."
    Write-Host "MORPHEUS launcher: $resolvedLauncher"
    Write-Host "MINOS JAR:        $resolvedMinosJar"
    Write-Host "MINOS Java:       $launcherDescription"

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
    if ($null -eq $previousPin) { Remove-Item Env:MORPHEUS_MINOS_JAR_SHA256 -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_JAR_SHA256 = $previousPin }
    if ($null -eq $previousJava) { Remove-Item Env:MORPHEUS_MINOS_JAVA -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_JAVA = $previousJava }
    if ($null -eq $previousHome) { Remove-Item Env:MORPHEUS_MINOS_HOME -ErrorAction SilentlyContinue } else { $env:MORPHEUS_MINOS_HOME = $previousHome }
}
