[CmdletBinding()]
param(
    [string]$Version = '1.1.0',
    [string]$BaseRef = 'origin/develop',
    [switch]$SkipPortable,
    [switch]$SkipInstaller
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\r2'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Assert-NativeSuccess([string]$Label) {
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

function Assert-ReactorVersion([string]$ExpectedVersion) {
    $poms = @(Get-ChildItem -Path $repo -Recurse -File -Filter 'pom.xml' |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' })
    # The root POM plus one per declared module. A literal here counted the reactor of the milestone that
    # wrote this check, so every module added afterwards made the gate refuse a correct repository.
    $rootPom = Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw
    $expectedPoms = 1 + ([regex]::Matches($rootPom, '<module>')).Count
    if ($poms.Count -ne $expectedPoms) {
        throw "Unexpected Maven reactor POM count: $($poms.Count), expected $expectedPoms"
    }
    foreach ($pom in $poms) {
        $content = Get-Content -LiteralPath $pom.FullName -Raw
        if (-not $content.Contains("<version>$ExpectedVersion</version>")) {
            throw "MORPHEUS $ExpectedVersion version missing from $($pom.FullName)"
        }
        if ($content.Contains('<version>1.0.0</version>')) {
            throw "Stale MORPHEUS 1.0.0 version remains in $($pom.FullName)"
        }
    }
    Write-Host "Maven reactor version: PASS ($ExpectedVersion across $expectedPoms POMs)"
}

function Assert-PackagedM25M26 {
    $launcher = Join-Path $repo 'validation-output\m27\dist\.m20-windows\image\morpheus\morpheus.exe'
    if (-not (Test-Path -LiteralPath $launcher)) { throw "R2 packaged launcher is missing: $launcher" }

    $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path -LiteralPath $jarTool)) { throw "jar tool not found under JAVA_HOME=$env:JAVA_HOME" }
    $shadedJar = Get-ChildItem (Join-Path $repo 'morpheus-cli\target') -Filter 'morpheus-cli-*-all.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $shadedJar) { throw 'Shaded MORPHEUS CLI JAR not found' }
    $entries = & $jarTool tf $shadedJar.FullName
    Assert-NativeSuccess 'Inspect shaded MORPHEUS JAR'

    foreach ($entry in @(
        'com/morpheus/application/policy/PolicyPackService.class',
        'com/morpheus/application/policy/PolicyEvaluationService.class',
        'com/morpheus/store/sqlite/SqlitePolicyPackStore.class',
        'com/morpheus/cli/MorpheusPolicyCli.class',
        'com/morpheus/mcp/MorpheusPolicyMcpTools.class',
        'com/morpheus/api/MorpheusPolicyApiService.class',
        'com/morpheus/api/MorpheusPolicyHttpRoutes.class',
        'db/migration/V015__policy_packs.sql',
        'com/morpheus/api/MorpheusRemoteHttpServer.class',
        'com/morpheus/api/MorpheusRemoteIdentityFile.class',
        'com/morpheus/api/MorpheusRemoteRole.class',
        'com/morpheus/store/sqlite/SqliteServerMaintenance.class',
        'com/morpheus/cli/RemoteApiLaunchOptions.class',
        'com/morpheus/cli/MorpheusServerCli.class')) {
        if ($entries -notcontains $entry) { throw "R2 packaged runtime is missing $entry" }
    }

    $help = (& $launcher help) -join "`n"
    if ($LASTEXITCODE -ne 0 `
            -or $help -notmatch 'Policy packs / governance automation \(M25\)' `
            -or $help -notmatch 'Team / remote server \(M26, opt-in\)') {
        throw "R2 packaged M25/M26 CLI help smoke failed: $help"
    }
    Write-Host 'Packaged M25 policy + M26 remote classes, migration and CLI surfaces: PASS'
}

Write-Host "R2 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "R2 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

Assert-ReactorVersion $Version

& (Join-Path $PSScriptRoot 'validate-m27.ps1') -Version $Version -BaseRef $BaseRef -SkipPortable:$SkipPortable
Assert-NativeSuccess 'M27 inherited exact-head gate'

