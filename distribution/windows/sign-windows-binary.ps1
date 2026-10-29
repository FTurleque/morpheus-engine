<#
.SYNOPSIS
Signs one Windows binary of a MORPHEUS build with the identity its caller selected.

.DESCRIPTION
The only caller of a signing tool in the repository. It takes a file path and nothing else: build-portable.ps1 calls it
for the executables of the application image, and the Inno Setup compiler calls it for the installer and the
uninstaller. The signing state and the identity reach it through the process environment, never through an argument,
so a process listing, the compiler's command and a build log hold a script path and a file name only. It prints no
value it reads from the environment.

MORPHEUS_WINDOWS_SIGNING_STATE selects the identity:
  test     an identity generated for one run, found in the current user's personal store by the thumbprint in
           MORPHEUS_WINDOWS_SIGNING_TEST_THUMBPRINT; no timestamp authority is contacted
  release  the declared publisher's identity, timestamped by the authority of
           distribution/windows/authenticode-signing.properties
Any other value, or none, is refused: this script is called only by a build that was asked to sign.

Every failure ends with WINDOWS_SIGNING_FAILED (or WINDOWS_SIGNING_TIMESTAMP_FAILED) and the name of the file, and a
non-zero exit, which the compiler and the packaging script turn into a failed build: an unsigned file never passes.

.EXAMPLE
$env:MORPHEUS_WINDOWS_SIGNING_STATE = 'test'
distribution\windows\sign-windows-binary.ps1 -Path dist\.m20-windows\image\morpheus\morpheus.exe
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$name = Split-Path -Leaf $Path

function Stop-Signing([string]$Reason, [string]$Detail) {
    [Console]::Error.WriteLine("${Reason}: $name -- $Detail")
    exit 1
}

function Assert-SignedBy([string]$File, [string]$Thumbprint) {
    $observed = Get-AuthenticodeSignature -LiteralPath $File
    if ([string]$observed.SignatureType -ne 'Authenticode' -or $null -eq $observed.SignerCertificate) {
        Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the file carries no embedded signature after signing'
    }
    if ($observed.SignerCertificate.Thumbprint -ne $Thumbprint) {
        Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the file is signed by another identity than the one selected'
    }
}

# jpackage writes the launcher read-only. A signature is written into the file, so the attribute is lifted for the
# signature only and set back, whatever happens.
function Invoke-WithWritableFile([string]$File, [scriptblock]$Action) {
    $item = Get-Item -LiteralPath $File
    $readOnly = $item.IsReadOnly
    if ($readOnly) { $item.IsReadOnly = $false }
    try { & $Action } finally { if ($readOnly) { (Get-Item -LiteralPath $File).IsReadOnly = $true } }
}

try {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the file to sign does not exist'
    }
    $file = (Resolve-Path -LiteralPath $Path).Path
    $state = [string]$env:MORPHEUS_WINDOWS_SIGNING_STATE

    switch -CaseSensitive ($state) {
        'test' {
            $thumbprint = [string]$env:MORPHEUS_WINDOWS_SIGNING_TEST_THUMBPRINT
            if ($thumbprint -notmatch '^[0-9A-F]{40}$') {
                Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the test identity is not selected by a certificate thumbprint'
            }
            $certificate = Get-Item -LiteralPath "Cert:\CurrentUser\My\$thumbprint" -ErrorAction SilentlyContinue
            if ($null -eq $certificate -or -not $certificate.HasPrivateKey) {
                Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the test identity is not in the personal certificate store'
            }
            # An untrusted test identity makes Set-AuthenticodeSignature report a chain status, not a failure to
            # sign: whether the file is signed, and by whom, is read back from the file below.
            Invoke-WithWritableFile $file {
                Set-AuthenticodeSignature -LiteralPath $file -Certificate $certificate -HashAlgorithm SHA256 | Out-Null
            }
            Assert-SignedBy $file $thumbprint
            Write-Host "Windows signing (test identity, no timestamp authority contacted): $name"
        }
        'release' {
            $declarationPath = Join-Path $PSScriptRoot 'authenticode-signing.properties'
            if (-not (Test-Path -LiteralPath $declarationPath -PathType Leaf)) {
                Stop-Signing 'WINDOWS_SIGNING_FAILED' 'the signing declaration is missing'
            }
            $declared = @{}
            foreach ($line in Get-Content -LiteralPath $declarationPath) {
                if ($line -match '^\s*(publisherSubject|timestampAuthority)\s*=(.*)$') { $declared[$Matches[1]] = $Matches[2].Trim() }
            }
            if (-not $declared.ContainsKey('publisherSubject') -or [string]::IsNullOrEmpty($declared['publisherSubject'])) {
                Stop-Signing 'WINDOWS_SIGNING_FAILED' 'no publisher is declared for the release identity'
            }
            if (-not $declared.ContainsKey('timestampAuthority') -or [string]::IsNullOrEmpty($declared['timestampAuthority'])) {
                Stop-Signing 'WINDOWS_SIGNING_TIMESTAMP_FAILED' 'no timestamp authority is declared for the release identity'
            }
            # The provider part -- which tool, which endpoint, which credential variable -- is filled when the
            # certificate exists and its provider is chosen. Until then a release signature cannot be produced, and a
            # build that asks for one fails here instead of publishing an unsigned binary.
            Stop-Signing 'WINDOWS_SIGNING_FAILED' 'no release signing provider is configured'
        }
        default {
            Stop-Signing 'WINDOWS_SIGNING_FAILED' 'unknown signing state; expected test or release'
        }
    }
    exit 0
}
catch {
    [Console]::Error.WriteLine("WINDOWS_SIGNING_FAILED: $name -- $($_.Exception.Message)")
    exit 1
}
