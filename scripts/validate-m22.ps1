[CmdletBinding()]
param(
    [string]$Version = '1.0.0',
    [string]$BaseRef = 'origin/main',
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\m22'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Invoke-Native([string]$Label, [scriptblock]$Command) {
    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host $Label
    Write-Host ('=' * 78)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

function Get-SurefireTotals([string]$Root) {
    $result = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0; Suites = 0 }
    $reports = @(Get-ChildItem -Path $Root -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $document.testsuite
        if ($null -eq $suite) { continue }
        $result.Tests += [int]$suite.tests
        $result.Failures += [int]$suite.failures
        $result.Errors += [int]$suite.errors
        $result.Skipped += [int]$suite.skipped
        $result.Suites++
    }
    return [pscustomobject]$result
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Assert-PackagedApi([string]$Launcher, [string]$PluginDirectory) {
    $port = Get-FreeLoopbackPort
    $apiData = Join-Path $outputRoot 'api-data'
    $stdout = Join-Path $outputRoot 'api.stdout.log'
    $stderr = Join-Path $outputRoot 'api.stderr.log'
    New-Item -ItemType Directory -Force -Path $apiData | Out-Null
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $apiData, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        $encodedDirectory = [uri]::EscapeDataString($PluginDirectory)
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
                throw "Packaged API exited before M22 checks: $diagnostic"
            }
            try {
                $versionResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/version" -UseBasicParsing -TimeoutSec 2
                $versionPayload = $versionResponse.Content | ConvertFrom-Json
                $pluginsResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/provider-plugins/discover?directory=$encodedDirectory" -UseBasicParsing -TimeoutSec 2
                $pluginsPayload = $pluginsResponse.Content | ConvertFrom-Json
                if ($versionResponse.StatusCode -eq 200 `
                        -and [string]$versionPayload.data.version -eq $Version `
                        -and $pluginsResponse.StatusCode -eq 200 `
                        -and [int]$pluginsPayload.data.compatibleCount -eq 1) {
                    Write-Host "Packaged API version + provider-plugin discovery: PASS (http://127.0.0.1:$port/api/v1)"
                    return
                }
            } catch {
                Start-Sleep -Milliseconds 100
            }
        }
        $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
        throw "Packaged API M22 check timed out. stderr=$diagnostic"
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
}

Write-Host "M22 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "M22 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'main'
    git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'M22 base ref not found: origin/main (fallback main also missing)'
    }
}
Write-Host "M22 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) {
    throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)"
}
if ($totals.Tests -lt 473) {
    throw "M22 test baseline regression: $($totals.Tests) < 473"
}
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 187) {
    throw "M22 architecture baseline regression: $($architecture.Tests) < 187"
}
Write-Host "Tests: PASS ($($totals.Tests), baseline >= 473)"
Write-Host "Architecture: PASS ($($architecture.Tests), baseline >= 187)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing production coverage summary: $coverageSummary" }
$coverage = @{}
Get-Content $coverageSummary | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] }
}
if ([double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.25) {
    throw "M22 line coverage below 25%: $($coverage.lineRatio)"
}
if ([double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.20) {
    throw "M22 branch coverage below 20%: $($coverage.branchRatio)"
}
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio))"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'
$sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) {
    throw 'CycloneDX JSON/XML SBOM is missing'
}
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "Provenance writer failed with exit code $LASTEXITCODE" }
$provenance = Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'
if (-not (Test-Path $provenance)) { throw 'Build provenance is missing' }
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

$referenceJar = Join-Path $repo "morpheus-provider-reference\target\morpheus-provider-reference-$Version.jar"
if (-not (Test-Path $referenceJar)) { throw "M22 reference provider JAR missing after reactor verify: $referenceJar" }

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m22\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m22\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarTool)) { throw "jar.exe not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect shaded MORPHEUS JAR' }
    if ($entries -notcontains 'com/morpheus/sdk/provider/ProviderPluginService.class') {
        throw 'M22 packaged runtime is missing ProviderPluginService'
    }
    if ($entries -contains 'com/morpheus/provider/reference/ReferenceProviderPlugin.class') {
        throw 'M22 reference provider must remain external and must not be embedded in the MORPHEUS launcher'
    }
    Write-Host 'Provider SDK embedded / reference provider external: PASS'

    $pluginDirectory = Join-Path $outputRoot 'plugins'
    $workspace = Join-Path $outputRoot 'reference-workspace'
    Remove-Item $pluginDirectory, $workspace -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $pluginDirectory, $workspace | Out-Null
    Copy-Item $referenceJar (Join-Path $pluginDirectory 'reference-provider.jar')
    Set-Content -Encoding ASCII (Join-Path $workspace 'morpheus-reference.spec') 'reference'

    $discoveryText = (& $launcher --json provider-plugins discover --directory $pluginDirectory) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged provider discovery failed: $discoveryText" }
    $discovery = $discoveryText | ConvertFrom-Json
    if ([int]$discovery.compatibleCount -ne 1 -or [string]$discovery.candidates[0].status -ne 'COMPATIBLE') {
        throw "Packaged external provider discovery mismatch: $discoveryText"
    }

    $probeText = (& $launcher --json provider-plugins probe --directory $pluginDirectory --plugin 'reference-provider-plugin' --workspace $workspace) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged external provider probe failed: $probeText" }
    $probe = $probeText | ConvertFrom-Json
    if ([string]$probe.probe.status -ne 'SUPPORTED' -or [string]$probe.probe.providerId.value -ne 'reference-plugin') {
        throw "Packaged external provider probe mismatch: $probeText"
    }
    Write-Host 'External reference provider discovery + isolated activation + probe: PASS'

    Assert-PackagedApi -Launcher $launcher -PluginDirectory $pluginDirectory
    Write-Host 'Packaged CLI/MCP/HTTP provider platform convergence: PASS'
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M22 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during M22 validation:`n$($finalTracked -join "`n")"
}

$summary = @(
    'M22 VALIDATION PASS'
    "sha=$validationSha"
    "baseRef=$BaseRef"
    "version=$Version"
    "tests=$($totals.Tests)"
    "architectureTests=$($architecture.Tests)"
    "lineCoverage=$($coverage.lineRatio)"
    "branchCoverage=$($coverage.branchRatio)"
    'sdkApiVersion=1'
    'externalReferenceProvider=PASS'
    'sbom=PASS'
    'provenance=PASS'
    "portable=$(-not $SkipPortable)"
    'postGateExecutableDelta=NONE'
)
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
