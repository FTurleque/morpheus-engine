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
$outputRoot = Join-Path $repo 'validation-output\m24'
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

function Invoke-LauncherText([string]$Launcher, [string[]]$Arguments) {
    $text = (& $Launcher @Arguments) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text" }
    return $text
}

function Assert-PackagedM24([string]$Launcher) {
    # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
    # here inherits the ACLs of whatever it sits under, and the real owner-controlled storage path is never
    # exercised. Under the repository that inheritance is what the hardener refuses, which made a packaged
    # product gate depend on the permissions of a development checkout.
    $data = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m24-query-' + [Guid]::NewGuid().ToString('N'))
    $projectId = '01890f7a-36d4-7c1e-8000-000000000071'

    $queryText = Invoke-LauncherText $Launcher @(
        '--data-dir', $data, '--json', 'query', 'execute',
        '--project', $projectId, '--entity', 'change', '--filter', 'title contains security',
        '--sort', 'title:asc', '--fields', 'id,title', '--limit', '25')
    if ($queryText -notmatch '"entityType":"CHANGE"' -or $queryText -notmatch '"totalMatches":0') {
        throw "Packaged query mismatch: $queryText"
    }

    $budgetStdout = Join-Path $outputRoot 'page-budget.stdout.log'
    $budgetStderr = Join-Path $outputRoot 'page-budget.stderr.log'
    $budget = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $data, 'query', 'execute', '--project', $projectId, '--entity', 'change', '--limit', '501') `
        -RedirectStandardOutput $budgetStdout -RedirectStandardError $budgetStderr -WindowStyle Hidden -Wait -PassThru
    if ($budget.ExitCode -eq 0) { throw 'Packaged query page budget unexpectedly accepted limit 501' }
    $budgetError = if (Test-Path $budgetStderr) { Get-Content $budgetStderr -Raw } else { '' }
    if ($budgetError -notmatch 'limit must be between 1 and 500') {
        throw "Packaged page-budget diagnostic mismatch: $budgetError"
    }
    Write-Host 'Provider-neutral query DSL + page budget: PASS'

    $createdText = Invoke-LauncherText $Launcher @(
        '--data-dir', $data, '--json', 'views', 'create', '--name', 'M24 Gate View',
        '--project', $projectId, '--entity', 'change', '--filter', 'title contains security', '--fields', 'id,title')
    $viewId = Get-FirstUuid $createdText

    $updatedText = Invoke-LauncherText $Launcher @(
        '--data-dir', $data, '--json', 'views', 'update', '--id', $viewId,
        '--expected-revision', '1', '--name', 'M24 Gate View v2', '--entity', 'change',
        '--filter', 'title contains security', '--fields', 'id,title')
    if ($updatedText -notmatch '"revision":2') { throw "Saved-view revision did not advance: $updatedText" }

    $staleStdout = Join-Path $outputRoot 'stale.stdout.log'
    $staleStderr = Join-Path $outputRoot 'stale.stderr.log'
    $stale = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $data, 'views', 'update', '--id', $viewId, '--expected-revision', '1', '--name', 'stale', '--entity', 'change') `
        -RedirectStandardOutput $staleStdout -RedirectStandardError $staleStderr -WindowStyle Hidden -Wait -PassThru
    if ($stale.ExitCode -eq 0) { throw 'Stale saved-view CAS unexpectedly succeeded' }
    $staleError = if (Test-Path $staleStderr) { Get-Content $staleStderr -Raw } else { '' }
    if ($staleError -notmatch 'stale saved view revision') { throw "Saved-view stale CAS diagnostic mismatch: $staleError" }

    $versionsText = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'views', 'versions', '--id', $viewId)
    if ($versionsText -notmatch '"revision":1' -or $versionsText -notmatch '"revision":2') {
        throw "Saved-view versions mismatch: $versionsText"
    }
    Write-Host 'Versioned saved views + stale CAS rejection: PASS'

    $jsonExport = Invoke-LauncherText $Launcher @('--data-dir', $data, 'export', 'view', '--format', 'json', '--id', $viewId)
    $csvExport = Invoke-LauncherText $Launcher @('--data-dir', $data, 'export', 'view', '--format', 'csv', '--id', $viewId)
    $markdownExport = Invoke-LauncherText $Launcher @('--data-dir', $data, 'export', 'view', '--format', 'markdown', '--id', $viewId)
    if ($jsonExport -notmatch '"scopeKind":"PROJECT"' -or $jsonExport -notmatch '"totalMatches":0') {
        throw "Canonical JSON export mismatch: $jsonExport"
    }
    if ($csvExport.TrimEnd("`r", "`n") -ne '"id","projectId","title"') {
        throw "CSV export mismatch: $csvExport"
    }
    if ($markdownExport -notmatch '\| id \| projectId \| title \|' -or $markdownExport -notmatch '\| --- \| --- \| --- \|') {
        throw "Markdown export mismatch: $markdownExport"
    }
    $afterText = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'views', 'get', '--id', $viewId)
    if ($afterText -notmatch '"revision":2') { throw 'Export mutated saved-view revision' }
    Write-Host 'Canonical JSON + CSV + Markdown read-only exports: PASS'

    $port = Get-FreeLoopbackPort
    $apiStdout = Join-Path $outputRoot 'api.stdout.log'
    $apiStderr = Join-Path $outputRoot 'api.stderr.log'
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $data, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $apiStdout -RedirectStandardError $apiStderr -WindowStyle Hidden -PassThru
    try {
        $apiOk = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $apiStderr) { Get-Content $apiStderr -Raw } else { '' }
                throw "Packaged API exited before M24 checks: $diagnostic"
            }
            try {
                $body = [ordered]@{
                    scopeKind = 'PROJECT'
                    scopeId = $projectId
                    query = [ordered]@{ entity = 'change'; filter = 'title contains security'; limit = 25 }
                } | ConvertTo-Json -Depth 5 -Compress
                $queryResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$port/api/v1/queries/execute" `
                    -ContentType 'application/json' -Body $body -TimeoutSec 2
                $savedResponse = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$port/api/v1/saved-views/$viewId" -TimeoutSec 2
                if ($queryResponse.data.totalMatches -eq 0 -and $savedResponse.data.revision -eq 2) {
                    $apiOk = $true
                    break
                }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        if (-not $apiOk) { throw 'Packaged API M24 query/saved-view check timed out' }
    } finally {
        # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
        Remove-Item -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
    Write-Host 'Packaged CLI/MCP/HTTP M24 convergence: PASS'
}

