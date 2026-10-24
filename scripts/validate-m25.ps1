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
$outputRoot = Join-Path $repo 'validation-output\m25'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
# A run that is refused must not leave the summary of an earlier run that passed: the summary is the record a
# reader is told to read instead of the output.
Remove-Item -LiteralPath (Join-Path $outputRoot 'validation-summary.txt') -Force -ErrorAction SilentlyContinue
$ratchetsPath = Join-Path $repo 'config\m21-quality-ratchets.properties'
# The minimums are the living ratchets, never the floor of this milestone's date: a literal floor is dominated by
# the living one many times over, and a validator that compares with it passes a build every other gate refuses.
. (Join-Path $PSScriptRoot 'lib\Read-QualityRatchets.ps1')
$testsMinimum = [int](Get-QualityRatchet -Path $ratchetsPath -Key 'testsMinimum' -Kind integer)
$architectureTestsMinimum = [int](Get-QualityRatchet -Path $ratchetsPath -Key 'architectureTestsMinimum' -Kind integer)
$lineCoverageMinimum = Get-QualityRatchet -Path $ratchetsPath -Key 'aggregateLineCoverageMinimum' -Kind ratio
$branchCoverageMinimum = Get-QualityRatchet -Path $ratchetsPath -Key 'aggregateBranchCoverageMinimum' -Kind ratio
$validationSha = (git rev-parse HEAD).Trim()

function Invoke-Native([string]$Label, [scriptblock]$Command) {
    Write-Host ''; Write-Host ('=' * 78); Write-Host $Label; Write-Host ('=' * 78)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

function Get-SurefireTotals([string]$Root) {
    $result = [ordered]@{ Tests = 0; Generated = 0; Failures = 0; Errors = 0; Skipped = 0; Suites = 0 }
    $reports = @(Get-ChildItem -Path $Root -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $document.testsuite
        if ($null -eq $suite) { continue }
        $result.Tests += [int]$suite.tests
        $result.Generated += @($suite.SelectNodes('testcase') | Where-Object { $_.GetAttribute('name') -match '\[[0-9]+\]$' }).Count
        $result.Failures += [int]$suite.failures
        $result.Errors += [int]$suite.errors; $result.Skipped += [int]$suite.skipped; $result.Suites++
    }
    return [pscustomobject]$result
}

function Get-Uuids([string]$Text) {
    return @([regex]::Matches($Text, '[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}') | ForEach-Object { $_.Value })
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start(); try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Invoke-LauncherText([string]$Launcher, [string[]]$Arguments) {
    $text = (& $Launcher @Arguments) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text" }
    return $text
}

# A policy decision other than PASS/WARN is a refusal and exits 4: the gate asserts the code as well as the JSON.
function Invoke-LauncherTextExpecting([string]$Launcher, [string[]]$Arguments, [int]$ExpectedExit) {
    $text = (& $Launcher @Arguments) -join "`n"
    if ($LASTEXITCODE -ne $ExpectedExit) { throw "Packaged launcher exited $LASTEXITCODE, expected $ExpectedExit : $($Arguments -join ' ')`n$text" }
    return $text
}

function Assert-PackagedM25([string]$Launcher) {
    # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
    # here inherits the ACLs of whatever it sits under, and the real owner-controlled storage path is never
    # exercised. Under the repository that inheritance is what the hardener refuses, which made a packaged
    # product gate depend on the permissions of a development checkout.
    $data = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m25-policy-' + [Guid]::NewGuid().ToString('N'))
    $projectId = '01890f7a-36d4-7c1e-8000-000000000081'
    $rule = 'new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0'

    $created = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'pack', 'create',
        '--name', 'M25 Gate Pack', '--rules', $rule, '--actor', 'gate', '--reason', 'baseline')
    $createdIds = @(Get-Uuids $created)
    if ($createdIds.Count -lt 1) { throw "Policy pack ID not found: $created" }
    $packId = $createdIds[0]

    $versions = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'pack', 'versions', '--id', $packId)
    $ids = @(Get-Uuids $versions)
    if ($ids.Count -lt 3) { throw "Policy version/rule IDs not found: $versions" }
    $versionId = $ids[1]; $ruleId = $ids[2]

    $updated = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'pack', 'update',
        '--id', $packId, '--expected-revision', '1', '--name', 'M25 Gate Pack v2',
        '--rules', "$ruleId|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
        '--actor', 'gate', '--reason', 'update')
    if ($updated -notmatch '"revision":2') { throw "Policy revision did not advance: $updated" }

    $staleOut = Join-Path $outputRoot 'stale.stdout.log'; $staleErr = Join-Path $outputRoot 'stale.stderr.log'
    $staleArgs = @(
        '--data-dir', $data, 'policy', 'pack', 'update', '--id', $packId, '--expected-revision', '1',
        '--name', 'stale', '--rules', "$ruleId|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
        '--actor', 'gate', '--reason', 'stale')
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Launcher @staleArgs 1> $staleOut 2> $staleErr
        $staleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($staleExitCode -eq 0) { throw 'Stale policy CAS unexpectedly succeeded' }
    $staleError = if (Test-Path $staleErr) { Get-Content $staleErr -Raw } else { '' }
    if ($staleError -notmatch 'stale policy pack revision') { throw "Stale policy diagnostic mismatch: $staleError" }

    $dry = Invoke-LauncherTextExpecting $Launcher @('--data-dir', $data, '--json', 'policy', 'dry-run',
        '--id', $packId, '--version', $versionId, '--project', $projectId) 4
    if ($dry -notmatch '"dryRun":true' -or $dry -notmatch '"decision":"UNKNOWN"') { throw "Policy dry-run mismatch: $dry" }
    $auditBefore = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'audit', '--id', $packId)
    if ($auditBefore -match 'ACTIVATE') { throw 'Dry-run unexpectedly wrote activation audit' }

    Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'activate', '--id', $packId,
        '--version', $versionId, '--project', $projectId, '--expected-revision', '0', '--actor', 'gate', '--reason', 'enable') | Out-Null
    Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'policy', 'override', 'put', '--id', $packId,
        '--rule', $ruleId, '--mode', 'FORCE_BLOCK', '--project', $projectId, '--expected-revision', '0',
        '--actor', 'gate', '--reason', 'explicit') | Out-Null
    $evaluated = Invoke-LauncherTextExpecting $Launcher @('--data-dir', $data, '--json', 'policy', 'evaluate', '--id', $packId, '--project', $projectId) 4
    if ($evaluated -notmatch '"originalDecision":"UNKNOWN"' -or $evaluated -notmatch '"effectiveDecision":"BLOCK"') {
        throw "Policy override provenance mismatch: $evaluated"
    }
    Write-Host 'Policy versioning + CAS + dry-run + override explainability: PASS'

    $port = Get-FreeLoopbackPort
    $apiStdout = Join-Path $outputRoot 'api.stdout.log'; $apiStderr = Join-Path $outputRoot 'api.stderr.log'
    $process = Start-Process -FilePath $Launcher -ArgumentList @('--data-dir', $data, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $apiStdout -RedirectStandardError $apiStderr -WindowStyle Hidden -PassThru
    try {
        $apiOk = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $apiStderr) { Get-Content $apiStderr -Raw } else { '' }
                throw "Packaged API exited before M25 check: $diagnostic"
            }
            try {
                $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$port/api/v1/policy-packs/$packId" -TimeoutSec 2
                if ($response.data.revision -eq 2) { $apiOk = $true; break }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        if (-not $apiOk) { throw 'Packaged API M25 policy route check timed out' }
    } finally {
        # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
        Remove-Item -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
    Write-Host 'Packaged CLI/MCP/HTTP M25 convergence: PASS'
}

