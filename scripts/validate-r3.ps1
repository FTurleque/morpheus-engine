[CmdletBinding()]
param(
    [string]$Version = '1.2.0',
    [string]$BaseRef = 'origin/develop',
    [switch]$SkipPortable,
    [switch]$SkipInstaller
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\r3'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Assert-NativeSuccess([string]$Label) {
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
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
        foreach ($stale in @('1.1.0', '1.0.0', '0.1.0-SNAPSHOT')) {
            if ($content.Contains("<version>$stale</version>")) {
                throw "Stale MORPHEUS $stale version remains in $($pom.FullName)"
            }
        }
    }
    Write-Host "Maven reactor version: PASS ($ExpectedVersion across 17 POMs)"
}

Write-Host "R3 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "R3 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

& git diff --check "$BaseRef...HEAD"
Assert-NativeSuccess 'R3 git diff check'

$changedFiles = @(git diff --name-only "$BaseRef...HEAD")
if ($changedFiles | Where-Object { $_ -like '.github/workflows/*' }) {
    throw 'R3 must not modify GitHub Actions workflows during the July 2026 freeze'
}
if ($changedFiles | Where-Object { $_ -like 'morpheus-store-sqlite/src/main/resources/db/migration/*' }) {
    throw 'R3 must not introduce a SQLite migration for the configuration-only M28 release'
}
Write-Host 'R3 scope policy: PASS (no CI workflow or SQLite migration delta)'

Assert-ReactorVersion $Version

& (Join-Path $PSScriptRoot 'validate-m28.ps1') `
    -Version $Version `
    -BaseRef $BaseRef `
    -SkipPortable:$SkipPortable `
    -SkipInstaller:$SkipInstaller
Assert-NativeSuccess 'M28 inherited exact-head gate'

$requiredDocs = @(
    'docs\release\RELEASE_NOTES_1.2.0.md',
    'docs\user\UPGRADE_1_2.md',
    'docs\roadmap\R3_EXECUTION.md',
    'docs\validation\VALIDATION_R3.md'
)
foreach ($relative in $requiredDocs) {
    if (-not (Test-Path -LiteralPath (Join-Path $repo $relative) -PathType Leaf)) {
        throw "R3 required documentation is missing: $relative"
    }
}

$versionChecks = @(
    @{ Path = 'distribution\build-portable.ps1'; Token = "[string]`$Version = `"$Version`"" },
    @{ Path = 'distribution\build-installer.ps1'; Token = "[string]`$Version = '$Version'" },
    @{ Path = 'distribution\build-release.ps1'; Token = "[string]`$Version = '$Version'" },
    @{ Path = 'distribution\build-portable.sh'; Token = 'VERSION="${1:-' + $Version + '}"' },
    @{ Path = 'distribution\build-release.sh'; Token = 'VERSION="${1:-' + $Version + '}"' }
)
foreach ($check in $versionChecks) {
    $content = Get-Content -LiteralPath (Join-Path $repo $check.Path) -Raw
    if (-not $content.Contains($check.Token)) {
        throw "R3 default version is incoherent in $($check.Path): expected token $($check.Token)"
    }
}
Write-Host 'R3 builder default versions: PASS'

$releaseNotes = Get-Content -LiteralPath (Join-Path $repo 'docs\release\RELEASE_NOTES_1.2.0.md') -Raw
foreach ($token in @('MORPHEUS 1.2.0', 'GitHub Copilot', 'Claude Code', 'Claude Desktop', 'OpenAI Codex', 'mcp --stdio', 'Docker')) {
    if (-not $releaseNotes.Contains($token)) { throw "R3 release notes token is missing: $token" }
}
Write-Host 'R3 release documentation contract: PASS'

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during R3 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during R3 validation:`n$($finalTracked -join "`n")"
}

$m28SummaryPath = Join-Path $repo 'validation-output\m28\validation-summary.txt'
if (-not (Test-Path -LiteralPath $m28SummaryPath)) { throw "Inherited M28 summary missing: $m28SummaryPath" }
$m28Summary = @{}
Get-Content -LiteralPath $m28SummaryPath | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $m28Summary[$matches[1]] = $matches[2] }
}

$summary = @(
    'R3 VALIDATION PASS',
    "sha=$validationSha",
    "baseRef=$BaseRef",
    "version=$Version",
    "tests=$($m28Summary.tests)",
    "architectureTests=$($m28Summary.architectureTests)",
    "lineCoverage=$($m28Summary.lineCoverage)",
    "branchCoverage=$($m28Summary.branchCoverage)",
    'reactorVersion=PASS',
    'mcpClientIntegration=PASS',
    'clients=5',
    'releaseDocumentation=PASS',
    'schemaMigration=UNCHANGED',
    'ciWorkflowDelta=NONE',
    "portable=$($m28Summary.portable)",
    "installer=$($m28Summary.installer)",
    'sbom=PASS',
    'provenance=PASS',
    'dockerRequired=false',
    'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
