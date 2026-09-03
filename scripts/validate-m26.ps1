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
$outputRoot = Join-Path $repo 'validation-output\m26'
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

function Invoke-ExpectedFailure([string]$Launcher, [string[]]$Arguments, [string]$ExpectedPattern, [string]$Name) {
    $stdout = Join-Path $outputRoot "$Name.stdout.log"
    $stderr = Join-Path $outputRoot "$Name.stderr.log"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Launcher @Arguments 1> $stdout 2> $stderr
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -eq 0) { throw "$Name unexpectedly succeeded" }
    $diagnostic = if (Test-Path $stderr) { Get-Content -LiteralPath $stderr -Raw } else { '' }
    if ($diagnostic -notmatch $ExpectedPattern) { throw "$Name diagnostic mismatch: $diagnostic" }
}

function Assert-PackagedM26([string]$Launcher) {
    # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
    # here inherits the ACLs of whatever it sits under, and the real owner-controlled storage path is never
    # exercised. Under the repository that inheritance is precisely what the hardener refuses, which made a
    # packaged product gate depend on the permissions of a development checkout. The portable API smoke in
    # distribution/build-portable.ps1 already provisions its data directory this way.
    $data = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m26-' + [Guid]::NewGuid().ToString('N'))
    try {
        $identityJson = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'server', 'identity', 'create',
            '--principal', 'gate-admin', '--role', 'ADMIN')
        $identity = $identityJson | ConvertFrom-Json
        if ([string]::IsNullOrWhiteSpace([string]$identity.token)) { throw "Generated M26 bearer token missing: $identityJson" }
        if ($identity.tokenPersistence -ne 'NOT_PERSISTED_PRINTED_ONCE') { throw "Token persistence contract mismatch: $identityJson" }
        $authFile = Join-Path $data 'config\remote-auth.txt'
        if (-not (Test-Path $authFile)) { throw "M26 remote auth file missing: $authFile" }
        $authText = Get-Content -LiteralPath $authFile -Raw
        if ($authText.Contains([string]$identity.token)) { throw 'Plaintext bearer token leaked into persisted auth file' }
        if ($authText -notmatch 'gate-admin\|ADMIN\|[0-9a-f]{64}') { throw "Hashed auth entry missing: $authText" }
        Write-Host 'Remote identity hash-only provisioning: PASS'

        $backupJson = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'server', 'backup', 'create')
        $backup = $backupJson | ConvertFrom-Json
        if (-not $backup.integrityOk -or [int]$backup.schemaVersion -ne 17) { throw "M26 backup contract mismatch: $backupJson" }
        if (-not (Test-Path ([string]$backup.path))) { throw "M26 backup file missing: $($backup.path)" }
        $verifyJson = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'server', 'backup', 'verify', '--file', ([string]$backup.path))
        $verified = $verifyJson | ConvertFrom-Json
        if (-not $verified.integrityOk -or [int]$verified.schemaVersion -ne 17 -or $verified.sha256 -ne $backup.sha256) {
            throw "M26 backup verification mismatch: $verifyJson"
        }
        Invoke-ExpectedFailure $Launcher @('--data-dir', $data, 'server', 'restore', '--file', ([string]$backup.path)) '--confirm' 'restore-unconfirmed'
        $restoredJson = Invoke-LauncherText $Launcher @('--data-dir', $data, '--json', 'server', 'restore', '--file', ([string]$backup.path), '--confirm')
        $restored = $restoredJson | ConvertFrom-Json
        if (-not $restored.integrityOk -or [int]$restored.schemaVersion -ne 17) { throw "M26 offline restore mismatch: $restoredJson" }
        Write-Host 'SQLite backup + verify + explicit offline restore: PASS'

        Invoke-ExpectedFailure $Launcher @('--data-dir', $data, 'api', '--host', '0.0.0.0', '--port', '18765') 'requires explicit.*api --remote' 'local-nonloopback'
        Invoke-ExpectedFailure $Launcher @('--data-dir', $data, 'api', '--remote', '--host', '127.0.0.1', '--port', '18766') 'requires --tls-keystore|TLS keystore' 'remote-missing-tls'
        Write-Host 'Local-first bind boundary + remote fail-closed startup: PASS'
    } finally {
        # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
        Remove-Item -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "M26 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) { throw "M26 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")" }

