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
    [switch]$SmokeMode,

    # none: nothing is signed. test: the run's ephemeral identity. release: the declared publisher's identity. The
    # compiler signs the installer and the uninstaller; the launcher was signed by build-portable.ps1 before the
    # payload below is made from the image.
    [ValidateSet('none', 'test', 'release')][string]$SigningState = 'none'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $repo $OutputDirectory
$portableWork = Join-Path $dist '.m20-windows'
$appImage = Join-Path $portableWork 'image\morpheus'
$iss = Join-Path $PSScriptRoot 'windows\MORPHEUS.iss'
$signingEntryPoint = Join-Path $PSScriptRoot 'windows\sign-windows-binary.ps1'
. (Join-Path $repo 'scripts\lib\MorpheusAuthenticode.ps1')

# The smoke installer exists to drive the wizard against sandboxed client files; it is never a release artifact, so it
# is never signed with the release identity.
if ($SmokeMode -and $SigningState -eq 'release') {
    throw 'WINDOWS_SIGNING_REFUSED: the smoke installer is never signed with the release identity'
}
Write-Host "Windows signing state: $SigningState"

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

    # Keep internal resolution local to this invocation. Publishing the bootstrapped compiler through
    # MORPHEUS_ISCC would turn a trusted internal result into an apparent operator override for the next
    # installer build in the same PowerShell process (for example M28's production + SmokeMode builds).
    # Genuine operator-provided MORPHEUS_ISCC values remain handled fail-closed by ensure-inno-setup.ps1.
    return (Resolve-Path -LiteralPath $path).Path
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
    & (Join-Path $PSScriptRoot 'build-portable.ps1') -Version $Version -OutputDirectory $OutputDirectory -SigningState $SigningState
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
if ($SigningState -ne 'none') {
    # The launcher of the payload is the launcher of the archive: both are made from the image build-portable.ps1
    # signed. A payload made from an unsigned image would install an unsigned launcher from a signed installer.
    $payloadCheck = Join-Path $portableWork 'payload-signature-check'
    Remove-Item -LiteralPath $payloadCheck -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $payloadZip -DestinationPath $payloadCheck -Force
    $payloadExecutables = Get-MorpheusImageExecutables -ImageRoot $payloadCheck
    $unsignedPayload = @($payloadExecutables.Product | Where-Object { (Get-MorpheusSignatureStatement -Path $_)['status'] -ne 'signed' })
    Remove-Item -LiteralPath $payloadCheck -Recurse -Force -ErrorAction SilentlyContinue
    if ($payloadExecutables.Product.Count -eq 0 -or $unsignedPayload.Count -ne 0) {
        throw "WINDOWS_SIGNING_FAILED: the installer payload $payloadZip carries an unsigned executable outside the runtime"
    }

    # The tool handed to the compiler is the entry point and the compiler's file placeholder ($f), quoted by the
    # compiler's own placeholder ($q): no credential, no secret expression, nothing a process listing should not show.
    # The entry point reads the state, and the identity it selects, from the environment this process passes on.
    $env:MORPHEUS_WINDOWS_SIGNING_STATE = $SigningState
    $powershellHost = Join-Path $PSHOME 'powershell.exe'
    $isccArgs += '/DSignBuild=1'
    $isccArgs += ('/Smorpheus=$q' + $powershellHost + '$q -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $q' + $signingEntryPoint + '$q -Path $f')
}
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

if ($SigningState -ne 'none') {
    if ((Get-MorpheusSignatureStatement -Path $setup)['status'] -ne 'signed') {
        throw "WINDOWS_SIGNING_FAILED: the compiler produced an unsigned installer: $setup"
    }
    Write-Host "Windows setup signed ($SigningState): PASS"
}
else {
    Write-Host 'Windows setup: unsigned (signing not requested)'
}

$checksum = Write-And-VerifySha256 -Path $setup
Write-Host "Windows setup: $setup"
Write-Host "Windows setup checksum: $checksum"
