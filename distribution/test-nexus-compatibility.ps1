param(
    [Parameter(Mandatory = $true)]
    [string]$NexusRunnerJar,
    # Empty: NEXUS runs on the runtime of the MORPHEUS under test, which is what an installation does by default.
    # Otherwise the absolute path of a Java launcher; MORPHEUS refuses a bare command or a relative path.
    [string]$NexusJava = "",
    [string]$MorpheusLauncher = "dist\.m20-windows\image\morpheus\morpheus.exe",
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
$previousPin = $env:MORPHEUS_NEXUS_JAR_SHA256
$previousJava = $env:MORPHEUS_NEXUS_JAVA
$previousHome = $env:MORPHEUS_NEXUS_HOME

try {
    $env:MORPHEUS_NEXUS_JAR = $resolvedNexusJar
    # MORPHEUS executes a peer JAR only when it is pinned; the pin of a smoke is the digest of the JAR it was given.
    $env:MORPHEUS_NEXUS_JAR_SHA256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedNexusJar).Hash.ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($NexusJava)) {
        Remove-Item Env:MORPHEUS_NEXUS_JAVA -ErrorAction SilentlyContinue
        $launcherDescription = "(the runtime of the MORPHEUS under test)"
    } else {
        $env:MORPHEUS_NEXUS_JAVA = (Resolve-Path $NexusJava -ErrorAction Stop).Path
        $launcherDescription = $env:MORPHEUS_NEXUS_JAVA
    }
    if ([string]::IsNullOrWhiteSpace($NexusHome)) {
        Remove-Item Env:MORPHEUS_NEXUS_HOME -ErrorAction SilentlyContinue
    } else {
        $env:MORPHEUS_NEXUS_HOME = (Resolve-Path $NexusHome -ErrorAction Stop).Path
    }

    Write-Host "Checking MORPHEUS <-> real NEXUS MCP compatibility..."
    Write-Host "MORPHEUS launcher: $resolvedLauncher"
    Write-Host "NEXUS runner JAR:  $resolvedNexusJar"
    Write-Host "NEXUS Java:        $launcherDescription"

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
    if ($null -eq $previousPin) { Remove-Item Env:MORPHEUS_NEXUS_JAR_SHA256 -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_JAR_SHA256 = $previousPin }
    if ($null -eq $previousJava) { Remove-Item Env:MORPHEUS_NEXUS_JAVA -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_JAVA = $previousJava }
    if ($null -eq $previousHome) { Remove-Item Env:MORPHEUS_NEXUS_HOME -ErrorAction SilentlyContinue } else { $env:MORPHEUS_NEXUS_HOME = $previousHome }
}
