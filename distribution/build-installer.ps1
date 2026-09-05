[CmdletBinding()]
param(
    [string]$Version = '1.2.1',
    [string]$OutputDirectory = 'dist',
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $repo $OutputDirectory
$portableWork = Join-Path $dist '.m20-windows'
$appImage = Join-Path $portableWork 'image\morpheus'
$iss = Join-Path $PSScriptRoot 'windows\MORPHEUS.iss'

function Resolve-Iscc {
    if ($env:MORPHEUS_ISCC -and (Test-Path -LiteralPath $env:MORPHEUS_ISCC)) {
        return (Resolve-Path -LiteralPath $env:MORPHEUS_ISCC).Path
    }

    $roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }
    $candidates = foreach ($root in $roots) {
        foreach ($major in 7, 6) {
            $candidate = Join-Path $root "Inno Setup $major\ISCC.exe"
            if (Test-Path -LiteralPath $candidate) { $candidate }
        }
    }

    if (@($candidates).Count -gt 0) {
        return (Resolve-Path -LiteralPath @($candidates)[0]).Path
    }

    $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $bootstrap = Join-Path $PSScriptRoot 'ensure-inno-setup.ps1'
    if (-not (Test-Path -LiteralPath $bootstrap)) {
        throw "Inno Setup compiler is missing and bootstrap script was not found: $bootstrap"
    }
    Write-Verbose 'ISCC.exe not found locally; bootstrapping pinned, Authenticode-verified Inno Setup 7.0.2...'
    $resolved = @(& $bootstrap)
    if ($LASTEXITCODE -ne 0 -or $resolved.Count -eq 0) {
        throw 'Inno Setup bootstrap failed'
    }
    $path = [string]$resolved[-1]
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Inno Setup bootstrap returned an invalid compiler path: $path"
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
Write-Host "Building MORPHEUS $Version per-user Windows setup with $iscc"

& $iscc `
    "/DMyAppVersion=$Version" `
    "/DSourceDir=$appImage" `
    "/DOutputDir=$dist" `
    "/DPayloadZip=$payloadZip" `
    "/DUpdateInstallationScript=$updateInstallationScript" `
    $iss
if ($LASTEXITCODE -ne 0) { throw "Inno Setup build failed with exit code $LASTEXITCODE" }

$setup = Join-Path $dist "MORPHEUS-$Version-windows-x64-setup.exe"
if (-not (Test-Path -LiteralPath $setup)) {
    throw "Windows setup was not produced: $setup"
}

$checksum = Write-And-VerifySha256 -Path $setup
Write-Host "Windows setup: $setup"
Write-Host "Windows setup checksum: $checksum"