$upgradeReport = Join-Path $repo 'morpheus-store-sqlite\target\surefire-reports\TEST-com.morpheus.store.sqlite.R2UpgradeCompatibilityTest.xml'
if (-not (Test-Path -LiteralPath $upgradeReport)) { throw "R2 upgrade report is missing: $upgradeReport" }
[xml]$upgrade = Get-Content -LiteralPath $upgradeReport -Raw
if ([int]$upgrade.testsuite.tests -lt 1 -or [int]$upgrade.testsuite.failures -ne 0 -or [int]$upgrade.testsuite.errors -ne 0) {
    throw "R2 upgrade compatibility test failed: tests=$($upgrade.testsuite.tests) failures=$($upgrade.testsuite.failures) errors=$($upgrade.testsuite.errors)"
}
Write-Host 'SQLite V012 -> V015 upgrade compatibility: PASS'

$packagedM25M26 = 'SKIPPED'
if (-not $SkipPortable) {
    Assert-PackagedM25M26
    $packagedM25M26 = 'PASS'
}

$portableBuilt = -not $SkipPortable
$installerBuilt = $false
if (-not $SkipInstaller) {
    if ($SkipPortable) {
        & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory 'validation-output\r2\dist'
        Assert-NativeSuccess 'R2 Windows portable build'
        $installerOutput = 'validation-output\r2\dist'
        $portableBuilt = $true
    } else {
        $installerOutput = 'validation-output\m27\dist'
    }

    & .\distribution\build-installer.ps1 -Version $Version -OutputDirectory $installerOutput -SkipPortable
    Assert-NativeSuccess 'R2 Windows installer build'
    $setup = Join-Path $repo "$installerOutput\MORPHEUS-$Version-windows-x64-setup.exe"
    $setupHash = $setup + '.sha256'
    if (-not (Test-Path -LiteralPath $setup) -or -not (Test-Path -LiteralPath $setupHash)) {
        throw "R2 setup or checksum is missing under $installerOutput"
    }
    $installerBuilt = $true
    Write-Host "Windows installer + checksum: PASS ($setup)"
}

$rootPom = Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw
if (-not $rootPom.Contains("<version>$Version</version>")) { throw "Root POM is not $Version" }
$windowsRelease = Get-Content -LiteralPath (Join-Path $repo 'distribution\build-release.ps1') -Raw
$linuxRelease = Get-Content -LiteralPath (Join-Path $repo 'distribution\build-release.sh') -Raw
if (-not $windowsRelease.Contains("[string]`$Version = '$Version'")) { throw 'Windows release default version is incoherent' }
if (-not $linuxRelease.Contains('VERSION="${1:-' + $Version + '}"')) { throw 'Linux release default version is incoherent' }
Write-Host 'Release script default versions: PASS'

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during R2 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during R2 validation:`n$($finalTracked -join "`n")"
}

$m27SummaryPath = Join-Path $repo 'validation-output\m27\validation-summary.txt'
if (-not (Test-Path -LiteralPath $m27SummaryPath)) { throw "Inherited M27 summary missing: $m27SummaryPath" }
$m27Summary = @{}
Get-Content -LiteralPath $m27SummaryPath | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $m27Summary[$matches[1]] = $matches[2] }
}

$summary = @(
    'R2 VALIDATION PASS',
    "sha=$validationSha",
    "baseRef=$BaseRef",
    "version=$Version",
    "tests=$($m27Summary.tests)",
    "architectureTests=$($m27Summary.architectureTests)",
    "lineCoverage=$($m27Summary.lineCoverage)",
    "branchCoverage=$($m27Summary.branchCoverage)",
    'reactorVersion=PASS',
    'sqliteV012ToV015Upgrade=PASS',
    'policyPacks=PASS',
    'remoteServer=PASS',
    'assistedReasoning=PASS',
    'surfaceConvergence=PASS',
    "packagedM25M26=$packagedM25M26",
    'sbom=PASS',
    'provenance=PASS',
    "portable=$portableBuilt",
    "installer=$installerBuilt",
    'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }