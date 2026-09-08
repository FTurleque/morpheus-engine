<#
.SYNOPSIS
Verifies a PUBLISHED MORPHEUS GitHub release end to end.

.DESCRIPTION
This script proves nothing about a release that does not exist: it reads what GitHub actually published
and fails closed on anything it cannot confirm. It never creates a tag, a release or an asset, so running
it can never be mistaken for having qualified one.

.EXAMPLE
scripts\verify-release-provenance.ps1 -Tag v1.2.1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [string]$WorkDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$message) { throw "RELEASE QUALIFICATION FAIL: $message" }
function Pass([string]$check) { Write-Host "${check}: PASS" }

function Assert-NativeSuccess([string]$what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what (exit $LASTEXITCODE)" }
}

if ($Tag -notmatch '^v(?<version>[0-9]+\.[0-9]+\.[0-9]+)$') {
    Fail "Release tag must use semantic version form vX.Y.Z: $Tag"
}
$version = $Matches.version
$repo = Split-Path -Parent $PSScriptRoot

foreach ($tool in @('gh', 'git')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "required tool not found: $tool" }
}

$temporary = $false
if (-not $WorkDirectory) {
    $WorkDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-release-" + [guid]::NewGuid().ToString('n'))
    $temporary = $true
}
New-Item -ItemType Directory -Force -Path $WorkDirectory | Out-Null
$assets = Join-Path $WorkDirectory 'assets'

Write-Host 'MORPHEUS release qualification'
Write-Host "Tag:     $Tag"
Write-Host "Version: $version"
Write-Host "Workdir: $WorkDirectory"
Write-Host ''

