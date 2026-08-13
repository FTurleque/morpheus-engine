[CmdletBinding()]
param(
    [string]$Version = '1.2.1',
    [string]$BaseRef = 'origin/main',
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\m21'
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

function Assert-PackagedApiVersion([string]$Launcher) {
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
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
                throw "Packaged API exited before version check: $diagnostic"
            }
            try {
                $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/version" -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200) {
                    $payload = $response.Content | ConvertFrom-Json
                    if ([string]$payload.data.version -ne $Version) {
                        throw "HTTP product version is $($payload.data.version); expected $Version"
                    }
                    Write-Host "HTTP product version convergence: PASS ($Version)"
                    return
                }
            } catch {
                if ($_.Exception.Message -like 'HTTP product version is*') { throw }
                Start-Sleep -Milliseconds 100
            }
        }
        throw 'Packaged API version check timed out'
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
}

Write-Host "M21 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "M21 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'main'
    git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'M21 base ref not found: origin/main (fallback main also missing)'
    }
}
Write-Host "M21 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) {
    throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)"
}
if ($totals.Tests -lt 711) {
    throw "M21 test baseline regression: $($totals.Tests) < 711"
}
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 253) {
    throw "M21 architecture baseline regression: $($architecture.Tests) < 253"
}
Write-Host "Tests: PASS ($($totals.Tests), baseline >= 711)"
Write-Host "Architecture: PASS ($($architecture.Tests), baseline >= 253)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing M21 coverage summary: $coverageSummary" }
$coverage = @{}
Get-Content $coverageSummary | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] }
}
if ([double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.47) {
    throw "M21 line coverage below 47% ratchet: $($coverage.lineRatio)"
}
if ([double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.40) {
    throw "M21 branch coverage below 40% ratchet: $($coverage.branchRatio)"
}
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio), ratchet=47%/40%)"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'
$sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) {
    throw 'M21 CycloneDX JSON/XML SBOM is missing'
}
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "M21 provenance writer failed with exit code $LASTEXITCODE" }
$provenance = Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'
if (-not (Test-Path $provenance)) { throw 'M21 build provenance is missing' }
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m21\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m21\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

    $productInfoText = (& $launcher --json product-info) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged product-info smoke failed: $productInfoText" }
    $productInfo = $productInfoText | ConvertFrom-Json
    if ([string]$productInfo.version -ne $Version -or [string]$productInfo.updateChannel -ne 'stable') {
        throw "Packaged product metadata mismatch: $productInfoText"
    }

    $manifest = Join-Path $outputRoot 'update.properties'
    @(
        "version=$Version"
        'channel=stable'
        'artifactUri=https://example.invalid/morpheus.zip'
        'sha256=dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
    ) | Set-Content -Encoding ASCII $manifest
    $updateText = (& $launcher --json update-check --manifest $manifest) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged update-check smoke failed: $updateText" }
    $update = $updateText | ConvertFrom-Json
    if ([bool]$update.updateAvailable) { throw "Same-version manifest must not report an update: $updateText" }
    Assert-PackagedApiVersion $launcher
    Write-Host 'Packaged CLI/update/API convergence: PASS'
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M21 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during M21 validation:`n$($finalTracked -join "`n")"
}

$summary = @(
    'M21 VALIDATION PASS'
    "sha=$validationSha"
    "baseRef=$BaseRef"
    "version=$Version"
    "tests=$($totals.Tests)"
    "architectureTests=$($architecture.Tests)"
    "lineCoverage=$($coverage.lineRatio)"
    "branchCoverage=$($coverage.branchRatio)"
    'sbom=PASS'
    'provenance=PASS'
    "portable=$(-not $SkipPortable)"
    'postGateExecutableDelta=NONE'
)
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
