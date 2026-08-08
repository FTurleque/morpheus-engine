[CmdletBinding()]
param(
    [string]$Version = '1.2.0',
    [string]$BaseRef = 'origin/develop',
    [switch]$SkipSecurityScan,
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\d2'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Assert-NativeSuccess([string]$Label) {
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

function Resolve-BaseRef([string]$Requested) {
    & git rev-parse --verify "$Requested^{commit}" *> $null
    if ($LASTEXITCODE -eq 0) { return $Requested }
    & git rev-parse --verify 'develop^{commit}' *> $null
    if ($LASTEXITCODE -eq 0) { return 'develop' }
    throw "D2 base ref not found: $Requested (fallback develop also missing)"
}

function Assert-ReactorVersion([string]$ExpectedVersion) {
    $poms = @(Get-ChildItem -Path $repo -Recurse -File -Filter 'pom.xml' |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' })
    if ($poms.Count -ne 17) { throw "Unexpected Maven reactor POM count: $($poms.Count), expected 17" }
    foreach ($pom in $poms) {
        $content = Get-Content -LiteralPath $pom.FullName -Raw
        if (-not $content.Contains("<version>$ExpectedVersion</version>")) {
            throw "MORPHEUS $ExpectedVersion version missing from $($pom.FullName)"
        }
    }
}

function Read-KeyValueFile([string]$Path) {
    $result = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') { $result[$matches[1]] = $matches[2] }
    }
    return $result
}

$BaseRef = Resolve-BaseRef $BaseRef
Write-Host "D2 exact-head validation SHA: $validationSha"
Write-Host "D2 diff base: $BaseRef"

$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "D2 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

& git diff --check "$BaseRef...HEAD"
Assert-NativeSuccess 'D2 git diff check'

$changedFiles = @(git diff --name-only "$BaseRef...HEAD")
if ($changedFiles | Where-Object { $_ -like '.github/workflows/*' }) {
    throw 'D2 is local-only and must not modify GitHub Actions workflows'
}
Write-Host 'D2 no-CI scope: PASS (.github/workflows delta NONE)'

Assert-ReactorVersion $Version
$pom = Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw
foreach ($token in @(
    '<jackson.version>3.1.5</jackson.version>',
    '<sqlite-jdbc.version>3.53.2.0</sqlite-jdbc.version>',
    '<dependency-check.maven.plugin.version>12.2.2</dependency-check.maven.plugin.version>',
    '<failOnWarning>true</failOnWarning>',
    '<id>d2-security</id>',
    '<failBuildOnCVSS>7.0</failBuildOnCVSS>')) {
    if (-not $pom.Contains($token)) { throw "D2 dependency/quality token missing from pom.xml: $token" }
}
Write-Host 'D2 dependency baseline: PASS'

& .\mvnw.cmd clean verify
Assert-NativeSuccess 'D2 Maven clean verify'

$reports = @(Get-ChildItem -Path $repo -Recurse -File -Filter 'TEST-*.xml' |
    Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
$architectureTests = 0
foreach ($report in $reports) {
    [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
    $suite = $xml.testsuite
    if ($null -eq $suite) { continue }
    $tests += [int]$suite.tests
    $failures += [int]$suite.failures
    $errors += [int]$suite.errors
    $skipped += [int]$suite.skipped
    if ($report.FullName -like "$(Join-Path $repo 'morpheus-architecture-tests')*") {
        $architectureTests += [int]$suite.tests
    }
}
if ($failures -ne 0 -or $errors -ne 0) {
    throw "D2 Surefire failures=$failures errors=$errors"
}
if ($tests -lt 613) { throw "D2 test baseline regression: $tests < 613" }
if ($architectureTests -lt 247) { throw "D2 architecture baseline regression: $architectureTests < 247" }
Write-Host "D2 tests: PASS ($tests tests, architecture=$architectureTests, skipped=$skipped)"

$coveragePath = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path -LiteralPath $coveragePath -PathType Leaf)) {
    throw "D2 coverage summary missing: $coveragePath"
}
$coverage = Read-KeyValueFile $coveragePath
$lineCoverage = [double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture)
$branchCoverage = [double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture)
if ($lineCoverage -lt 0.40) { throw "D2 line coverage below 0.40: $lineCoverage" }
if ($branchCoverage -lt 0.35) { throw "D2 branch coverage below 0.35: $branchCoverage" }
Write-Host "D2 coverage: PASS (line=$lineCoverage branch=$branchCoverage)"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'
$sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path -LiteralPath $sbomJson -PathType Leaf) -or -not (Test-Path -LiteralPath $sbomXml -PathType Leaf)) {
    throw 'D2 CycloneDX aggregate SBOM JSON/XML missing after clean verify'
}
Write-Host 'D2 SBOM: PASS'

$securityScan = 'SKIPPED'
if (-not $SkipSecurityScan) {
    & .\mvnw.cmd '-Pd2-security' 'org.owasp:dependency-check-maven:12.2.2:aggregate'
    Assert-NativeSuccess 'D2 OWASP Dependency-Check aggregate'
    $securityReport = Join-Path $repo 'target\d2-security\dependency-check-report.json'
    if (-not (Test-Path -LiteralPath $securityReport -PathType Leaf)) {
        throw "D2 dependency-check JSON report missing: $securityReport"
    }
    $securityScan = 'PASS'
    Write-Host 'D2 SCA: PASS (OWASP Dependency-Check, CVSS >= 7 fails the gate)'
}

$portable = 'SKIPPED'
if (-not $SkipPortable) {
    $dist = 'validation-output\d2\dist'
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory $dist
    Assert-NativeSuccess 'D2 Windows portable build'
    $launcher = Join-Path $repo "$dist\.m20-windows\image\morpheus\morpheus.exe"
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw "D2 packaged launcher missing: $launcher"
    }
    $productInfoRaw = & $launcher --json product-info
    Assert-NativeSuccess 'D2 packaged product-info smoke'
    $productInfo = $productInfoRaw | ConvertFrom-Json
    if ($productInfo.version -ne $Version) {
        throw "D2 packaged version mismatch: $($productInfo.version) != $Version"
    }
    $portable = 'PASS'
    Write-Host 'D2 Windows portable: PASS'
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during D2 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during D2 validation:`n$($finalTracked -join "`n")"
}

$summary = @(
    'D2 VALIDATION PASS',
    "sha=$validationSha",
    "baseRef=$BaseRef",
    "version=$Version",
    "tests=$tests",
    "architectureTests=$architectureTests",
    "lineCoverage=$($coverage.lineRatio)",
    "branchCoverage=$($coverage.branchRatio)",
    'dependencyHygiene=PASS',
    "securityScan=$securityScan",
    'sbom=PASS',
    "portable=$portable",
    'ciUsed=false',
    'ciWorkflowDelta=NONE',
    'workspaceTrackedClean=PASS')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
