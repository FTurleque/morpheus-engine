[CmdletBinding()]
param(
    [string]$Version = '1.0.0',
    [string]$OutputDirectory = 'dist',
    [switch]$SkipPortable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $repo $OutputDirectory
$portableWork = Join-Path $dist '.m19-windows'
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

    throw @'
Inno Setup compiler (ISCC.exe, version 7 or 6) was not found.
Install the build dependency with:
  winget install --id JRSoftware.InnoSetup -e
or set MORPHEUS_ISCC to the full path of ISCC.exe.
This dependency is required only to build the Windows setup; MORPHEUS end users do not need it.
'@
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
    Write-Host "SHA-256: PASS ($($item.Name) -> $checksumPath)"
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
$iscc = Resolve-Iscc
Write-Host "Building MORPHEUS $Version per-user Windows setup with $iscc"

& $iscc `
    "/DMyAppVersion=$Version" `
    "/DSourceDir=$appImage" `
    "/DOutputDir=$dist" `
    $iss
if ($LASTEXITCODE -ne 0) { throw "Inno Setup build failed with exit code $LASTEXITCODE" }

$setup = Join-Path $dist "MORPHEUS-$Version-windows-x64-setup.exe"
if (-not (Test-Path -LiteralPath $setup)) {
    throw "Windows setup was not produced: $setup"
}

$checksum = Write-And-VerifySha256 -Path $setup
Write-Host "Windows setup: $setup"
Write-Host "Windows setup checksum: $checksum"