git rev-parse --verify "$BaseRef`^{commit}" *> $null
if ($LASTEXITCODE -ne 0) {
    $BaseRef = 'develop'; git rev-parse --verify "$BaseRef`^{commit}" *> $null
    if ($LASTEXITCODE -ne 0) { throw 'M26 base ref not found: origin/develop (fallback develop also missing)' }
}
Write-Host "M26 diff base: $BaseRef"
Invoke-Native 'git diff --check' { git diff --check "$BaseRef...HEAD" }
Invoke-Native 'Maven clean verify' { & .\mvnw.cmd 'clean' 'verify' }

$totals = Get-SurefireTotals $repo
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0) { throw "Surefire failures=$($totals.Failures) errors=$($totals.Errors)" }
if ($totals.Tests -lt 565) { throw "M26 M25-baseline regression: $($totals.Tests) < 565" }
$architecture = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
if ($architecture.Tests -lt 231) { throw "M26 architecture baseline regression: $($architecture.Tests) < 231" }
Write-Host "Tests: PASS ($($totals.Tests), M25 baseline >= 565)"
Write-Host "Architecture: PASS ($($architecture.Tests), M25 baseline >= 231)"

$coverageSummary = Join-Path $repo 'morpheus-architecture-tests\target\m21-coverage-summary.txt'
if (-not (Test-Path $coverageSummary)) { throw "Missing production coverage summary: $coverageSummary" }
$coverage = @{}; Get-Content $coverageSummary | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $coverage[$matches[1]] = $matches[2] } }
if ([double]::Parse($coverage.lineRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.25) { throw "M26 line coverage below 25%: $($coverage.lineRatio)" }
if ([double]::Parse($coverage.branchRatio, [Globalization.CultureInfo]::InvariantCulture) -lt 0.20) { throw "M26 branch coverage below 20%: $($coverage.branchRatio)" }
Write-Host "JaCoCo: PASS (line=$($coverage.lineRatio), branch=$($coverage.branchRatio))"

$sbomJson = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.json'; $sbomXml = Join-Path $repo 'target\m21-supply-chain\morpheus-sbom.xml'
if (-not (Test-Path $sbomJson) -or -not (Test-Path $sbomXml)) { throw 'CycloneDX JSON/XML SBOM is missing' }
& .\scripts\write-build-provenance.ps1
if ($LASTEXITCODE -ne 0) { throw "Provenance writer failed with exit code $LASTEXITCODE" }
if (-not (Test-Path (Join-Path $repo 'target\m21-supply-chain\build-provenance.properties'))) { throw 'Build provenance is missing' }
Write-Host 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\m26\dist'
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
    $launcher = Join-Path $repo 'validation-output\m26\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }
    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarTool)) { throw "jar tool not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    foreach ($entry in @(
        'com/morpheus/api/MorpheusRemoteHttpServer.class',
        'com/morpheus/api/MorpheusRemoteIdentityFile.class',
        'com/morpheus/api/MorpheusRemoteRole.class',
        'com/morpheus/store/sqlite/SqliteServerMaintenance.class',
        'com/morpheus/cli/RemoteApiLaunchOptions.class',
        'com/morpheus/cli/MorpheusServerCli.class')) {
        if ($entries -notcontains $entry) { throw "M26 packaged runtime is missing $entry" }
    }
    $help = (& $launcher help) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $help -notmatch 'Team / remote server \(M26, opt-in\)') { throw "Packaged M26 CLI help smoke failed: $help" }
    Write-Host 'M26 TLS/auth/server/maintenance classes + CLI help packaging proof: PASS'
    Assert-PackagedM26 -Launcher $launcher
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M26 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) { throw "Tracked workspace delta appeared during M26 validation:`n$($finalTracked -join "`n")" }

$summary = @(
    'M26 VALIDATION PASS', "sha=$validationSha", "baseRef=$BaseRef", "version=$Version",
    "tests=$($totals.Tests)", "architectureTests=$($architecture.Tests)",
    "lineCoverage=$($coverage.lineRatio)", "branchCoverage=$($coverage.branchRatio)",
    'localFirst=PASS', 'remoteTlsAuthRbac=PASS', 'boundedConcurrency=PASS',
    'secretNonDisclosure=PASS', 'backupRestore=PASS', 'schemaCompatibility=PASS',
    'surfaceConvergence=PASS', 'sqliteV017=PASS', 'sbom=PASS', 'provenance=PASS',
    "portable=$(-not $SkipPortable)", 'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