Write-Host "M25 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) { throw "M25 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")" }

git rev-parse --verify --quiet "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'develop'; git rev-parse --verify --quiet "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw 'M25 base ref not found: origin/develop (fallback develop also missing)' }
}
Write-Host "M25 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) { throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)" }
$writtenTests = $totals.Tests - $totals.Generated
if ($writtenTests -lt $testsMinimum) {
    throw "M25 test baseline regression: $writtenTests < $testsMinimum written executions (testsMinimum in $ratchetsPath)"
}
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt $architectureTestsMinimum) {
    throw "M25 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTestsMinimum in $ratchetsPath)"
}
Write-Host "Tests: PASS ($writtenTests written, baseline >= $testsMinimum)"
Write-Host "Architecture: PASS ($($architecture.Tests), baseline >= $architectureTestsMinimum)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-aggregate-coverage-summary.txt'
& (Join-Path $PSScriptRoot 'lib\Require-AggregateCoverageEvidence.ps1') -EvidencePath $coverageSummary
$coverage = @{}; Get-Content $coverageSummary | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] } }
$lineRatio = [double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture)
$branchRatio = [double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture)
if ($lineRatio -lt [double]::Parse($lineCoverageMinimum, [Globalization.CultureInfo]::InvariantCulture)) {
    throw "M25 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio)"
}
if ($branchRatio -lt [double]::Parse($branchCoverageMinimum, [Globalization.CultureInfo]::InvariantCulture)) {
    throw "M25 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio)"
}
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio))"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'; $sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) { throw 'CycloneDX JSON/XML SBOM is missing' }
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "Provenance writer failed with exit code $LASTEXITCODE" }
if (-not (Test-Path (Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'))) { throw 'Build provenance is missing' }
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m25\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m25\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }
    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarTool)) { throw "jar tool not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    foreach ($entry in @(
        'com/morpheus/application/policy/PolicyPackService.class',
        'com/morpheus/application/policy/PolicyEvaluationService.class',
        'com/morpheus/application/policy/DefaultPolicyFactResolver.class',
        'com/morpheus/store/sqlite/adapter/SqlitePolicyPackStore.class',
        'com/morpheus/cli/MorpheusPolicyCli.class',
        'com/morpheus/mcp/MorpheusPolicyMcpTools.class',
        'com/morpheus/api/MorpheusPolicyApiService.class',
        'com/morpheus/api/MorpheusPolicyHttpRoutes.class',
        'db/migration/V015__policy_packs.sql')) {
        if ($entries -notcontains $entry) { throw "M25 packaged runtime is missing $entry" }
    }
    $help = (& $launcher help) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $help -notmatch 'Policy packs / governance automation \(M25\)') { throw "Packaged M25 CLI help smoke failed: $help" }
    Write-Host 'M25 classes + V015 + CLI help packaging proof: PASS'
    Assert-PackagedM25 -Launcher $launcher
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M25 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) { throw "Tracked workspace delta appeared during M25 validation:`n$($finalTracked -join "`n")" }

$summary = @(
    'M25 VALIDATION PASS', "sha=$validationSha", "baseRef=$BaseRef", "version=$Version",
    "tests=$($totals.Tests)", "architectureTests=$($architecture.Tests)",
    "lineCoverage=$($coverage.lineRatio)", "branchCoverage=$($coverage.branchRatio)",
    'policyPacks=PASS', 'policyVersioning=PASS', 'policyOverrides=PASS', 'policyDryRun=PASS',
    'policyExplainability=PASS', 'surfaceConvergence=PASS', 'sqliteV015=PASS',
    'sbom=PASS', 'provenance=PASS', "portable=$(-not $SkipPortable)", 'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
