[CmdletBinding()]
param(
    [string]$Version = '1.2.0',
    [string]$ExpectedTag,
    [string]$OutputDirectory = 'dist'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
if ([string]::IsNullOrWhiteSpace($ExpectedTag)) { $ExpectedTag = "v$Version" }
$dist = Join-Path $repo $OutputDirectory

function Assert-CleanTaggedHead {
    $status = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Git workspace' }
    if ($status.Count -gt 0) { throw 'Release build requires a clean Git workspace' }

    $head = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve Git HEAD' }
    $tagSha = (git rev-list -n 1 $ExpectedTag 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($tagSha)) {
        throw "Release build requires tag '$ExpectedTag' to exist"
    }
    if ($tagSha -ne $head) {
        throw "Release tag '$ExpectedTag' points to $tagSha, but HEAD is $head"
    }
    return $head
}

function Write-And-VerifySha256([string]$Path) {
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    $hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumPath = $item.FullName + '.sha256'
    "$hash  $($item.Name)" | Set-Content -LiteralPath $checksumPath -Encoding ascii -NoNewline
    $recorded = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($recorded -ne $actual) { throw "SHA-256 verification failed for $($item.FullName)" }
    [pscustomobject]@{
        name = $item.Name
        bytes = $item.Length
        sha256 = $actual
        checksum = (Split-Path -Leaf $checksumPath)
    }
}

$head = Assert-CleanTaggedHead
New-Item -ItemType Directory -Force -Path $dist | Out-Null

& (Join-Path $PSScriptRoot 'build-portable.ps1') -Version $Version -OutputDirectory $OutputDirectory
if ($LASTEXITCODE -ne 0) { throw "Windows portable build failed with exit code $LASTEXITCODE" }

$portable = Join-Path $dist "morpheus-$Version-windows-x64.zip"
if (-not (Test-Path -LiteralPath $portable)) { throw "Portable ZIP missing: $portable" }
$portableAsset = Write-And-VerifySha256 -Path $portable

& (Join-Path $PSScriptRoot 'build-installer.ps1') -Version $Version -OutputDirectory $OutputDirectory -SkipPortable
if ($LASTEXITCODE -ne 0) { throw "Windows setup build failed with exit code $LASTEXITCODE" }

$setup = Join-Path $dist "MORPHEUS-$Version-windows-x64-setup.exe"
if (-not (Test-Path -LiteralPath $setup)) { throw "Setup missing: $setup" }
$setupAsset = Write-And-VerifySha256 -Path $setup

$manifest = [ordered]@{
    schemaVersion = 1
    product = 'MORPHEUS'
    version = $Version
    tag = $ExpectedTag
    gitSha = $head
    platform = 'windows-x64'
    runtimeEmbedded = $true
    userJdkRequired = $false
    defaultInstallPath = '%LOCALAPPDATA%\Programs\MORPHEUS'
    persistentStateRoot = '%LOCALAPPDATA%\MORPHEUS'
    uninstallPreservesPersistentState = $true
    assets = @($setupAsset, $portableAsset)
}
$manifestPath = Join-Path $dist "morpheus-$Version-windows-x64-release-manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8

Write-Host "Tagged Windows release build: PASS"
Write-Host "Tag:      $ExpectedTag"
Write-Host "Git SHA:  $head"
Write-Host "Manifest: $manifestPath"
$manifest.assets | ForEach-Object { Write-Host ("Asset: {0} bytes={1} sha256={2}" -f $_.name, $_.bytes, $_.sha256) }
