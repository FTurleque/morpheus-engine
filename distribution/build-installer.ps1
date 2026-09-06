[CmdletBinding()]
param(
    [string]$Version = '1.2.1',
    [string]$OutputDirectory = 'dist',
    [switch]$SkipPortable,

    # Builds a distinctly-AppId'd, distinctly-named "MORPHEUS Setup Smoke" installer from the SAME app-image.
    # Only this build ever contains the MORPHEUS_SMOKE_* environment-variable override mechanism in
    # MORPHEUS.iss; the production build (no -SmokeMode) compiles that branch out entirely (see MORPHEUS.iss,
    # #ifdef SmokeMode). Exists purely so scripts/verify-windows-setup-mcp-smoke.ps1 can drive the real wizard
    # code path against sandboxed client configuration files instead of the real ones.
    [switch]$SmokeMode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $repo $OutputDirectory
$portableWork = Join-Path $dist '.m20-windows'
$appImage = Join-Path $portableWork 'image\morpheus'
$iss = Join-Path $PSScriptRoot 'windows\MORPHEUS.iss'

function Resolve-Iscc {
    # All discovery and trust validation live in one place. In particular, this caller must never bypass the
    # version/signature checks merely because ISCC.exe happens to be installed or present on PATH.
    $bootstrap = Join-Path $PSScriptRoot 'ensure-inno-setup.ps1'
    if (-not (Test-Path -LiteralPath $bootstrap -PathType Leaf)) {
        throw "Inno Setup trust/bootstrap script was not found: $bootstrap"
    }
    $resolved = @(& $bootstrap)
    if ($LASTEXITCODE -ne 0 -or $resolved.Count -eq 0) {
        throw 'Inno Setup trust/bootstrap resolution failed'
    }
    $path = [string]$resolved[-1]
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Inno Setup trust/bootstrap returned an invalid compiler path: $path"
    }
    $env:MORPHEUS_ISCC = (Resolve-Path -LiteralPath $path).Path
    return $env:MORPHEUS_ISCC
}

function Write-And-VerifySha256([string]$Path) {
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    $hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumPath = $item.FullName + '.sha256'
    "$hash  $($item.Name)" | Set-Content -LiteralPath $checksumPath -Encoding ascii -NoNewline

    $recorded = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($recorded -ne $actual) {
        throw "SHA-256 verification failed for $($item.FullName): recorded=$recorded actual=$actual"
    }
    Write-Verbose "SHA-256: PASS ($($item.Name) -> $checksumPath)"
    return $checksumPath
}

if (-not $SkipPortable) {
    & (Join-Path $PSScriptRoot 'build-portable.ps1') -Version $Version -OutputDirectory $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw "Portable Windows build failed with exit code $LASTEXITCODE" }
}

if (-not (Test-Path -LiteralPath (Join-Path $appImage 'morpheus.exe'))) {
    throw "Windows app-image is missing: $appImage"
}
if (-not (Test-Path -LiteralPath $iss)) {
    throw "Inno Setup definition is missing: $iss"
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null

# The setup's [Files] section carries no direct copy of the app-image: PrepareToInstall's transactional
# engine (distribution/windows/update-installation.ps1) stages and activates this zip instead, so it must be
# rebuilt from the current app-image on every run rather than reused from a previous build.
$payloadZip = Join-Path $dist 'morpheus-payload.zip'
if (Test-Path -LiteralPath $payloadZip) { Remove-Item -LiteralPath $payloadZip -Force }
Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $payloadZip -CompressionLevel Optimal
$updateInstallationScript = Join-Path $PSScriptRoot 'windows\update-installation.ps1'
if (-not (Test-Path -LiteralPath $updateInstallationScript -PathType Leaf)) {
    throw "Transactional installation engine is missing: $updateInstallationScript"
}

$iscc = Resolve-Iscc
$isccArgs = @(
    "/DMyAppVersion=$Version",
    "/DSourceDir=$appImage",
    "/DOutputDir=$dist",
    "/DPayloadZip=$payloadZip",
    "/DUpdateInstallationScript=$updateInstallationScript"
)
$outputSuffix = 'setup'
if ($SmokeMode) {
    $isccArgs += '/DSmokeMode=1'
    $outputSuffix = 'setup-smoke'
    Write-Host "Building MORPHEUS $Version per-user Windows SMOKE setup (distinct AppId, never production) with $iscc"
}
else {
    Write-Host "Building MORPHEUS $Version per-user Windows setup with $iscc"
}

& $iscc @isccArgs $iss
if ($LASTEXITCODE -ne 0) { throw "Inno Setup build failed with exit code $LASTEXITCODE" }

$setup = Join-Path $dist "MORPHEUS-$Version-windows-x64-$outputSuffix.exe"
if (-not (Test-Path -LiteralPath $setup)) {
    throw "Windows setup was not produced: $setup"
}

$checksum = Write-And-VerifySha256 -Path $setup
Write-Host "Windows setup: $setup"
Write-Host "Windows setup checksum: $checksum"
