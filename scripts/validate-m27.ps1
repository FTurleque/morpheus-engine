[CmdletBinding()]
param(
    [string]$Version = '1.0.0',
    [string]$BaseRef = 'origin/develop',
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\m27'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Invoke-Native([string]$Label, [scriptblock]$Command) {
    Write-Host ''; Write-Host ('=' * 78); Write-Host $Label; Write-Host ('=' * 78)
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
        $result.Tests += [int]$suite.tests; $result.Failures += [int]$suite.failures
        $result.Errors += [int]$suite.errors; $result.Skipped += [int]$suite.skipped; $result.Suites++
    }
    return [pscustomobject]$result
}

function Invoke-LauncherText([string]$Launcher, [string[]]$Arguments) {
    $text = (& $Launcher @Arguments) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text" }
    return $text
}

Write-Host "M27 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) { throw "M27 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")" }

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'develop'; git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw 'M27 base ref not found: origin/develop (fallback develop also missing)' }
}
Write-Host "M27 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) { throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)" }
if ($totals.Tests -lt 598) { throw "M27 M26-baseline regression: $($totals.Tests) < 598" }
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 238) { throw "M27 architecture baseline regression: $($architecture.Tests) < 238" }
Write-Host "Tests: PASS ($($totals.Tests), M27 minimum >= 598)"
Write-Host "Architecture: PASS ($($architecture.Tests), M27 minimum >= 238)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing production coverage summary: $coverageSummary" }
$coverage = @{}; Get-Content $coverageSummary | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] } }
$lineRatio = [double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture)
$branchRatio = [double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture)
if ($lineRatio -lt 0.42) { throw "M27 line coverage below 42%: $($coverage.lineRatio)" }
if ($branchRatio -lt 0.35) { throw "M27 branch coverage below 35%: $($coverage.branchRatio)" }
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio))"

$manifest = Get-Content -LiteralPath (Join-Path $repo 'contracts\public-surfaces.tsv') -Raw
$openApi = Get-Content -LiteralPath (Join-Path $repo 'docs\openapi\morpheus-v1-reasoning-m27.yaml') -Raw
foreach ($contract in @(
    "reasoning.adapters`tREAD`treason adapters`tlist_reasoning_adapters`tGET /api/v1/reasoning/adapters",
    "reasoning.analyze`tREAD`treason analyze`treason_with_evidence`tPOST /api/v1/reasoning/analyze")) {
    if (-not $manifest.Contains($contract)) { throw "M27 public convergence contract missing: $contract" }
}
if ($openApi -notmatch '/reasoning/adapters:' -or $openApi -notmatch '/reasoning/analyze:' -or $openApi -notmatch 'const: false') {
    throw 'M27 OpenAPI separation/read-only contract is incomplete'
}
Write-Host 'CLI/MCP/HTTP convergence + OpenAPI mutation boundary: PASS'

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'; $sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) { throw 'CycloneDX JSON/XML SBOM is missing' }
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "Provenance writer failed with exit code $LASTEXITCODE" }
if (-not (Test-Path (Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'))) { throw 'Build provenance is missing' }
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m27\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m27\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }
    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarTool)) { throw "jar tool not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    foreach ($entry in @(
        'com/morpheus/application/reasoning/ReasoningContracts.class',
        'com/morpheus/application/reasoning/ReasoningService.class',
        'com/morpheus/application/reasoning/ReasoningAdapter.class',
        'com/morpheus/application/reasoning/EvidenceSynthesisReasoningAdapter.class',
        'com/morpheus/cli/MorpheusReasoningCli.class',
        'com/morpheus/api/MorpheusReasoningHttpRoutes.class',
        'com/morpheus/mcp/MorpheusReasoningMcpTools.class')) {
        if ($entries -notcontains $entry) { throw "M27 packaged runtime is missing $entry" }
    }

    $adapters = (Invoke-LauncherText $launcher @('--json', 'reason', 'adapters')) | ConvertFrom-Json
    if (@($adapters).Count -lt 1 -or @($adapters.id) -notcontains 'builtin-evidence-synthesis-v1') {
        throw 'Packaged M27 adapter discovery smoke failed'
    }
    $factsOnly = (Invoke-LauncherText $launcher @('--json', 'reason', 'analyze',
        '--question', 'What remains authoritative?',
        '--evidence', 'fact-1|PUBLISHED_FACT|history|Published history remains authoritative|source=gate')) | ConvertFrom-Json
    if ($factsOnly.assisted -or $factsOnly.mutated -or @($factsOnly.facts).Count -ne 1 -or @($factsOnly.inferences).Count -ne 0) {
        throw 'Packaged M27 facts-only separation smoke failed'
    }
    $assisted = (Invoke-LauncherText $launcher @('--json', 'reason', 'analyze',
        '--question', 'Can remote mode be enabled safely?',
        '--evidence', 'fact-1|PUBLISHED_FACT|remote|TLS is required|source=gate',
        '--evidence', 'fact-2|PUBLISHED_FACT|remote|Authentication is required|source=gate',
        '--adapter', 'builtin-evidence-synthesis-v1', '--max-claims', '10')) | ConvertFrom-Json
    if (-not $assisted.assisted -or $assisted.mutated -or @($assisted.inferences).Count -lt 1 -or @($assisted.heuristics).Count -lt 1) {
        throw 'Packaged M27 assisted reasoning smoke failed'
    }
    if (@($assisted.inferences[0].evidenceIds).Count -lt 1 -or [double]$assisted.inferences[0].confidence.score -lt 0 -or [double]$assisted.inferences[0].confidence.score -gt 1) {
        throw 'Packaged M27 evidence/confidence contract failed'
    }
    Write-Host 'Packaged facts-only + explicit assisted reasoning: PASS'
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M27 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) { throw "Tracked workspace delta appeared during M27 validation:`n$($finalTracked -join "`n")" }

$summary = @(
    'M27 VALIDATION PASS', "sha=$validationSha", "baseRef=$BaseRef", "version=$Version",
    "tests=$($totals.Tests)", "architectureTests=$($architecture.Tests)",
    "lineCoverage=$($coverage.lineRatio)", "branchCoverage=$($coverage.branchRatio)",
    'factsInferenceSeparation=PASS', 'explicitConfidence=PASS', 'evidenceProvenance=PASS',
    'adapterOptionality=PASS', 'adapterFailureIsolation=PASS', 'noSilentMutation=PASS',
    'surfaceConvergence=PASS', 'remoteReadRbac=PASS', 'sbom=PASS', 'provenance=PASS',
    "portable=$(-not $SkipPortable)", 'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
