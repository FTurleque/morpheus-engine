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
$outputRoot = Join-Path $repo 'validation-output\m28'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$validationSha = (git rev-parse HEAD).Trim()

function Assert-NativeSuccess([string]$Label) {
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

Write-Host "M28 exact-head validation SHA: $validationSha"
$initialTracked = @(git status --porcelain --untracked-files=no)
if ($initialTracked.Count -ne 0) {
    throw "M28 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n")"
}

& (Join-Path $PSScriptRoot 'validate-r2.ps1') -Version $Version -BaseRef $BaseRef -SkipPortable -SkipInstaller
Assert-NativeSuccess 'R2 inherited exact-head gate'

& (Join-Path $PSScriptRoot 'verify-m28-mcp-client-integration.ps1')
Assert-NativeSuccess 'M28 MCP client integration verification'

$manager = Join-Path $repo 'integration\configure-mcp-clients.ps1'
$setupWrapper = Join-Path $repo 'integration\configure-mcp-clients-setup.ps1'
$installer = Join-Path $repo 'distribution\windows\MORPHEUS.iss'
foreach ($required in @($manager, $setupWrapper, $installer)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "M28 required file is missing: $required" }
}

$managerText = Get-Content -LiteralPath $manager -Raw
foreach ($token in @(
    'CopilotJetBrains', 'CopilotCli', 'ClaudeCode', 'ClaudeDesktop', 'Codex',
    "args = @('mcp', '--stdio')", 'MORPHEUS_DATA_DIR', 'MORPHEUS_CONFIG_DIR',
    'existing-unmanaged-morpheus-entry', 'managed-entry-modified', 'Uninstall is state-driven')) {
    if (-not $managerText.Contains($token)) { throw "M28 manager contract token is missing: $token" }
}
if ($managerText -match '(?i)docker') { throw 'M28 native MCP client manager must not require Docker' }
Write-Host 'M28 static integration contract: PASS'

$portableBuilt = $false
$installerBuilt = $false
$dist = 'validation-output\m28\dist'
if (-not $SkipPortable) {
    & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory $dist
    Assert-NativeSuccess 'M28 Windows portable build'
    $packagedRoot = Join-Path $repo "$dist\.m20-windows\image\morpheus"
    foreach ($relative in @(
        'integration\configure-mcp-clients.ps1',
        'integration\configure-mcp-clients-setup.ps1',
        'integration\README.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $packagedRoot $relative) -PathType Leaf)) {
            throw "Packaged M28 integration file is missing: $relative"
        }
    }
    $portableBuilt = $true
    Write-Host 'M28 Windows portable integration payload: PASS'
}

if (-not $SkipInstaller) {
    if ($SkipPortable) {
        & .\distribution\build-portable.ps1 -Version $Version -OutputDirectory $dist
        Assert-NativeSuccess 'M28 Windows portable prerequisite'
        $portableBuilt = $true
    }
    & .\distribution\build-installer.ps1 -Version $Version -OutputDirectory $dist -SkipPortable
    Assert-NativeSuccess 'M28 Windows installer build'
    $setup = Join-Path $repo "$dist\MORPHEUS-$Version-windows-x64-setup.exe"
    $checksum = $setup + '.sha256'
    if (-not (Test-Path -LiteralPath $setup) -or -not (Test-Path -LiteralPath $checksum)) {
        throw 'M28 Windows setup or checksum is missing'
    }
    $installerBuilt = $true
    Write-Host "M28 Windows setup integration wiring: PASS ($setup)"
}

$currentSha = (git rev-parse HEAD).Trim()
if ($currentSha -ne $validationSha) { throw "HEAD changed during M28 validation: $validationSha -> $currentSha" }
$finalTracked = @(git status --porcelain --untracked-files=no)
if ($finalTracked.Count -ne 0) {
    throw "Tracked workspace delta appeared during M28 validation:`n$($finalTracked -join "`n")"
}

$r2SummaryPath = Join-Path $repo 'validation-output\r2\validation-summary.txt'
if (-not (Test-Path -LiteralPath $r2SummaryPath)) { throw "Inherited R2 summary missing: $r2SummaryPath" }
$r2Summary = @{}
Get-Content -LiteralPath $r2SummaryPath | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $r2Summary[$matches[1]] = $matches[2] }
}

$summary = @(
    'M28 VALIDATION PASS',
    "sha=$validationSha",
    "baseRef=$BaseRef",
    "version=$Version",
    "tests=$($r2Summary.tests)",
    "architectureTests=$($r2Summary.architectureTests)",
    "lineCoverage=$($r2Summary.lineCoverage)",
    "branchCoverage=$($r2Summary.branchCoverage)",
    'mcpClientManager=PASS',
    'clients=5',
    'jsonMerge=PASS',
    'cliRegistration=PASS',
    'idempotency=PASS',
    'foreignEntryPreservation=PASS',
    'modifiedEntryPreservation=PASS',
    'stateDrivenUninstall=PASS',
    'invalidJsonProtection=PASS',
    "portable=$portableBuilt",
    "installer=$installerBuilt",
    'dockerRequired=false',
    'postGateExecutableDelta=NONE')
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'validation-summary.txt')
$summary | ForEach-Object { Write-Host $_ }