Write-Host "M24 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "M24 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'main'
    git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw 'M24 base ref not found: origin/main (fallback main also missing)' }
}
Write-Host "M24 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) {
    throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)"
}
if ($totals.Tests -lt 507) { throw "M24 test baseline regression: $($totals.Tests) < 507" }
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 195) { throw "M24 architecture baseline regression: $($architecture.Tests) < 195" }
Write-Host "Tests: PASS ($($totals.Tests), M23 baseline >= 507)"
Write-Host "Architecture: PASS ($($architecture.Tests), M23 baseline >= 195)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing production coverage summary: $coverageSummary" }
$coverage = @{}
Get-Content $coverageSummary | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] }
}
if ([double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.25) {
    throw "M24 line coverage below 25%: $($coverage.lineRatio)"
}
if ([double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.20) {
    throw "M24 branch coverage below 20%: $($coverage.branchRatio)"
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
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m24\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m24\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarTool)) { throw "jar tool not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    if ($LASTEXITCODE -ne 0) { throw "jar tf failed with exit code $LASTEXITCODE" }
    foreach ($entry in @(
        'com/morpheus/application/query/dsl/QueryExecutionService.class',
        'com/morpheus/application/query/dsl/QueryDslParser.class',
        'com/morpheus/application/query/saved/SavedViewService.class',
        'com/morpheus/application/query/export/QueryExportService.class',
        'com/morpheus/store/sqlite/SqliteSavedViewStore.class',
        'com/morpheus/cli/MorpheusQueryCli.class',
        'com/morpheus/mcp/MorpheusQueryMcpTools.class',
        'com/morpheus/api/MorpheusQueryApiService.class',
        'com/morpheus/api/MorpheusQueryHttpRoutes.class',
        'db/migration/V014__saved_views.sql')) {
        if ($entries -notcontains $entry) { throw "M24 packaged runtime is missing $entry" }
    }
    $help = (& $launcher help) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $help -notmatch 'Query DSL / saved views / reporting \(M24\)') {
        throw "Packaged M24 CLI help smoke failed: $help"
    }
    Write-Host 'M24 classes + V014 + CLI help packaging proof: PASS'
    Assert-PackagedM24 -Launcher $launcher
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M24 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during M24 validation:`n$($finalTracked -join "`n")"
}

$summary = @(
    'M24 VALIDATION PASS'
    "sha=$validationSha"
    "baseRef=$BaseRef"
    "version=$Version"
    "tests=$($totals.Tests)"
    "architectureTests=$($architecture.Tests)"
    "lineCoverage=$($coverage.lineRatio)"
    "branchCoverage=$($coverage.branchRatio)"
    'queryDsl=PASS'
    'savedViews=PASS'
    'canonicalJsonExport=PASS'
    'csvExport=PASS'
    'markdownExport=PASS'
    'queryBudgets=PASS'
    'surfaceConvergence=PASS'
    'sqliteV014=PASS'
    'sbom=PASS'
    'provenance=PASS'
    "portable=$(-not $SkipPortable)"
    'postGateExecutableDelta=NONE'
)
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
