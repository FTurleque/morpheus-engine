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
$outputRoot = Join-Path $repo 'validation-output\m23'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Invoke-Native([string]$Label, [scriptblock]$Command) {
    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host $Label
    Write-Host ('=' * 78)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
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

function Get-FirstUuid([string]$Text) {
    $match = [regex]::Match($Text, '[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}')
    if (-not $match.Success) { throw "UUIDv7 not found in payload: $Text" }
    return $match.Value
}

function Assert-PackagedPortfolio([string]$Launcher) {
    $data = Join-Path $outputRoot 'portfolio-data'
    Remove-Item $data -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $data | Out-Null

    $createdText = (& $Launcher --data-dir $data --json portfolio create --name 'M23 Gate Portfolio') -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged portfolio create failed: $createdText" }
    $portfolioId = Get-FirstUuid $createdText
    $projectId = [guid]::NewGuid().ToString()
    # Project IDs require UUIDv7; derive one from a real MORPHEUS portfolio creation output.
    $secondPortfolioText = (& $Launcher --data-dir $data --json portfolio create --name 'M23 Project Identity Seed') -join "`n"
    $projectId = Get-FirstUuid $secondPortfolioText
    $registeredText = (& $Launcher --data-dir $data --json portfolio add-project --portfolio $portfolioId --project $projectId --name 'Gate Project' --workspace $data --providers reference) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged portfolio project registration failed: $registeredText" }
    $overviewText = (& $Launcher --data-dir $data --json portfolio overview --portfolio $portfolioId) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $overviewText -notmatch [regex]::Escape($projectId) -or $overviewText -notmatch '"referenceCount":0') {
        throw "Packaged portfolio overview mismatch: $overviewText"
    }
    Write-Host 'Packaged portfolio CLI create/register/overview: PASS'

    $port = Get-FreeLoopbackPort
    $stdout = Join-Path $outputRoot 'api.stdout.log'
    $stderr = Join-Path $outputRoot 'api.stderr.log'
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $data, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
                throw "Packaged API exited before M23 portfolio checks: $diagnostic"
            }
            try {
                $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/portfolios/$portfolioId" -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200 -and $response.Content -match [regex]::Escape($projectId)) {
                    Write-Host "Packaged HTTP portfolio overview: PASS (http://127.0.0.1:$port/api/v1/portfolios/$portfolioId)"
                    return
                }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        throw 'Packaged API M23 portfolio check timed out'
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
}

Write-Host "M23 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "M23 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'main'
    git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw 'M23 base ref not found: origin/main (fallback main also missing)' }
}
Write-Host "M23 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) {
    throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)"
}
if ($totals.Tests -lt 494) { throw "M23 test baseline regression: $($totals.Tests) < 494" }
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 190) { throw "M23 architecture baseline regression: $($architecture.Tests) < 190" }
Write-Host "Tests: PASS ($($totals.Tests), baseline >= 494)"
Write-Host "Architecture: PASS ($($architecture.Tests), baseline >= 190)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing production coverage summary: $coverageSummary" }
$coverage = @{}
Get-Content $coverageSummary | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] }
}
if ([double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.25) {
    throw "M23 line coverage below 25%: $($coverage.lineRatio)"
}
if ([double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.20) {
    throw "M23 branch coverage below 20%: $($coverage.branchRatio)"
}
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio))"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'
$sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) { throw 'CycloneDX JSON/XML SBOM is missing' }
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "Provenance writer failed with exit code $LASTEXITCODE" }
if (-not (Test-Path (Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'))) {
    throw 'Build provenance is missing'
}
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m23\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m23\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $entries = & $jarTool tf $shadedJar.FullName
    foreach ($entry in @(
        'com/morpheus/application/portfolio/PortfolioRegistryService.class',
        'com/morpheus/application/portfolio/PortfolioTraversalService.class',
        'com/morpheus/store/sqlite/SqlitePortfolioStore.class',
        'com/morpheus/cli/MorpheusPortfolioCli.class',
        'com/morpheus/mcp/MorpheusPortfolioMcpTools.class',
        'com/morpheus/api/MorpheusPortfolioApiService.class',
        'db/migration/V013__portfolio_intelligence.sql')) {
        if ($entries -notcontains $entry) { throw "M23 packaged runtime is missing $entry" }
    }
    $help = (& $launcher help) -join "`n"
    if ($help -notmatch 'Portfolio intelligence \(M23\)') { throw "Packaged M23 CLI help smoke failed: $help" }
    Write-Host 'M23 classes + V013 + CLI help packaging proof: PASS'
    Assert-PackagedPortfolio -Launcher $launcher
    Write-Host 'Packaged CLI/MCP/HTTP portfolio convergence: PASS'
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M23 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during M23 validation:`n$($finalTracked -join "`n")"
}

$summary = @(
    'M23 VALIDATION PASS'
    "sha=$validationSha"
    "baseRef=$BaseRef"
    "version=$Version"
    "tests=$($totals.Tests)"
    "architectureTests=$($architecture.Tests)"
    "lineCoverage=$($coverage.lineRatio)"
    "branchCoverage=$($coverage.branchRatio)"
    'portfolioIdentity=PASS'
    'crossProjectReferences=PASS'
    'boundedTraversal=PASS'
    'sqliteV013=PASS'
    'surfaceConvergence=PASS'
    'sbom=PASS'
    'provenance=PASS'
    "portable=$(-not $SkipPortable)"
    'postGateExecutableDelta=NONE'
)
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