try {
    # -----------------------------------------------------------------------
    # 1. The tag resolves to one commit, and that commit is reachable from main.
    # -----------------------------------------------------------------------
    & git -C $repo fetch --no-tags origin main | Out-Null
    Assert-NativeSuccess 'fetch origin/main'
    & git -C $repo fetch origin "refs/tags/${Tag}:refs/tags/${Tag}" | Out-Null
    Assert-NativeSuccess "fetch tag $Tag from origin"
    $tagSha = (& git -C $repo rev-list -n 1 $Tag).Trim()
    Assert-NativeSuccess "resolve tag $Tag"
    & git -C $repo merge-base --is-ancestor $tagSha 'origin/main'
    if ($LASTEXITCODE -ne 0) { Fail "tag $Tag points at $tagSha which is not reachable from main" }
    Pass "tag reachable from main ($tagSha)"

    # -----------------------------------------------------------------------
    # 2. The published release carries exactly the expected asset set.
    # -----------------------------------------------------------------------
    $expected = @(
        "morpheus-$version-linux-x64.tar.gz",
        "morpheus-$version-linux-x64.tar.gz.sha256",
        "morpheus-$version-linux-x64-release-manifest.json",
        "morpheus-$version-linux-x64.attestation.jsonl",
        "morpheus-$version-windows-x64.zip",
        "morpheus-$version-windows-x64.zip.sha256",
        "MORPHEUS-$version-windows-x64-setup.exe",
        "MORPHEUS-$version-windows-x64-setup.exe.sha256",
        "morpheus-$version-windows-x64-release-manifest.json",
        "morpheus-$version-windows-x64.attestation.jsonl"
    )

    $releaseJson = & gh release view $Tag --json tagName,assets
    Assert-NativeSuccess "read published release $Tag"
    $release = $releaseJson | ConvertFrom-Json
    if ($release.tagName -ne $Tag) { Fail "release tagName $($release.tagName) does not match $Tag" }

    $published = @($release.assets | ForEach-Object { $_.name })
    $missing = @($expected | Where-Object { $published -notcontains $_ })
    $unexpected = @($published | Where-Object { $expected -notcontains $_ })
    if ($missing.Count -ne 0) { Fail "release is missing expected assets: $($missing -join ', ')" }
    if ($unexpected.Count -ne 0) { Fail "release publishes unexpected assets: $($unexpected -join ', ')" }
    Pass "published asset set ($($published.Count) assets)"

    # -----------------------------------------------------------------------
    # 3. Every checksum published beside an artifact matches that artifact.
    # -----------------------------------------------------------------------
    & gh release download $Tag --dir $assets --clobber | Out-Null
    Assert-NativeSuccess 'download release assets'

    $checked = 0
    foreach ($checksum in Get-ChildItem -LiteralPath $assets -Filter '*.sha256') {
        $artifact = $checksum.FullName.Substring(0, $checksum.FullName.Length - '.sha256'.Length)
        if (-not (Test-Path -LiteralPath $artifact)) { Fail "checksum $($checksum.Name) has no artifact beside it" }
        $publishedDigest = ((Get-Content -LiteralPath $checksum.FullName -Raw).Trim() -split '\s+')[0]
        $actualDigest = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($publishedDigest.ToLowerInvariant() -ne $actualDigest) {
            Fail "checksum mismatch for $(Split-Path -Leaf $artifact): published=$publishedDigest actual=$actualDigest"
        }
        $checked++
    }
    if ($checked -lt 3) { Fail "expected at least three published checksums, verified $checked" }
    Pass "published checksums ($checked verified)"

    # -----------------------------------------------------------------------
    # 4. Each release manifest agrees with the tag, the version and the tagged commit.
    # -----------------------------------------------------------------------
    $manifests = @(Get-ChildItem -LiteralPath $assets -Filter 'morpheus-*-release-manifest.json')
    if ($manifests.Count -ne 2) { Fail "expected one release manifest per platform, found $($manifests.Count)" }
    $platforms = @()
    foreach ($manifest in $manifests) {
        $payload = Get-Content -LiteralPath $manifest.FullName -Raw | ConvertFrom-Json
        foreach ($pair in @(
                @{ Field = 'version'; Expected = $version },
                @{ Field = 'tag'; Expected = $Tag },
                @{ Field = 'gitSha'; Expected = $tagSha },
                @{ Field = 'product'; Expected = 'MORPHEUS' })) {
            $actual = $payload.($pair.Field)
            if ($actual -ne $pair.Expected) {
                Fail "$($manifest.Name): $($pair.Field)=$actual expected $($pair.Expected)"
            }
        }
        $platforms += $payload.platform
        foreach ($asset in $payload.assets) {
            $artifact = Join-Path $assets $asset.name
            if (-not (Test-Path -LiteralPath $artifact)) {
                Fail "$($manifest.Name) names an asset that was not published: $($asset.name)"
            }
            if ((Get-Item -LiteralPath $artifact).Length -ne $asset.bytes) {
                Fail "$($manifest.Name): $($asset.name) size does not match the manifest"
            }
        }
    }
    if (($platforms | Sort-Object) -join ',' -ne 'linux-x64,windows-x64') {
        Fail "release manifests must cover both platforms, found $($platforms -join ', ')"
    }
    Pass 'release manifests'

    # -----------------------------------------------------------------------
    # 5. Provenance is publicly verifiable for every distributed artifact.
    # -----------------------------------------------------------------------
    $slug = (& gh repo view --json nameWithOwner --jq .nameWithOwner).Trim()
    Assert-NativeSuccess 'resolve repository slug'
    $attested = 0
    foreach ($name in @(
            "morpheus-$version-linux-x64.tar.gz",
            "morpheus-$version-windows-x64.zip",
            "MORPHEUS-$version-windows-x64-setup.exe")) {
        & gh attestation verify (Join-Path $assets $name) --repo $slug | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "provenance attestation did not verify for $name" }
        $attested++
    }
    if ($attested -ne 3) { Fail "expected three attested artifacts, verified $attested" }
    Pass "public provenance ($attested artifacts)"

    # -----------------------------------------------------------------------
    # 6. The preserved attestation bundles are present and non-empty.
    # -----------------------------------------------------------------------
    foreach ($name in @(
            "morpheus-$version-linux-x64.attestation.jsonl",
            "morpheus-$version-windows-x64.attestation.jsonl")) {
        $bundle = Join-Path $assets $name
        if (-not (Test-Path -LiteralPath $bundle) -or (Get-Item -LiteralPath $bundle).Length -eq 0) {
            Fail "attestation bundle is empty: $name"
        }
    }
    Pass 'preserved attestation bundles'

    Write-Host ''
    Write-Host "RELEASE QUALIFICATION PASS: $Tag @ $tagSha"
    Write-Host 'Record this output in docs/validation/VALIDATION_RELEASE_<version>.md before closing issue #185.'
}
finally {
    if ($temporary -and (Test-Path -LiteralPath $WorkDirectory)) {
        Remove-Item -LiteralPath $WorkDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}
